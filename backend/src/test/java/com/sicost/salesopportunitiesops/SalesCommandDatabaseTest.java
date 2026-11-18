package com.sicost.salesopportunitiesops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.common.projects.*;
import com.sicost.profitability.*;
import com.sicost.projects.*;
import java.math.BigDecimal;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class SalesCommandDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;MockMvc mvc;ObjectMapper json;String opportunity="O-0101",part,developer,customer;
    @Configuration @EnableTransactionManagement
    static class Transactions {@Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}}
    @BeforeEach void install()throws Exception{
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var config=new org.apache.ibatis.session.Configuration();config.addMapper(AggregateLockMapper.class);config.setArgNameBasedConstructorAutoMapping(true);config.setMapUnderscoreToCamelCase(true);config.addInterceptor(new PersistenceBoundary());factory.setConfiguration(config);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(SalesCommandMapper.class,()->session.getMapper(SalesCommandMapper.class));context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));context.registerBean(SettlementSourceLockMapper.class,()->session.getMapper(SettlementSourceLockMapper.class));context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));context.registerBean(ProjectRegistrationMapper.class,()->session.getMapper(ProjectRegistrationMapper.class));
        var costs=mock(MonthlyCostRepository.class);var calculation=mock(MonthlyCostService.class);var projects=mock(ProjectRepository.class);
        context.registerBean(MonthlyCostRepository.class,()->costs);context.registerBean(MonthlyCostService.class,()->calculation);context.registerBean(ProjectRepository.class,()->projects);
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,CommitInvalidation.class,WriteCoordinator.class,SettlementSourceLocks.class,ProjectRegistrationService.class,SalesCommandRepository.class,SalesQuoteCalculation.class,SalesCommandService.class,SalesWinService.class);context.register(com.sicost.common.LegacyBusinessPolicyFixtures.class);context.refresh();
        mvc=MockMvcBuilders.standaloneSetup(new SalesCommandController(context.getBean(SalesCommandService.class),context.getBean(SalesWinService.class))).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        String key=UUID.randomUUID().toString().replace("-","").substring(0,12);part="T-"+key;developer="D-"+key;customer="C-"+key;
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,reg_user_id) VALUES (?,?,'admin')",part,part);
        sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES (?,'CUST',?,right('0000000000'||abs(hashtext(?))::text,10),'admin')",customer,customer,key);
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,grade_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FT','MD','BE','DRCT',?,'IDLE','admin')",developer,developer,developer,part);
        sql.update("INSERT INTO tb_sl_opp(opp_id,opp_nm,cust_clnt_id,part_id,sales_step_cd,win_prbl_rt,expct_amt,bgn_plan_ym,opp_stat_cd,expct_prft_rt,aprv_stat_cd,reg_user_id) VALUES (?,?,?,?,'PROP',30,10000000,'208007','ING',10,'WAIT','admin')",opportunity,"영업기회",customer,part);
        sql.update("INSERT INTO tb_sl_opp_need(opp_id,need_sn,job_cd,grade_cd,need_cnt,bgn_ym,prd_mcnt,mon_mm,expct_uprc,reg_user_id) VALUES (?,1,'BE',NULL,1,'208007',2,0.5,10000000,'admin')",opportunity);
        sql.update("INSERT INTO tb_sl_tent_asgn(opp_id,need_sn,dev_id,bgn_ym,tent_stat_cd,src_scrn_id,reg_user_id) VALUES (?,1,?,'208007','ACT','UI-29','admin')",opportunity,developer);
        sql.update("INSERT INTO tb_hr_mon_pay(pay_ym,dev_id,pay_amt,alwnc_amt,insr_cmpny_amt,retire_rsv_amt,emp_type_cd,drct_cls_cd,reg_user_id) VALUES ('208006',?,9000000,0,0,0,'FT','DRCT','admin')",developer);
        var source=mock(MonthlyCostDtos.Sources.class);var person=mock(MonthlyCostDtos.Person.class);var pay=mock(MonthlyCostDtos.Payroll.class);var row=mock(MonthlyCostDtos.MonthlyPersonCost.class);var cost=mock(MonthlyCostDtos.MonthlyCostRow.class);
        when(costs.loadMonth(2080,6)).thenReturn(source);when(source.people()).thenReturn(List.of(person));when(source.payroll()).thenReturn(List.of(pay));when(source.states()).thenReturn(List.of());
        when(person.personId()).thenReturn(developer);when(person.jobCode()).thenReturn("BE");when(person.gradeCode()).thenReturn("MD");when(pay.personId()).thenReturn(developer);when(pay.month()).thenReturn("208006");when(row.personId()).thenReturn(developer);when(row.completeness()).thenReturn(new MonthlyCostDtos.MonthlyCostCompleteness(true,true,false,true,List.of()));when(row.rows()).thenReturn(List.of(cost));when(cost.laborAllocated()).thenReturn("9000000");when(cost.teamExpense()).thenReturn("0");when(cost.projectExpense()).thenReturn("0");when(calculation.calculate(source)).thenReturn(new MonthlyCostDtos.MonthlyCostsData(null,List.of(row)));
        when(projects.defaults(anyString())).thenReturn(new ProjectDtos.DefaultsRow(new BigDecimal("202501"),new BigDecimal("20"),BigDecimal.ZERO,BigDecimal.ZERO));when(projects.customer(customer)).thenReturn(new ProjectDtos.Reference(customer,customer,true));when(projects.part(part)).thenReturn(new ProjectDtos.Reference(part,part,true));when(projects.activeType("TK")).thenReturn(true);
    }
    @AfterEach void close(){if(context!=null)context.close();}
    String revision(){return context.getBean(RevisionService.class).revision(context.getBean(SalesCommandRepository.class).snapshot(opportunity));}
    String body(String extra){return "{\"expectedRevision\":\""+revision()+"\""+extra+"}";}
    String state(){return sql.queryForObject("SELECT opp_stat_cd FROM tb_sl_opp WHERE opp_id=?",String.class,opportunity);}
    @Test void realMapperMaterializesNullsAndPreviewDoesNotWrite()throws Exception{
        var row=context.getBean(SalesCommandRepository.class).required(opportunity);assertThat(row.projectId()).isNull();assertThat(row.months()).isNull();assertThat(context.getBean(SalesCommandMapper.class).needs(opportunity).get(0).gradeCode()).isNull();
        String before=revision();mvc.perform(post("/api/sales-opportunities/quote-preview").contentType("application/json").content("{\"needs\":[{\"jobCode\":\"BE\",\"count\":1,\"startMonth\":\"2080-07\",\"months\":2,\"monthlyMm\":\"0.5\",\"expectedRate\":\"10000000\"}]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.cost").value("9000000")).andExpect(jsonPath("$.data.rows[0].costBasisMonth").value("2080-06"));assertThat(revision()).isEqualTo(before);assertThat(dbCount("tb_sy_chg_hist")).isZero();
    }
    long dbCount(String table){return sql.queryForObject("SELECT count(*) FROM "+table,Long.class);}
    @Test void rejectionPersistsActualHistoryNotificationAndAuditsAtomically()throws Exception{
        String reason="가".repeat(300);mvc.perform(post("/api/sales-opportunities/"+opportunity+"/approval").contentType("application/json").content(body(",\"action\":\"REJ\",\"reason\":\""+reason+"\""))).andExpect(status().isOk());
        assertThat(sql.queryForObject("SELECT act_rsn FROM tb_sl_aprv_hist WHERE opp_id=?",String.class,opportunity)).isEqualTo(reason);
        assertThat(sql.queryForObject("SELECT prft_rt FROM tb_sl_aprv_hist WHERE opp_id=?",BigDecimal.class,opportunity)).isEqualByComparingTo("10");
        var note=sql.queryForMap("SELECT * FROM tb_sy_noti WHERE link_key=?",opportunity);assertThat(note.get("noti_type_cd")).isEqualTo("QUOTE_REJ");assertThat(note.get("link_scrn_id")).isEqualTo("UI-29");assertThat(note.get("send_mthd_cd")).isEqualTo("SYS");assertThat(note.get("read_dttm")).isNull();assertThat(note.get("noti_cn").toString().length()).isLessThanOrEqualTo(300);
    }
    @Test void notificationFailureRollsBackApprovalAndHistory()throws Exception{
        sql.execute("ALTER TABLE tb_sy_noti ADD CONSTRAINT reject_sales_notification CHECK(noti_type_cd <> 'QUOTE_REJ')");String before=revision();
        mvc.perform(post("/api/sales-opportunities/"+opportunity+"/approval").contentType("application/json").content(body(",\"action\":\"REJ\""))).andExpect(status().isInternalServerError());assertThat(revision()).isEqualTo(before);assertThat(dbCount("tb_sl_aprv_hist")).isZero();assertThat(dbCount("tb_sy_chg_hist")).isZero();
    }
    @Test void stopResumeDoesNotRestoreOldRelAndAuditFailureRollsBack()throws Exception{
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_sales_audit CHECK(trgt_tbl_nm <> 'TB_SL_OPP')");String before=revision();
        mvc.perform(post("/api/sales-opportunities/"+opportunity+"/stop").contentType("application/json").content(body(""))).andExpect(status().isInternalServerError());assertThat(revision()).isEqualTo(before);
        sql.execute("ALTER TABLE tb_sy_chg_hist DROP CONSTRAINT reject_sales_audit");mvc.perform(post("/api/sales-opportunities/"+opportunity+"/stop").contentType("application/json").content(body(""))).andExpect(status().isOk());
        mvc.perform(post("/api/sales-opportunities/"+opportunity+"/resume").contentType("application/json").content(body(""))).andExpect(status().isOk());assertThat(state()).isEqualTo("ING");assertThat(sql.queryForObject("SELECT tent_stat_cd FROM tb_sl_tent_asgn WHERE opp_id=?",String.class,opportunity)).isEqualTo("REL");
    }
    String winBody(){return body(",\"contractAmount\":\"12000000\",\"contractType\":\"TK\",\"startDate\":\"2080-07-01\",\"endDate\":\"2081-06-30\",\"projectStatus\":\"ING\"");}
    void approve()throws Exception{mvc.perform(post("/api/sales-opportunities/"+opportunity+"/approval").contentType("application/json").content(body(",\"action\":\"OK\""))).andExpect(status().isOk());}
    @Test void realWinLinksOneProjectInitialContractPartAndOppPlan()throws Exception{
        approve();mvc.perform(post("/api/sales-opportunities/"+opportunity+"/win").contentType("application/json").content(winBody())).andExpect(status().isOk()).andExpect(jsonPath("$.data.projectId").isNotEmpty());
        String id=sql.queryForObject("SELECT proj_id FROM tb_sl_opp WHERE opp_id=?",String.class,opportunity);assertThat(sql.queryForObject("SELECT opp_id FROM tb_pj_proj WHERE proj_id=?",String.class,id)).isEqualTo(opportunity);assertThat(sql.queryForObject("SELECT src_cd FROM tb_pj_mnpw_plan WHERE proj_id=?",String.class,id)).isEqualTo("OPP");assertThat(sql.queryForObject("SELECT plan_mm FROM tb_pj_mnpw_plan WHERE proj_id=?",BigDecimal.class,id)).isEqualByComparingTo("1.00");assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_proj_part WHERE proj_id=?",Integer.class,id)).isEqualTo(1);assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_proj_chg WHERE proj_id=?",Integer.class,id)).isEqualTo(1);
        mvc.perform(post("/api/sales-opportunities/"+opportunity+"/win").contentType("application/json").content(winBody())).andExpect(status().isConflict());assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_proj WHERE opp_id=?",Integer.class,opportunity)).isEqualTo(1);
    }
    @Test void simultaneousWinCreatesAtMostOneProject()throws Exception{
        approve();String body=winBody();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{Callable<Integer> call=()->{start.await();return mvc.perform(post("/api/sales-opportunities/"+opportunity+"/win").contentType("application/json").content(body)).andReturn().getResponse().getStatus();};var a=pool.submit(call);var b=pool.submit(call);start.countDown();assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_proj WHERE opp_id=?",Integer.class,opportunity)).isEqualTo(1);}finally{pool.shutdownNow();}
    }
}
