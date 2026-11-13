package com.sicost.receiptsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.apiops.*;
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
class ReceiptBatchDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    MockMvc mvc;
    ObjectMapper json;
    ReceiptBatchRepository repository;
    String project,a,b;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);
        configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(ReceiptBatchMapper.class,()->session.getMapper(ReceiptBatchMapper.class));
        context.registerBean(BillingReviewMapper.class,()->session.getMapper(BillingReviewMapper.class));
        context.registerBean(SharedQueriesMapper.class,()->session.getMapper(SharedQueriesMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,
            WriteCoordinator.class,CommitInvalidation.class,BillingReviewRepository.class,ReceiptBatchRepository.class,ReceiptBatchService.class);
        context.refresh();repository=context.getBean(ReceiptBatchRepository.class);
        mvc=MockMvcBuilders.standaloneSetup(new ReceiptBatchController(context.getBean(ReceiptBatchService.class)))
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,14);
        project="P:"+suffix;a="A:"+suffix;b="B:"+suffix;
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'TK','END','2079-01-01','2079-12-31',10000,0,0,'admin')",project,"입금 시험 "+project);
        for(String id:List.of(a,b))sql.update("INSERT INTO tb_bl_bill(bill_id,proj_id,bill_ord,bill_ym,bill_dt,sply_amt,vat_rt,vat_amt,tot_amt,rcpt_sum_amt,unpaid_amt,pay_due_dt,reg_user_id) VALUES (?,?,1,?,'2079-12-01',1000,10,100,1100,0,1100,'2080-07-14','admin')",id,project,id.equals(a)?"207912":"208007");
    }
    @AfterEach void close() {if(context!=null)context.close();}
    com.fasterxml.jackson.databind.node.ObjectNode body() {
        var body=json.createObjectNode().put("year",2080).put("date","2080-07-15");
        var items=body.putArray("items");
        for(String id:List.of(a,b))items.addObject().put("billId",id).put("amount","500").put("expectedRevision",repository.revision(id));
        return body;
    }
    List<Map<String,Object>> bills() {return sql.queryForList("SELECT * FROM tb_bl_bill WHERE proj_id=? ORDER BY bill_id",project);}
    List<Map<String,Object>> receipts() {return sql.queryForList("SELECT r.* FROM tb_bl_rcpt r JOIN tb_bl_bill b ON b.bill_id=r.bill_id WHERE b.proj_id=? ORDER BY rcpt_id",project);}
    List<Map<String,Object>> audits() {return sql.queryForList("SELECT h.* FROM tb_sy_chg_hist h JOIN tb_bl_rcpt r ON r.rcpt_id::text=h.trgt_key JOIN tb_bl_bill b ON b.bill_id=r.bill_id WHERE h.trgt_tbl_nm='TB_BL_RCPT' AND b.proj_id=? ORDER BY hist_id",project);}
    Map<String,Object> parent() {return sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?",project);}
    @Test void twoBillsCommitIndependentRowsCachesAndNewAuditWithoutRewritingOriginalAmounts() throws Exception {
        var before=parent();
        sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('207912','CLOSE','admin')");
        mvc.perform(post("/api/receipts/batch").contentType("application/json").content(body().toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.completedCount").value(2)).andExpect(jsonPath("$.data.totalAmount").value("1000"))
            .andExpect(jsonPath("$.data.items[0].bill.unpaidAmount").value("600")).andExpect(jsonPath("$.data.items[1].bill.receiptAmount").value("500"));
        assertThat(receipts()).hasSize(2).allSatisfy(r->{assertThat(r.get("cncl_yn")).isEqualTo("N");assertThat(r.get("cncl_rsn")).isNull();assertThat(r.get("reg_user_id")).isEqualTo("admin");});
        assertThat(audits()).hasSize(8).allSatisfy(h->assertThat(h.get("chg_type_cd")).isEqualTo("NEW"));
        assertThat(parent()).isEqualTo(before);
        for(var row:bills()) {assertThat(row.get("sply_amt").toString()).isEqualTo("1000");assertThat(row.get("vat_amt").toString()).isEqualTo("100");assertThat(row.get("unpaid_amt").toString()).isEqualTo("600");}
    }
    @Test void secondReceiptAuditFailureRollsBackEveryReceiptCacheAndHistory() throws Exception {
        var before=bills();var parent=parent();long history=sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist",Long.class);
        sql.execute("CREATE FUNCTION reject_second_receipt_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.trgt_tbl_nm='TB_BL_RCPT' AND EXISTS (SELECT 1 FROM tb_bl_rcpt WHERE rcpt_id::text=NEW.trgt_key AND bill_id='"+b+"') THEN RAISE EXCEPTION 'test audit failure'; END IF; RETURN NEW; END $$");
        sql.execute("CREATE TRIGGER reject_second_receipt_audit BEFORE INSERT ON tb_sy_chg_hist FOR EACH ROW EXECUTE FUNCTION reject_second_receipt_audit()");
        mvc.perform(post("/api/receipts/batch").contentType("application/json").content(body().toString())).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertThat(receipts()).isEmpty();assertThat(bills()).isEqualTo(before);assertThat(parent()).isEqualTo(parent);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist",Long.class)).isEqualTo(history);
    }
    @Test void changedSecondRevisionWritesNothingAndRetainsFirstBill() throws Exception {
        var command=body();sql.update("UPDATE tb_bl_bill SET tax_invc_no='test change' WHERE bill_id=?",b);var before=bills();
        mvc.perform(post("/api/receipts/batch").contentType("application/json").content(command.toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        assertThat(receipts()).isEmpty();assertThat(audits()).isEmpty();assertThat(bills()).isEqualTo(before);
    }
    @Test void cancelledReceiptAndMicrosecondAuditTimestampAreIncludedInRevision() {
        String original=repository.revision(a);
        sql.update("INSERT INTO tb_bl_rcpt(bill_id,rcpt_dt,rcpt_amt,cncl_yn,cncl_rsn,reg_user_id,reg_dttm) VALUES (?,'2080-07-01',1,'Y','시험 취소','admin','2080-07-01 00:00:00.123456')",a);
        String cancelled=repository.revision(a);assertThat(cancelled).isNotEqualTo(original);
        sql.update("UPDATE tb_bl_rcpt SET reg_dttm='2080-07-01 00:00:00.123457' WHERE bill_id=?",a);
        assertThat(repository.revision(a)).isNotEqualTo(cancelled);
    }
}
