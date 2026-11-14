package com.sicost.tentativeassignmentsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.assignmentcandidatesops.*;
import com.sicost.assignments.AssignmentMapper;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class TentativeAssignmentDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    MockMvc mvc;
    ObjectMapper json;
    String opportunity,developer,tentative;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);
        configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(TentativeAssignmentMapper.class,()->session.getMapper(TentativeAssignmentMapper.class));
        context.registerBean(AssignmentCandidateMapper.class,()->session.getMapper(AssignmentCandidateMapper.class));
        context.registerBean(AssignmentMapper.class,()->session.getMapper(AssignmentMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(SettlementSourceLockMapper.class,()->session.getMapper(SettlementSourceLockMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,
            CommitInvalidation.class,SettlementSourceLocks.class,AssignmentCandidateRepository.class,
            TentativeAssignmentRepository.class,TentativeAssignmentService.class);context.refresh();
        mvc=MockMvcBuilders.standaloneSetup(new TentativeAssignmentController(context.getBean(TentativeAssignmentService.class)))
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        String key=UUID.randomUUID().toString().replace("-","").substring(0,12);
        opportunity="O-"+key;developer="D-"+key;String part="T-"+key,customer="C-"+key;
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,reg_user_id) VALUES (?,?,'admin')",part,part);
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FT','BE','DRCT',?,'IDLE','admin')",developer,developer,developer,part);
        sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES (?,'CUST',?,right('0000000000'||abs(hashtext(?))::text,10),'admin')",customer,customer,key);
        sql.update("INSERT INTO tb_sl_opp(opp_id,opp_nm,cust_clnt_id,part_id,sales_step_cd,win_prbl_rt,expct_amt,bgn_plan_ym,opp_stat_cd,aprv_stat_cd,reg_user_id) VALUES (?,?,?,?,'FIND',10,100,'208007','ING','NONE','admin')",opportunity,opportunity,customer,part);
        sql.update("INSERT INTO tb_sl_opp_need(opp_id,need_sn,job_cd,need_cnt,bgn_ym,prd_mcnt,mon_mm,reg_user_id) VALUES (?,1,'BE',1,'208007',2,0.5,'admin')",opportunity);
        tentative=sql.queryForObject("INSERT INTO tb_sl_tent_asgn(opp_id,need_sn,dev_id,bgn_ym,tent_stat_cd,src_scrn_id,reg_user_id) VALUES (?,1,?,'208007','ACT','UI-29','admin') RETURNING tent_id::text",String.class,opportunity,developer);
    }
    @AfterEach void close(){if(context!=null)context.close();}
    String revision(){return context.getBean(TentativeAssignmentRepository.class).revision(opportunity);}
    String body(String revision){return json.createObjectNode().put("expectedRevision",revision).put("reason","사용자 해제 확인").toString();}
    String path(){return "/api/tentative-assignments/"+tentative+"/release";}
    Map<String,Object> tentative(){return sql.queryForMap("SELECT * FROM tb_sl_tent_asgn WHERE tent_id=CAST(? AS numeric)",tentative);}
    Map<String,Object> opportunity(){return sql.queryForMap("SELECT * FROM tb_sl_opp WHERE opp_id=?",opportunity);}
    List<Map<String,Object>> audits(){return sql.queryForList("SELECT * FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_SL_TENT_ASGN' AND trgt_key=? ORDER BY hist_id",tentative);}
    void unchangedActuals(Map<String,List<Map<String,Object>>> before){before.forEach((table,rows)->assertThat(sql.queryForList("SELECT * FROM "+table)).as(table).isEqualTo(rows));}
    Map<String,List<Map<String,Object>>> actuals(){var result=new LinkedHashMap<String,List<Map<String,Object>>>();for(String table:List.of("tb_pj_asgn","tb_pj_asgn_hist","tb_pj_asgn_mon","tb_hr_mon_pay","tb_co_mon_cost"))result.put(table,sql.queryForList("SELECT * FROM "+table));return result;}
    @Test void publicReleaseMaterializesAndPersistsRelProcessingTimeReasonAndParentInOneTransaction() throws Exception {
        var original=tentative();var originalOpportunity=opportunity();var actuals=actuals();
        var mapped=context.getBean(TentativeAssignmentMapper.class).state(tentative);
        assertThat(mapped.id()).isEqualTo(tentative);assertThat(mapped.processedAt()).isNull();assertThat(mapped.developerId()).isEqualTo(developer);
        mvc.perform(post(path()).contentType("application/json").content(body(revision()))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.tentativeId").value(tentative)).andExpect(jsonPath("$.data.opportunityId").value(opportunity));
        var saved=tentative();assertThat(saved.get("tent_stat_cd")).isEqualTo("REL");assertThat(saved.get("proc_dttm")).isNotNull();
        for(String field:List.of("reg_user_id","reg_dttm","opp_id","need_sn","dev_id","bgn_ym","src_scrn_id"))assertThat(saved.get(field)).as(field).isEqualTo(original.get(field));
        assertThat(opportunity().get("reg_dttm")).isEqualTo(originalOpportunity.get("reg_dttm"));assertThat(opportunity().get("mod_user_id")).isEqualTo("admin");
        assertThat(audits()).hasSize(2).allSatisfy(a->{assertThat(a.get("chg_rsn").toString()).contains("사용자 해제 확인","sourceTentId="+tentative);assertThat(a.get("reg_user_id")).isEqualTo("admin");});
        assertThat(audits().get(0)).containsEntry("chg_item_nm","TENT_STAT_CD").containsEntry("bfr_val","ACT").containsEntry("aft_val","REL");unchangedActuals(actuals);
    }
    @Test void latestReplayDoesNotChangeAuditOrAnyTimestamp() throws Exception {
        mvc.perform(post(path()).contentType("application/json").content(body(revision()))).andExpect(status().isOk());
        var original=tentative();var parent=opportunity();var histories=audits();
        mvc.perform(post(path()).contentType("application/json").content(body(revision()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));
        assertThat(tentative()).isEqualTo(original);assertThat(opportunity()).isEqualTo(parent);assertThat(audits()).isEqualTo(histories);
    }
    @Test void auditFailureRollsBackTentativeAndParent() throws Exception {
        var original=tentative();var parent=opportunity();var actuals=actuals();
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_tentative CHECK(trgt_tbl_nm <> 'TB_SL_TENT_ASGN')");
        mvc.perform(post(path()).contentType("application/json").content(body(revision()))).andExpect(status().isInternalServerError());
        assertThat(tentative()).isEqualTo(original);assertThat(opportunity()).isEqualTo(parent);assertThat(audits()).isEmpty();unchangedActuals(actuals);
    }
    @Test void secondAuditFailureRollsBackFirstAuditToo() throws Exception {
        var original=tentative();var parent=opportunity();
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_processing CHECK(chg_item_nm <> 'PROC_DTTM')");
        mvc.perform(post(path()).contentType("application/json").content(body(revision()))).andExpect(status().isInternalServerError());
        assertThat(tentative()).isEqualTo(original);assertThat(opportunity()).isEqualTo(parent);assertThat(audits()).isEmpty();
    }
    @Test void simultaneousReleaseRechecksRevisionAfterLockAndCommitsOnce() throws Exception {
        String request=body(revision());ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<Integer> work=()->{start.await();return mvc.perform(post(path()).contentType("application/json").content(request)).andReturn().getResponse().getStatus();};
            var a=pool.submit(work);var b=pool.submit(work);start.countDown();
            assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(tentative()).containsEntry("tent_stat_cd","REL");assertThat(audits()).hasSize(2);
        } finally {pool.shutdownNow();}
    }
    @Test void approvalHistoryAndPastRelOutsideTheVisibleRowsInvalidateRevision() throws Exception {
        String request=body(revision());
        sql.update("INSERT INTO tb_sl_aprv_hist(opp_id,hist_sn,aprv_act_cd,prft_rt,reg_user_id) VALUES (?,1,'REQ',10,'admin')",opportunity);
        mvc.perform(post(path()).contentType("application/json").content(request)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        request=body(revision());sql.update("INSERT INTO tb_sl_tent_asgn(opp_id,need_sn,dev_id,bgn_ym,tent_stat_cd,src_scrn_id,reg_user_id) VALUES (?,1,?,'208007','REL','UI-05','admin')",opportunity,developer);
        mvc.perform(post(path()).contentType("application/json").content(request)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        assertThat(tentative()).containsEntry("tent_stat_cd","ACT");assertThat(audits()).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"CNVT","DONE","DROP"}) void terminalTentativesAreUnchangedOnRejectedRelease(String state) throws Exception {
        sql.update("UPDATE tb_sl_tent_asgn SET tent_stat_cd=? WHERE tent_id=CAST(? AS numeric)",state,tentative);var original=tentative();var parent=opportunity();
        mvc.perform(post(path()).contentType("application/json").content(body(revision()))).andExpect(status().isConflict());
        assertThat(tentative()).isEqualTo(original);assertThat(opportunity()).isEqualTo(parent);assertThat(audits()).isEmpty();
    }
    @Test void historicalDuplicateActIsConflictWithoutArbitraryCleanup() throws Exception {
        sql.update("INSERT INTO tb_sl_tent_asgn(opp_id,need_sn,dev_id,bgn_ym,tent_stat_cd,src_scrn_id,reg_user_id) VALUES (?,1,?,'208007','ACT','UI-30','admin')",opportunity,developer);
        var before=sql.queryForList("SELECT * FROM tb_sl_tent_asgn ORDER BY tent_id");
        mvc.perform(post(path()).contentType("application/json").content(body(revision()))).andExpect(status().isConflict()).andExpect(jsonPath("$.details.tentativeIds.length()").value(2));
        assertThat(sql.queryForList("SELECT * FROM tb_sl_tent_asgn ORDER BY tent_id")).isEqualTo(before);assertThat(audits()).isEmpty();
    }
}
