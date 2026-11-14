package com.sicost.businessplansops;

import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class BusinessPlanPerformanceDatabaseTest {
    PostgreSqlDatabase db;
    JdbcTemplate sql;
    BusinessPlanPerformanceMapper mapper;
    TransactionTemplate reads;
    final YearRange year=new YearRange("208001","208012");
    @BeforeEach void setup()throws Exception {
        db=new PostgreSqlDatabase();db.install();sql=db.sql;
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);
        configuration.setMapUnderscoreToCamelCase(true);configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        mapper=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject())).getMapper(BusinessPlanPerformanceMapper.class);
        reads=new TransactionTemplate(new DataSourceTransactionManager(db.source));reads.setReadOnly(true);reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    void target(String month,String part,int amount) {sql.update("INSERT INTO tb_fi_biz_plan(plan_ym,part_id,sales_tgt_amt,oprt_prft_tgt_amt,reg_user_id) VALUES (?,?,?,0,'admin')",month,part,amount);}
    Map<String,List<String>> state() {
        Map<String,List<String>> result=new LinkedHashMap<>();
        for(String table:List.of("tb_fi_biz_plan","tb_co_mon_pl","tb_co_mon_cost","tb_hr_mon_pay","tb_bl_bill","tb_fi_item","tb_fi_corp_tax","tb_cl_close","tb_sy_chg_hist","tb_sy_accs_log"))
            result.put(table,sql.queryForList("SELECT row_to_json(t)::text FROM "+table+" t ORDER BY row_to_json(t)::text",String.class));
        return result;
    }
    @Test void companySentinelDoesNotRequirePartAndRepeatedSelectsAreWriteFree() {
        target("208001","ALL",100);target("208002","other-part",900);
        var before=state();
        for(int i=0;i<2;i++)reads.execute(status->{assertThat(mapper.targets(year)).singleElement().satisfies(t->{assertThat(t.month()).isEqualTo("208001");assertThat(t.sales()).isEqualByComparingTo("100");});mapper.snapshots(year);mapper.financialSources(year);mapper.closes(year);mapper.annualTax("2080");return null;});
        assertThat(state()).isEqualTo(before);
    }
    @Test void storedOptionalNullMaterializesWithoutBackfill() {
        sql.update("INSERT INTO tb_co_mon_pl(pl_ym,sales_amt,sales_cost_amt,sga_tot_amt,oprt_prft_amt,migr_yn,fix_yn,reg_user_id) VALUES ('208001',100,20,30,-5,'Y','Y','admin')");
        var stored=mapper.snapshots(year).get(0);assertThat(stored.grossPrftAmt()).isNull();assertThat(stored.oprtPrftAmt()).isEqualByComparingTo("-5");
    }
    @Test void billingUsesBillDateSupplyAndCancellationNotCurrentProjectStatusOrVat() {
        String project="BP"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'TK','LOSE','2080-01-01','2080-12-31',1000,0,0,'admin')",project,project);
        bill(project,"valid", "2080-07-02","N",100);bill(project,"cancelled","2080-07-03","Y",200);bill(project,"outside","2081-01-01","N",300);
        sql.update("INSERT INTO tb_pj_drct_exps(proj_id,exps_ym,exps_sn,drct_exps_item_cd,exps_amt,reg_user_id) VALUES (?,'208007',1,'TRAVEL',7,'admin')",project);
        var rows=mapper.financialSources(year);assertThat(rows.stream().filter(r->"tkSales".equals(r.metric())).map(Source::amount)).containsExactly("100");
        assertThat(rows.stream().filter(r->"directExtra".equals(r.metric())).map(Source::amount)).containsExactly("7");
    }
    void bill(String project,String suffix,String date,String cancel,int amount) {
        String id=UUID.randomUUID().toString().replace("-","").substring(0,18);
        sql.update("INSERT INTO tb_bl_bill(bill_id,proj_id,bill_ord,bill_ym,bill_dt,sply_amt,vat_rt,vat_amt,tot_amt,rcpt_sum_amt,unpaid_amt,pay_due_dt,cncl_yn,reg_user_id) VALUES (?,?,1,'207912',?::date,?,10,999,9999,9999,0,'2081-01-31',?,'admin')",id,project,date,amount,cancel);
    }
    @Test void sourceCommitIsInvisibleInsideOneRepeatableReadButVisibleNextRequest() {
        target("208001","ALL",100);
        reads.execute(status->{
            assertThat(mapper.targets(year).get(0).sales()).isEqualByComparingTo("100");
            CompletableFuture.runAsync(()->sql.update("UPDATE tb_fi_biz_plan SET sales_tgt_amt=200 WHERE plan_ym='208001' AND part_id='ALL'")).join();
            assertThat(mapper.targets(year).get(0).sales()).isEqualByComparingTo("100");return null;
        });
        assertThat(mapper.targets(year).get(0).sales()).isEqualByComparingTo("200");
    }
}
