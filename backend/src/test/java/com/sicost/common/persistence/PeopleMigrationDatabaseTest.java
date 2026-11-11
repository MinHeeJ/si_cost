package com.sicost.common.persistence;

import com.sicost.common.installation.PostgreSqlDatabase;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class PeopleMigrationDatabaseTest {
    PostgreSqlDatabase db;
    SqlSessionTemplate session;
    TransactionTemplate transaction;
    List<Map<String,Object>> applied;
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();
        String schema=db.sql.queryForObject("SELECT current_schema()",String.class);
        Flyway.configure().dataSource(db.source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("3").load().migrate();
        applied=db.sql.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        db.sql.update("INSERT INTO tb_sy_adm_area(area_cd,area_lvl_cd,sido_nm,sido_full_nm,use_yn,reg_user_id) VALUES('1100000000','SIDO','기존명','기존 전체명','N','admin')");
        db.sql.update("INSERT INTO tb_sy_adm_hdong(hdong_cd,area_cd,hdong_nm,reg_user_id) VALUES('1111051500','1111010100','기존 행정동명','admin')");
        developer("DEV-000042","K-0042");
        db.sql.update("INSERT INTO tb_hr_cand(cand_id,cand_nm,emp_type_cd,job_cd,grade_cd,hope_uprc,input_psbl_dt,cntc_stat_cd,cand_src_cd,reg_user_id) VALUES('CAND-000024','기존 후보','FL','DEV','MD',1,DATE '2026-01-01','NEW','SITE','admin')");
        db.install();
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        session=new SqlSessionTemplate(factory.getObject());
        transaction=new TransactionTemplate(new DataSourceTransactionManager(db.source));
    }
    void developer(String id,String number) {
        db.sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) SELECT ?,?,'기존 개발자','FT','DEV','DRCT',part_id,'IDLE','admin' FROM tb_bs_part ORDER BY part_id LIMIT 1",id,number);
    }
    @Test void forwardMigrationPreservesAppliedHistorySeedAndExistingAddressValues() {
        assertThat(db.sql.queryForList("SELECT * FROM flyway_schema_history WHERE version IN ('1','2','3') ORDER BY installed_rank")).isEqualTo(applied);
        assertThat(db.sql.queryForObject("SELECT sido_nm FROM tb_sy_adm_area WHERE area_cd='1100000000'",String.class)).isEqualTo("기존명");
        assertThat(db.sql.queryForObject("SELECT use_yn FROM tb_sy_adm_area WHERE area_cd='1100000000'",String.class)).isEqualTo("N");
        assertThat(db.sql.queryForObject("SELECT hdong_nm FROM tb_sy_adm_hdong WHERE hdong_cd='1111051500' AND area_cd='1111010100'",String.class)).isEqualTo("기존 행정동명");
        assertThat(db.sql.queryForObject("SELECT dev_nm FROM tb_hr_dev WHERE dev_id='DEV-000042'",String.class)).isEqualTo("기존 개발자");
        assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_sy_user WHERE user_id='admin'",Long.class)).isEqualTo(1);
    }
    @Test void addressMigrationPreservesEverySourceKeyAndSecondExecutionChangesNothing() {
        var areas=db.sql.queryForList("SELECT * FROM tb_sy_adm_area ORDER BY area_cd");
        var hdong=db.sql.queryForList("SELECT * FROM tb_sy_adm_hdong ORDER BY hdong_cd,area_cd");
        assertThat(areas).hasSize(5338);assertThat(hdong.size()).isBetween(6271,6272);
        assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_sy_adm_area WHERE area_lvl_cd='EMD'",Long.class)).isEqualTo(5067);
        assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_sy_adm_area a WHERE upper_area_cd IS NOT NULL AND NOT EXISTS(SELECT 1 FROM tb_sy_adm_area p WHERE p.area_cd=a.upper_area_cd)",Long.class)).isZero();
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V5__administrative_area_data.sql")).execute(db.source);
        db.install();
        assertThat(db.sql.queryForList("SELECT * FROM tb_sy_adm_area ORDER BY area_cd")).isEqualTo(areas);
        assertThat(db.sql.queryForList("SELECT * FROM tb_sy_adm_hdong ORDER BY hdong_cd,area_cd")).isEqualTo(hdong);
    }
    @Test void peopleSequencesStartAboveExistingMaxAndNeverResetOnRestartOrRollback() {
        var ids=session.getMapper(MasterIdMapper.class);
        assertThat(ids.nextDeveloper()).isEqualTo(new BigInteger("43"));
        assertThat(ids.nextCandidate()).isEqualTo(new BigInteger("25"));
        assertThat(ids.nextManagementNo()).isEqualTo(new BigInteger("43"));
        transaction.executeWithoutResult(status -> {assertThat(ids.nextDeveloper()).isEqualTo(new BigInteger("44"));status.setRollbackOnly();});
        db.install();
        assertThat(ids.nextDeveloper()).isEqualTo(new BigInteger("45"));
        assertThat(db.sql.queryForList("SELECT cycle FROM pg_sequences WHERE schemaname=current_schema() AND sequencename IN ('sq_tb_hr_dev_id','sq_tb_hr_cand_id','sq_tb_hr_dev_mng_no')",Boolean.class)).containsExactly(false,false,false);
    }
    @Test void preexistingAllocatorIsNotReinitialized() throws Exception {
        db.sql.execute("SELECT setval('sq_tb_hr_cand_id',100,true)");
        var script=new ClassPathResource("db/migration/V4__people_identifier_sequences.sql");
        db.sql.execute(script.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(session.getMapper(MasterIdMapper.class).nextCandidate()).isEqualTo(new BigInteger("101"));
    }
    @Test void manualManagementNumberIsSkippedAndDatabaseUniquenessStillGuardsRaces() {
        developer("DEV-000043","K-0043");
        var ids=new MasterIdService(session.getMapper(MasterIdMapper.class));
        String allocated=transaction.execute(status -> ids.nextManagementNo());
        assertThat(allocated).isEqualTo("K-0044");
        assertThatThrownBy(() -> developer("DEV-000044","K-0043")).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }
    @Test void allocatorDoesNotReuseMyBatisSessionCacheInsideOneTransaction() {
        var ids=session.getMapper(MasterIdMapper.class);
        transaction.executeWithoutResult(status -> {
            assertThat(ids.nextDeveloper()).isEqualTo(new BigInteger("43"));
            assertThat(ids.nextDeveloper()).isEqualTo(new BigInteger("44"));
            assertThat(ids.nextCandidate()).isEqualTo(new BigInteger("25"));
            assertThat(ids.nextCandidate()).isEqualTo(new BigInteger("26"));
        });
    }
}
