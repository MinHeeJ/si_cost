package com.sicost.corporatetaxesops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.shared.SharedQueriesMapper;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static com.sicost.corporatetaxesops.CorporateTaxDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class CorporateTaxDatabaseTest {
    AnnotationConfigApplicationContext context;JdbcTemplate sql;CorporateTaxService service;CorporateTaxViews views;MockMvc mvc;ObjectMapper json;
    @Configuration @EnableTransactionManagement
    static class Transactions {@Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}}
    @BeforeEach void install()throws Exception{
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-10-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(CorporateTaxMapper.class,()->session.getMapper(CorporateTaxMapper.class));context.registerBean(SharedQueriesMapper.class,()->session.getMapper(SharedQueriesMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));context.registerBean(MasterLockMapper.class,()->session.getMapper(MasterLockMapper.class));context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,WriteCoordinator.class,CommitInvalidation.class,CorporateTaxRepository.class,CorporateTaxAllocationService.class,CorporateTaxViews.class,CorporateTaxCommands.class,CorporateTaxService.class);context.refresh();service=context.getBean(CorporateTaxService.class);views=context.getBean(CorporateTaxViews.class);
        mvc=MockMvcBuilders.standaloneSetup(new CorporateTaxController(service)).setControllerAdvice(new ApiExceptionHandler()).setMessageConverters(new MappingJackson2HttpMessageConverter(json)).addFilters(new RequestIdFilter()).build();
    }
    @AfterEach void close(){if(context!=null)context.close();}
    void annual(String year,String amount){sql.update("INSERT INTO tb_fi_corp_tax(tax_yr,est_tax_amt,reg_user_id) VALUES (?,?,'admin')",year,new BigDecimal(amount));}
    void payment(String amount){sql.update("INSERT INTO tb_fi_tax_pay(tax_yr,pay_type_cd,pay_due_dt,pay_amt,pay_yn,reg_user_id) VALUES ('2079','CNFM','2080-03-31',?,'N','admin')",amount==null?null:new BigDecimal(amount));}
    Confirmation input(){return new Confirmation(YearMonth.of(2080,10),views.confirmationRevision("2079","CNFM"),LocalDate.of(2080,9,30),BigDecimal.ZERO);}
    List<Map<String,Object>> payments(){return sql.queryForList("SELECT * FROM tb_fi_tax_pay ORDER BY tax_yr,pay_type_cd");}
    List<Map<String,Object>> annuals(){return sql.queryForList("SELECT * FROM tb_fi_corp_tax ORDER BY tax_yr");}
    List<Map<String,Object>> audits(){return sql.queryForList("SELECT * FROM tb_sy_chg_hist WHERE trgt_tbl_nm IN ('TB_FI_CORP_TAX','TB_FI_TAX_PAY') ORDER BY hist_id");}
    void closed(String month){sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES (?,'CLOSE','admin')",month);}
    @Test void concurrentInitializeInsertsEachMissingRowOnlyOnceAndReplayPreservesTimes()throws Exception{
        annual("2079","18400000");CountDownLatch start=new CountDownLatch(1);
        var first=CompletableFuture.supplyAsync(()->initialize(start));var second=CompletableFuture.supplyAsync(()->initialize(start));start.countDown();
        assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        assertThat(payments()).hasSize(2);assertThat(annuals()).hasSize(2);assertThat(annuals().get(1).get("est_tax_amt")).isEqualTo(new BigDecimal("18400000"));
        assertThat(payments()).extracting(r->r.get("pay_due_dt").toString()).containsExactly("2081-03-31","2080-08-31");
        var before=payments();var annual=annuals();var audit=audits();assertThat(service.createCorporateTaxDefaults(YearMonth.of(2080,10)).changed()).isFalse();assertThat(payments()).isEqualTo(before);assertThat(annuals()).isEqualTo(annual);assertThat(audits()).isEqualTo(audit);
    }
    boolean initialize(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");return service.createCorporateTaxDefaults(YearMonth.of(2080,10)).changed();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
    @Test void initializationAuditFailureRollsBackEveryNewRow(){sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_tax_new CHECK(trgt_tbl_nm <> 'TB_FI_TAX_PAY')");var before=annuals();assertThatThrownBy(()->service.createCorporateTaxDefaults(YearMonth.of(2080,10))).isInstanceOf(RuntimeException.class);assertThat(annuals()).isEqualTo(before);assertThat(payments()).isEmpty();assertThat(audits()).isEmpty();}
    @Test void confirmationCommitsExactlyOnePaymentAndThreeAuditsEvenSameExplicitZero()throws Exception{
        annual("2079","20000000");payment("0");closed("208003");var annual=annuals();var before=payments().get(0);var request=input();
        var body=json.createObjectNode().put("month","2080-10").put("expectedRevision",request.expectedRevision()).put("paidDate","2080-09-30").put("payAmount","0");
        mvc.perform(post("/api/corporate-taxes/2079/payments/CNFM/confirmation").contentType("application/json").content(body.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.payment.payYn").value("Y")).andExpect(jsonPath("$.data.payment.payAmount").value("0"));
        var after=payments().get(0);assertThat(after.get("pay_due_dt")).isEqualTo(before.get("pay_due_dt"));assertThat(after.get("reg_dttm")).isEqualTo(before.get("reg_dttm"));assertThat(annuals()).isEqualTo(annual);assertThat(audits()).hasSize(3).extracting(r->r.get("chg_item_nm")).containsExactly("PAY_DT","PAY_AMT","PAY_YN");
        sql.update("UPDATE tb_fi_corp_tax SET est_tax_amt=1 WHERE tax_yr='2079'");assertThat(views.payment(context.getBean(CorporateTaxRepository.class).payment("2079","CNFM")).displayAmount()).isEqualTo("0");assertThatThrownBy(()->service.saveCorporateTaxPaymentConfirmation("2079","CNFM",input())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("TAX_ALREADY_PAID"));assertThat(payments().get(0)).isEqualTo(after);
    }
    @Test void secondFieldAuditFailureRollsBackAllPaymentFieldsAndEarlierAudit(){payment(null);var before=payments();var history=audits();sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_tax_amount CHECK(chg_item_nm <> 'PAY_AMT')");assertThatThrownBy(()->service.saveCorporateTaxPaymentConfirmation("2079","CNFM",input())).isInstanceOf(RuntimeException.class);assertThat(payments()).isEqualTo(before);assertThat(audits()).isEqualTo(history);}
    @Test void staleConfirmationKeepsPaymentAndAuditUnchanged(){payment(null);var request=input();annual("2079","1");var before=payments();assertThatThrownBy(()->service.saveCorporateTaxPaymentConfirmation("2079","CNFM",request)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));assertThat(payments()).isEqualTo(before);assertThat(audits()).isEmpty();}
    @Test void realAllocationReadsClosedCopiesWithoutAnyWrites()throws Exception{
        annual("2080","20000000");for(int m=1;m<=9;m++){String month=String.format("2080%02d",m);closed(month);sql.update("INSERT INTO tb_co_mon_pl(pl_ym,sales_amt,sales_cost_amt,sga_tot_amt,oprt_prft_amt,corp_tax_amt,reg_user_id) VALUES (?,0,0,0,0,1533333,'admin')",month);}
        var snapshots=sql.queryForList("SELECT * FROM tb_co_mon_pl ORDER BY pl_ym");var annual=annuals();var history=audits();var sequence=sql.queryForMap("SELECT last_value,is_called FROM sq_tb_sy_chg_hist");
        mvc.perform(get("/api/corporate-taxes/2080/allocation")).andExpect(status().isOk()).andExpect(jsonPath("$.data.months[9].amount").value("2066667")).andExpect(jsonPath("$.data.months[11].amount").value("2066669"));
        assertThat(sql.queryForList("SELECT * FROM tb_co_mon_pl ORDER BY pl_ym")).isEqualTo(snapshots);assertThat(annuals()).isEqualTo(annual);assertThat(audits()).isEqualTo(history);assertThat(payments()).isEmpty();assertThat(sql.queryForMap("SELECT last_value,is_called FROM sq_tb_sy_chg_hist")).isEqualTo(sequence);
    }
}
