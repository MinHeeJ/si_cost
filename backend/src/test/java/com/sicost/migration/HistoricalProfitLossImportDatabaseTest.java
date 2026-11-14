package com.sicost.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.time.*;
import java.util.*;
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
import static com.sicost.migration.HistoricalProfitLossImportDtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class HistoricalProfitLossImportDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    HistoricalProfitLossImportService service;
    org.springframework.mock.web.MockMultipartFile file;
    @Configuration @EnableTransactionManagement
    static class Transactions { @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);} }
    @BeforeEach void install()throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        sql.update("UPDATE tb_sy_oper_std SET std_val=208007 WHERE std_cd='CASH_BASE_YM'");
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        context.registerBean(ObjectMapper.class,()->JsonMapper.builder().findAndAddModules().build());
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(HistoricalProfitLossImportMapper.class,()->session.getMapper(HistoricalProfitLossImportMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));context.registerBean(MasterLockMapper.class,()->session.getMapper(MasterLockMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,WriteCoordinator.class,CommitInvalidation.class,ExcelImportService.class,HistoricalProfitLossImportDefinition.class,HistoricalProfitLossImportRepository.class,HistoricalProfitLossImportService.class);
        context.refresh();service=context.getBean(HistoricalProfitLossImportService.class);file=HistoricalProfitLossWorkbook.valid();
    }
    @AfterEach void close() {if(context!=null)context.close();}
    Validation checked() {return service.validate(file);}
    Applied apply(Validation checked) {return service.apply(file,new Request(checked.uploadId(),checked.expectedRevision()));}
    List<Map<String,Object>> rows() {return sql.queryForList("SELECT * FROM tb_co_mon_pl ORDER BY pl_ym");}
    List<Map<String,Object>> audits() {return sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id");}
    void otherMonth() {sql.update("INSERT INTO tb_co_mon_pl(pl_ym,sales_amt,sales_cost_amt,sga_tot_amt,oprt_prft_amt,migr_yn,fix_yn,reg_user_id) VALUES ('207902',2,1,0,1,'Y','Y','admin')");}
    @Test void validateOnlyStoresCheckedThenApplyPersistsRawValuesAtomicAuditsAndNoSourceChanges() {
        var before=rows();var history=audits();var checked=checked();assertThat(rows()).isEqualTo(before);assertThat(audits()).isEqualTo(history);
        assertThat(sql.queryForMap("SELECT form_type_cd,scrn_id,upld_stat_cd,trgt_ym FROM tb_sy_upld WHERE upld_id=?",Long.parseLong(checked.uploadId()))).containsEntry("form_type_cd","B11").containsEntry("scrn_id","UI-08").containsEntry("upld_stat_cd","CHECKED").containsEntry("trgt_ym",null);
        var result=apply(checked);assertThat(result.status()).isEqualTo("APPLIED");assertThat(rows()).hasSize(1);
        assertThat(rows().get(0)).containsEntry("migr_yn","Y").containsEntry("fix_yn","Y").containsEntry("gross_prft_amt",null);
        assertThat((java.math.BigDecimal)rows().get(0).get("sales_amt")).isEqualByComparingTo("1000");
        assertThat(sql.queryForList("SELECT * FROM tb_cl_close")).isEmpty();assertThat(sql.queryForList("SELECT * FROM tb_co_mon_cost")).isEmpty();
        assertThat(sql.queryForObject("SELECT upld_stat_cd FROM tb_sy_upld WHERE upld_id=?",String.class,Long.parseLong(result.uploadId()))).isEqualTo("APPLIED");
        assertThat(audits()).allMatch(a->a.get("trgt_key").equals("207901")&&a.get("chg_rsn").equals("HistoricalProfitLossImport:"+result.uploadId()+":2:ALL"));
    }
    @Test void auditFailureRollsBackPlAndStatus() {
        var checked=checked();assertThat(checked.canApply()).isTrue();sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_historical_history CHECK(trgt_tbl_nm <> 'TB_CO_MON_PL') NOT VALID");
        assertThatThrownBy(()->apply(checked)).isInstanceOf(RuntimeException.class);assertThat(rows()).isEmpty();assertThat(audits()).isEmpty();
        assertThat(((Result)service.result(Long.parseLong(checked.uploadId()))).status()).isEqualTo("CHECKED");
    }
    @Test void statusFailureRollsBackBusinessAndAudit() {
        var checked=checked();assertThat(checked.canApply()).isTrue();sql.execute("ALTER TABLE tb_sy_upld ADD CONSTRAINT reject_historical_apply CHECK(upld_stat_cd <> 'APPLIED') NOT VALID");
        assertThatThrownBy(()->apply(checked)).isInstanceOf(RuntimeException.class);assertThat(rows()).isEmpty();assertThat(audits()).isEmpty();
    }
    @Test void identicalSecondUploadPreservesModAuditOtherMonthsAndStillCompletes() {
        otherMonth();apply(checked());var original=rows();var history=audits();var next=checked();var result=apply(next);
        assertThat(result.changedMonths()).isEmpty();assertThat(result.unchangedMonths()).containsExactly("2079-01");assertThat(result.keptMonths()).contains("2079-02");
        assertThat(rows()).isEqualTo(original);assertThat(audits()).isEqualTo(history);assertThat(((Result)service.result(Long.parseLong(next.uploadId()))).status()).isEqualTo("APPLIED");
    }
    @Test void optionalNullReplacesPriorValueWithoutTouchingOtherMonth()throws Exception {
        otherMonth();var other=rows().get(0);file=HistoricalProfitLossWorkbook.file(b->b.getSheetAt(0).getRow(1).getCell(8).setCellValue(9));apply(checked());
        file=HistoricalProfitLossWorkbook.valid();var result=apply(checked());assertThat(rows()).contains(other);assertThat(rows().get(0)).containsEntry("gross_prft_amt",null);
        assertThat(sql.queryForList("SELECT * FROM tb_sy_chg_hist WHERE chg_rsn=? AND chg_item_nm='GROSS_PRFT_AMT'","HistoricalProfitLossImport:"+result.uploadId()+":2:ALL")).singleElement().satisfies(a->assertThat(a).containsEntry("bfr_val","9").containsEntry("aft_val",null));
    }
    @Test void rangeRowOutsideFileChangeIsStale() {
        otherMonth();var checked=checked();sql.update("UPDATE tb_co_mon_pl SET sales_amt=3 WHERE pl_ym='207902'");var original=rows();
        assertThatThrownBy(()->apply(checked)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));assertThat(rows()).isEqualTo(original);assertThat(audits()).isEmpty();
    }
    @Test void replayPreservesAllTimesAuditsAndSummary() {
        var checked=checked();apply(checked);var original=rows();var history=audits();var uploads=sql.queryForList("SELECT * FROM tb_sy_upld ORDER BY upld_id");
        var replay=apply(checked);assertThat(replay.alreadyApplied()).isTrue();assertThat(replay.unchangedMonths()).isNull();assertThat(replay.keptMonths()).isNull();assertThat(replay.changedMonths()).containsExactly("2079-01");
        assertThat(rows()).isEqualTo(original);assertThat(audits()).isEqualTo(history);assertThat(sql.queryForList("SELECT * FROM tb_sy_upld ORDER BY upld_id")).isEqualTo(uploads);
    }
    @Test void invalidApplyCommitsLatestIssuesButNoPl()throws Exception {
        var checked=checked();file=HistoricalProfitLossWorkbook.file(b->b.getSheetAt(0).getRow(1).getCell(4).setBlank());
        sql.update("UPDATE tb_sy_upld SET file_size=? WHERE upld_id=?",file.getSize(),Long.parseLong(checked.uploadId()));
        assertThatThrownBy(()->apply(checked)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(422));
        assertThat(rows()).isEmpty();assertThat(audits()).isEmpty();assertThat(sql.queryForList("SELECT col_nm,rslt_type_cd FROM tb_sy_upld_err WHERE upld_id=?",Long.parseLong(checked.uploadId()))).anyMatch(a->a.get("col_nm").equals("영업이익")&&a.get("rslt_type_cd").equals("ERR"));
    }
    @Test void issueReplacementFailureKeepsPreviousCheckedSummaryAndIssues()throws Exception {
        file=HistoricalProfitLossWorkbook.file(b->b.getSheetAt(0).getRow(1).getCell(4).setBlank());var checked=checked();
        var uploads=sql.queryForList("SELECT * FROM tb_sy_upld");var issues=sql.queryForList("SELECT * FROM tb_sy_upld_err");
        sql.execute("ALTER TABLE tb_sy_upld_err ADD CONSTRAINT reject_historical_issue CHECK(false) NOT VALID");
        assertThatThrownBy(()->apply(checked)).isInstanceOf(RuntimeException.class);assertThat(rows()).isEmpty();assertThat(sql.queryForList("SELECT * FROM tb_sy_upld")).isEqualTo(uploads);assertThat(sql.queryForList("SELECT * FROM tb_sy_upld_err")).isEqualTo(issues);
    }
}
