package com.sicost.net;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.businessplansops.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.developers.*;
import com.sicost.operating.OperatingProfitDtos.Query;
import com.sicost.profitability.*;
import com.sicost.shared.*;
import com.sicost.structure.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class NetProfitDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    NetProfitService service;
    NetProfitExportService exports;
    PlatformTransactionManager manager;
    String project;
    final Query query=new Query(2080,"2080-07","WON");
    @Configuration @EnableTransactionManagement
    static class Transactions { @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);} }
    @BeforeEach void install()throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addMapper(AggregateLockMapper.class);configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));context=new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class,()->db.source);context.registerBean(ObjectMapper.class,()->JsonMapper.builder().findAndAddModules().build());
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(MonthlyCostMapper.class,()->session.getMapper(MonthlyCostMapper.class));context.registerBean(DeveloperMapper.class,()->session.getMapper(DeveloperMapper.class));context.registerBean(DeveloperRevisionMapper.class,()->session.getMapper(DeveloperRevisionMapper.class));
        context.registerBean(SharedQueriesMapper.class,()->session.getMapper(SharedQueriesMapper.class));context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(BusinessPlanPerformanceMapper.class,()->session.getMapper(BusinessPlanPerformanceMapper.class));context.registerBean(ProfitStructureMapper.class,()->session.getMapper(ProfitStructureMapper.class));context.registerBean(AccessLogMapper.class,()->session.getMapper(AccessLogMapper.class));
        context.register(Transactions.class,RevisionService.class,DeveloperAggregateRevision.class,CurrentUserProvider.class,DeveloperRepository.class,SharedQueriesService.class,MenuService.class,MonthlyCostRepository.class,MonthlyCostService.class,MonthlyCostCalculation.class,EstimatedPayrollAdapter.class,TurnkeyRevenueCalculation.class,BusinessPlanActuals.class,ProfitStructureRepository.class,RevenueCompositionCalculation.class,NetProfitService.class,NetProfitExportService.class,ExcelExportService.class,AccessLogWriter.class);
        context.refresh();service=context.getBean(NetProfitService.class);exports=context.getBean(NetProfitExportService.class);manager=context.getBean(PlatformTransactionManager.class);
        project="NET"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'TK','LOSE','2080-01-01','2080-12-31',1000,0,0,'admin')",project,project);
        sql.update("INSERT INTO tb_fi_corp_tax(tax_yr,est_tax_amt,reg_user_id) VALUES ('2080',120,'admin')");
    }
    @AfterEach void close() {if(context!=null)context.close();}
    void bill(String date,String cancel,int amount) {
        sql.update("INSERT INTO tb_bl_bill(bill_id,proj_id,bill_ord,bill_ym,bill_dt,sply_amt,vat_rt,vat_amt,tot_amt,rcpt_sum_amt,unpaid_amt,pay_due_dt,cncl_yn,reg_user_id) VALUES (?,?,1,'207912',?::date,?,10,999,9999,9999,0,'2081-01-31',?,'admin')",UUID.randomUUID().toString().replace("-","").substring(0,18),project,date,amount,cancel);
    }
    Map<String,List<String>> state() {
        Map<String,List<String>> result=new LinkedHashMap<>();for(String table:List.of("tb_co_mon_pl","tb_co_mon_cost","tb_hr_mon_pay","tb_pj_asgn_mon","tb_bl_bill","tb_fi_item","tb_fi_corp_tax","tb_fi_tax_pay","tb_cl_close","tb_sy_chg_hist"))result.put(table,sql.queryForList("SELECT row_to_json(t)::text FROM "+table+" t ORDER BY row_to_json(t)::text",String.class));return result;
    }
    @Test void realSourcesNetAndContributionExportAgreeWithoutBusinessMutation()throws Exception {
        bill("2080-07-01","N",100);bill("2080-07-02","Y",999);bill("2081-01-01","N",999);
        var before=state();var data=service.getNetProfitLoss(query);assertThat(data.months().get(6).amounts().get("salesAmt")).isEqualTo("100");assertThat(data.summary().get(1).amount()).isEqualTo(new java.math.BigDecimal("100").subtract(new java.math.BigDecimal("10")).toPlainString());assertThat(service.getNetProfitLoss(query)).isEqualTo(data);
        assertThat(data.contributions().contractTypes().get(0).contributionAmt()).isEqualTo("100");
        try(var file=exports.create(query,new MockHttpServletRequest())) {
            var bytes=new java.io.ByteArrayOutputStream();file.transferTo(bytes);
            try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
                assertThat(file.filename()).isEqualTo("순이익표_208007.xlsx");assertThat(workbook.getSheet("순이익표").getRow(1).getCell(7).getNumericCellValue()).isEqualTo(100);assertThat(workbook.getSheet("기준월 기여도").getRow(1).getCell(3).getNumericCellValue()).isEqualTo(100);
            }
            assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE scrn_id='UI-09' AND act_type_cd='DNLD'",Integer.class)).isEqualTo(1);
        }
        assertThat(state()).isEqualTo(before);
    }
    @Test void currentResponseRetainsSnapshotWhileNextRequestSeesCommittedSources() {
        bill("2080-07-01","N",100);
        var reads=new TransactionTemplate(manager);reads.setReadOnly(true);reads.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        reads.execute(status->{var first=service.getNetProfitLoss(query);CompletableFuture.runAsync(()->sql.update("UPDATE tb_bl_bill SET sply_amt=200 WHERE proj_id=?",project)).join();var second=service.getNetProfitLoss(query);assertThat(second).isEqualTo(first);return null;});
        assertThat(service.getNetProfitLoss(query).months().get(6).amounts().get("salesAmt")).isEqualTo("200");
    }
    @Test void downloadAuditFailureProducesNoFileAndPreservesBusinessState() {
        bill("2080-07-01","N",100);var before=state();
        sql.execute("ALTER TABLE tb_sy_accs_log ADD CONSTRAINT reject_net_download CHECK(scrn_id <> 'UI-09') NOT VALID");
        assertThatThrownBy(()->exports.create(query,new MockHttpServletRequest())).isInstanceOfSatisfying(com.sicost.common.api.ApiException.class,e->assertThat(e.code()).isEqualTo("INTERNAL_ERROR"));assertThat(state()).isEqualTo(before);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE scrn_id='UI-09'",Integer.class)).isZero();
    }
}
