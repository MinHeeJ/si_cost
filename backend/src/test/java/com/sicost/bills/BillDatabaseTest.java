package com.sicost.bills;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.shared.SharedQueriesMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class BillDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    BillService service;
    MockMvc mvc;
    ObjectMapper json;
    String projectId;
    SqlSessionTemplate session;
    @Configuration @EnableTransactionManagement
    static class Transactions {@Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}}
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(BillMapper.class,()->session.getMapper(BillMapper.class));context.registerBean(SharedQueriesMapper.class,()->session.getMapper(SharedQueriesMapper.class));context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.registerBean(com.sicost.receipts.ReceiptMapper.class,()->session.getMapper(com.sicost.receipts.ReceiptMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,WriteCoordinator.class,CommitInvalidation.class,BillRepository.class,BillService.class,com.sicost.receipts.ReceiptRepository.class,com.sicost.receipts.ReceiptService.class);context.refresh();service=context.getBean(BillService.class);
        mvc=MockMvcBuilders.standaloneSetup(new BillController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        projectId="B:"+UUID.randomUUID().toString().replace("-","").substring(0,16);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2079-01-01','2081-12-31',1,0,0,0,'admin')",projectId,"청구 시험 "+projectId);
    }
    @AfterEach void close() {if(context!=null)context.close();}
    com.fasterxml.jackson.databind.node.ObjectNode draft() {return json.createObjectNode().put("year",2080).put("projectId",projectId).put("billingMonth","2080-07").put("supplyAmount","101").put("vatRate","10").put("billingDate","2080-07-15").put("dueDate","2080-08-25");}
    String create() throws Exception {var r=mvc.perform(post("/api/bills").contentType("application/json").content(draft().toString())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.bill.totalAmount").value("111")).andReturn();return json.readTree(r.getResponse().getContentAsString()).at("/data/bill/id").asText();}
    List<Map<String,Object>> bills() {return sql.queryForList("SELECT * FROM tb_bl_bill ORDER BY bill_id");}
    List<Map<String,Object>> history() {return sql.queryForList("SELECT * FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_BL_BILL' ORDER BY hist_id");}
    @Test void phaseSlicesShareBillAndCheckRevisionsAndAcceptListTokenForReceipt() throws Exception {
        String id=create();
        RevisionService revisions=context.getBean(RevisionService.class);
        var operations=session.getMapper(com.sicost.billsops.BillOperationsMapper.class);
        var reviews=session.getMapper(com.sicost.apiops.BillingReviewMapper.class);
        var receipts=context.getBean(com.sicost.receipts.ReceiptService.class);
        String token=service.getBill(id,2080).revision();
        assertThat(revisions.revision(operations.snapshot(id))).isEqualTo(token);
        assertThat(revisions.revision(reviews.billSnapshot(id))).isEqualTo(token);
        assertThat(receipts.getBillReceipts(id,2080).revision()).isEqualTo(token);
        assertThat(revisions.revision(context.getBean(BillRepository.class).checkSnapshot(projectId,"2080-07")))
            .isEqualTo(revisions.revision(reviews.checkSnapshot(new com.sicost.apiops.BillingReviewDtos.CheckKey(projectId,"208007"))));
        assertThat(operations.greatestNumber("208007")).isEqualTo(1);
        var result=receipts.createBillReceipt(id,new com.sicost.receipts.ReceiptDtos.CreateReceipt(2080,token,LocalDate.of(2080,7,15),java.math.BigDecimal.ONE));
        assertThat(result.revision()).isEqualTo(service.getBill(id,2080).revision()).isNotEqualTo(token);
        assertThat(revisions.revision(operations.snapshot(id))).isEqualTo(result.revision());
        assertThat(revisions.revision(reviews.billSnapshot(id))).isEqualTo(result.revision());
    }
    @Test void actualCreateReadUpdateNoopAndAuditAreAtomicAndExact() throws Exception {
        String id=create();assertThat(bills()).hasSize(1);assertThat(bills().get(0).get("reg_user_id")).isEqualTo("admin");assertThat(history()).isNotEmpty();
        mvc.perform(get("/api/bills/"+id).param("year","2080")).andExpect(status().isOk()).andExpect(jsonPath("$.data.bill.supplyAmount").value("101")).andExpect(jsonPath("$.data.receipts.total").value(0));
        var b=draft();b.remove(List.of("projectId","billingMonth"));b.put("expectedRevision",service.getBill(id,2080).revision()).put("taxInvoiceNo","INV-TEST");
        mvc.perform(put("/api/bills/"+id).contentType("application/json").content(b.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        assertThat(sql.queryForObject("SELECT tax_invc_no FROM tb_bl_bill WHERE bill_id=?",String.class,id)).isEqualTo("INV-TEST");var before=bills();var audits=history();
        b.put("expectedRevision",service.getBill(id,2080).revision());mvc.perform(put("/api/bills/"+id).contentType("application/json").content(b.toString())).andExpect(jsonPath("$.data.changed").value(false));assertThat(bills()).isEqualTo(before);assertThat(history()).isEqualTo(audits);
    }
    @Test void readDoesNotRepairCacheOrMultiplyReceiptsByDunning() throws Exception {
        String id=create();sql.update("INSERT INTO tb_bl_rcpt(bill_id,rcpt_dt,rcpt_amt,reg_user_id) VALUES (?,'2080-07-15',11,'admin')",id);
        sql.update("INSERT INTO tb_bl_dun_hist(bill_id,dun_sn,dun_step,send_dt,rcvr_desc,send_mthd_cd,reg_user_id) VALUES (?,1,1,'2080-07-15','시험 수신자','MAIL','admin'),(?,2,1,'2080-07-15','시험 수신자','MAIL','admin')",id,id);
        var before=bills();var audits=history();var receipts=sql.queryForList("SELECT * FROM tb_bl_rcpt");var dunning=sql.queryForList("SELECT * FROM tb_bl_dun_hist");
        mvc.perform(get("/api/bills").param("year","2080")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].receiptAmount").value("11")).andExpect(jsonPath("$.data.totals.unpaidAmount").value("100"));
        mvc.perform(get("/api/bills/"+id).param("year","2080")).andExpect(status().isOk());assertThat(bills()).isEqualTo(before);assertThat(history()).isEqualTo(audits);assertThat(sql.queryForList("SELECT * FROM tb_bl_rcpt")).isEqualTo(receipts);assertThat(sql.queryForList("SELECT * FROM tb_bl_dun_hist")).isEqualTo(dunning);
    }
    @Test void auditFailureRollsBackBillAndNumberAllocation() throws Exception {
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_bill_history CHECK(trgt_tbl_nm <> 'TB_BL_BILL')");
        mvc.perform(post("/api/bills").contentType("application/json").content(draft().toString())).andExpect(status().isInternalServerError());assertThat(bills()).isEmpty();assertThat(history()).isEmpty();
        sql.execute("ALTER TABLE tb_sy_chg_hist DROP CONSTRAINT reject_bill_history");assertThat(create()).isEqualTo("B-208007-001");
    }
    @Test void carryoverMonthStatusAndFooterUseWholeFilteredSet() throws Exception {
        String id=create();sql.update("UPDATE tb_bl_bill SET bill_ym='207912',bill_id='B-207912-001',pay_due_dt='2080-07-14' WHERE bill_id=?",id);
        mvc.perform(get("/api/bills").param("year","2080")).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].carryover").value(true)).andExpect(jsonPath("$.data.items[0].status").value("LATE"));
        mvc.perform(get("/api/bills").param("year","2080").param("includeCarryover","false")).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/bills").param("year","2080").param("month","2080-07")).andExpect(jsonPath("$.data.total").value(0));
        sql.update("UPDATE tb_bl_bill SET cncl_yn='Y',cncl_rsn='시험 취소' WHERE bill_id='B-207912-001'");
        mvc.perform(get("/api/bills").param("year","2079").param("status","CNCL")).andExpect(jsonPath("$.data.items[0].totalAmount").value("111")).andExpect(jsonPath("$.data.items[0].unpaidAmount").value("0")).andExpect(jsonPath("$.data.totals.totalAmount").value("0"));
    }
    @Test void staleCancelledReceiptAndBelowReceiptUpdateLeaveEverythingUnchanged() throws Exception {
        String id=create();var b=draft();b.remove(List.of("projectId","billingMonth"));b.put("expectedRevision",service.getBill(id,2080).revision());
        sql.update("INSERT INTO tb_bl_rcpt(bill_id,rcpt_dt,rcpt_amt,cncl_yn,reg_user_id) VALUES (?,'2080-07-15',100,'N','admin'),(?,'2080-07-15',5,'Y','admin')",id,id);var before=bills();var audits=history();
        mvc.perform(put("/api/bills/"+id).contentType("application/json").content(b.toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        b.put("expectedRevision",service.getBill(id,2080).revision()).put("supplyAmount","90");mvc.perform(put("/api/bills/"+id).contentType("application/json").content(b.toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BILL_TOTAL_BELOW_RECEIPTS"));assertThat(bills()).isEqualTo(before);assertThat(history()).isEqualTo(audits);
    }
}
