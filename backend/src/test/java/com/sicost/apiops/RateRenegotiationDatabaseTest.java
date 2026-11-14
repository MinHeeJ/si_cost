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
    String developer, project, assignment;
    @Configuration @EnableTransactionManagement
    static class Transactions { @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); } }
    @BeforeEach void install() throws Exception {
        var db=new PostgreSqlDatabase(); db.install(); sql=db.sql;
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addMapper(RateRenegotiationMapper.class);configuration.addMapper(AggregateLockMapper.class);configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
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
        context.refresh();service=context.getBean(RateRenegotiationService.class);
        String key=UUID.randomUUID().toString().replace("-","").substring(0,12);developer="D"+key;project="P"+key;assignment="A"+key;
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FL','BE','DRCT','HQ','WORK','admin')",developer,developer,developer);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2080-07-01','2081-01-01',6000000,0,0,'admin')",project,project);
        sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,bill_uprc,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES (?,?,?,'DEV','2080-07-01','2081-01-01',1,6000000,'NEW',0,'ACT','admin')",assignment,project,developer);
        sql.update("INSERT INTO tb_hr_mon_pay(pay_ym,dev_id,pay_amt,alwnc_amt,insr_cmpny_amt,insr_self_amt,retire_rsv_amt,incm_tax_amt,wthld_tax_amt,real_pay_amt,vndr_sply_amt,emp_type_cd,drct_cls_cd,reg_user_id) VALUES ('208007',?,5400000,0,0,0,0,0,0,5400000,0,'FL','DRCT','admin')",developer);
        sql.update("INSERT INTO tb_sy_oper_std(std_cd,aply_bgn_ym,std_nm,std_val,unit_nm,reg_user_id) VALUES ('TGT_MRGN_RT','208007','시험 목표',20,'%','admin')");
    }
    @AfterEach void close() { if(context!=null)context.close(); }
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
}
