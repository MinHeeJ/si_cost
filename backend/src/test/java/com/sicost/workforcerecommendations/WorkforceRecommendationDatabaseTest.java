package com.sicost.workforcerecommendations;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.profitability.*;
import com.sicost.candidates.RecommendationActionDtos;
import com.sicost.candidates.RecommendationActionMapper;
import com.sicost.candidates.RecommendationActionRepository;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class WorkforceRecommendationDatabaseTest {
    AnnotationConfigApplicationContext context;JdbcTemplate sql;MockMvc mvc;ObjectMapper json;String project,part;
    @Configuration @EnableTransactionManagement
    static class Transactions {@Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}}
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(WorkforceRecommendationMapper.class,()->session.getMapper(WorkforceRecommendationMapper.class));context.registerBean(AssignmentMonthlyMapper.class,()->session.getMapper(AssignmentMonthlyMapper.class));
        context.registerBean(RecommendationActionMapper.class,()->session.getMapper(RecommendationActionMapper.class));
        context.register(RecommendationActionRepository.class);
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));context.registerBean(SettlementSourceLockMapper.class,()->session.getMapper(SettlementSourceLockMapper.class));
        var costs=mock(MonthlyCostService.class);when(costs.getMonthlyCosts(any())).thenReturn(new MonthlyCostDtos.MonthlyCostsData(null,List.of()));context.registerBean(MonthlyCostService.class,()->costs);
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,CommitInvalidation.class,SettlementSourceLocks.class,AssignmentMonthlyService.class,WorkforceRecommendationRepository.class,WorkforceRecommendationService.class,AiWorkforceScoring.class);context.register(com.sicost.common.LegacyBusinessPolicyFixtures.class);context.refresh();
        mvc=MockMvcBuilders.standaloneSetup(new WorkforceRecommendationController(context.getBean(WorkforceRecommendationService.class))).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        String key=UUID.randomUUID().toString().replace("-","").substring(0,10);project="P-"+key;part="T-"+key;
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,reg_user_id) VALUES (?,?,'admin')",part,part);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2080-07-01','2081-12-31',100,100,0,0,'admin')",project,project);
        for(int i=1;i<=3;i++){String id="D-"+key+i;sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,grade_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FT','MD','BE','DRCT',?,'IDLE','admin')",id,id,id,part);}
        int[] weights={25,20,15,10,5,5,10,10};for(int i=0;i<8;i++)sql.update("INSERT INTO tb_sy_oper_std(std_cd,aply_bgn_ym,std_nm,std_val,unit_nm,reg_user_id) VALUES (?,'208007',?,?,'점','admin')","AI_SCORE_"+AiWorkforceScoring.ITEMS.get(i),"추천 배점",weights[i]);
    }
    @AfterEach void close(){if(context!=null)context.close();}
    String body(){return json.createObjectNode().put("targetType","PROJECT").put("targetId",project).put("jobCode","BE").put("startMonth","2080-07").put("requestedGradeCode","MD").toString();}
    org.springframework.test.web.servlet.ResultActions create() throws Exception{return mvc.perform(post("/api/workforce-recommendations").contentType("application/json").content(body()));}
    int count(String table){return sql.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    @Test void realMapperPreservesSnapshotAfterStandardsChangeAndReadHasNoSideEffects() throws Exception{
        var result=create().andExpect(status().isOk()).andReturn();String id=json.readTree(result.getResponse().getContentAsString()).path("data").path("rcmdId").asText();
        assertThat(count("tb_sl_rcmd_log")).isEqualTo(3);assertThat(count("tb_sy_chg_hist")).isEqualTo(9);
        var actions=context.getBean(RecommendationActionRepository.class);
        var generated=json.readTree(result.getResponse().getContentAsString()).path("data").path("candidates");
        for(var candidate:generated){
            var row=actions.required(new RecommendationActionDtos.Key(id,candidate.path("rank").asInt()));
            var expected=new RecommendationActionDtos.Revisions(candidate.path("revision").asText(),candidate.path("targetRevision").asText(),candidate.path("candidateRevision").asText());
            assertThatCode(()->actions.requireCurrent(row,expected)).doesNotThrowAnyException();
        }
        var before=sql.queryForList("SELECT * FROM tb_sl_rcmd_log ORDER BY rank_ord");
        sql.update("UPDATE tb_sy_oper_std SET std_val=99 WHERE std_cd='AI_SCORE_JOB' AND aply_bgn_ym='208007'");
        mvc.perform(get("/api/workforce-recommendations/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.candidates[0].scoreItems[0].weight").value("25"));
        assertThat(sql.queryForList("SELECT * FROM tb_sl_rcmd_log ORDER BY rank_ord")).isEqualTo(before);assertThat(count("tb_sy_chg_hist")).isEqualTo(9);assertThat(count("tb_hr_mon_pay")).isZero();assertThat(count("tb_co_mon_cost")).isZero();
    }
    @Test void thirdRankConstraintRollsBackAllRowsAndMetadata() throws Exception{sql.execute("ALTER TABLE tb_sl_rcmd_log ADD CONSTRAINT reject_third CHECK(rank_ord <> 3)");create().andExpect(status().isInternalServerError());assertThat(count("tb_sl_rcmd_log")).isZero();assertThat(count("tb_sy_chg_hist")).isZero();}
    @Test void mandatoryAuditConstraintRollsBackEntireRound() throws Exception{sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_recommendation CHECK(trgt_tbl_nm <> 'TB_SL_RCMD_LOG')");create().andExpect(status().isInternalServerError());assertThat(count("tb_sl_rcmd_log")).isZero();assertThat(count("tb_sy_chg_hist")).isZero();}
    @Test void inactivePeopleProduceNoSequenceAllocationOrPlaceholder() throws Exception{sql.update("UPDATE tb_hr_dev SET use_yn='N' WHERE part_id=?",part);create().andExpect(status().isOk()).andExpect(jsonPath("$.data.rcmdId").isEmpty()).andExpect(jsonPath("$.data.candidates").isEmpty());assertThat(count("tb_sl_rcmd_log")).isZero();assertThat(count("tb_sy_chg_hist")).isZero();}
}
