package com.sicost.common.persistence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PersistenceBoundaryTest {
    @Test void existingAndExplicitMasterTablesOnlyAreWritable() {
        for (String table : new String[]{"TB_SY_CODE_GRP", "TB_SY_CODE", "TB_SY_CODE_MAP", "TB_SY_OPER_STD", "TB_SY_USER_PREF", "TB_SY_CHG_HIST",
                "TB_SY_USER", "TB_SY_USER_ROLE", "TB_BS_CLNT", "TB_BS_PART", "TB_BS_PART_STD", "TB_SY_UPLD", "TB_SY_UPLD_ERR", "TB_SY_ACCS_LOG",
                "TB_HR_DEV", "TB_HR_DEV_PRC", "TB_HR_DEV_SKILL", "TB_HR_DEV_STAT", "TB_HR_CAND", "TB_HR_CAND_SKILL", "TB_HR_CAND_CNTC"}) {
            assertThatCode(() -> PersistenceBoundary.requireAllowed("INSERT INTO " + table + " (x) VALUES (?)")).doesNotThrowAnyException();
        }
        for (String table : new String[]{"TB_CL_CLOSE", "TB_SY_ROLE", "TB_CO_PART_SETL", "TB_SY_ADM_AREA", "TB_SY_ADM_HDONG", "TB_HR_MON_PAY", "TB_PJ_ASGN"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("UPDATE " + table + " SET x = ?")).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void peopleSkillReplacementRequiresExactlyOneParentKey() {
        for (String table : new String[]{"TB_HR_DEV_SKILL", "TB_HR_CAND_SKILL"}) {
            String key = table.equals("TB_HR_DEV_SKILL") ? "dev_id" : "cand_id";
            assertThatCode(() -> PersistenceBoundary.requireAllowed("DELETE FROM " + table + " WHERE " + key + " = ?")).doesNotThrowAnyException();
            for (String where : new String[]{"", " WHERE " + key + "=? OR 1=1", " WHERE " + key + " IS NOT NULL", " WHERE " + key + "=?; DELETE FROM tb_hr_dev"}) {
                assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("DELETE FROM " + table + where)).isInstanceOf(IllegalStateException.class);
            }
        }
    }
    @Test void onlyTargetedWorkOrRetirementCancellationCanDeleteState() {
        for (String state : new String[]{"work_stat_cd IN ('WORK','RETIRE')", "work_stat_cd='WORK'", "work_stat_cd='RETIRE'"}) {
            assertThatCode(() -> PersistenceBoundary.requireAllowed("DELETE FROM tb_hr_dev_stat WHERE dev_id=? AND stat_sn=? AND " + state)).doesNotThrowAnyException();
        }
        for (String where : new String[]{"", " WHERE dev_id=?", " WHERE dev_id=? AND stat_sn=?", " WHERE dev_id=? AND stat_sn=? AND work_stat_cd='LEAVE'", " WHERE dev_id=? AND stat_sn=? AND work_stat_cd=?", " WHERE dev_id=? AND stat_sn=? AND work_stat_cd='WORK' OR 1=1"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("DELETE FROM tb_hr_dev_stat" + where)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void peopleParentPriceAndContactDeletionRemainForbidden() {
        for (String table : new String[]{"TB_HR_DEV", "TB_HR_CAND", "TB_HR_DEV_PRC", "TB_HR_CAND_CNTC"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("DELETE FROM " + table + " WHERE dev_id=?")).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void historyIsAppendOnly() {
        assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("DELETE FROM tb_sy_chg_hist WHERE hist_id = ?")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("UPDATE tb_sy_chg_hist SET aft_val = ?")).isInstanceOf(IllegalStateException.class);
    }
    @Test void onlyTheExistingGroupCreationTableLockIsAllowed() {
        assertThatCode(() -> PersistenceBoundary.requireAllowed("LOCK TABLE tb_sy_code_grp IN SHARE ROW EXCLUSIVE MODE"))
                .doesNotThrowAnyException();
        for (String sql : new String[]{"LOCK TABLE tb_sy_user IN SHARE ROW EXCLUSIVE MODE",
                "LOCK TABLE tb_sy_code_grp IN ACCESS EXCLUSIVE MODE",
                "LOCK TABLE tb_sy_code_grp IN SHARE ROW EXCLUSIVE MODE; DELETE FROM tb_sy_user"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void businessDeletionIsBoundedToQualificationAndSameUploadIssues() {
        assertThatCode(() -> PersistenceBoundary.requireAllowed("DELETE FROM tb_sy_user_role WHERE user_id=? AND role_id IN ('PM','SALES_LDR')"))
                .doesNotThrowAnyException();
        assertThatCode(() -> PersistenceBoundary.requireAllowed("DELETE FROM tb_sy_upld_err WHERE upld_id=?")).doesNotThrowAnyException();
        for (String sql : new String[]{"DELETE FROM tb_bs_clnt WHERE clnt_id=?", "DELETE FROM tb_bs_part", "DELETE FROM tb_bs_part_std",
                "DELETE FROM tb_sy_user", "DELETE FROM tb_sy_upld", "DELETE FROM tb_sy_user_role WHERE user_id=?", "DELETE FROM tb_sy_upld_err"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void referenceLocksAndAppendOnlyAccessLogAreNarrowlyAllowed() {
        assertThatCode(() -> PersistenceBoundary.requireAllowed("LOCK TABLE tb_cl_close IN SHARE MODE")).doesNotThrowAnyException();
        assertThatCode(() -> PersistenceBoundary.requireAllowed("LOCK TABLE tb_co_part_setl IN SHARE MODE")).doesNotThrowAnyException();
        for (String sql : new String[]{"UPDATE tb_sy_accs_log SET trgt_desc=?", "DELETE FROM tb_sy_accs_log",
                "INSERT INTO tb_sy_chg_hist(x) VALUES (?) ON CONFLICT(x) DO UPDATE SET x=?",
                "LOCK TABLE tb_cl_close IN ACCESS EXCLUSIVE MODE"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void multiStatementAndIndirectWritesAreRefused() {
        for (String sql : new String[]{"UPDATE tb_sy_code SET code_nm = ?; DELETE FROM tb_sy_user", "WITH x AS (DELETE FROM tb_sy_user) UPDATE tb_sy_code SET code_nm = ?", "TRUNCATE tb_sy_code", "DROP TABLE tb_sy_code"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void explicitProjectAndSourceTargetsJoinTheExistingAllowlist() {
        for (String table : new String[]{"TB_PJ_PROJ", "TB_PJ_PROJ_PART", "TB_PJ_PROJ_CHG", "TB_PJ_MNPW_PLAN",
                "TB_PJ_TK_DSTRB", "TB_PJ_DRCT_EXPS", "TB_SY_USER_SCOPE", "TB_PJ_ASGN_MON", "TB_PJ_ASGN_HIST"}) {
            assertThatCode(() -> PersistenceBoundary.requireAllowed("INSERT INTO " + table + " (reg_user_id) VALUES (?)")).doesNotThrowAnyException();
        }
        assertThatCode(() -> PersistenceBoundary.requireAllowed("UPDATE tb_pj_proj SET proj_nm=? WHERE proj_id=?")).doesNotThrowAnyException();
        assertThatCode(() -> PersistenceBoundary.requireAllowed("INSERT INTO tb_pj_mnpw_plan(proj_id,job_cd,plan_mm) VALUES(?,?,?) ON CONFLICT(proj_id,job_cd) DO UPDATE SET plan_mm=?")).doesNotThrowAnyException();
    }
    @Test void projectChildrenCanOnlyDeleteTheApprovedBoundedRows() {
        for (String sql : new String[]{
                "DELETE FROM tb_pj_drct_exps WHERE proj_id=? AND exps_ym=? AND exps_sn=?",
                "DELETE FROM tb_pj_tk_dstrb WHERE proj_id=? AND dstrb_step_cd=?",
                "DELETE FROM tb_pj_proj_part WHERE proj_id=? AND aply_bgn_ym>=? AND aply_bgn_ym<=?",
                "DELETE FROM tb_sy_user_scope WHERE user_id=? AND scope_sn=? AND scope_type_cd='PROJ' AND proj_id=?",
                "DELETE FROM tb_pj_asgn_mon WHERE asgn_id=? AND input_ym=? AND fix_yn<>'Y'"}) {
            assertThatCode(() -> PersistenceBoundary.requireAllowed(sql)).doesNotThrowAnyException();
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql + " OR 1=1")).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void projectPlanAndTableWideDeletionsRemainForbidden() {
        for (String table : new String[]{"tb_pj_proj", "tb_pj_mnpw_plan", "tb_pj_proj_chg", "tb_pj_asgn", "tb_pj_asgn_hist",
                "tb_pj_proj_part", "tb_pj_tk_dstrb", "tb_pj_drct_exps", "tb_sy_user_scope", "tb_pj_asgn_mon"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("DELETE FROM " + table)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("DELETE FROM " + table + " WHERE proj_id=?")).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void projectAndAssignmentHistoryAreAppendOnlySingleStatements() {
        for (String table : new String[]{"tb_sy_chg_hist", "tb_sy_accs_log", "tb_pj_proj_chg", "tb_pj_asgn_hist"}) {
            for (String sql : new String[]{"UPDATE " + table + " SET aft_val=?", "DELETE FROM " + table,
                    "INSERT INTO " + table + "(reg_user_id) VALUES(?) ON CONFLICT(reg_user_id) DO NOTHING",
                    "INSERT INTO " + table + "(reg_user_id) VALUES(?); UPDATE tb_pj_proj SET proj_nm=?"}) {
                assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
            }
        }
    }
    @Test void sourceWritesAreLimitedToLossAndCostClassificationNeverAmounts() {
        assertThatCode(() -> PersistenceBoundary.requireAllowed("UPDATE tb_pj_asgn SET input_end_dt=?,early_wthdr_yn='Y',mod_user_id=?,mod_dttm=? WHERE asgn_id=? AND asgn_stat_cd='ACT'")).doesNotThrowAnyException();
        assertThatCode(() -> PersistenceBoundary.requireAllowed("UPDATE tb_co_mon_cost SET cost_cls_cd='IDLE',mod_user_id=?,mod_dttm=? WHERE proj_id=? AND cost_cls_cd='PRE' AND fix_yn='N' AND cost_ym=?")).doesNotThrowAnyException();
        for (String sql : new String[]{"INSERT INTO tb_pj_asgn(asgn_id) VALUES(?)", "UPDATE tb_pj_asgn SET bill_uprc=? WHERE asgn_id=?",
                "UPDATE tb_co_mon_cost SET sales_amt=? WHERE proj_id=?", "UPDATE tb_co_mon_cost SET cost_cls_cd='IDLE' WHERE proj_id=?",
                "UPDATE tb_co_mon_cost SET cost_cls_cd='IDLE' WHERE proj_id=? AND cost_cls_cd='PRE' AND fix_yn='Y'",
                "INSERT INTO tb_co_mon_cost(cost_cls_cd) VALUES('IDLE')", "UPDATE tb_pj_drct_exps SET exps_amt=? WHERE proj_id=?",
                "UPDATE tb_sy_user_scope SET proj_id=? WHERE user_id=?"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
        }
    }
}
