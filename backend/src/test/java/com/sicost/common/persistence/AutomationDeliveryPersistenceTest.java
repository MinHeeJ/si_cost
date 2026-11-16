package com.sicost.common.persistence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AutomationDeliveryPersistenceTest {
    @Test void runUpdatesPreserveOriginalIdentityAndStartTime() {
        allow("UPDATE tb_sy_automation_run SET state_cd=?,last_step=?,end_dttm=?,error_cn=?,mod_user_id=?,mod_dttm=? WHERE job_cd=? AND run_dt=?");
        reject("UPDATE tb_sy_automation_run SET start_dttm=? WHERE job_cd=? AND run_dt=?");
        reject("UPDATE tb_sy_automation_run SET state_cd=? WHERE job_cd=?");
        reject("UPDATE tb_sy_automation_run SET state_cd=? WHERE job_cd=? AND run_dt=? OR 1=1");
    }
    @Test void taskUpdatesPermitOnlyResultsOnExactUuid() {
        allow("UPDATE tb_sy_delivery_task SET state_cd='SENDING',mod_dttm=? WHERE task_id=CAST(? AS uuid) AND state_cd='PENDING'");
        allow("UPDATE tb_sy_delivery_task SET state_cd=?,sent_dttm=?,error_cd=?,error_cn=? WHERE task_id=?");
        for (String field : new String[]{"event_key","rcvr_user_id","rcvr_nm","mail_to","mail_body","mail_subject","bill_id","dun_step","dun_sn","noti_cn","reg_dttm"}) {
            reject("UPDATE tb_sy_delivery_task SET " + field + "=? WHERE task_id=?");
        }
        reject("UPDATE tb_sy_delivery_task SET state_cd=?");
    }
    @Test void notificationReadsAreRecipientBoundAndFirstReadOnly() {
        String set="UPDATE tb_sy_noti SET read_yn='Y',read_dttm=?,mod_user_id=?,mod_dttm=? WHERE ";
        String suffix="read_yn='N' AND send_mthd_cd IN ('SYS','BOTH')";
        allow(set+"rcvr_user_id=? AND noti_id=? AND "+suffix);
        allow(set+"rcvr_user_id=? AND "+suffix+" AND noti_dttm<=?");
        reject(set+"noti_id=? AND "+suffix);
        reject(set+"rcvr_user_id=? AND read_yn='Y' AND send_mthd_cd IN ('SYS','BOTH')");
        reject("UPDATE tb_sy_noti SET noti_cn=? WHERE noti_id=?");
    }
    @Test void cleanupCannotDeleteDurableDedupOrHistories() {
        allow("DELETE FROM tb_sy_noti WHERE noti_dttm<?");
        reject("DELETE FROM tb_sy_noti WHERE noti_id=?");
        for(String table:new String[]{"tb_sy_delivery_task","tb_sy_automation_run","tb_bl_dun_hist","tb_sy_chg_hist","tb_sy_accs_log","tb_sy_upld"}) {
            reject("DELETE FROM "+table+" WHERE reg_dttm<?");
        }
        reject("UPDATE tb_bl_dun_hist SET send_dt=? WHERE bill_id=? AND dun_sn=?");
    }
    @Test void explicitDunningHistoryIsInsertOnlyWithoutUpsert() {
        String sql="INSERT INTO tb_bl_dun_hist (bill_id,dun_sn,dun_step,send_dt,rcvr_desc,send_mthd_cd,resend_yn,reg_user_id,reg_dttm) VALUES (?,?,?,?,?,?,?,?,?)";
        allow(sql);
        reject(sql+" ON CONFLICT (bill_id,dun_sn) DO UPDATE SET send_dt=?");
        reject(sql+"; DELETE FROM tb_sy_noti");
    }
    @Test void systemTablesCannotBeInsertedByUnboundedSelectOrUnknownColumns() {
        allow("INSERT INTO tb_sy_automation_run (job_cd,run_dt,state_cd,reg_user_id,reg_dttm) VALUES (?,?,'RUNNING','SYSTEM',?) ON CONFLICT (job_cd,run_dt) DO NOTHING");
        reject("INSERT INTO tb_sy_automation_run (job_cd,run_dt,state_cd,reg_user_id,reg_dttm) SELECT ?,?,?,?,?");
        reject("INSERT INTO tb_sy_automation_run (job_cd,run_dt,state_cd,reg_user_id,reg_dttm,secret) VALUES (?,?,?,?,?,?)");
    }
    void allow(String sql) { assertThatCode(()->PersistenceBoundary.requireAllowed(sql)).doesNotThrowAnyException(); }
    void reject(String sql) { assertThatThrownBy(()->PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class); }
}
