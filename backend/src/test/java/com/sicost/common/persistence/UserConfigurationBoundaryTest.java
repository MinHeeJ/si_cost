package com.sicost.common.persistence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UserConfigurationBoundaryTest {
    @Test void auditDateUsesSeoulEvenWhenTheInjectedClockUsesUtc() {
        var clock=java.time.Clock.fixed(java.time.Instant.parse("2026-06-30T15:01:00Z"),java.time.ZoneOffset.UTC);
        var audit=AuditFields.now("admin",clock);
        assertThat(audit.userId()).isEqualTo("admin");
        assertThat(audit.occurredAt().toString()).isEqualTo("2026-07-01T00:01");
    }
    @Test void fullRoleDetachRequiresBothPhysicalKeysAndPreservesPartialCompatibility() {
        for (String predicate : new String[]{"user_id=? AND role_id=?", "user_id=? AND role_id IN ('PM','SALES_LDR') AND role_id=?"}) {
            String sql="DELETE FROM tb_sy_user_role WHERE "+predicate;
            assertThatCode(() -> PersistenceBoundary.requireAllowed(sql)).doesNotThrowAnyException();
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql+" OR 1=1")).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("DELETE FROM tb_sy_user_role WHERE user_id=?"))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void headquartersDetachCannotDeleteNormalPartsOrOtherUsersScopes() {
        String sql="DELETE FROM tb_sy_user_scope WHERE user_id=? AND scope_sn=? AND scope_type_cd='PART' AND part_id='HQ' AND proj_id IS NULL";
        assertThatCode(() -> PersistenceBoundary.requireAllowed(sql)).doesNotThrowAnyException();
        for (String invalid : new String[]{sql.replace(" AND scope_sn=?", ""), sql.replace("'HQ'", "?"), sql+" OR 1=1"})
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(invalid)).isInstanceOf(IllegalStateException.class);
    }
    @Test void onlyEditableUserColumnsAndExplicitUnlockCounterAreWritable() {
        assertThatCode(() -> PersistenceBoundary.requireAllowed("UPDATE tb_sy_user SET user_nm=?,email_addr=?,mod_user_id=?,mod_dttm=? WHERE user_id=?")).doesNotThrowAnyException();
        assertThatCode(() -> PersistenceBoundary.requireAllowed("UPDATE tb_sy_user SET user_stat_cd=?,pwd_err_cnt=0,mod_user_id=?,mod_dttm=? WHERE user_id=?")).doesNotThrowAnyException();
        // Req18 §3.1: these two exact bound authentication commands now persist login results.
        for(String field:new String[]{"pwd_err_cnt","last_login_dttm"}) {
            assertThatCode(()->PersistenceBoundary.requireAllowed("UPDATE tb_sy_user SET "+field+"=? WHERE user_id=?")).doesNotThrowAnyException();
            assertThatThrownBy(()->PersistenceBoundary.requireAllowed("UPDATE tb_sy_user SET "+field+"=? WHERE user_id=? OR 1=1")).isInstanceOf(IllegalStateException.class);
        }
        for (String field : new String[]{"user_id","reg_user_id","reg_dttm","pwd_hash","pwd_chg_dttm","mfa_use_yn","mfa_code_hash","mfa_expr_dttm","mfa_err_cnt","temp_pwd_yn"})
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("UPDATE tb_sy_user SET "+field+"=? WHERE user_id=?")).isInstanceOf(IllegalStateException.class);
    }
    @Test void roleSettingsPreserveNameSystemFlagOrderIdentityAndRegistration() {
        assertThatCode(() -> PersistenceBoundary.requireAllowed("INSERT INTO tb_sy_role(role_id,role_nm,data_scope_cd,snstv_view_yn,sys_role_yn,sort_ord,use_yn,reg_user_id,reg_dttm) VALUES(?,?,?,'N','N',?,'Y',?,?)")).doesNotThrowAnyException();
        assertThatCode(() -> PersistenceBoundary.requireAllowed("UPDATE tb_sy_role SET data_scope_cd=?,snstv_view_yn=?,mod_user_id=?,mod_dttm=? WHERE role_id=?")).doesNotThrowAnyException();
        assertThatCode(() -> PersistenceBoundary.requireAllowed("UPDATE tb_sy_role SET use_yn='N',mod_user_id=?,mod_dttm=? WHERE role_id=?")).doesNotThrowAnyException();
        for (String column : new String[]{"role_id","role_nm","sys_role_yn","sort_ord","reg_user_id","reg_dttm"})
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("UPDATE tb_sy_role SET "+column+"=? WHERE role_id=?")).isInstanceOf(IllegalStateException.class);
    }
    @Test void menuWholeSaveOnlyChangesApprovedExistingColumns() {
        assertThatCode(() -> PersistenceBoundary.requireAllowed("UPDATE tb_sy_menu SET menu_nm=?,upper_menu_id=?,sort_ord=?,use_yn=?,pc_only_yn=?,mod_user_id=?,mod_dttm=? WHERE menu_id=?")).doesNotThrowAnyException();
        for (String column : new String[]{"menu_id","scrn_id","menu_lvl","lock_yn","icon_nm","reg_user_id","reg_dttm"})
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("UPDATE tb_sy_menu SET "+column+"=? WHERE menu_id=?")).isInstanceOf(IllegalStateException.class);
    }
    @Test void permissionWritesRequireCompositeKeyAndAllSevenActionsAreIndependent() {
        for (String column : new String[]{"inq_yn","reg_yn","upd_yn","del_yn","upld_yn","dnld_yn","cnfm_yn"}) {
            String sql="UPDATE tb_sy_role_menu_auth SET "+column+"=?,mod_user_id=?,mod_dttm=? WHERE role_id=? AND menu_id=?";
            assertThatCode(() -> PersistenceBoundary.requireAllowed(sql)).doesNotThrowAnyException();
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql.replace(" AND menu_id=?", ""))).isInstanceOf(IllegalStateException.class);
        }
        String insert="INSERT INTO tb_sy_role_menu_auth(role_id,menu_id,reg_user_id,reg_dttm) VALUES(?,?,?,?)";
        assertThatCode(() -> PersistenceBoundary.requireAllowed(insert)).doesNotThrowAnyException();
        assertThatCode(() -> PersistenceBoundary.requireAllowed(insert+" ON CONFLICT(role_id,menu_id) DO UPDATE SET inq_yn=EXCLUDED.inq_yn,mod_user_id=?,mod_dttm=?")).doesNotThrowAnyException();
        assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(insert+" ON CONFLICT(role_id,menu_id) DO UPDATE SET reg_user_id=?")).isInstanceOf(IllegalStateException.class);
    }
    @Test void configurationAndAppendOnlyLedgersCannotBeDeletedOrReset() {
        for (String table : new String[]{"tb_sy_user","tb_sy_role","tb_sy_menu","tb_sy_role_menu_auth","tb_sy_chg_hist","tb_sy_accs_log","tb_pj_proj_chg"})
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("DELETE FROM "+table+" WHERE user_id=?")).isInstanceOf(IllegalStateException.class);
        for (String table : new String[]{"tb_sy_chg_hist","tb_sy_accs_log"})
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("UPDATE "+table+" SET mod_user_id=?")).isInstanceOf(IllegalStateException.class);
    }
}
