package com.sicost.plans;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.budget.ProjectBudgetMapper;
import com.sicost.budget.ProjectBudgetRepository;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
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
import static com.sicost.plans.ProjectPlanDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "SICOST_TEST_DB_URL", matches = ".+")
class ProjectPlanDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    ProjectPlanService service;
    MockMvc mvc;
    ObjectMapper json;
    String id, part, developer;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db = new PostgreSqlDatabase(); db.install(); sql = db.sql;
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean(); factory.setDataSource(db.source);
        var configuration = new org.apache.ibatis.session.Configuration();
        configuration.setArgNameBasedConstructorAutoMapping(true); configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary()); factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session = new SqlSessionTemplate(factory.getObject());
        context = new AnnotationConfigApplicationContext(); context.registerBean(DataSource.class, () -> db.source);
        json = JsonMapper.builder().findAndAddModules().build(); context.registerBean(ObjectMapper.class, () -> json);
        context.registerBean(Clock.class, () -> Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"), ZoneId.of("Asia/Seoul")));
        context.registerBean(ProjectPlanMapper.class, () -> session.getMapper(ProjectPlanMapper.class));
        context.registerBean(ProjectBudgetMapper.class, () -> session.getMapper(ProjectBudgetMapper.class));
        context.registerBean(AggregateLockMapper.class, () -> session.getMapper(AggregateLockMapper.class));
        context.registerBean(CurrentUserMapper.class, () -> session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class, () -> session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class, CurrentUserProvider.class, ChangeHistoryWriter.class, RevisionService.class,
            WriteCoordinator.class, CommitInvalidation.class, ProjectBudgetRepository.class, ProjectPlanRepository.class, ProjectPlanService.class);
        context.refresh(); service = context.getBean(ProjectPlanService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProjectPlanController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        id = "P-" + key; part = "S-" + key; developer = "D-" + key;
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'TK','ING','2080-01-01','2081-12-31',100,0,0,'admin')", id, "계획 시험 " + key);
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES (?,?,'N','Y','admin')", part, "계획 파트 " + key);
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FT','PJOB_A','DRCT',?,'IDLE','admin')", developer, developer, "계획 개발자", part);
        job("PJOB_A", 2, "N"); job("PJOB_B", 1, "Y"); job("PJOB_C", 1, "Y"); job("PJOB_D", 3, "N");
    }
    @AfterEach void close() { if (context != null) context.close(); }
    void job(String job, int sort, String active) {
        sql.update("INSERT INTO tb_sy_code(code_grp_id,code,code_nm,sort_ord,use_yn,del_yn,reg_user_id) VALUES ('JOB',?,?,?,?,'N','admin')", job, job, sort, active);
    }
    void plan(String job, String mm, String source) {
        sql.update("INSERT INTO tb_pj_mnpw_plan(proj_id,job_cd,plan_mm,src_cd,reg_user_id) VALUES (?,?,CAST(? AS numeric),?,'admin')", id, job, mm, source);
    }
    String assignment(String job, String state, String mm, String month) {
        String asgn = "A-" + UUID.randomUUID().toString().replace("-", "").substring(0,12);
        sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES (?,?,?,?,'2080-01-01','2081-12-31',1,'CTRT',0,?,'admin')", asgn, id, developer, job, state);
        if (mm != null) sql.update("INSERT INTO tb_pj_asgn_mon(asgn_id,input_ym,dev_id,proj_id,input_day_cnt,mon_mm,sales_amt,fix_yn,reg_user_id) VALUES (?,?,?,?,30,CAST(? AS numeric),0,'Y','admin')", asgn, month, developer, id, mm);
        return asgn;
    }
    SaveProjectPlansRequest request(PlanInput... inputs) { return new SaveProjectPlansRequest(2080, service.getProjectPlans(id).revision(), List.of(inputs)); }
    List<Map<String,Object>> plans() { return sql.queryForList("SELECT * FROM tb_pj_mnpw_plan WHERE proj_id=? ORDER BY job_cd", id); }
    List<Map<String,Object>> audits() { return sql.queryForList("SELECT * FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_PJ_MNPW_PLAN' AND trgt_key LIKE ? ORDER BY hist_id", id + ":%"); }
    @Test void realUnionSortFutureMonthsAndSameDeveloperAssignmentsAreSummedWithoutWrites() throws Exception {
        plan("PJOB_A", "5", "OPP"); assignment("PJOB_A", "ACT", "1.25", "208001");
        assignment("PJOB_A", "ACT", "2.50", "208112"); assignment("PJOB_A", "CNCL", "9.99", "208112");
        assignment("PJOB_C", "ACT", null, null); assignment("PJOB_B", "ACT", "0.50", "208108");
        var before = plans(); var project = sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?", id);
        var months = sql.queryForList("SELECT * FROM tb_pj_asgn_mon ORDER BY asgn_id,input_ym");
        var costs = sql.queryForList("SELECT * FROM tb_co_mon_cost ORDER BY cost_ym,dev_id,proj_id,asgn_id");
        mvc.perform(get("/api/projects/" + id + "/plans")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].jobCode").value("PJOB_B")).andExpect(jsonPath("$.data.items[1].jobCode").value("PJOB_C"))
            .andExpect(jsonPath("$.data.items[2].jobCode").value("PJOB_A")).andExpect(jsonPath("$.data.items[2].actualMm").value("3.75"))
            .andExpect(jsonPath("$.data.items[2].differenceMm").value("-1.25")).andExpect(jsonPath("$.data.items[2].useYn").value("N"))
            .andExpect(jsonPath("$.data.items[0].planMm").isEmpty()).andExpect(jsonPath("$.data.items[1].actualMm").value("0.00"))
            .andExpect(jsonPath("$.data.actualMmTotal").value("4.25"));
        assertThat(plans()).isEqualTo(before); assertThat(audits()).isEmpty();
        assertThat(sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?", id)).isEqualTo(project);
        assertThat(sql.queryForList("SELECT * FROM tb_pj_asgn_mon ORDER BY asgn_id,input_ym")).isEqualTo(months);
        assertThat(sql.queryForList("SELECT * FROM tb_co_mon_cost ORDER BY cost_ym,dev_id,proj_id,asgn_id")).isEqualTo(costs);
    }
    @Test void existingActorCanFirstCreateZeroAndMaximumThenRereadWithFullRevision() {
        assertThat(context.getBean(CurrentUserProvider.class).userId()).isEqualTo("admin");
        var saved = service.saveProjectPlans(id, request(new PlanInput("PJOB_B", "0"), new PlanInput("PJOB_C", "9999.99")));
        assertThat(saved.changed()).isTrue(); assertThat(saved.value().planMmTotal()).isEqualTo("9999.99");
        assertThat(service.getProjectPlans(id)).isEqualTo(saved.value()); assertThat(saved.revision()).isEqualTo(saved.value().revision());
        assertThat(plans()).allSatisfy(row -> { assertThat(row.get("src_cd")).isEqualTo("MANUAL"); assertThat(row.get("reg_user_id")).isEqualTo("admin"); });
        assertThat(audits()).hasSize(2).allSatisfy(row -> { assertThat(row.get("chg_type_cd")).isEqualTo("NEW"); assertThat(row.get("chg_item_nm")).isEqualTo("PLAN_MM"); });
    }
    @Test void blankInactiveOppRetainsSourceOmittedRowsAndExactAuditBeforeAfter() {
        plan("PJOB_A", "5", "OPP"); plan("PJOB_B", "2", "MANUAL");
        var omitted = plans().get(1);
        var result = service.saveProjectPlans(id, request(new PlanInput("PJOB_A", ""), new PlanInput("PJOB_C", "9999.99")));
        assertThat(result.value().items().stream().filter(row -> row.jobCode().equals("PJOB_A")).findFirst().orElseThrow().source()).isEqualTo("OPP");
        assertThat(plans().get(1)).isEqualTo(omitted);
        var changes = audits(); assertThat(changes).hasSize(2);
        assertThat(changes.get(0).get("trgt_key")).isEqualTo(id + ":PJOB_A");
        assertThat(changes.get(0).get("chg_type_cd")).isEqualTo("UPD"); assertThat(changes.get(0).get("bfr_val")).isEqualTo("5.00");
        assertThat(changes.get(0).get("aft_val")).isEqualTo("0.00"); assertThat(changes.get(1).get("chg_type_cd")).isEqualTo("NEW");
    }
    @Test void noOpPreservesTimestampsAndHistoryAndLargeTotalDoesNotClamp() {
        service.saveProjectPlans(id, request(new PlanInput("PJOB_B", "9999.99"), new PlanInput("PJOB_C", "9999.99")));
        var before = plans(); var audit = audits();
        var result = service.saveProjectPlans(id, request(new PlanInput("PJOB_B", "9999.99")));
        assertThat(result.changed()).isFalse(); assertThat(result.value().planMmTotal()).isEqualTo("19999.98");
        assertThat(plans()).isEqualTo(before); assertThat(audits()).isEqualTo(audit);
    }
    @Test void inactiveNewJobIsRejectedButExistingInactiveActualRowCanSave() {
        assertThatThrownBy(() -> service.saveProjectPlans(id, request(new PlanInput("PJOB_D", "1"))))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_INPUT"));
        assertThat(plans()).isEmpty(); assignment("PJOB_D", "ACT", null, null);
        assertThat(service.saveProjectPlans(id, request(new PlanInput("PJOB_D", "1"))).changed()).isTrue();
    }
    @Test void duplicateAndInvalidMmHttpKeepEveryBusinessAndAuditValue() throws Exception {
        plan("PJOB_A", "2", "OPP"); var before = plans();
        for (String bad : List.of("1.001", "-1", "+0", "1e2")) {
            var body = request(new PlanInput("PJOB_B", "1"), new PlanInput("PJOB_C", bad));
            mvc.perform(put("/api/projects/"+id+"/plans").contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        mvc.perform(put("/api/projects/"+id+"/plans").contentType("application/json").content(json.writeValueAsString(request(new PlanInput("PJOB_B", "1"),new PlanInput("PJOB_B", "2")))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("items"));
        assertThat(plans()).isEqualTo(before); assertThat(audits()).isEmpty();
    }
    @Test void yearAndLifecycleAndStaleFailuresPreservePlansSourceAndAudit() {
        plan("PJOB_A", "2", "OPP"); var before = plans(); var current = request(new PlanInput("PJOB_A", "0"));
        assertThatThrownBy(() -> service.saveProjectPlans(id,new SaveProjectPlansRequest(2079,current.expectedRevision(),current.items())))
            .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("READ_ONLY_YEAR"));
        sql.update("UPDATE tb_pj_proj SET feature_cn='외부 변경' WHERE proj_id=?",id);
        assertThatThrownBy(() -> service.saveProjectPlans(id,current)).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("STALE_WRITE"));
        sql.update("UPDATE tb_pj_proj SET proj_stat_cd='LOSE' WHERE proj_id=?",id);
        assertThatThrownBy(() -> service.saveProjectPlans(id,request(new PlanInput("PJOB_A","0"))))
            .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("INVALID_PROJECT_STATUS"));
        assertThat(plans()).isEqualTo(before); assertThat(audits()).isEmpty();
    }
    @Test void requiredAuditFailureRollsBackEveryChangedPlan() {
        plan("PJOB_A", "5", "OPP");var before=plans();
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_plan_history CHECK(trgt_tbl_nm <> 'TB_PJ_MNPW_PLAN')");
        assertThatThrownBy(() -> service.saveProjectPlans(id,request(new PlanInput("PJOB_A","0"),new PlanInput("PJOB_B","1")))).isInstanceOf(RuntimeException.class);
        assertThat(plans()).isEqualTo(before);assertThat(audits()).isEmpty();
    }
    @Test void emptyProjectAndMissingProjectHaveDistinctResponses() throws Exception {
        mvc.perform(get("/api/projects/"+id+"/plans")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/projects/not-present/plans")).andExpect(status().isNotFound()); assertThat(audits()).isEmpty();
    }
    @Test void simultaneousWritesWithSameAggregateRevisionCommitOnlyOne() throws Exception {
        SaveProjectPlansRequest input=request(new PlanInput("PJOB_B","1"));CountDownLatch start=new CountDownLatch(1);
        var first=CompletableFuture.supplyAsync(() -> concurrent(start,input));var second=CompletableFuture.supplyAsync(() -> concurrent(start,input));start.countDown();
        assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","STALE_WRITE");
        assertThat(plans()).hasSize(1);assertThat(audits()).hasSize(1);
    }
    String concurrent(CountDownLatch start,SaveProjectPlansRequest input) {
        try { if (!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");service.saveProjectPlans(id,input);return "OK"; }
        catch(ApiException e) { return e.code(); } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); }
    }
}
