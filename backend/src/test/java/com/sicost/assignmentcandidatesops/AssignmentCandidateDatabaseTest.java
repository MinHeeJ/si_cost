package com.sicost.assignmentcandidatesops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.assignments.*;
import com.sicost.assignmentsops.*;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.*;
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
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class AssignmentCandidateDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;MockMvc mvc;ObjectMapper json;
    String project,developer,part,opportunity,tentative;
    @Configuration @EnableTransactionManagement
    static class Transactions {@Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}}
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(AssignmentActionsMapper.class,()->session.getMapper(AssignmentActionsMapper.class));context.registerBean(AssignmentMapper.class,()->session.getMapper(AssignmentMapper.class));context.registerBean(AssignmentMonthlyMapper.class,()->session.getMapper(AssignmentMonthlyMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));context.registerBean(MasterIdMapper.class,()->session.getMapper(MasterIdMapper.class));
        context.registerBean(AssignmentCandidateMapper.class,()->session.getMapper(AssignmentCandidateMapper.class));context.registerBean(SettlementSourceLockMapper.class,()->session.getMapper(SettlementSourceLockMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,CommitInvalidation.class,MasterIdService.class,AssignmentMonthlyService.class,AssignmentActionsRepository.class,AssignmentActionsService.class,AssignmentRepository.class,AssignmentService.class,SettlementSourceLocks.class,AssignmentCandidateRepository.class,AssignmentCandidateService.class);context.register(com.sicost.common.LegacyBusinessPolicyFixtures.class);context.refresh();
        mvc=MockMvcBuilders.standaloneSetup(new AssignmentCandidateController(context.getBean(AssignmentCandidateService.class))).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        String key=UUID.randomUUID().toString().replace("-","").substring(0,12);project="P-"+key;developer="D-"+key;part="T-"+key;opportunity="O-"+key;
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,reg_user_id) VALUES (?,?,'admin')",part,part);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2080-07-10','2081-12-31',100,100,0,0,'admin')",project,project);
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FT','BE','DRCT',?,'IDLE','admin')",developer,developer,developer,part);
        sql.update("INSERT INTO tb_hr_dev_prc(dev_id,aply_bgn_ym,mon_uprc,reg_user_id) VALUES (?,'208001',101,'admin')",developer);
        String customer="C-"+key;
        sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES (?,'CUST',?,right('0000000000'||abs(hashtext(?))::text,10),'admin')",customer,customer,key);
        sql.update("INSERT INTO tb_sl_opp(opp_id,opp_nm,cust_clnt_id,part_id,sales_step_cd,win_prbl_rt,expct_amt,bgn_plan_ym,opp_stat_cd,aprv_stat_cd,proj_id,reg_user_id) VALUES (?,?,?,?,'PRE',100,100,'208007','WIN','NONE',?,'admin')",opportunity,opportunity,customer,part,project);
        sql.update("INSERT INTO tb_sl_opp_need(opp_id,need_sn,job_cd,need_cnt,bgn_ym,prd_mcnt,mon_mm,expct_uprc,reg_user_id) VALUES (?,1,'BE',1,'208007',2,0.5,999,'admin')",opportunity);
        tentative=sql.queryForObject("INSERT INTO tb_sl_tent_asgn(opp_id,need_sn,dev_id,bgn_ym,tent_stat_cd,src_scrn_id,reg_user_id) VALUES (?,1,?,'208007','CNVT','UI-29','admin') RETURNING tent_id::text",String.class,opportunity,developer);
    }
    @AfterEach void close(){if(context!=null)context.close();}
    String revision(){return context.getBean(AssignmentCandidateRepository.class).revision(opportunity);}
    ObjectNode body(){return json.createObjectNode().put("year",2080).put("projectId",project).put("developerId",developer).put("jobCode","BE").put("startDate","2080-07-10").put("endDate","2080-08-31").put("contractMm","0.50").put("sourceTentId",tentative).put("expectedRevision",revision());}
    String path(String action){return "/api/assignment-candidates/"+tentative+"/"+action;}
    int count(String table){return sql.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    String state(){return sql.queryForObject("SELECT tent_stat_cd FROM tb_sl_tent_asgn WHERE tent_id=CAST(? AS numeric)",String.class,tentative);}
    void unchanged(){assertThat(state()).isEqualTo("CNVT");for(String table:List.of("tb_pj_asgn","tb_pj_asgn_hist","tb_pj_asgn_mon","tb_sy_chg_hist"))assertThat(count(table)).as(table).isZero();}
    @Test void actualMapperMaterializesNullsAndConfirmPersistsAtomicSourceLinkAndReplay() throws Exception {
        var row=context.getBean(AssignmentCandidateMapper.class).origin(tentative);assertThat(row.processedAt()).isNull();assertThat(row.projectId()).isEqualTo(project);
        ObjectNode request=body();var response=mvc.perform(post(path("confirm")).contentType("application/json").content(request.toString())).andExpect(status().isOk()).andReturn();
        String id=json.readTree(response.getResponse().getContentAsString()).path("data").path("assignmentId").asText();
        assertThat(state()).isEqualTo("DONE");assertThat(count("tb_pj_asgn")).isEqualTo(1);assertThat(count("tb_pj_asgn_hist")).isEqualTo(1);assertThat(count("tb_pj_asgn_mon")).isEqualTo(2);
        assertThat(sql.queryForObject("SELECT bill_uprc FROM tb_pj_asgn WHERE asgn_id=?",String.class,id)).isEqualTo("101");
        assertThat(sql.queryForObject("SELECT new_ctrt_mm FROM tb_pj_asgn_hist WHERE asgn_id=?",String.class,id)).isEqualTo("0.50");
        assertThat(sql.queryForObject("SELECT proc_dttm FROM tb_sl_tent_asgn WHERE tent_id=CAST(? AS numeric)",LocalDateTime.class,tentative)).isNotNull();
        var audits=sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id");var opportunityBefore=sql.queryForMap("SELECT * FROM tb_sl_opp WHERE opp_id=?",opportunity);
        assertThat(context.getBean(AssignmentCandidateMapper.class).confirmedAssignment(tentative)).isEqualTo(id);
        mvc.perform(post(path("confirm")).contentType("application/json").content(request.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false)).andExpect(jsonPath("$.data.assignmentId").value(id));
        assertThat(count("tb_pj_asgn")).isEqualTo(1);assertThat(sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id")).isEqualTo(audits);assertThat(sql.queryForMap("SELECT * FROM tb_sl_opp WHERE opp_id=?",opportunity)).isEqualTo(opportunityBefore);
    }
    @Test void dropAndLatestReplayKeepNoAssignmentAndOriginalRegistration() throws Exception {
        var original=sql.queryForMap("SELECT reg_user_id,reg_dttm FROM tb_sl_tent_asgn WHERE tent_id=CAST(? AS numeric)",tentative);
        mvc.perform(post(path("drop")).contentType("application/json").content(json.createObjectNode().put("expectedRevision",revision()).toString())).andExpect(status().isOk());
        assertThat(state()).isEqualTo("DROP");assertThat(count("tb_pj_asgn")).isZero();int audits=count("tb_sy_chg_hist");
        mvc.perform(post(path("drop")).contentType("application/json").content(json.createObjectNode().put("expectedRevision",revision()).toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));
        assertThat(count("tb_sy_chg_hist")).isEqualTo(audits);assertThat(sql.queryForMap("SELECT reg_user_id,reg_dttm FROM tb_sl_tent_asgn WHERE tent_id=CAST(? AS numeric)",tentative)).isEqualTo(original);
    }
    @Test void candidateAuditFailureRollsBackAssignmentHistoryMonthlyAndDone() throws Exception {
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_candidate CHECK(trgt_tbl_nm <> 'TB_SL_TENT_ASGN')");
        mvc.perform(post(path("confirm")).contentType("application/json").content(body().toString())).andExpect(status().isInternalServerError());unchanged();
    }
    @Test void candidateDropAuditFailureRollsBackStatusAndOpportunityMod() throws Exception {
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_candidate CHECK(trgt_tbl_nm <> 'TB_SL_TENT_ASGN')");
        var before=sql.queryForMap("SELECT * FROM tb_sl_opp WHERE opp_id=?",opportunity);
        mvc.perform(post(path("drop")).contentType("application/json").content(json.createObjectNode().put("expectedRevision",revision()).toString())).andExpect(status().isInternalServerError());unchanged();assertThat(sql.queryForMap("SELECT * FROM tb_sl_opp WHERE opp_id=?",opportunity)).isEqualTo(before);
    }
    @Test void inputHistoryFailureNeverMarksCandidateDone() throws Exception {
        sql.execute("ALTER TABLE tb_pj_asgn_hist ADD CONSTRAINT reject_input CHECK(asgn_chg_type_cd <> 'INPUT')");
        mvc.perform(post(path("confirm")).contentType("application/json").content(body().toString())).andExpect(status().isInternalServerError());unchanged();
    }
    @Test void sameCandidateRaceReturnsOneIdAndSingleAssignment() throws Exception {
        String request=body().toString();ExecutorService pool=new org.springframework.security.concurrent.DelegatingSecurityContextExecutorService(Executors.newFixedThreadPool(2),org.springframework.security.core.context.SecurityContextHolder.getContext());CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<String> work=()->{start.await();var response=mvc.perform(post(path("confirm")).contentType("application/json").content(request)).andExpect(status().isOk()).andReturn();return json.readTree(response.getResponse().getContentAsString()).path("data").path("assignmentId").asText();};
            var a=pool.submit(work);var b=pool.submit(work);start.countDown();assertThat(a.get(30,TimeUnit.SECONDS)).isEqualTo(b.get(30,TimeUnit.SECONDS));assertThat(count("tb_pj_asgn")).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void simultaneousDropChecksRevisionAfterLockAndStoresOnce() throws Exception {
        String request=json.createObjectNode().put("expectedRevision",revision()).toString();ExecutorService pool=new org.springframework.security.concurrent.DelegatingSecurityContextExecutorService(Executors.newFixedThreadPool(2),org.springframework.security.core.context.SecurityContextHolder.getContext());CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<Integer> work=()->{start.await();return mvc.perform(post(path("drop")).contentType("application/json").content(request)).andReturn().getResponse().getStatus();};
            var a=pool.submit(work);var b=pool.submit(work);start.countDown();assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);assertThat(count("tb_pj_asgn")).isZero();assertThat(state()).isEqualTo("DROP");
        } finally {pool.shutdownNow();}
    }
    @Test void staleSourceOutsideVisibleCandidateChangesRevisionAndSavesNothing() throws Exception {
        ObjectNode request=body();sql.update("INSERT INTO tb_sl_aprv_hist(opp_id,hist_sn,aprv_act_cd,prft_rt,reg_user_id) VALUES (?,1,'OK',20,'admin')",opportunity);
        mvc.perform(post(path("confirm")).contentType("application/json").content(request.toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));unchanged();
    }
}
