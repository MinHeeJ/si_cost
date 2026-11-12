package com.sicost.apiops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.util.LinkedMultiValueMap;
import static com.sicost.apiops.PayrollDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "SICOST_TEST_DB_URL", matches = ".+")
class PayrollDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql; PayrollService service; PayrollRepository repository; MockMvc mvc;
    String first, second, key;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db = new PostgreSqlDatabase();db.install();sql = db.sql;
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration = new org.apache.ibatis.session.Configuration();
        configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session = new SqlSessionTemplate(factory.getObject());context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> db.source);
        context.registerBean(ObjectMapper.class, () -> JsonMapper.builder().findAndAddModules().build());
        context.registerBean(PayrollMapper.class, () -> session.getMapper(PayrollMapper.class));
        context.registerBean(CurrentUserMapper.class, () -> session.getMapper(CurrentUserMapper.class));
        context.register(Transactions.class, CurrentUserProvider.class, RevisionService.class, PayrollRepository.class, PayrollService.class);
        context.refresh();service = context.getBean(PayrollService.class);repository = context.getBean(PayrollRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new PayrollController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        key = UUID.randomUUID().toString().replace("-", "").substring(0, 12);first = "D-" + key + "A";second = "D-" + key + "B";
        developer(first, "Same " + key, "M_%" + key);developer(second, "Same " + key, "Other" + key);
    }
    @AfterEach void close() { if (context != null) context.close(); }
    void developer(String id, String name, String management) {
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,use_yn,reg_user_id) VALUES (?,?,?,'OS','DEV','DRCT','HQ','RETIRE','N','admin')", id, management, name);
        sql.update("INSERT INTO tb_hr_dev_prc(dev_id,aply_bgn_ym,mon_pay_amt,reg_user_id) VALUES (?,'208007',999,'admin')", id);
    }
    void pay(String month, String id) {
        sql.update("INSERT INTO tb_hr_mon_pay(pay_ym,dev_id,pay_amt,alwnc_amt,insr_cmpny_amt,insr_self_amt,retire_rsv_amt,incm_tax_amt,wthld_tax_amt,real_pay_amt,vndr_sply_amt,emp_type_cd,drct_cls_cd,reg_user_id) VALUES (?,?,100,20,0,50,10,80,0,-10,0,'FT','INDRCT','admin')", month, id);
    }
    Query query(String month, String q, int page, int size) {
        var values = new LinkedMultiValueMap<String, String>();values.add("month", month);
        if (q != null) values.add("q", q);values.add("page", String.valueOf(page));values.add("size", String.valueOf(size));
        return PayrollInput.query(values);
    }
    List<Map<String, Object>> source() { return sql.queryForList("SELECT * FROM tb_hr_mon_pay ORDER BY pay_ym,dev_id"); }
    @Test void realMonthSetStoredSnapshotsAndAuditTimesRemainUnchangedAcrossHttpReads() throws Exception {
        pay("208007", second);pay("208007", first);pay("208006", first);
        var before = source();var history = sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id");
        var users = sql.queryForList("SELECT * FROM tb_sy_user ORDER BY user_id");
        Page page = service.getPayroll(query("2080-07", null, 1, 50));
        assertThat(page.items()).extracting(Row::developerId).containsExactly(first, second);
        assertThat(page.items()).allSatisfy(r -> {assertThat(r.employeeType()).isEqualTo("FT");assertThat(r.directClass()).isEqualTo("INDRCT");assertThat(r.payAmount()).isEqualTo("100");assertThat(r.realPayAmount()).isEqualTo("-10");});
        assertThat(page.revision()).isEqualTo(context.getBean(RevisionService.class).revision(repository.snapshot("208007")));
        mvc.perform(get("/api/payroll").param("month", "2080-07")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.items[0].developerId").value(first));
        assertThat(source()).isEqualTo(before);assertThat(sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id")).isEqualTo(history);
        assertThat(sql.queryForList("SELECT * FROM tb_sy_user ORDER BY user_id")).isEqualTo(users);
        assertThat(sql.queryForObject("SELECT reg_user_id FROM tb_hr_mon_pay WHERE pay_ym='208007' AND dev_id=?", String.class, first)).isEqualTo("admin");
    }
    @Test void literalSearchCountAndPageShareFiltersButNeverNarrowRevision() {
        pay("208007", first);pay("208007", second);
        var all = service.getPayroll(query("2080-07", null, 1, 50));
        var filtered = service.getPayroll(query("2080-07", "m_%", 1, 20));
        assertThat(filtered.total()).isEqualTo(1);assertThat(filtered.items()).extracting(Row::developerId).containsExactly(first);
        assertThat(filtered.revision()).isEqualTo(all.revision());
        assertThat(service.getPayroll(query("2080-07", "m_%", 2, 20)).items()).isEmpty();
        assertThat(service.getPayroll(query("2080-07", "m_%", 2, 20)).total()).isEqualTo(1);
    }
    @Test void emptyMonthHasStableMonthSpecificRevisionAndPriceHistoryDoesNotCreatePayroll() {
        var before = source();var empty = service.getPayroll(query("2080-07", null, 1, 50));
        assertThat(empty.items()).isEmpty();assertThat(empty.total()).isZero();assertThat(empty.revision()).isNotBlank();
        assertThat(service.getPayroll(query("2080-07", "absent", 2, 20)).revision()).isEqualTo(empty.revision());
        assertThat(service.getPayroll(query("2080-08", null, 1, 50)).revision()).isNotEqualTo(empty.revision());assertThat(source()).isEqualTo(before);
    }
    @Test void changingMasterLabelsAndStatusDoesNotChangeStoredPayrollOrRevision() {
        pay("208007", first);var before = source();var page = service.getPayroll(query("2080-07", null, 1, 50));
        sql.update("UPDATE tb_hr_dev SET emp_type_cd='FL',drct_cls_cd='DRCT',work_stat_cd='LEAVE',dev_nm=? WHERE dev_id=?", "Renamed " + key, first);
        var after = service.getPayroll(query("2080-07", null, 1, 50));
        assertThat(after.items().get(0).employeeType()).isEqualTo("FT");assertThat(after.items().get(0).directClass()).isEqualTo("INDRCT");
        assertThat(after.revision()).isEqualTo(page.revision());assertThat(source()).isEqualTo(before);
    }
    @Test void revisionIncludesUnfilteredRowsNullsAndTimestampPrecision() {
        pay("208007", first);pay("208007", second);
        var before = service.getPayroll(query("2080-07", "M_%", 1, 20));
        sql.update("UPDATE tb_hr_mon_pay SET mod_dttm=TIMESTAMP '2080-07-01 00:00:00.000001' WHERE pay_ym='208007' AND dev_id=?", second);
        var changed = service.getPayroll(query("2080-07", "M_%", 1, 20));assertThat(changed.revision()).isNotEqualTo(before.revision());
        sql.update("UPDATE tb_hr_mon_pay SET mod_dttm=TIMESTAMP '2080-07-01 00:00:00.000002',real_pay_amt=NULL WHERE pay_ym='208007' AND dev_id=?", second);
        assertThat(service.getPayroll(query("2080-07", null, 1, 50)).revision()).isNotEqualTo(changed.revision());
    }
    @Test void rejectedInputAndMissingSingleResourceLeaveAllSourceAndHistoryUntouched() throws Exception {
        pay("208007", first);var before = source();var history = sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id");
        for (var pair : List.of(Map.entry("month", "2080-13"), Map.entry("q", "x".repeat(101)), Map.entry("size", "30"), Map.entry("actor", "forged"), Map.entry("REG_USER_ID", "forged"))) {
            var request = get("/api/payroll");if (!pair.getKey().equals("month")) request.param("month", "2080-07");
            mvc.perform(request.param(pair.getKey(), pair.getValue())).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/payroll/missing")).andExpect(status().isNotFound());
        assertThat(source()).isEqualTo(before);assertThat(sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id")).isEqualTo(history);
    }
}
