package com.sicost.common.persistence;

import com.sicost.common.installation.PostgreSqlDatabase;
import java.sql.Timestamp;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class AutomationDeliverySchemaDatabaseTest {
    PostgreSqlDatabase db;
    String user;
    List<String> previousTables;
    List<String> previousHistory;
    List<String> previousStandards;
    @BeforeEach void installDisposableSchema() throws Exception {
        db=new PostgreSqlDatabase();
        String schema=db.sql.queryForObject("SELECT current_schema()",String.class);
        Flyway.configure().dataSource(db.source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("8").load().migrate();
        previousTables=db.sql.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=current_schema() ORDER BY table_name",String.class);
        previousHistory=db.sql.queryForList("SELECT row_to_json(t)::text FROM flyway_schema_history t ORDER BY installed_rank",String.class);
        previousStandards=db.sql.queryForList("SELECT row_to_json(t)::text FROM tb_sy_oper_std t ORDER BY std_cd,aply_bgn_ym",String.class);
        db.install();
        user=db.sql.queryForObject("SELECT user_id FROM tb_sy_user ORDER BY user_id LIMIT 1",String.class);
    }
    @AfterEach void removeOnlyOwnedSchema() {
        if(db!=null) {
            String schema=db.sql.queryForObject("SELECT current_schema()",String.class);
            if(schema==null||!schema.matches("sicost_test_[a-f0-9]{32}"))throw new IllegalStateException("시험 스키마만 정리합니다.");
            db.sql.execute("DROP SCHEMA "+schema+" CASCADE");
        }
    }
    Map<String,Object> system() {
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("task_id",UUID.randomUUID());row.put("event_key","UPLD:"+UUID.randomUUID()+":APPLIED");row.put("rcvr_user_id",user);
        row.put("rcvr_nm","수신자");row.put("channel_cd","SYS");row.put("noti_type_cd","UPLD_DONE");row.put("noti_cn","반영 완료");
        row.put("reg_user_id","SYSTEM");row.put("reg_dttm",Timestamp.valueOf("2026-07-15 02:00:00"));return row;
    }
    void insert(Map<String,Object> row) {
        db.sql.update("INSERT INTO tb_sy_delivery_task ("+String.join(",",row.keySet())+") VALUES ("+String.join(",",Collections.nCopies(row.size(),"?"))+")",row.values().toArray());
    }
    void rejects(Map<String,Object> row) {assertThatThrownBy(()->insert(row)).isInstanceOf(DataIntegrityViolationException.class);}
    String bill() {
        String key=UUID.randomUUID().toString().replace("-","").substring(0,14),project="P-"+key,bill="B-"+key;
        db.sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2026-01-01','2026-12-31',1,1,0,0,?)",project,project,user);
        db.sql.update("INSERT INTO tb_bl_bill(bill_id,proj_id,bill_ord,bill_ym,bill_dt,sply_amt,vat_rt,vat_amt,tot_amt,rcpt_sum_amt,unpaid_amt,pay_due_dt,reg_user_id) VALUES (?,?,1,'202607','2026-07-01',100,10,10,110,0,110,'2026-07-08',?)",bill,project,user);
        return bill;
    }
    Map<String,Object> mail() {
        var row=system();row.put("channel_cd","MAIL");row.put("noti_type_cd","DUN");row.put("bill_id",bill());row.put("dun_step",1);row.put("dun_sn",1);
        row.put("mail_to","receiver@example.test");row.put("mail_subject","미수 알림");row.put("mail_body","장문".repeat(1000));row.put("message_id",UUID.randomUUID()+"@example.test");return row;
    }
    @Test void defaultsAndPhysicalNullabilityMatchConditionalModel() {
        var row=system();insert(row);
        assertThat(db.sql.queryForMap("SELECT state_cd,resend_yn,mail_to,bill_id FROM tb_sy_delivery_task WHERE task_id=?",row.get("task_id")))
            .containsEntry("state_cd","PENDING").containsEntry("resend_yn","N").containsEntry("mail_to",null).containsEntry("bill_id",null);
        assertThat(db.sql.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_sy_delivery_task' AND is_nullable='NO'",String.class))
            .containsExactlyInAnyOrder("task_id","event_key","rcvr_user_id","rcvr_nm","channel_cd","state_cd","noti_type_cd","noti_cn","resend_yn","reg_user_id","reg_dttm");
    }
    @Test void onlyApprovedStatesAndChannelsAreAccepted() {
        for(String state:List.of("PENDING","SENDING","SENT","FAILED","UNKNOWN","SKIPPED")) {var row=system();row.put("state_cd",state);insert(row);}
        var invalidState=system();invalidState.put("state_cd","SUCCESS");rejects(invalidState);
        var invalidChannel=system();invalidChannel.put("channel_cd","SMS");rejects(invalidChannel);
    }
    @Test void mailMissingAddressRequiresExplicitFailedMissingEmailIncludingNonNullErrorCode() {
        var row=mail();row.put("mail_to",null);rejects(row);
        row.put("state_cd","FAILED");rejects(row);
        row.put("error_cd","MAIL_NOT_CONFIGURED");rejects(row);
        row.put("error_cd","RECIPIENT_EMAIL_MISSING");insert(row);
        assertThat(db.sql.queryForObject("SELECT length(mail_body) FROM tb_sy_delivery_task WHERE task_id=?",Integer.class,row.get("task_id"))).isEqualTo(2000);
    }
    @Test void channelsAndDunningFieldsCannotLeakIntoOtherEventTypes() {
        for(String field:List.of("mail_to","mail_subject","mail_body","message_id")) {var row=system();row.put(field,"value");rejects(row);}
        var nonDunning=system();nonDunning.put("dun_step",1);rejects(nonDunning);
        var missing=mail();missing.put("dun_sn",null);rejects(missing);
        var invalidStep=mail();invalidStep.put("dun_step",0);rejects(invalidStep);
        var noSubject=mail();noSubject.put("mail_subject",null);rejects(noSubject);
        var generalMail=mail();generalMail.put("noti_type_cd","AI_DONE");generalMail.put("bill_id",null);generalMail.put("dun_step",null);generalMail.put("dun_sn",null);rejects(generalMail);
    }
    @Test void eventUserChannelUniquenessSurvivesNotificationDeletionWithSetNull() {
        var row=system();
        Long id=db.sql.queryForObject("INSERT INTO tb_sy_noti(rcvr_user_id,noti_type_cd,noti_cn,send_mthd_cd,noti_dttm,reg_user_id) VALUES (?,'UPLD_DONE','완료','SYS',CURRENT_TIMESTAMP,'SYSTEM') RETURNING noti_id",Long.class,user);
        row.put("noti_id",id);insert(row);
        db.sql.update("DELETE FROM tb_sy_noti WHERE noti_id=?",id);
        assertThat(db.sql.queryForObject("SELECT noti_id FROM tb_sy_delivery_task WHERE task_id=?",Long.class,row.get("task_id"))).isNull();
        row.put("task_id",UUID.randomUUID());row.put("noti_id",null);rejects(row);
        assertThat(db.count("tb_sy_delivery_task")).isEqualTo(1);
    }
    @Test void userAndBillForeignKeysRejectMissingMasters() {
        var row=system();row.put("rcvr_user_id","missing-"+UUID.randomUUID().toString().substring(0,8));rejects(row);
        var missingBill=mail();missingBill.put("bill_id","missing");rejects(missingBill);
    }
    @Test void runDefaultsAndEnumsDoNotFabricateAStartTime() {
        db.sql.update("INSERT INTO tb_sy_automation_run(job_cd,run_dt,state_cd,reg_user_id,reg_dttm) VALUES ('DAILY_OPERATIONS',DATE '2026-07-15','RUNNING','SYSTEM',CURRENT_TIMESTAMP)");
        var row=db.sql.queryForMap("SELECT last_step,start_dttm FROM tb_sy_automation_run");
        assertThat(((Number)row.get("last_step")).intValue()).isZero();assertThat(row.get("start_dttm")).isNull();
        assertThatThrownBy(()->db.sql.update("UPDATE tb_sy_automation_run SET state_cd='PENDING'")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.sql.update("UPDATE tb_sy_automation_run SET job_cd='OTHER'")).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void nextMigrationAddsOnlyTwoTablesAndPreservesAppliedRowsAndSeeds() {
        var tables=new ArrayList<>(db.sql.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=current_schema() ORDER BY table_name",String.class));
        tables.removeAll(previousTables);
        assertThat(tables).containsExactlyInAnyOrder("tb_sy_automation_run","tb_sy_delivery_task");
        assertThat(db.sql.queryForList("SELECT row_to_json(t)::text FROM flyway_schema_history t WHERE version::int<=8 ORDER BY installed_rank",String.class)).isEqualTo(previousHistory);
        assertThat(db.sql.queryForList("SELECT row_to_json(t)::text FROM tb_sy_oper_std t ORDER BY std_cd,aply_bgn_ym",String.class)).isEqualTo(previousStandards);
        String schema=db.sql.queryForObject("SELECT current_schema()",String.class);
        var before=db.sql.queryForList("SELECT row_to_json(t)::text FROM tb_sy_oper_std t ORDER BY std_cd,aply_bgn_ym",String.class);
        var applied=db.sql.queryForList("SELECT row_to_json(t)::text FROM flyway_schema_history t ORDER BY installed_rank",String.class);
        Flyway.configure().dataSource(db.source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate();
        assertThat(db.sql.queryForList("SELECT row_to_json(t)::text FROM tb_sy_oper_std t ORDER BY std_cd,aply_bgn_ym",String.class)).isEqualTo(before);
        assertThat(db.sql.queryForList("SELECT row_to_json(t)::text FROM flyway_schema_history t ORDER BY installed_rank",String.class)).isEqualTo(applied);
        assertThat(db.sql.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=current_schema() AND table_name IN ('tb_sy_automation_run','tb_sy_delivery_task')",String.class)).hasSize(2);
    }
}
