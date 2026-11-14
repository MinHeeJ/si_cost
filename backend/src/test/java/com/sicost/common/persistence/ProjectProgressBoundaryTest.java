package com.sicost.common.persistence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectProgressBoundaryTest {
    @Test void insertsUseExplicitColumnsAndBoundValuesIncludingSequenceDefault() {
        for (String sql : new String[]{
            "INSERT INTO tb_pj_step(proj_id,step_sn,proj_step_cd,step_wght_rt,plan_bgn_dt,plan_end_dt,reg_user_id,reg_dttm) VALUES(?,?,?,?,?,?,?,?)",
            "INSERT INTO tb_pj_week_memo(proj_id,memo_sn,wrt_dt,this_week_cn,next_week_cn,req_cn,actl_prgrs_rt,reg_user_id,reg_dttm) VALUES(?,?,?,?,?,?,?,?,?)",
            "INSERT INTO tb_pj_issue(proj_id,issue_ttl,impr_cd,ddln_dt,issue_stat_cd,actn_cn,reg_user_id,reg_dttm) VALUES(?,?,?,?,?,?,?,?)",
            "INSERT INTO tb_pj_issue(issue_id,proj_id,issue_ttl,impr_cd,ddln_dt,issue_stat_cd,reg_user_id) VALUES(DEFAULT,?,?,?,?,?,?) RETURNING issue_id"}) {
            assertThatCode(() -> PersistenceBoundary.requireAllowed(sql)).doesNotThrowAnyException();
        }
    }
    @Test void progressUpdatesAreBoundToFullChildAndProjectIdentity() {
        for (String sql : new String[]{
            "UPDATE tb_pj_step SET plan_bgn_dt=?,plan_end_dt=?,cmpl_dt=?,mod_user_id=?,mod_dttm=? WHERE proj_id=? AND step_sn=?",
            "UPDATE tb_pj_week_memo SET this_week_cn=?,next_week_cn=NULL,req_cn=?,actl_prgrs_rt=?,mod_user_id=?,mod_dttm=? WHERE proj_id=? AND memo_sn=?",
            "UPDATE tb_pj_issue SET issue_ttl=?,impr_cd=?,ddln_dt=?,issue_stat_cd=?,actn_cn=?,mod_user_id=?,mod_dttm=? WHERE issue_id=? AND proj_id=?",
            "UPDATE tb_pj_issue SET actn_cn=NULL WHERE proj_id=? AND issue_id=?"}) {
            assertThatCode(() -> PersistenceBoundary.requireAllowed(sql)).doesNotThrowAnyException();
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql + " OR 1=1")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql.replaceFirst(" AND [a-z_]+=\\?$", ""))).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void originalRegistrationAndBusinessIdentityAreNeverUpdated() {
        for (String table : new String[]{"tb_pj_step", "tb_pj_week_memo", "tb_pj_issue"}) {
            String key = table.equals("tb_pj_step") ? "proj_id=? AND step_sn=?"
                : table.equals("tb_pj_week_memo") ? "proj_id=? AND memo_sn=?" : "issue_id=? AND proj_id=?";
            for (String field : new String[]{"proj_id", "reg_user_id", "reg_dttm", "unknown", "wrt_dt", "step_wght_rt", "proj_step_cd"}) {
                assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("UPDATE " + table + " SET " + field + "=? WHERE " + key))
                    .isInstanceOf(IllegalStateException.class);
            }
        }
    }
    @Test void deletionUpsertAndIndirectOrMultiRowInsertStayForbidden() {
        for (String table : new String[]{"tb_pj_step", "tb_pj_week_memo", "tb_pj_issue"}) {
            for (String sql : new String[]{
                "DELETE FROM " + table + " WHERE proj_id=?",
                "INSERT INTO " + table + "(proj_id) VALUES(?) ON CONFLICT(proj_id) DO NOTHING",
                "INSERT INTO " + table + "(proj_id) VALUES(?),(?)",
                "INSERT INTO " + table + "(proj_id,proj_id) VALUES(?,?)",
                "INSERT INTO " + table + "(unknown) VALUES(?)",
                "INSERT INTO " + table + "(proj_id) SELECT proj_id FROM tb_pj_proj",
                "INSERT INTO " + table + "(proj_id) VALUES(?); DELETE FROM tb_pj_proj"}) {
                assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
            }
        }
    }
    @Test void financialAndCloseSourcesHaveNoNewWritePermission() {
        for (String sql : new String[]{"UPDATE tb_co_mon_cost SET labor_alloc_amt=? WHERE proj_id=?",
            "UPDATE tb_co_mon_cost SET drct_exps_amt=? WHERE proj_id=?", "INSERT INTO tb_co_mon_cost(proj_id) VALUES(?)",
            "UPDATE tb_cl_close SET close_stat_cd=?", "UPDATE tb_co_mon_pl SET sales_amt=?"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
        }
    }
}
