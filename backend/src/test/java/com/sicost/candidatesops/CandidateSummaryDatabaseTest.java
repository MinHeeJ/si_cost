package com.sicost.candidatesops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.api.RequestIdFilter;
import com.sicost.common.context.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "SICOST_TEST_DB_URL", matches = ".+")
class CandidateSummaryDatabaseTest {
    AnnotationConfigApplicationContext context;
    PostgreSqlDatabase db;
    MockMvc mvc;
    ObjectMapper json;
    static final LocalDate TODAY = LocalDate.of(2026, 12, 1);
    static final List<String> TABLES = List.of("tb_hr_cand", "tb_hr_cand_skill", "tb_hr_cand_cntc", "tb_sy_chg_hist", "tb_sy_accs_log");
    @Configuration @EnableTransactionManagement static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void install() throws Exception {
        db = new PostgreSqlDatabase(); db.install();
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean(); factory.setDataSource(db.source);
        var configuration = new org.apache.ibatis.session.Configuration();
        configuration.setArgNameBasedConstructorAutoMapping(true); configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary()); factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session = new SqlSessionTemplate(factory.getObject());
        context = new AnnotationConfigApplicationContext(); context.registerBean(DataSource.class, () -> db.source);
        json = JsonMapper.builder().findAndAddModules().build();
        context.registerBean(Clock.class, () -> Clock.fixed(Instant.parse("2026-11-30T16:00:00Z"), ZoneOffset.UTC));
        context.registerBean(CandidateSummaryMapper.class, () -> session.getMapper(CandidateSummaryMapper.class));
        context.registerBean(CurrentUserMapper.class, () -> session.getMapper(CurrentUserMapper.class));
        context.register(Transactions.class, CurrentUserProvider.class, CandidateSummaryRepository.class, CandidateSummaryService.class);
        context.refresh();
        mvc = MockMvcBuilders.standaloneSetup(new CandidateSummaryController(context.getBean(CandidateSummaryService.class)))
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }
    @AfterEach void close() { if (context != null) context.close(); }
    record Scenario(String rule, String status, LocalDate available, LocalDate contacted, LocalDate registered,
                    CandidateSummaryDtos.Summary increment) {
        @Override public String toString() { return rule; }
    }
    static List<Scenario> boundaries() {
        var recent = TODAY.minusDays(1);
        return List.of(
            new Scenario("next-month-first-inclusive", "BEFORE", LocalDate.of(2027, 1, 1), recent, recent, new CandidateSummaryDtos.Summary(1, 1, 0, 1, 0)),
            new Scenario("next-month-second-excluded", "DONE", LocalDate.of(2027, 1, 2), recent, recent, new CandidateSummaryDtos.Summary(1, 1, 0, 0, 0)),
            new Scenario("next-month-end-not-summary-boundary", "DONE", LocalDate.of(2027, 1, 31), recent, recent, new CandidateSummaryDtos.Summary(1, 1, 0, 0, 0)),
            new Scenario("past-availability-included", "BEFORE", TODAY.minusYears(1), recent, recent, new CandidateSummaryDtos.Summary(1, 1, 0, 1, 0)),
            new Scenario("interview-scheduled", "ITVW", TODAY, recent, recent, new CandidateSummaryDtos.Summary(1, 1, 1, 1, 0)),
            new Scenario("hold-not-progress-or-overdue", "HOLD", TODAY, TODAY.minusDays(31), recent, new CandidateSummaryDtos.Summary(1, 0, 0, 0, 0)),
            new Scenario("contract-not-progress-or-overdue", "CTRT", TODAY, TODAY.minusDays(31), recent, new CandidateSummaryDtos.Summary(1, 0, 0, 0, 0)),
            new Scenario("exactly-thirty-not-overdue", "DONE", TODAY, TODAY.minusDays(30), recent, new CandidateSummaryDtos.Summary(1, 1, 0, 1, 0)),
            new Scenario("thirty-one-overdue", "DONE", TODAY, TODAY.minusDays(31), recent, new CandidateSummaryDtos.Summary(1, 1, 0, 1, 1)),
            new Scenario("null-contact-registration-thirty", "BEFORE", TODAY, null, TODAY.minusDays(30), new CandidateSummaryDtos.Summary(1, 1, 0, 1, 0)),
            new Scenario("null-contact-registration-thirty-one", "BEFORE", TODAY, null, TODAY.minusDays(31), new CandidateSummaryDtos.Summary(1, 1, 0, 1, 1)),
            new Scenario("contact-overrides-old-registration", "DONE", TODAY, recent, TODAY.minusYears(1), new CandidateSummaryDtos.Summary(1, 1, 0, 1, 0))
        );
    }
    @ParameterizedTest(name = "{0}") @MethodSource("boundaries")
    void ruleMatchesActualRowsAndNeverChangesBusinessOrHistory(Scenario scenario) throws Exception {
        JsonNode baseline = summary();
        insert(scenario);
        var before = snapshot();
        JsonNode result = summary();
        JsonNode increment = json.valueToTree(scenario.increment());
        increment.fieldNames().forEachRemaining(field ->
            assertThat(result.get(field).asLong()).as(field).isEqualTo(baseline.get(field).asLong() + increment.get(field).asLong()));
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void childRowsDoNotMultiplyCountsAndRepeatedReadsStayReadOnly() throws Exception {
        var scenario = boundaries().get(4);
        insert(scenario);
        db.sql.update("INSERT INTO tb_hr_cand_skill(cand_id,skill_nm,sort_ord,reg_user_id) VALUES('CAND-900001','Java',1,'admin'),('CAND-900001','SQL',2,'admin')");
        db.sql.update("INSERT INTO tb_hr_cand_cntc(cand_id,cntc_sn,cntc_stat_cd,cntc_memo,cntc_dt,reg_user_id) VALUES('CAND-900001',1,'ITVW','연락',?,'admin'),('CAND-900001',2,'ITVW','연락',?,'admin')", TODAY, TODAY);
        var before = snapshot();
        JsonNode result = summary();
        long total = db.sql.queryForObject("SELECT count(*) FROM tb_hr_cand", Long.class);
        long interviews = db.sql.queryForObject("SELECT count(*) FROM tb_hr_cand WHERE cntc_stat_cd='ITVW'", Long.class);
        assertThat(result.get("total").asLong()).isEqualTo(total);
        assertThat(result.get("interviewScheduled").asLong()).isEqualTo(interviews);
        assertThat(summary()).isEqualTo(result);
        assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings = {"actor", "page", "availability", "contactStatus", "regUserId", "q", "sort"})
    void rejectedQueryDoesNotChangeAnyCandidateOrHistoryRow(String field) throws Exception {
        insert(boundaries().get(0));
        var before = snapshot();
        mvc.perform(get("/api/candidates/summary").param(field, "other"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void emptyFreshInstallationReturnsAllFiveZeroes() throws Exception {
        assertThat(db.count("tb_hr_cand")).isZero();
        assertThat(summary()).isEqualTo(json.readTree(json.writeValueAsString(new CandidateSummaryDtos.Summary(0, 0, 0, 0, 0))));
    }
    JsonNode summary() throws Exception {
        var response = mvc.perform(get("/api/candidates/summary")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).get("data");
    }
    void insert(Scenario scenario) {
        String job = code("JOB"), grade = code("GRADE"), source = code("CAND_SRC");
        db.sql.update("INSERT INTO tb_hr_cand(cand_id,cand_nm,emp_type_cd,job_cd,grade_cd,hope_uprc,input_psbl_dt,cntc_stat_cd,last_cntc_dt,cand_src_cd,reg_user_id,reg_dttm) VALUES('CAND-900001','요약검증','FL',?,?,1,?,?,?,?,'admin',?)",
            job, grade, scenario.available(), scenario.status(), scenario.contacted(), source, Timestamp.valueOf(scenario.registered().atTime(23, 59, 59)));
    }
    String code(String group) {
        return db.sql.queryForObject("SELECT code FROM tb_sy_code WHERE code_grp_id=? AND use_yn='Y' AND del_yn='N' ORDER BY sort_ord,code LIMIT 1", String.class, group);
    }
    Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : TABLES) result.put(table, db.sql.queryForList("SELECT * FROM " + table + " ORDER BY 1,2"));
        return result;
    }
}
