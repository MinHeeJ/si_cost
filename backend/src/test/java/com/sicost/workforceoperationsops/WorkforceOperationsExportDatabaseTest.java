package com.sicost.workforceoperationsops;

import com.sicost.apiops.*;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.context.*;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.forecast.*;
import com.sicost.workforceforecastsops.WorkforceForecastExportService;
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
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Each test migrates its own UUID schema; no shared seed row is mutated. */
@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL", matches=".+")
class WorkforceOperationsExportDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    MockMvc mvc;
    String developerId;
    String schema;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void install() throws Exception {
        var db = new PostgreSqlDatabase(); sql = db.sql;
        schema = sql.queryForObject("SELECT current_schema()", String.class); db.install();
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(db.source);
        var config = new org.apache.ibatis.session.Configuration();
        config.setArgNameBasedConstructorAutoMapping(true); config.setMapUnderscoreToCamelCase(true);
        config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        config.addInterceptor(new PersistenceBoundary()); factory.setConfiguration(config);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        context = new AnnotationConfigApplicationContext();
        context.registerBean(com.fasterxml.jackson.databind.ObjectMapper.class,()->new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
        context.registerBean(DataSource.class, () -> db.source);
        context.registerBean(Clock.class, () -> Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"), ZoneId.of("Asia/Seoul")));
        context.registerBean(StaffingReadMapper.class, () -> session.getMapper(StaffingReadMapper.class));
        context.registerBean(SalesForecastMapper.class, () -> session.getMapper(SalesForecastMapper.class));
        context.registerBean(AssignmentMonthlyMapper.class, () -> session.getMapper(AssignmentMonthlyMapper.class));
        context.registerBean(CurrentUserMapper.class, () -> session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class, () -> session.getMapper(ChangeHistoryMapper.class));
        context.registerBean(AccessLogMapper.class, () -> session.getMapper(AccessLogMapper.class));
        context.registerBean(com.sicost.shared.SharedQueriesMapper.class, () -> session.getMapper(com.sicost.shared.SharedQueriesMapper.class));
        context.register(com.sicost.common.excel.ExportLabels.class);
        context.register(Transactions.class, CurrentUserProvider.class, ChangeHistoryWriter.class, AssignmentMonthlyService.class,
            AccessLogWriter.class, ExcelExportService.class, SalesForecastRepository.class, SalesWorkforceProjectionService.class,
            WorkforceCalendarService.class, WorkforceForecastExportService.class, WorkforceOperationsExportService.class,
            WorkforceOperationsExportController.class);
        context.register(com.sicost.common.LegacyBusinessPolicyFixtures.class);context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(WorkforceOperationsExportController.class))
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        developerId = "D" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String part = sql.queryForObject("SELECT part_id FROM tb_bs_part ORDER BY part_id LIMIT 1", String.class);
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,jncmp_dt,work_stat_cd,reg_user_id) VALUES (?,?,?,'FT','0001','DRCT',?,'2080-01-01','WORK','admin')",
            developerId, developerId, "=owned fixture", part);
    }
    @AfterEach void cleanup() {
        if (context != null) context.close();
        if (sql != null && schema != null && schema.matches("sicost_test_[a-f0-9]{32}")) sql.execute("DROP SCHEMA " + schema + " CASCADE");
    }
    Map<String,List<String>> businessState() {
        var state = new LinkedHashMap<String,List<String>>();
        for (String table : List.of("tb_hr_dev", "tb_hr_dev_stat", "tb_pj_asgn", "tb_pj_asgn_mon", "tb_pj_asgn_hist", "tb_sl_opp", "tb_sl_opp_need", "tb_sl_tent_asgn", "tb_sy_chg_hist"))
            state.put(table, sql.queryForList("SELECT row_to_json(t)::text FROM " + table + " t ORDER BY row_to_json(t)::text", String.class));
        return state;
    }
    long logs() { return sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE scrn_id='UI-28' AND act_type_cd='DNLD'", Long.class); }
    @Test void realPostgresqlCalendarAndSupplyWorkbooksCommitOnlyDownloadLogs() throws Exception {
        var before = businessState(); long logCount = logs();
        var response = mvc.perform(get("/api/workforce-operations/export").param("tab", "CALENDAR").param("year", "2080").param("jobCode", "0001"))
            .andExpect(status().isOk()).andExpect(header().exists("Content-Disposition")).andReturn().getResponse();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = book.getSheet("인력 운영 달력");
            var ids = new ArrayList<String>();
            for (int row=1; row<=sheet.getLastRowNum(); row++) ids.add(sheet.getRow(row).getCell(0).getStringCellValue());
            assertThat(ids).contains(developerId);
            assertThat(book.getSheet("월별 합계").getRow(1).getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
        }
        assertThat(logs()).isEqualTo(logCount+1); assertThat(businessState()).isEqualTo(before);
        var supply = mvc.perform(get("/api/workforce-operations/export").param("tab", "SUPPLY_DEMAND").param("year", "2080").param("jobCode", "ABSENT"))
            .andExpect(status().isOk()).andReturn().getResponse();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(supply.getContentAsByteArray()))) {
            var header = book.getSheet("월별 가용 vs 필요").getRow(0);
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("2080-08");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("2081-01");
        }
        assertThat(logs()).isEqualTo(logCount+2); assertThat(businessState()).isEqualTo(before);
    }
    @Test void realDatabaseAuditFailurePreventsBinaryAndPreservesBusinessRows() throws Exception {
        var before = businessState(); long logCount = logs();
        sql.execute("CREATE FUNCTION reject_operations_download() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.scrn_id='UI-28' THEN RAISE EXCEPTION 'private SQL'; END IF; RETURN NEW; END $$");
        sql.execute("CREATE TRIGGER reject_operations_download BEFORE INSERT ON tb_sy_accs_log FOR EACH ROW EXECUTE FUNCTION reject_operations_download()");
        mvc.perform(get("/api/workforce-operations/export").param("tab", "CALENDAR").param("year", "2080"))
            .andExpect(status().isInternalServerError()).andExpect(header().doesNotExist("Content-Disposition"))
            .andExpect(content().contentTypeCompatibleWith("application/json")).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
        assertThat(logs()).isEqualTo(logCount); assertThat(businessState()).isEqualTo(before);
    }
}
