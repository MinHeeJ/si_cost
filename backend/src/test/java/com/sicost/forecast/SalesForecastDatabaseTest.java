package com.sicost.forecast;

import com.sicost.common.assignments.*;
import com.sicost.common.context.*;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.io.ByteArrayOutputStream;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class SalesForecastDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    String project,opportunity,bill;
    @Configuration @EnableTransactionManagement
    static class Transactions { @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);} }
    @BeforeEach void install() throws Exception {
        var db=new PostgreSqlDatabase();db.install();sql=db.sql;
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addMapper(SalesForecastMapper.class);configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));context=new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class,()->db.source);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(SalesForecastMapper.class,()->session.getMapper(SalesForecastMapper.class));
        context.registerBean(AssignmentMonthlyMapper.class,()->session.getMapper(AssignmentMonthlyMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.registerBean(AccessLogMapper.class,()->session.getMapper(AccessLogMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,AssignmentMonthlyService.class,AccessLogWriter.class,
            ExcelExportService.class,SalesForecastRepository.class,SalesWorkforceProjectionService.class,SalesRevenueForecastService.class,SalesForecastExportService.class);
        context.refresh();
        String key=UUID.randomUUID().toString().replace("-","").substring(0,12);project="P"+key;opportunity="O"+key;bill="B"+key;
        sql.update("INSERT INTO tb_sl_opp(opp_id,opp_nm,cust_clnt_id,part_id,sales_step_cd,win_prbl_rt,expct_amt,bgn_plan_ym,opp_stat_cd,aprv_stat_cd,reg_user_id) SELECT ?,?,clnt_id,'HQ','FIND',50,600,'208008','WIN','WAIT','admin' FROM tb_bs_clnt ORDER BY clnt_id LIMIT 1",opportunity,opportunity);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,opp_id,reg_user_id) VALUES (?,?,'TK','ING','2080-07-01','2080-10-31',150,0,0,?,'admin')",project,project,opportunity);
        sql.update("UPDATE tb_sl_opp SET proj_id=? WHERE opp_id=?",project,opportunity);
        sql.update("INSERT INTO tb_bl_bill(bill_id,proj_id,bill_ord,bill_ym,bill_dt,sply_amt,vat_rt,vat_amt,tot_amt,rcpt_sum_amt,unpaid_amt,pay_due_dt,reg_user_id) VALUES (?,?,1,'208112','2081-12-01',50,0,0,50,0,50,'2081-12-31','admin')",bill,project);
    }
    @AfterEach void close() {if(context!=null)context.close();}
    Map<String,List<String>> state() {
        var result=new LinkedHashMap<String,List<String>>();
        for(String table:List.of("tb_sl_opp","tb_sl_opp_need","tb_sl_tent_asgn","tb_sl_aprv_hist","tb_pj_proj","tb_pj_asgn","tb_pj_asgn_mon","tb_pj_asgn_hist","tb_hr_mon_pay","tb_co_mon_cost","tb_bl_bill","tb_bl_rcpt","tb_cl_close","tb_co_part_setl","tb_co_part_setl_dtl","tb_sy_chg_hist"))
            result.put(table,sql.queryForList("SELECT row_to_json(t)::text FROM "+table+" t ORDER BY row_to_json(t)::text",String.class));
        return result;
    }
    @Test void realPostgresFutureRegisteredBillsAreDeductedAndRepeatedReadsDoNotWrite() {
        var before=state();var service=context.getBean(SalesRevenueForecastService.class);
        var first=service.forecast();var second=service.forecast();
        assertThat(first).isEqualTo(second);
        assertThat(first.revenueSources().stream().filter(r->project.equals(r.sourceId())).map(SalesForecastDtos.RevenueSource::amount)).containsExactly("33","33","34");
        assertThat(first.revenueSources()).noneMatch(r->opportunity.equals(r.sourceId()));assertThat(state()).isEqualTo(before);
        sql.update("UPDATE tb_bl_bill SET cncl_yn='Y' WHERE bill_id=?",bill);
        assertThat(service.forecast().revenueSources().stream().filter(r->project.equals(r.sourceId())).map(SalesForecastDtos.RevenueSource::amount)).containsExactly("50","50","50");
    }
    @Test void downloadCommitsOnlyDnldAndMandatoryAuditFailureReturnsNoDownload() throws Exception {
        var before=state();long count=sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE scrn_id='UI-29' AND act_type_cd='DNLD'",Long.class);
        var exporter=context.getBean(SalesForecastExportService.class);
        try(var download=exporter.export("WON",new MockHttpServletRequest())) {
            assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE scrn_id='UI-29' AND act_type_cd='DNLD'",Long.class)).isEqualTo(count+1);
            var bytes=new ByteArrayOutputStream();download.transferTo(bytes);assertThat(bytes.toByteArray()).startsWith((byte)'P',(byte)'K');
        }
        assertThat(state()).isEqualTo(before);
        sql.execute("CREATE FUNCTION reject_forecast_download() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.scrn_id='UI-29' THEN RAISE EXCEPTION 'private SQL'; END IF; RETURN NEW; END $$");
        sql.execute("CREATE TRIGGER reject_forecast_download BEFORE INSERT ON tb_sy_accs_log FOR EACH ROW EXECUTE FUNCTION reject_forecast_download()");
        assertThatThrownBy(()->exporter.export("WON",new MockHttpServletRequest())).isInstanceOf(com.sicost.common.api.ApiException.class).hasMessageNotContaining("private SQL");
        assertThat(state()).isEqualTo(before);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE scrn_id='UI-29' AND act_type_cd='DNLD'",Long.class)).isEqualTo(count+1);
    }
}
