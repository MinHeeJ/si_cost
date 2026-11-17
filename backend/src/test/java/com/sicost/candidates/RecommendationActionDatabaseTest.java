package com.sicost.candidates;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.apiops.*;
import com.sicost.assignmentcandidatesops.*;
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
import static com.sicost.candidates.RecommendationActionDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class RecommendationActionDatabaseTest {
    AnnotationConfigApplicationContext context;JdbcTemplate sql;MockMvc mvc;ObjectMapper json;
    RecommendationActionRepository repository;RecommendationActionMapper mapper;
    String opportunity,previous,developer,second,project,part;String round="101";
    @Configuration @EnableTransactionManagement
    static class Transactions {@Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}}
    @BeforeEach void install()throws Exception{
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        register(AssignmentActionsMapper.class,session);register(AssignmentMapper.class,session);register(AssignmentMonthlyMapper.class,session);register(AggregateLockMapper.class,session);register(CurrentUserMapper.class,session);register(ChangeHistoryMapper.class,session);register(MasterIdMapper.class,session);register(AssignmentCandidateMapper.class,session);register(SettlementSourceLockMapper.class,session);register(TentativeSelectionMapper.class,session);register(RecommendationActionMapper.class,session);
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,CommitInvalidation.class,MasterIdService.class,AssignmentMonthlyService.class,AssignmentActionsRepository.class,AssignmentActionsService.class,AssignmentRepository.class,AssignmentService.class,SettlementSourceLocks.class,AssignmentCandidateRepository.class,TentativeSelectionService.class,RecommendationActionRepository.class,RecommendationActionService.class,RecommendationAssignmentSelection.class);context.register(com.sicost.common.LegacyBusinessPolicyFixtures.class);context.refresh();
        repository=context.getBean(RecommendationActionRepository.class);mapper=context.getBean(RecommendationActionMapper.class);
        mvc=MockMvcBuilders.standaloneSetup(new RecommendationActionController(context.getBean(RecommendationActionService.class)),new AssignmentController(context.getBean(AssignmentService.class))).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        String key=UUID.randomUUID().toString().replace("-","").substring(0,12);part="T-"+key;developer="D-"+key;second="E-"+key;project="P-"+key;opportunity="O-"+key;previous="R-"+key;
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,reg_user_id) VALUES (?,?,'admin')",part,part);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2080-07-10','2081-12-31',100,100,0,0,'admin')",project,project);
        for(String id:List.of(developer,second)){
            sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FT','BE','DRCT',?,'IDLE','admin')",id,id,id,part);
            sql.update("INSERT INTO tb_hr_dev_prc(dev_id,aply_bgn_ym,mon_uprc,reg_user_id) VALUES (?,'208001',101,'admin')",id);
        }
        String customer="C-"+key;sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES (?,'CUST',?,right('0000000000'||abs(hashtext(?))::text,10),'admin')",customer,customer,key);
        for(String id:List.of(opportunity,previous)){
            sql.update("INSERT INTO tb_sl_opp(opp_id,opp_nm,cust_clnt_id,part_id,sales_step_cd,win_prbl_rt,expct_amt,bgn_plan_ym,opp_stat_cd,aprv_stat_cd,ctrt_type_cd,reg_user_id) VALUES (?,?,?,?,'PRE',100,100,'208007','ING','NONE','MO','admin')",id,id,customer,part);
            for(int seat:List.of(1,2))sql.update("INSERT INTO tb_sl_opp_need(opp_id,need_sn,job_cd,need_cnt,bgn_ym,prd_mcnt,mon_mm,expct_uprc,reg_user_id) VALUES (?,?,'BE',1,'208007',6,0.5,999,'admin')",id,seat);
        }
        log("101",1,developer);log("101",3,second);
    }
    <T> void register(Class<T> type,SqlSessionTemplate session){context.registerBean(type,()->session.getMapper(type));}
    @AfterEach void close(){if(context!=null)context.close();}
    void log(String id,int rank,String dev){sql.update("INSERT INTO tb_sl_rcmd_log(rcmd_id,rank_ord,rcmd_type_cd,opp_id,need_sn,job_cd,bgn_ym,req_grade_cd,dev_id,tot_score,score_dtl,rcmd_rsn,reg_user_id) VALUES (CAST(? AS numeric),?,'AI',?,1,'BE','208007','SR',?,80,'{}','검토 근거','admin')",id,rank,opportunity,dev);}
    ObjectNode body(String id,int rank,String action){var row=repository.required(new Key(id,rank));var r=repository.current(row);var b=json.createObjectNode().put("expectedRevision",r.expectedRevision());if(action.equals("exclude"))return b.put("reasonCode","SCHD");b.put("needSn","2").put("targetRevision",r.targetRevision()).put("candidateRevision",r.candidateRevision());b.putArray("confirmedWarnings");return b;}
    org.springframework.test.web.servlet.ResultActions call(String id,int rank,String action,ObjectNode body)throws Exception{return mvc.perform(post("/api/workforce-recommendations/"+id+"/candidates/"+rank+"/"+action).contentType("application/json").content(body.toString()));}
    int count(String table){return sql.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    String action(){return sql.queryForObject("SELECT user_act_cd FROM tb_sl_rcmd_log WHERE rcmd_id=101 AND rank_ord=1",String.class);}
    String active(String opp,String dev){return sql.queryForObject("INSERT INTO tb_sl_tent_asgn(opp_id,need_sn,dev_id,bgn_ym,tent_stat_cd,src_scrn_id,reg_user_id) VALUES (?,1,?,'208007','ACT','UI-29','admin') RETURNING tent_id::text",String.class,opp,dev);}
    @Test void exclusionPersistsFourFieldsAndReplayRetainsOtherRankAndSnapshot()throws Exception{
        var before=sql.queryForMap("SELECT * FROM tb_sl_rcmd_log WHERE rcmd_id=101 AND rank_ord=3");var b=body("101",1,"exclude");
        call("101",1,"exclude",b).andExpect(status().isOk());assertThat(action()).isEqualTo("EXCL");assertThat(mapper.excluded(repository.required(new Key("101",1)))).isTrue();
        int audits=count("tb_sy_chg_hist");sql.update("UPDATE tb_hr_dev_prc SET mon_uprc=102 WHERE dev_id=?",developer);
        call("101",1,"exclude",b).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));assertThat(count("tb_sy_chg_hist")).isEqualTo(audits);assertThat(sql.queryForMap("SELECT * FROM tb_sl_rcmd_log WHERE rcmd_id=101 AND rank_ord=3")).isEqualTo(before);
    }
    @Test void exclusionIdentityIgnoresMonthGradeSeatAndDoesNotCrossJobOrTarget()throws Exception{
        call("101",1,"exclude",body("101",1,"exclude")).andExpect(status().isOk());Row r=repository.required(new Key("101",1));
        assertThat(mapper.excluded(new Row(r.rcmdId(),r.rank(),r.type(),r.opportunityId(),"2",null,r.jobCode(),"208009",r.developerId(),r.scoreDetail(),null,null,null,null,r.registeredAt()))).isTrue();
        assertThat(mapper.excluded(new Row(r.rcmdId(),r.rank(),r.type(),r.opportunityId(),r.needSn(),null,"FE",r.startMonth(),r.developerId(),r.scoreDetail(),null,null,null,null,r.registeredAt()))).isFalse();
        assertThat(mapper.excluded(new Row(r.rcmdId(),r.rank(),r.type(),null,null,project,r.jobCode(),r.startMonth(),r.developerId(),r.scoreDetail(),null,null,null,null,r.registeredAt()))).isFalse();
    }
    @Test void stopResumeKeepsExclusionHistoryButStartsNewLifecycle()throws Exception{
        call("101",1,"exclude",body("101",1,"exclude")).andExpect(status().isOk());
        sql.update("INSERT INTO tb_sy_chg_hist(trgt_tbl_nm,trgt_key,chg_type_cd,chg_item_nm,bfr_val,aft_val,reg_user_id,reg_dttm) VALUES ('TB_SL_OPP',?,'RESUME','OPP_STAT_CD','STOP','ING','admin','2080-07-16')",opportunity);
        assertThat(mapper.excluded(repository.required(new Key("101",1)))).isFalse();assertThat(action()).isEqualTo("EXCL");assertThat(count("tb_sl_rcmd_log")).isEqualTo(2);
    }
    @Test void exclusionAuditFailureRollsBackSelectionAndMetadata()throws Exception{
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_recommendation CHECK(trgt_tbl_nm <> 'TB_SL_RCMD_LOG')");
        call("101",1,"exclude",body("101",1,"exclude")).andExpect(status().isInternalServerError());assertThat(action()).isNull();assertThat(count("tb_sy_chg_hist")).isZero();assertThat(mapper.excluded(repository.required(new Key("101",1)))).isFalse();
    }
    @Test void tentativeCallsCommonServiceAndLinksFinalSeatWithoutCreatingActualAssignment()throws Exception{
        var b=body("101",1,"tentative-assignment");call("101",1,"tentative-assignment",b).andExpect(status().isOk()).andExpect(jsonPath("$.data.selection.finalNeedSn").value("2"));
        assertThat(sql.queryForObject("SELECT need_sn FROM tb_sl_rcmd_log WHERE rcmd_id=101 AND rank_ord=1",Integer.class)).isEqualTo(1);assertThat(sql.queryForObject("SELECT src_scrn_id FROM tb_sl_tent_asgn",String.class)).isEqualTo("UI-30");assertThat(count("tb_pj_asgn")).isZero();
        int audits=count("tb_sy_chg_hist");call("101",1,"tentative-assignment",b).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));assertThat(count("tb_sl_tent_asgn")).isEqualTo(1);assertThat(count("tb_sy_chg_hist")).isEqualTo(audits);
    }
    @Test void failedMoveRollsBackOldReleaseBothOpportunityTimestampsAndChoice()throws Exception{
        active(previous,developer);var old=sql.queryForList("SELECT * FROM tb_sl_tent_asgn");var opportunities=sql.queryForList("SELECT * FROM tb_sl_opp ORDER BY opp_id");
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_choice CHECK(chg_item_nm <> 'USER_ACT_CD')");var b=body("101",1,"tentative-assignment");b.putArray("confirmedWarnings").add("TENTATIVE_MOVE");
        call("101",1,"tentative-assignment",b).andExpect(status().isInternalServerError());assertThat(sql.queryForList("SELECT * FROM tb_sl_tent_asgn")).isEqualTo(old);assertThat(sql.queryForList("SELECT * FROM tb_sl_opp ORDER BY opp_id")).isEqualTo(opportunities);assertThat(action()).isNull();assertThat(count("tb_sy_chg_hist")).isZero();
    }
    @Test void confirmedMoveReleasesOnlyThisDeveloperAndKeepsOneActive()throws Exception{
        active(previous,developer);var b=body("101",1,"tentative-assignment");b.putArray("confirmedWarnings").add("TENTATIVE_MOVE");call("101",1,"tentative-assignment",b).andExpect(status().isOk());
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sl_tent_asgn WHERE tent_stat_cd='ACT'",Integer.class)).isEqualTo(1);assertThat(sql.queryForObject("SELECT count(*) FROM tb_sl_tent_asgn WHERE tent_stat_cd='REL'",Integer.class)).isEqualTo(1);assertThat(count("tb_pj_asgn")).isZero();
    }
    @Test void concurrentLastSlotHasExactlyOneWinnerAndSlotFullLoser()throws Exception{
        var a=body("101",1,"tentative-assignment");var b=body("101",3,"tentative-assignment");ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{var first=pool.submit(()->{start.await();return call("101",1,"tentative-assignment",a).andReturn().getResponse();});var next=pool.submit(()->{start.await();return call("101",3,"tentative-assignment",b).andReturn().getResponse();});start.countDown();var results=List.of(first.get(30,TimeUnit.SECONDS),next.get(30,TimeUnit.SECONDS));assertThat(results.stream().map(r->r.getStatus()).toList()).containsExactlyInAnyOrder(200,409);assertThat(results.stream().filter(r->r.getStatus()==409).findFirst().orElseThrow().getContentAsString()).contains("SLOT_FULL");assertThat(sql.queryForObject("SELECT count(*) FROM tb_sl_tent_asgn WHERE tent_stat_cd='ACT'",Integer.class)).isEqualTo(1);}finally{pool.shutdownNow();}
    }
    @Test void concurrentSameCardReplaysCommittedResultWithoutSecondTentativeOrAudit() throws Exception {
        var request=body("101",1,"tentative-assignment");
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<org.springframework.mock.web.MockHttpServletResponse> task=()->{start.await();return call("101",1,"tentative-assignment",request).andReturn().getResponse();};
            var first=pool.submit(task);var second=pool.submit(task);start.countDown();
            var results=List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
            assertThat(results).allSatisfy(r->assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200));
            assertThat(results.stream().map(r->{try{return json.readTree(r.getContentAsString()).path("data").path("changed").asBoolean();}catch(Exception e){throw new RuntimeException(e);}}).toList()).containsExactlyInAnyOrder(true,false);
            assertThat(count("tb_sl_tent_asgn")).isEqualTo(1);
            int audits=count("tb_sy_chg_hist");
            call("101",1,"tentative-assignment",request).andExpect(status().isOk());
            assertThat(count("tb_sy_chg_hist")).isEqualTo(audits);
        } finally {pool.shutdownNow();}
    }
    @Test void changedOffPageMonthAndPayrollRejectOriginalRevision()throws Exception{
        var b=body("101",1,"tentative-assignment");sql.update("UPDATE tb_hr_dev_prc SET mon_pay_amt=200 WHERE dev_id=?",developer);call("101",1,"tentative-assignment",b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));assertThat(count("tb_sl_tent_asgn")).isZero();assertThat(action()).isNull();
    }
    @Test void ordinaryAssignmentRecommendationReferenceIsAtomicAndReplayUsesSameId()throws Exception{
        sql.update("UPDATE tb_sl_rcmd_log SET opp_id=NULL,need_sn=NULL,proj_id=? WHERE rcmd_id=101 AND rank_ord=1",project);
        ObjectNode b=json.createObjectNode().put("year",2080).put("projectId",project).put("developerId",developer).put("jobCode","BE").put("startDate","2080-07-10").put("endDate","2080-07-31").put("contractMm","1.00").put("inputType","NEW");b.putObject("recommendationRef").put("rcmdId","101").put("rank",1);
        var response=mvc.perform(post("/api/assignments").contentType("application/json").content(b.toString())).andExpect(status().isCreated()).andReturn();String id=json.readTree(response.getResponse().getContentAsString()).path("data").path("assignment").path("id").asText();assertThat(action()).isEqualTo("ASGN");
        mvc.perform(post("/api/assignments").contentType("application/json").content(b.toString())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.assignment.id").value(id));assertThat(count("tb_pj_asgn")).isEqualTo(1);
    }
}
