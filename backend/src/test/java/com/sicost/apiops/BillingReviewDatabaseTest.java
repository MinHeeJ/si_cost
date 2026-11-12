package com.sicost.apiops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.concurrent.RevisionService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static com.sicost.apiops.BillingReviewDtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class BillingReviewDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    BillingCheckService checks;
    ReceivableService receivables;
    String project,assignment,developer,bill;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var config=new org.apache.ibatis.session.Configuration();config.setArgNameBasedConstructorAutoMapping(true);config.setMapUnderscoreToCamelCase(true);config.addInterceptor(new PersistenceBoundary());factory.setConfiguration(config);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        context.registerBean(ObjectMapper.class,()->JsonMapper.builder().findAndAddModules().build());
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(BillingReviewMapper.class,()->session.getMapper(BillingReviewMapper.class));
        context.registerBean(SharedQueriesMapper.class,()->session.getMapper(SharedQueriesMapper.class));
        context.registerBean(AssignmentMonthlyMapper.class,()->session.getMapper(AssignmentMonthlyMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,RevisionService.class,ChangeHistoryWriter.class,AssignmentMonthlyService.class,BillingReviewRepository.class,BillingCheckService.class,ReceivableService.class);context.refresh();
        checks=context.getBean(BillingCheckService.class);receivables=context.getBean(ReceivableService.class);
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,14);project="P:"+suffix;assignment="A:"+suffix;developer="D:"+suffix;bill="B:"+suffix;
        String part=sql.queryForObject("SELECT part_id FROM tb_bs_part ORDER BY part_id LIMIT 1",String.class);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','END','2079-01-01','2080-12-31',100000,0,0,'admin')",project,project);
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'EMP','JAVA','DRCT',?,'WORK','admin')",developer,developer,developer,part);
        sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,bill_uprc,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES (?,?,?,'JAVA','2080-07-01','2080-07-31',0.50,100001,'PAID',0,'ACT','admin')",assignment,project,developer);
    }
    @AfterEach void close() {if(context!=null)context.close();}
    void bill(String id,String cancelled,String date,String due,int supply) {
        sql.update("INSERT INTO tb_bl_bill(bill_id,proj_id,bill_ord,bill_ym,bill_dt,sply_amt,vat_rt,vat_amt,tot_amt,rcpt_sum_amt,unpaid_amt,pay_due_dt,cncl_yn,reg_user_id) VALUES (?,?,1,'208007',?::date,?,10,0,?,0,?,?::date,?,'admin')",id,project,date,supply,supply,supply,due,cancelled);
    }
    CheckRow row() {return checks.getBillingChecks(new PageQuery("2080-07",1,50)).items().stream().filter(r->project.equals(r.projectId())).findFirst().orElseThrow();}
    Map<String,List<Map<String,Object>>> snapshot() {
        Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();
        for(String table:List.of("tb_bl_bill","tb_bl_rcpt","tb_bl_dun_hist","tb_pj_asgn","tb_pj_asgn_mon","tb_sy_chg_hist","tb_sy_accs_log"))
            result.put(table,sql.queryForList("SELECT * FROM "+table));
        return result;
    }
    @Test void selectedEngineReadsSourcesSumsOnlyActiveBillsAndNeverChangesRowsOrSequences() {
        bill(bill,"N","2080-07-02","2080-07-14",10000);bill(bill+"x","N","2080-07-03","2080-07-14",15000);bill(bill+"c","Y","2080-07-04","2080-07-14",999);
        var before=snapshot();var sequence=sql.queryForMap("SELECT last_value,is_called FROM sq_tb_bl_rcpt");
        var row=row();assertThat(row.estimatedAmount()).isEqualTo("50000");assertThat(row.billedAmount()).isEqualTo("25000");assertThat(row.billCount()).isEqualTo(2);assertThat(row.remedy().billId()).isEqualTo(bill+"x");assertThat(row.remedy().proposedSupplyAmount()).isEqualTo("40000");
        receivables.getReceivables(new PageQuery(null,1,50));assertThat(snapshot()).isEqualTo(before);assertThat(sql.queryForMap("SELECT last_value,is_called FROM sq_tb_bl_rcpt")).isEqualTo(sequence);
    }
    @Test void protectedMonthlySnapshotSurvivesCancelledAssignmentWithoutRevaluation() {
        sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('208007','CLOSE','admin')");
        sql.update("INSERT INTO tb_pj_asgn_mon(asgn_id,input_ym,dev_id,proj_id,input_day_cnt,mon_mm,aply_uprc,sales_amt,fix_yn,reg_user_id) VALUES (?,'208007',?,?,31,0.25,12345,3086,'Y','admin')",assignment,developer,project);
        sql.update("UPDATE tb_pj_asgn SET asgn_stat_cd='CNCL',bill_uprc=999999 WHERE asgn_id=?",assignment);
        var row=row();assertThat(row.estimatedAmount()).isEqualTo("3086");assertThat(row.sources().get(0).protectedSource()).isTrue();assertThat(row.remedy().mode()).isEqualTo("DETAIL_ONLY");
    }
    @Test void receiptAndHistoryAreNeverCrossJoinedAndOnlyRealSortedHistoryIsReturned() {
        bill(bill,"N","2079-12-01","2080-07-14",10000);
        sql.update("INSERT INTO tb_bl_rcpt(bill_id,rcpt_dt,rcpt_amt,reg_user_id) VALUES (?,'2080-07-15',100,'admin'),(?,'2080-07-15',200,'admin')",bill,bill);
        sql.update("INSERT INTO tb_bl_rcpt(bill_id,rcpt_dt,rcpt_amt,cncl_yn,reg_user_id) VALUES (?,'2080-07-15',999,'Y','admin')",bill);
        sql.update("INSERT INTO tb_bl_dun_hist(bill_id,dun_sn,dun_step,send_dt,rcvr_desc,send_mthd_cd,resend_yn,reg_user_id) VALUES (?,2,1,'2080-07-14','과거수신2','EMAIL','Y','admin'),(?,1,1,'2080-07-13','과거수신1','EMAIL','N','admin')",bill,bill);
        var before=snapshot();var result=receivables.getReceivables(new PageQuery(null,1,50));var row=result.items().stream().filter(r->bill.equals(r.billId())).findFirst().orElseThrow();
        assertThat(row.unpaidAmount()).isEqualTo("9700");assertThat(row.dunning().step()).isZero();assertThat(row.sentHistory()).extracting(DunningHistory::sn).containsExactly(1,2);assertThat(row.sentHistory().get(1).resend()).isTrue();assertThat(snapshot()).isEqualTo(before);
    }
    @Test void previewRevisionTracksReceiptAndSourceChangesEvenCancelledReceipts() {
        bill(bill,"N","2080-07-01","2080-07-14",1);String initial=row().checkRevision();
        sql.update("INSERT INTO tb_bl_rcpt(bill_id,rcpt_dt,rcpt_amt,cncl_yn,reg_user_id) VALUES (?,'2080-07-15',1,'Y','admin')",bill);
        String receipt=row().checkRevision();assertThat(receipt).isNotEqualTo(initial);
        sql.update("UPDATE tb_pj_asgn SET bill_uprc=100002 WHERE asgn_id=?",assignment);assertThat(row().checkRevision()).isNotEqualTo(receipt);
    }
    @Test void preLostTurnkeyAndInternalProjectsAreExcludedFromChecks() {
        for(String state:List.of("PRE","LOSE")) {sql.update("UPDATE tb_pj_proj SET proj_stat_cd=? WHERE proj_id=?",state,project);assertThat(checks.getBillingChecks(new PageQuery("2080-07",1,50)).items()).noneMatch(r->project.equals(r.projectId()));}
        for(String type:List.of("TK","IN")) {sql.update("UPDATE tb_pj_proj SET proj_stat_cd='ING',ctrt_type_cd=? WHERE proj_id=?",type,project);assertThat(checks.getBillingChecks(new PageQuery("2080-07",1,50)).items()).noneMatch(r->project.equals(r.projectId()));}
    }
}
