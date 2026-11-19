package com.sicost.apiops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.assignments.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.developers.*;
import com.sicost.profitability.*;
import com.sicost.shared.*;
import java.time.*;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.util.LinkedMultiValueMap;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class RateRenegotiationDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    RateRenegotiationService service;
    final com.sicost.permission.PermissionService permissions=org.mockito.Mockito.mock(com.sicost.permission.PermissionService.class);
    String developer, project, assignment;
    @Configuration @EnableTransactionManagement
    static class Transactions { @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); } }
    @BeforeEach void install() throws Exception {
        var db=new PostgreSqlDatabase(); db.install(); sql=db.sql;
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addMapper(RateRenegotiationMapper.class);configuration.addMapper(AggregateLockMapper.class);configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        var policies=new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        policies.registerSingleton("permissions",permissions);
        configuration.addInterceptor(new com.sicost.permission.PermissionScopeService(
            policies.getBeanProvider(com.sicost.permission.PermissionService.class),
            Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul"))));
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));context=new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class,()->db.source);context.registerBean(ObjectMapper.class,()->JsonMapper.builder().findAndAddModules().build());
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(MonthlyCostMapper.class,()->session.getMapper(MonthlyCostMapper.class));context.registerBean(DeveloperMapper.class,()->session.getMapper(DeveloperMapper.class));context.registerBean(DeveloperRevisionMapper.class,()->session.getMapper(DeveloperRevisionMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(SharedQueriesMapper.class,()->session.getMapper(SharedQueriesMapper.class));context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(AssignmentMonthlyMapper.class,()->session.getMapper(AssignmentMonthlyMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));context.registerBean(RateRenegotiationMapper.class,()->session.getMapper(RateRenegotiationMapper.class));
        context.register(Transactions.class,RevisionService.class,DeveloperAggregateRevision.class,CurrentUserProvider.class,DeveloperRepository.class,SharedQueriesService.class,MenuService.class,
            MonthlyCostRepository.class,MonthlyCostService.class,MonthlyCostCalculation.class,EstimatedPayrollAdapter.class,TurnkeyRevenueCalculation.class,ChangeHistoryWriter.class,AssignmentMonthlyService.class,
            RateRenegotiationRepository.class,RateRenegotiationCalculator.class,RateRenegotiationService.class);
        context.register(com.sicost.common.LegacyBusinessPolicyFixtures.class);context.refresh();service=context.getBean(RateRenegotiationService.class);
        String key=UUID.randomUUID().toString().replace("-","").substring(0,12);developer="D"+key;project="P"+key;assignment="A"+key;
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FL','BE','DRCT','HQ','WORK','admin')",developer,developer,developer);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2080-07-01','2081-01-01',6000000,0,0,'admin')",project,project);
        sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,bill_uprc,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES (?,?,?,'DEV','2080-07-01','2081-01-01',1,6000000,'NEW',0,'ACT','admin')",assignment,project,developer);
        sql.update("INSERT INTO tb_hr_mon_pay(pay_ym,dev_id,pay_amt,alwnc_amt,insr_cmpny_amt,insr_self_amt,retire_rsv_amt,incm_tax_amt,wthld_tax_amt,real_pay_amt,vndr_sply_amt,emp_type_cd,drct_cls_cd,reg_user_id) VALUES ('208007',?,5400000,0,0,0,0,0,0,5400000,0,'FL','DRCT','admin')",developer);
        sql.update("INSERT INTO tb_sy_oper_std(std_cd,aply_bgn_ym,std_nm,std_val,unit_nm,reg_user_id) VALUES ('TGT_MRGN_RT','208007','시험 목표',20,'%','admin')");
    }
    @AfterEach void close() {
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        if(context!=null)context.close();
    }
    Map<String,List<String>> state() {
        var result=new LinkedHashMap<String,List<String>>();
        for(String table:List.of("tb_pj_asgn","tb_pj_asgn_mon","tb_pj_asgn_hist","tb_hr_mon_pay","tb_co_mon_cost","tb_co_part_setl","tb_co_part_setl_dtl","tb_sy_oper_std","tb_sy_user_pref","tb_sy_chg_hist","tb_bl_bill"))
            result.put(table,sql.queryForList("SELECT row_to_json(t)::text FROM "+table+" t ORDER BY row_to_json(t)::text",String.class));
        return result;
    }
    @Test void realSqlPreviewAllocationAndWholePopulationSimulationDoNotWriteAnySources() {
        var before=state();var params=new LinkedMultiValueMap<String,String>();params.add("renewalWindow","ALL");
        var first=service.getRateRenegotiations(RateRenegotiationInput.query(params));
        var row=first.items().stream().filter(r->assignment.equals(r.assignmentId())).findFirst().orElseThrow();
        assertThat(row.marginRate()).isEqualTo("10.0");assertThat(row.targetUnitPrice()).isEqualTo("6750000");assertThat(row.requiredIncreaseRate()).isEqualTo("12.5");assertThat(row.targetAnnualGain()).isEqualTo("9000000");assertThat(row.annualGain()).isEqualTo("3600000");
        params.set("increaseRate","50");params.set("page","2");
        var next=service.getRateRenegotiations(RateRenegotiationInput.query(params));
        assertThat(next.summary().personCount()).isEqualTo(first.summary().personCount());
        assertThat(state()).isEqualTo(before);
    }
    @Test void projectPartScopeIncludesCrossPartPeopleAndSharesAllSummariesWithExport() {
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES ('OPERATIONS','운영사업파트','N','Y','admin'),('DIGITAL','디지털사업파트','N','Y','admin')");
        sql.update("INSERT INTO tb_pj_proj_part(proj_id,aply_bgn_ym,part_id,reg_user_id) VALUES (?,'208007','OPERATIONS','admin')",project);
        sql.update("UPDATE tb_hr_dev SET dev_nm='이저마진',part_id='DIGITAL' WHERE dev_id=?",developer);
        addPerson("D-second","A-second","임겸임","DIGITAL","2080-12-01",4200000);
        addPerson("D-third","A-third","오원천검증","OPERATIONS","2081-03-01",5400000);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES ('P-digital','유통 운영개발','MO','ING','2080-07-01','2082-01-01',6000000,0,0,'admin')");
        sql.update("INSERT INTO tb_pj_proj_part(proj_id,aply_bgn_ym,part_id,reg_user_id) VALUES ('P-digital','208007','DIGITAL','admin')");
        sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,bill_uprc,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES ('A-digital','P-digital','D-third','DEV','2080-07-01','2081-01-01',1,6000000,'NEW',0,'ACT','admin')");
        var params=new LinkedMultiValueMap<String,String>();
        params.add("renewalWindow","ALL");
        params.add("marginBelow","ALL");
        var query=RateRenegotiationInput.query(params);
        var before=state();
        scope(false,"OPERATIONS");
        var operations=service.getRateRenegotiations(query);
        assertThat(operations.items()).extracting(RateRenegotiationDtos.Row::name)
            .containsExactlyInAnyOrder("이저마진","임겸임","오원천검증");
        assertThat(operations.items()).allMatch(row->row.projectId().equals(project));
        assertThat(operations.summary()).isEqualTo(new RateRenegotiationCalculator().summarize(operations.items()));
        assertThat(operations.summary().personCount()).isEqualTo(3);
        assertThat(service.export(query).summary()).isEqualTo(operations.summary());
        assertThat(service.export(query).items()).isEqualTo(operations.items());
        params.set("page","2");
        var secondPage=service.getRateRenegotiations(RateRenegotiationInput.query(params));
        assertThat(secondPage.items()).isEmpty();
        assertThat(secondPage.summary()).isEqualTo(operations.summary());
        params.clear();
        var defaults=service.getRateRenegotiations(RateRenegotiationInput.query(params));
        assertThat(defaults.items()).extracting(RateRenegotiationDtos.Row::name).containsExactly("이저마진");
        params.add("renewalWindow","ALL");
        params.add("marginBelow","ALL");
        params.add("employeeType","FL");
        assertThat(service.getRateRenegotiations(RateRenegotiationInput.query(params)).items()).hasSize(3);
        scope(false,"DIGITAL");
        var digital=service.getRateRenegotiations(query);
        assertThat(digital.items()).extracting(RateRenegotiationDtos.Row::projectId).containsExactly("P-digital");
        scope(true,"FIN");
        var finance=service.getRateRenegotiations(query);
        assertThat(finance.items()).hasSize(4);
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        assertThat(service.getRateRenegotiations(query)).isEqualTo(finance);
        assertThat(state()).isEqualTo(before);
    }
    private void addPerson(String id,String assignmentId,String name,String part,String end,int pay) {
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FL','BE','DRCT',?,'WORK','admin')",id,id,name,part);
        sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,bill_uprc,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES (?,?,?,'DEV','2080-07-01',?::date,1,6000000,'NEW',0,'ACT','admin')",assignmentId,project,id,end);
        sql.update("INSERT INTO tb_hr_mon_pay(pay_ym,dev_id,pay_amt,alwnc_amt,insr_cmpny_amt,insr_self_amt,retire_rsv_amt,emp_type_cd,drct_cls_cd,reg_user_id) VALUES ('208007',?,?,0,0,0,0,'FL','DRCT','admin')",id,pay);
    }
    private void scope(boolean all,String part) {
        org.mockito.Mockito.when(permissions.capabilities()).thenReturn(new com.sicost.permission.PermissionService.Capabilities(
            Map.of("UI-32",Set.of("INQ","DNLD")),new com.sicost.permission.PermissionService.Scope(all,Set.of(part),Set.of()),
            all,Map.of(),Map.of(),Set.of()));
        org.mockito.Mockito.when(permissions.userId()).thenReturn("leader");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("leader",null,List.of()));
        var request=new org.springframework.mock.web.MockHttpServletRequest("GET","/api/rate-renegotiations");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
            new org.springframework.web.context.request.ServletRequestAttributes(request));
    }
}
