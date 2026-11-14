package com.sicost.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.b7.ExcelImportService;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.time.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static com.sicost.imports.BusinessPlanImportDtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class BusinessPlanImportDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    BusinessPlanImportService service;
    org.springframework.mock.web.MockMultipartFile file;
    @Configuration @EnableTransactionManagement
    static class Transactions { @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);} }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,() -> db.source);
        context.registerBean(ObjectMapper.class,() -> JsonMapper.builder().findAndAddModules().build());
        context.registerBean(Clock.class,() -> Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(BusinessPlanImportMapper.class,() -> session.getMapper(BusinessPlanImportMapper.class));
        context.registerBean(AggregateLockMapper.class,() -> session.getMapper(AggregateLockMapper.class));context.registerBean(MasterLockMapper.class,() -> session.getMapper(MasterLockMapper.class));
        context.registerBean(CurrentUserMapper.class,() -> session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,() -> session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,WriteCoordinator.class,CommitInvalidation.class,ExcelImportService.class,BusinessPlanImportDefinition.class,BusinessPlanImportRepository.class,BusinessPlanImportService.class);
        context.refresh();service=context.getBean(BusinessPlanImportService.class);file=BusinessPlanWorkbook.valid();
    }
    @AfterEach void close() {if(context!=null)context.close();}
    Validation checked() {return service.validate(file,2080);}
    Applied apply(Validation checked,String mode) {return service.apply(file,new Request(2080,checked.uploadId(),checked.expectedRevision(),mode));}
    java.util.List<java.util.Map<String,Object>> rows() {return sql.queryForList("SELECT * FROM tb_fi_biz_plan ORDER BY plan_ym,part_id");}
    @Test void validationOnlyStoresCheckedAndSuccessfulApplyPersistsTwelveWithAtomicAudits() {
        var before=rows();var checked=checked();assertThat(rows()).isEqualTo(before);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_FI_BIZ_PLAN'",Integer.class)).isZero();
        var result=apply(checked,"ALL");assertThat(result.changedMonths()).hasSize(12);assertThat(rows()).hasSize(12);
        assertThat(sql.queryForObject("SELECT upld_stat_cd FROM tb_sy_upld WHERE upld_id=?",String.class,Long.parseLong(result.uploadId()))).isEqualTo("APPLIED");
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_FI_BIZ_PLAN' AND chg_type_cd='NEW'",Integer.class)).isEqualTo(36);
        assertThat(sql.queryForList("SELECT * FROM tb_co_mon_pl")).isEmpty();
    }
    @Test void auditFailureRollsBackAllTargetsAndAppliedStatus() {
        var checked=checked();sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_target_history CHECK(trgt_tbl_nm <> 'TB_FI_BIZ_PLAN') NOT VALID");
        assertThatThrownBy(() -> apply(checked,"ALL")).isInstanceOf(RuntimeException.class);assertThat(rows()).isEmpty();
        assertThat(sql.queryForObject("SELECT upld_stat_cd FROM tb_sy_upld WHERE upld_id=?",String.class,Long.parseLong(checked.uploadId()))).isEqualTo("CHECKED");
    }
    @Test void keepClosedNeverCreatesAbsentMonthAndKeepsExistingFullRow() {
        sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('208001','CLOSE','admin'),('208002','CLOSE','admin')");
        sql.update("INSERT INTO tb_fi_biz_plan(plan_ym,part_id,sales_tgt_amt,oprt_prft_tgt_amt,reg_user_id) VALUES ('208001','ALL',9,1,'admin')");
        var original=rows().get(0);var result=apply(checked(),"KEEP_CLOSED");
        assertThat(result.keptMonths()).containsExactly("2080-01","2080-02");assertThat(rows()).contains(original).hasSize(11);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_fi_biz_plan WHERE plan_ym='208002'",Integer.class)).isZero();
    }
    @Test void identicalSecondUploadCompletesWithoutTouchingTargetAuditOrTimestamps() {
        apply(checked(),"ALL");var original=rows();var history=sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id");
        var next=checked();var result=apply(next,"ALL");assertThat(result.changedMonths()).isEmpty();assertThat(result.unchangedMonths()).hasSize(12);
        assertThat(rows()).isEqualTo(original);assertThat(sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id")).isEqualTo(history);
        assertThat(((Result)service.result(Long.parseLong(next.uploadId()))).status()).isEqualTo("APPLIED");
    }
    @Test void changedCloseStatusIsStaleWithoutPartialWrites() {
        var checked=checked();sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('208001','CLOSE','admin')");
        assertThatThrownBy(() -> apply(checked,"ALL")).isInstanceOfSatisfying(com.sicost.common.api.ApiException.class,e -> assertThat(e.code()).isEqualTo("STALE_WRITE"));assertThat(rows()).isEmpty();
    }
}
