package com.sicost.billsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.shared.SharedQueriesMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static com.sicost.billsops.BillOperationsDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class BillOperationsDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    BillOperationsService service;
    MockMvc mvc;
    String project,developer,assignment,id;
    @Configuration @EnableTransactionManagement
    static class Transactions {@Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}}
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        context.registerBean(ObjectMapper.class,()->JsonMapper.builder().findAndAddModules().build());
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(BillOperationsMapper.class,()->session.getMapper(BillOperationsMapper.class));
        context.registerBean(AssignmentMonthlyMapper.class,()->session.getMapper(AssignmentMonthlyMapper.class));
        context.registerBean(SharedQueriesMapper.class,()->session.getMapper(SharedQueriesMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,WriteCoordinator.class,
            CommitInvalidation.class,AssignmentMonthlyService.class,BillOperationsRepository.class,BillEstimateService.class,BillOperationsService.class);
        context.refresh();service=context.getBean(BillOperationsService.class);
        mvc=MockMvcBuilders.standaloneSetup(new BillOperationsController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        String unique=UUID.randomUUID().toString().replace("-","").substring(0,14);project="P:"+unique;developer="D:"+unique;assignment="A:"+unique;
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2080-01-01','2081-12-31',10000,0,0,'admin')",project,project);
        String part=sql.queryForObject("SELECT part_id FROM tb_bs_part ORDER BY part_id LIMIT 1",String.class);
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'EMP','DEV','DRCT',?,'WORK','admin')",developer,developer,developer,part);
        sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,bill_uprc,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES (?,?,?,'DEV','2080-01-01','2081-12-31',1,1001,'PAID',0,'ACT','admin')",assignment,project,developer);
        int serial=sql.queryForObject("SELECT COALESCE(max(substring(bill_id FROM 10 FOR 3)::int),0)+1 FROM tb_bl_bill WHERE bill_ym='208007'",Integer.class);
        id=String.format(Locale.ROOT,"B-208007-%03d",serial);
        sql.update("INSERT INTO tb_bl_bill(bill_id,proj_id,bill_ord,bill_ym,bill_dt,sply_amt,vat_rt,vat_amt,tot_amt,rcpt_sum_amt,unpaid_amt,pay_due_dt,tax_invc_no,reg_user_id) VALUES (?,?,1,'208007','2080-07-01',1000,7.25,72,1072,0,1072,'2080-08-25','KEEP','admin')",id,project);
    }
    @AfterEach void close() {if(context!=null)context.close();}
    MutationRequest command() {return new MutationRequest(2080,service.revision(id),"취소 사유");}
    Map<String,Object> original() {return sql.queryForMap("SELECT * FROM tb_bl_bill WHERE bill_id=?",id);}
    List<Map<String,Object>> audits() {return sql.queryForList("SELECT * FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_BL_BILL' AND trgt_key IN (SELECT bill_id FROM tb_bl_bill WHERE proj_id=?) ORDER BY hist_id",project);}
    int billCount() {return sql.queryForObject("SELECT count(*) FROM tb_bl_bill WHERE proj_id=?",Integer.class,project);}
    @Test void allFourReadsMaterializeRealPostgresAndNeverWrite() throws Exception {
        var before=original();var audit=audits();
        mvc.perform(get("/api/bills/estimate").param("projectId",project).param("month","2080-08")).andExpect(status().isOk()).andExpect(jsonPath("$.data.supplyAmount").value("1001"));
        mvc.perform(get("/api/bills/summary").param("year","2080")).andExpect(status().isOk()).andExpect(jsonPath("$.data.billed.count").isNumber());
        mvc.perform(get("/api/bills/selection").param("year","2080").param("projectId",project)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].expectedRevision").value(service.revision(id)));
        mvc.perform(get("/api/bills/"+id+"/copy-draft").param("year","2080")).andExpect(status().isOk()).andExpect(jsonPath("$.data.billingMonth").value("2080-08")).andExpect(jsonPath("$.data.vatRate").value("7.25"));
        assertThat(original()).isEqualTo(before);assertThat(audits()).isEqualTo(audit);assertThat(billCount()).isEqualTo(1);
    }
    @Test void nextMonthPersistsExactNewRowAndNewAuditOnly() {
        var before=original();var value=service.createNextMonthBill(id,command());assertThat(value.bill().supplyAmount()).isEqualTo("1001");
        assertThat(value.bill().vatRate()).isEqualTo("7.25");assertThat(value.bill().taxInvoiceNo()).isNull();assertThat(value.bill().billingDate()).isEqualTo(LocalDate.of(2080,7,15));
        assertThat(value.bill().dueDate()).isEqualTo(LocalDate.of(2080,9,25));assertThat(original()).isEqualTo(before);assertThat(billCount()).isEqualTo(2);
        assertThat(audits()).isNotEmpty().allSatisfy(row->assertThat(row.get("chg_type_cd")).isEqualTo("NEW"));
    }
    @Test void cancellationPreservesAmountsAndRecordsLogicalUpd() {
        var before=original();var result=service.saveBillCancellation(id,command());assertThat(result.bill().cancelled()).isTrue();assertThat(result.bill().unpaidAmount()).isEqualTo("0");
        var after=original();for(String column:List.of("sply_amt","vat_rt","vat_amt","tot_amt","tax_invc_no","bill_ord"))assertThat(after.get(column)).isEqualTo(before.get(column));
        assertThat(after.get("cncl_user_id")).isEqualTo("admin");assertThat(audits()).allSatisfy(row->assertThat(row.get("chg_type_cd")).isEqualTo("UPD"));
    }
    @Test void normalReceiptBlocksCancellationAndCancelledReceiptDoesNot() {
        sql.update("INSERT INTO tb_bl_rcpt(bill_id,rcpt_dt,rcpt_amt,reg_user_id) VALUES (?,'2080-07-15',1,'admin')",id);var input=command();var before=original();
        assertThatThrownBy(()->service.saveBillCancellation(id,input)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("INVALID_BILL_STATE"));assertThat(original()).isEqualTo(before);
        sql.update("UPDATE tb_bl_rcpt SET cncl_yn='Y' WHERE bill_id=?",id);service.saveBillCancellation(id,command());assertThat(sql.queryForObject("SELECT count(*) FROM tb_bl_rcpt WHERE bill_id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void auditFailureRollsBackAllNewBillState() {
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_bill_audit CHECK(trgt_tbl_nm <> 'TB_BL_BILL')");var before=original();
        assertThatThrownBy(()->service.createNextMonthBill(id,command())).isInstanceOf(RuntimeException.class);assertThat(billCount()).isEqualTo(1);assertThat(original()).isEqualTo(before);assertThat(audits()).isEmpty();
    }
    @Test void sameConcurrentRequestCreatesOnce() throws Exception {
        var input=command();CountDownLatch start=new CountDownLatch(1);
        var a=CompletableFuture.supplyAsync(()->concurrent(start,input));var b=CompletableFuture.supplyAsync(()->concurrent(start,input));start.countDown();
        assertThat(List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","INVALID_BILL_STATE");assertThat(billCount()).isEqualTo(2);
    }
    String concurrent(CountDownLatch start,MutationRequest input) {try{if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");service.createNextMonthBill(id,input);return "OK";}catch(ApiException e){return e.code();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
