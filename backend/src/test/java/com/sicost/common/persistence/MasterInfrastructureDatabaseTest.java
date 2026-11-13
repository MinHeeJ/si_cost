package com.sicost.common.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.concurrent.MasterLockMapper;
import com.sicost.common.context.CurrentUserMapper;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import java.math.BigInteger;
import java.time.Clock;
import java.time.ZoneId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class MasterInfrastructureDatabaseTest {
    PostgreSqlDatabase db;
    SqlSessionTemplate session;
    TransactionTemplate transaction;
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();
        String schema=db.sql.queryForObject("SELECT current_schema()",String.class);
        Flyway.configure().dataSource(db.source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("2").load().migrate();
        db.sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES('C-099','CUST','검증 거래처','123-45-67890','admin')");
        db.sql.update("INSERT INTO tb_bs_part(part_id,part_nm,reg_user_id) VALUES('PART-120','검증 파트','admin')");
        db.install();
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        session=new SqlSessionTemplate(factory.getObject());
        transaction=new TransactionTemplate(new DataSourceTransactionManager(db.source));
    }
    @Test void migrationAllocatesAboveExistingMaximumWithoutRestartResetAndAllowsRollbackGaps() {
        var ids=session.getMapper(MasterIdMapper.class);
        assertThat(ids.nextClient()).isEqualTo(new BigInteger("100"));
        assertThat(ids.nextPart()).isEqualTo(new BigInteger("121"));
        transaction.executeWithoutResult(status->{assertThat(ids.nextClient()).isEqualTo(new BigInteger("101"));status.setRollbackOnly();});
        db.install();
        assertThat(ids.nextClient()).isEqualTo(new BigInteger("102"));
        assertThat(db.sql.queryForObject("SELECT cycle FROM pg_sequences WHERE schemaname=current_schema() AND sequencename='sq_tb_bs_clnt_id'",Boolean.class)).isFalse();
    }
    @Test void selectedEngineSequenceExhaustionBecomes409() {
        long before = db.count("tb_bs_part");
        db.sql.execute("SELECT setval('sq_tb_bs_part_id',999999999999999,true)");
        var ids=new MasterIdService(session.getMapper(MasterIdMapper.class));
        assertThatThrownBy(()->transaction.execute(status->ids.nextPartId())).isInstanceOfSatisfying(com.sicost.common.api.ApiException.class,e->assertThat(e.code()).isEqualTo("ID_EXHAUSTED"));
        assertThat(db.count("tb_bs_part")).isEqualTo(before);
    }
    @Test void newHistoryUsesRealPluginAndRollsBackWithBusinessChangeOnFailure() {
        var history=new ChangeHistoryWriter(session.getMapper(ChangeHistoryMapper.class),new CurrentUserProvider(session.getMapper(CurrentUserMapper.class)),
                Clock.system(ZoneId.of("Asia/Seoul")),JsonMapper.builder().findAndAddModules().build());
        transaction.executeWithoutResult(status->{
            var locks=session.getMapper(MasterLockMapper.class);locks.lockClose();locks.lockSettlement();
            history.append(new ChangeHistoryWriter.Change("TB_BS_PART","PART-120","NEW","등록",null,"PART-120 검증 파트",null));
        });
        assertThat(db.count("tb_sy_chg_hist")).isEqualTo(1);
        db.sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_master_history CHECK (chg_item_nm <> '실패')");
        assertThatThrownBy(()->transaction.executeWithoutResult(status->{
            db.sql.update("UPDATE tb_bs_part SET part_nm='변경' WHERE part_id='PART-120'");
            history.append(new ChangeHistoryWriter.Change("TB_BS_PART","PART-120","UPD","실패","이전","이후",null));
        })).isInstanceOf(RuntimeException.class);
        assertThat(db.sql.queryForObject("SELECT part_nm FROM tb_bs_part WHERE part_id='PART-120'",String.class)).isEqualTo("검증 파트");
        assertThat(db.count("tb_sy_chg_hist")).isEqualTo(1);
    }
}
