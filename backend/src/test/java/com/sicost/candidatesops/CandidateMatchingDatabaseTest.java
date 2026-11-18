package com.sicost.candidatesops;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.apiops.WorkforceForecastService;
import com.sicost.forecast.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.*;
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

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class CandidateMatchingDatabaseTest {
    PostgreSqlDatabase db; AnnotationConfigApplicationContext context; ObjectMapper json; MockMvc mvc;
    String job,grade,source;
    @Configuration @EnableTransactionManagement static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();
        configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-11-30T16:00:00Z"),ZoneOffset.UTC));
        context.registerBean(CandidateMatchingMapper.class,()->session.getMapper(CandidateMatchingMapper.class));
        context.registerBean(SalesForecastMapper.class,()->session.getMapper(SalesForecastMapper.class));
        context.registerBean(AssignmentMonthlyMapper.class,()->session.getMapper(AssignmentMonthlyMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,AssignmentMonthlyService.class,
            SalesForecastRepository.class,SalesWorkforceProjectionService.class,WorkforceForecastService.class,
            CandidateMatchingRepository.class,CandidateMatchingService.class);context.register(com.sicost.common.LegacyBusinessPolicyFixtures.class);context.refresh();
        mvc=MockMvcBuilders.standaloneSetup(new CandidateMatchingController(context.getBean(CandidateMatchingService.class)))
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        db.sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES ('C-match','CUST','후보 시험 고객','1234567890','admin')");
        job=code("JOB");grade=code("GRADE");source=code("CAND_SRC");
    }
    @AfterEach void close() { if(context!=null)context.close(); }
    String code(String group) { return db.sql.queryForObject("SELECT code FROM tb_sy_code WHERE code_grp_id=? AND use_yn='Y' AND del_yn='N' ORDER BY sort_ord,code LIMIT 1",String.class,group); }
    void candidate(String id,String status,String date,String type) {
        db.sql.update("INSERT INTO tb_hr_cand(cand_id,cand_nm,emp_type_cd,job_cd,grade_cd,hope_uprc,input_psbl_dt,cntc_stat_cd,last_cntc_dt,cand_src_cd,reg_user_id) VALUES(?,? ,?,?,?,750000,?::date,?,'2026-12-01',?,'admin')",id,id,type,job,grade,date,status,source);
    }
    void opportunity(String id,String status,String start,int months,String needJob,String needGrade) {
        String customer=db.sql.queryForObject("SELECT clnt_id FROM tb_bs_clnt WHERE use_yn='Y' ORDER BY clnt_id LIMIT 1",String.class);
        String part=db.sql.queryForObject("SELECT part_id FROM tb_bs_part WHERE use_yn='Y' ORDER BY part_id LIMIT 1",String.class);
        db.sql.update("INSERT INTO tb_sl_opp(opp_id,opp_nm,cust_clnt_id,part_id,sales_step_cd,win_prbl_rt,expct_amt,bgn_plan_ym,opp_stat_cd,aprv_stat_cd,reg_user_id) VALUES(?,?,?,?,'FIND',100,1000000,?,?,'NONE','admin')",id,id,customer,part,start,status);
        db.sql.update("INSERT INTO tb_sl_opp_need(opp_id,need_sn,job_cd,grade_cd,need_cnt,bgn_ym,prd_mcnt,mon_mm,expct_uprc,reg_user_id) VALUES(?,1,?,?,2,?,?,1,1000000,'admin')",id,needJob,needGrade,start,months);
    }
    JsonNode read(String path) throws Exception {
        var response=mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }
    Map<String,List<Map<String,Object>>> snapshot() {
        Map<String,List<Map<String,Object>>> result=new TreeMap<>();
        for(String table:List.of("tb_hr_cand","tb_hr_cand_cntc","tb_hr_cand_skill","tb_sl_opp","tb_sl_opp_need","tb_sl_tent_asgn","tb_sl_aprv_hist","tb_pj_proj","tb_pj_asgn","tb_pj_asgn_mon","tb_hr_mon_pay","tb_co_mon_cost","tb_cl_close","tb_sy_chg_hist"))
            result.put(table,db.sql.queryForList("SELECT * FROM "+table+" ORDER BY 1,2"));
        return result;
    }
    @Test void shortageDoneMonthEndIncludedNextDayHoldContractExcludedAndReadsArePure() throws Exception {
        opportunity("O-test","ING","202701",6,job,grade);
        candidate("C-end","DONE","2027-01-31","FL"); candidate("C-next","BEFORE","2027-02-01","FL");
        candidate("C-hold","HOLD","2027-01-01","FL");candidate("C-contract","CTRT","2027-01-01","FL");
        var before=snapshot(); JsonNode result=read("/api/candidates/shortages").get("data");
        assertThat(result.get("includeUnfilled").asBoolean()).isFalse();
        JsonNode row=result.get("items").get(0);
        assertThat(row.get("jobCode").asText()).isEqualTo(job);
        assertThat(row.get("firstShortageMonth").asText()).isEqualTo("2027-01");
        assertThat(row.get("availableCandidateCount").asInt()).isEqualTo(1);
        assertThat(row.get("activeCandidateCount").asInt()).isEqualTo(2);
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void matchingSqlRespectsStateJobEndMonthAndNeverChangesContactOrAudits() throws Exception {
        candidate("C-match","DONE","2027-01-16","FL");
        opportunity("O-same","ING","202612",2,job,grade);
        opportunity("O-ended","ING","202612",1,job,grade);
        opportunity("O-stopped","STOP","202701",2,job,grade);
        String other=db.sql.queryForObject("SELECT code FROM tb_sy_code WHERE code_grp_id='JOB' AND code<>? ORDER BY code LIMIT 1",String.class,job);
        opportunity("O-other","ING","202701",2,other,grade);
        var before=snapshot(); JsonNode data=read("/api/candidates/C-match/positions").get("data");
        assertThat(data.get("total").asInt()).isEqualTo(1);JsonNode row=data.get("items").get(0);
        assertThat(row.get("opportunityId").asText()).isEqualTo("O-same");
        assertThat(row.get("candidateCost").asText()).isEqualTo("850000");
        assertThat(row.get("availableDate").asText()).isEqualTo("2027-01-16");
        assertThat(row.get("belowApprovalThreshold").asBoolean()).isFalse();
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void outsourcedCandidateUsesOnlySupplyCostAndNullNeedRateUsesLiveDefault() throws Exception {
        db.sql.update("INSERT INTO tb_sy_code(code_grp_id,code,code_nm,sort_ord,add_val2,reg_user_id) VALUES('JOB','MATCHJOB','매칭 검증 직무',999,'1000000','admin')");
        job="MATCHJOB";
        candidate("C-os","BEFORE","2027-01-31","OS");opportunity("O-os","ING","202701",1,job,null);
        db.sql.update("UPDATE tb_sl_opp_need SET expct_uprc=NULL WHERE opp_id='O-os'");
        var before=snapshot();var row=read("/api/candidates/C-os/positions").get("data").get("items").get(0);
        assertThat(row.get("candidateCost").asText()).isEqualTo("750000");
        assertThat(row.get("effectiveRate").asText()).isEqualTo("1000000");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void mapperMaterializesAllForecastSourcesOnExistingSchema() {
        var mapper=context.getBean(SalesForecastMapper.class);
        assertThat(mapper.people()).isNotNull();assertThat(mapper.leaves()).isNotNull();assertThat(mapper.assignmentIds()).isNotNull();
        assertThat(mapper.standards("202612")).isNotEmpty();
    }
}
