package com.sicost.structure;

import com.sicost.businessplansops.BusinessPlanPerformanceMapper;
import com.sicost.businessplansops.BusinessPlanPerformanceDtos.YearRange;
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
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class ProfitStructureDatabaseTest {
    JdbcTemplate sql;
    ProfitStructureMapper mapper;
    BusinessPlanPerformanceMapper financial;
    TransactionTemplate reads;
    final YearRange year=new YearRange("208001","208012");
    String project;
    @BeforeEach void setup()throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));mapper=session.getMapper(ProfitStructureMapper.class);financial=session.getMapper(BusinessPlanPerformanceMapper.class);
        reads=new TransactionTemplate(new DataSourceTransactionManager(db.source));reads.setReadOnly(true);reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        project="STR"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'TK','LOSE','2080-01-01','2080-12-31',1000,0,0,'admin')",project,project);
    }
    void bill(String date,String cancel,int amount) {
        sql.update("INSERT INTO tb_bl_bill(bill_id,proj_id,bill_ord,bill_ym,bill_dt,sply_amt,vat_rt,vat_amt,tot_amt,rcpt_sum_amt,unpaid_amt,pay_due_dt,cncl_yn,reg_user_id) VALUES (?,?,1,'207912',?::date,?,10,999,9999,9999,0,'2081-01-31',?,'admin')",UUID.randomUUID().toString().replace("-","").substring(0,18),project,date,amount,cancel);
    }
    Map<String,List<String>> state() {Map<String,List<String>> result=new LinkedHashMap<>();for(String table:List.of("tb_co_mon_pl","tb_co_mon_cost","tb_hr_mon_pay","tb_bl_bill","tb_fi_item","tb_fi_corp_tax","tb_cl_close","tb_sy_chg_hist","tb_sy_accs_log"))result.put(table,sql.queryForList("SELECT row_to_json(t)::text FROM "+table+" t ORDER BY row_to_json(t)::text",String.class));return result;}
    @Test void actualBillsUseBillDateAndSupplyEvenForLostProjectAndNeverWriteSources() {
        bill("2080-07-01","N",100);bill("2080-07-02","Y",999);bill("2081-01-01","N",999);
        var before=state();reads.execute(status->{assertThat(mapper.bills(year)).singleElement().satisfies(b->{assertThat(b.month()).isEqualTo("2080-07");assertThat(b.amount()).isEqualByComparingTo("100");});assertThat(financial.financialSources(year).stream().filter(s->"tkSales".equals(s.metric())).findFirst().orElseThrow().amount()).isEqualTo("100");mapper.customers();return null;});assertThat(state()).isEqualTo(before);
    }
    @Test void additionalExpensesAreAggregatedBeforeAnyPersonnelJoin() {
        sql.update("INSERT INTO tb_pj_drct_exps(proj_id,exps_ym,exps_sn,drct_exps_item_cd,exps_amt,reg_user_id) VALUES (?,'208007',1,'TRAVEL',7,'admin'),(?,'208007',2,'TRAVEL',3,'admin')",project,project);
        assertThat(mapper.expenses(year)).singleElement().satisfies(e->assertThat(e.amount()).isEqualByComparingTo("10"));
    }
    @Test void oneSnapshotDoesNotMixCommittedBillChangesButNextRequestRefreshes() {
        bill("2080-07-01","N",100);
        reads.execute(status->{assertThat(mapper.bills(year).get(0).amount()).isEqualByComparingTo("100");CompletableFuture.runAsync(()->sql.update("UPDATE tb_bl_bill SET sply_amt=200 WHERE proj_id=?",project)).join();assertThat(mapper.bills(year).get(0).amount()).isEqualByComparingTo("100");return null;});assertThat(mapper.bills(year).get(0).amount()).isEqualByComparingTo("200");
    }
}
