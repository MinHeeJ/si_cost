package com.sicost.receipts;

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
import static com.sicost.receipts.ReceiptDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class ReceiptDatabaseTest {
    AnnotationConfigApplicationContext context;JdbcTemplate sql;ReceiptService service;MockMvc mvc;ObjectMapper json;String id;String project;
    @Configuration @EnableTransactionManagement
    static class Transactions {@Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}}
    @BeforeEach void install()throws Exception{
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(ReceiptMapper.class,()->session.getMapper(ReceiptMapper.class));context.registerBean(SharedQueriesMapper.class,()->session.getMapper(SharedQueriesMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,WriteCoordinator.class,CommitInvalidation.class,ReceiptRepository.class,ReceiptService.class);context.refresh();service=context.getBean(ReceiptService.class);
        mvc=MockMvcBuilders.standaloneSetup(new ReceiptController(service)).setControllerAdvice(new ApiExceptionHandler()).setMessageConverters(new MappingJackson2HttpMessageConverter(json)).addFilters(new RequestIdFilter()).build();
        project="R:"+UUID.randomUUID().toString().replace("-","").substring(0,16);id="B-207901-001";
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'TK','END','2079-01-01','2079-12-31',1000,0,0,0,'admin')",project,project);
        sql.update("INSERT INTO tb_bl_bill(bill_id,proj_id,bill_ord,bill_ym,bill_dt,sply_amt,vat_rt,vat_amt,tot_amt,rcpt_sum_amt,unpaid_amt,pay_due_dt,reg_user_id) VALUES (?,?,1,'207901','2079-01-01',1000,10,100,1100,0,1100,'2080-07-15','admin')",id,project);
        sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('207901','CLOSE','admin')");
    }
    @AfterEach void close(){if(context!=null)context.close();}
    CreateReceipt input(String amount){return new CreateReceipt(2080,service.getBillReceipts(id,2080).revision(),LocalDate.of(2080,7,15),new BigDecimal(amount));}
    BillReceiptResult create(String amount){return service.createBillReceipt(id,input(amount));}
    BillReceiptResult cancel(long receiptId){return service.saveBillReceiptCancellation(id,receiptId,new CancelReceipt(2080,service.getBillReceipts(id,2080).revision(),"오입금"));}
    List<Map<String,Object>> rows(){return sql.queryForList("SELECT * FROM tb_bl_rcpt WHERE bill_id=? ORDER BY rcpt_id",id);}
    Map<String,Object> bill(){return sql.queryForMap("SELECT * FROM tb_bl_bill WHERE bill_id=?",id);}
    List<Map<String,Object>> audits(){return sql.queryForList("SELECT * FROM tb_sy_chg_hist WHERE trgt_tbl_nm IN ('TB_BL_BILL','TB_BL_RCPT') ORDER BY hist_id");}
    void immutable(Map<String,Object> before){var after=bill();for(String key:List.of("sply_amt","vat_rt","vat_amt","tot_amt","bill_ym","bill_dt","pay_due_dt","tax_invc_no","reg_user_id","reg_dttm"))assertThat(after.get(key)).isEqualTo(before.get(key));}
    @Test void publicCreateUsesRealSequenceIndependentRowsAndExactDecimalsThenCancellation()throws Exception{
        var before=bill();var projectBefore=sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?",project);
        var body=json.createObjectNode().put("year",2080).put("date","2080-07-15").put("amount","400").put("expectedRevision",input("400").expectedRevision());
        mvc.perform(post("/api/bills/"+id+"/receipts").contentType("application/json").content(body.toString())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.receipt.amount").value("400")).andExpect(jsonPath("$.data.bill.unpaidAmount").value("700"));
        var paid=create("700");assertThat(paid.bill().status()).isEqualTo("PAID");assertThat(rows()).hasSize(2);assertThat(paid.receipt().id()).isGreaterThan(((BigDecimal)rows().get(0).get("rcpt_id")).longValue());
        assertThat(rows().get(0).get("cncl_rsn")).isNull();assertThat(rows().get(0).get("cncl_user_id")).isNull();assertThat(rows().get(0).get("cncl_dttm")).isNull();
        var cancelled=cancel(paid.receipt().id());assertThat(cancelled.bill().unpaidAmount()).isEqualTo("700");assertThat(cancelled.bill().status()).isEqualTo("UNPAID");assertThat(cancelled.receipt().cancelUserId()).isEqualTo("admin");assertThat(rows()).hasSize(2);immutable(before);
        assertThat(sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?",project)).isEqualTo(projectBefore);
        assertThat(audits()).anySatisfy(a->{assertThat(a.get("trgt_tbl_nm")).isEqualTo("TB_BL_RCPT");assertThat(a.get("trgt_key")).isEqualTo(Long.toString(paid.receipt().id()));assertThat(a.get("chg_type_cd")).isEqualTo("UPD");});
        mvc.perform(get("/api/bills/"+id+"/receipts").param("year","2080")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.normalCount").value(1));
    }
    @Test void readDoesNotRepairCacheWriteHistoryOrConsumeSequence()throws Exception{
        create("1");sql.update("UPDATE tb_bl_bill SET rcpt_sum_amt=77,unpaid_amt=88 WHERE bill_id=?",id);
        var before=bill();var receipts=rows();var history=audits();var sequence=sql.queryForMap("SELECT last_value,is_called FROM sq_tb_bl_rcpt");
        var sent=sql.queryForList("SELECT * FROM tb_bl_dun_hist");
        mvc.perform(get("/api/bills/"+id+"/receipts").param("year","2080")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].amount").value("1"));
        assertThat(bill()).isEqualTo(before);assertThat(rows()).isEqualTo(receipts);assertThat(audits()).isEqualTo(history);assertThat(sql.queryForList("SELECT * FROM tb_bl_dun_hist")).isEqualTo(sent);assertThat(sql.queryForMap("SELECT last_value,is_called FROM sq_tb_bl_rcpt")).isEqualTo(sequence);
    }
    @Test void auditFailureActuallyRollsBackInsertAndCache(){var before=bill();sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_receipt_audit CHECK(trgt_tbl_nm <> 'TB_BL_RCPT')");assertThatThrownBy(()->create("1")).isInstanceOf(RuntimeException.class);assertThat(rows()).isEmpty();assertThat(bill()).isEqualTo(before);assertThat(audits()).isEmpty();}
    @Test void auditFailureActuallyRollsBackCancellationAndCache(){var r=create("1");var before=bill();var receipts=rows();var history=audits();sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_receipt_upd CHECK(NOT(trgt_tbl_nm='TB_BL_RCPT' AND chg_type_cd='UPD'))");assertThatThrownBy(()->cancel(r.receipt().id())).isInstanceOf(RuntimeException.class);assertThat(rows()).isEqualTo(receipts);assertThat(bill()).isEqualTo(before);assertThat(audits()).isEqualTo(history);}
    @Test void repeatedCancellationPreservesAllTimesAndAudits(){var r=create("1");cancel(r.receipt().id());var before=bill();var receipts=rows();var history=audits();assertThat(cancel(r.receipt().id()).changed()).isFalse();assertThat(bill()).isEqualTo(before);assertThat(rows()).isEqualTo(receipts);assertThat(audits()).isEqualTo(history);}
    @Test void revisionIncludesCancelledRowsNullsAndMicrosecondAuditButNotProjectName(){var r=create("1");cancel(r.receipt().id());String before=input("1").expectedRevision();sql.update("UPDATE tb_pj_proj SET proj_nm=? WHERE proj_id=?","변경된 이름",project);assertThat(input("1").expectedRevision()).isEqualTo(before);sql.update("UPDATE tb_bl_rcpt SET mod_dttm='2080-07-15 09:00:00.123456' WHERE rcpt_id=?",r.receipt().id());assertThat(input("1").expectedRevision()).isNotEqualTo(before);}
    @Test void closedReceiptMonthRejectsWithoutMutation(){sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('208007','CLOSE','admin')");var before=bill();assertThatThrownBy(()->create("1")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("CLOSED_MONTH"));assertThat(rows()).isEmpty();assertThat(bill()).isEqualTo(before);}
    @Test void overReceiptLeavesAllRowsAndAuditsUnchanged(){var before=bill();assertThatThrownBy(()->create("1101")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("OVER_RECEIPT"));assertThat(rows()).isEmpty();assertThat(audits()).isEmpty();assertThat(bill()).isEqualTo(before);}
    @Test void concurrentSameRevisionCommitsOnlyOneReceipt()throws Exception{
        var command=input("1");CountDownLatch start=new CountDownLatch(1);var a=CompletableFuture.supplyAsync(()->concurrent(start,command));var b=CompletableFuture.supplyAsync(()->concurrent(start,command));start.countDown();assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","STALE_WRITE");assertThat(rows()).hasSize(1);
    }
    String concurrent(CountDownLatch start,CreateReceipt command){try{if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");service.createBillReceipt(id,command);return "OK";}catch(ApiException e){return e.code();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
