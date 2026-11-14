package com.sicost.workforceforecastsops;

import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.context.*;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.forecast.*;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "SICOST_TEST_DB_URL", matches = ".+")
class WorkforceForecastExportDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    MockMvc mvc;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void install() throws Exception {
        var db = new PostgreSqlDatabase(); db.install(); sql = db.sql;
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(db.source);
        var config = new org.apache.ibatis.session.Configuration();
        config.setArgNameBasedConstructorAutoMapping(true); config.setMapUnderscoreToCamelCase(true);
        config.addMapper(SalesForecastMapper.class); config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        config.addInterceptor(new PersistenceBoundary()); factory.setConfiguration(config);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        context = new AnnotationConfigApplicationContext();
        context.registerBean(com.fasterxml.jackson.databind.ObjectMapper.class,()->new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
        context.registerBean(DataSource.class, () -> db.source);
        context.registerBean(Clock.class, () -> Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"), ZoneId.of("Asia/Seoul")));
        context.registerBean(SalesForecastMapper.class, () -> session.getMapper(SalesForecastMapper.class));
        context.registerBean(AssignmentMonthlyMapper.class, () -> session.getMapper(AssignmentMonthlyMapper.class));
        context.registerBean(CurrentUserMapper.class, () -> session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class, () -> session.getMapper(ChangeHistoryMapper.class));
        context.registerBean(AccessLogMapper.class, () -> session.getMapper(AccessLogMapper.class));
        context.register(Transactions.class, CurrentUserProvider.class, ChangeHistoryWriter.class, AssignmentMonthlyService.class,
            AccessLogWriter.class, ExcelExportService.class, SalesForecastRepository.class, SalesWorkforceProjectionService.class,
            WorkforceForecastExportService.class, WorkforceForecastExportController.class);
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(WorkforceForecastExportController.class))
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        String id = "O" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String job = sql.queryForObject("SELECT code FROM tb_sy_code WHERE code_grp_id='JOB' AND use_yn='Y' AND del_yn='N' ORDER BY sort_ord,code LIMIT 1", String.class);
        sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES ('C-export','CUST','다운로드 시험 고객','1234567890','admin')");
        sql.update("INSERT INTO tb_sl_opp(opp_id,opp_nm,cust_clnt_id,part_id,sales_step_cd,win_prbl_rt,expct_amt,bgn_plan_ym,opp_stat_cd,aprv_stat_cd,reg_user_id) SELECT ?,?,clnt_id,'HQ','FIND',50,600,'208007','ING','WAIT','admin' FROM tb_bs_clnt ORDER BY clnt_id LIMIT 1", id, id);
        sql.update("INSERT INTO tb_sl_opp_need(opp_id,need_sn,job_cd,need_cnt,bgn_ym,prd_mcnt,mon_mm,reg_user_id) VALUES (?,1,?,3,'208007',18,0.5,'admin')", id, job);
    }
    @AfterEach void close() { if (context != null) context.close(); }
    Map<String,List<String>> state() {
        var result = new LinkedHashMap<String,List<String>>();
        for (String table : List.of("tb_sl_opp", "tb_sl_opp_need", "tb_sl_tent_asgn", "tb_sl_aprv_hist", "tb_pj_proj", "tb_pj_mnpw_plan", "tb_pj_asgn", "tb_pj_asgn_mon", "tb_pj_asgn_hist", "tb_hr_mon_pay", "tb_co_mon_cost", "tb_bl_bill", "tb_bl_rcpt", "tb_cl_close", "tb_co_part_setl", "tb_co_part_setl_dtl", "tb_sy_chg_hist"))
            result.put(table, sql.queryForList("SELECT row_to_json(t)::text FROM " + table + " t ORDER BY row_to_json(t)::text", String.class));
        return result;
    }
    long logs() { return sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE scrn_id='UI-17' AND act_type_cd='DNLD'", Long.class); }
    @Test void realMapperAndPublicHttpDownloadMatchSameFullProjectionAndOnlyCommitDnld() throws Exception {
        var before = state(); long count = logs();
        var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        transaction.setReadOnly(true); transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var expected = transaction.execute(status -> context.getBean(SalesWorkforceProjectionService.class).calculate(
            context.getBean(SalesForecastRepository.class).load(YearMonth.of(2080, 7)), LocalDate.of(2080, 7, 15), YearMonth.of(2080, 7), 6, true));
        var response = mvc.perform(get("/api/workforce-forecasts/export").param("from", "2080-07").param("months", "6"))
            .andExpect(status().isOk()).andExpect(header().exists("Content-Disposition"))
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn().getResponse();
        assertThat(logs()).isEqualTo(count + 1);
        assertThat(state()).isEqualTo(before);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(book.getSheet("직무별 계산근거").getLastRowNum()).isEqualTo(expected.jobDiagnostics().size());
            for (int i = 0; i < 6; i++) {
                var cell = book.getSheetAt(0).getRow(1).getCell(i + 1);
                assertThat(cell.getNumericCellValue()).isEqualTo(new java.math.BigDecimal(expected.totals().get(i).demand()).doubleValue());
            }
            assertThat(book.getSheetAt(0).getRow(0).getCell(6).getStringCellValue()).isEqualTo("2080-12");
        }
        assertThat(context.getBean(CurrentUserProvider.class).currentUser().dataScope()).isEqualTo("ALL");
    }
    @Test void databaseAuditFailureHasNoBinaryAndNoBusinessMutation() throws Exception {
        var before = state(); long count = logs();
        sql.execute("CREATE FUNCTION reject_workforce_download() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.scrn_id='UI-17' THEN RAISE EXCEPTION 'private SQL'; END IF; RETURN NEW; END $$");
        sql.execute("CREATE TRIGGER reject_workforce_download BEFORE INSERT ON tb_sy_accs_log FOR EACH ROW EXECUTE FUNCTION reject_workforce_download()");
        mvc.perform(get("/api/workforce-forecasts/export").param("from", "2080-07").param("months", "6"))
            .andExpect(status().isInternalServerError()).andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
        assertThat(logs()).isEqualTo(count); assertThat(state()).isEqualTo(before);
    }
}
