package com.sicost.common.persistence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PersistenceBoundaryTest {
    @Test void financialSourcesAreWritableButDoNotEnableProfitCloseOrOtherLedgers() {
        for (String table : new String[]{"TB_FI_ITEM", "TB_FI_CORP_TAX", "TB_FI_TAX_PAY", "TB_CO_PREV_ADJ",
                "TB_FI_LOAN", "TB_FI_CARD", "TB_FI_CARD_PAY", "TB_FI_CARD_MON", "TB_FI_XFER"}) {
            assertThatCode(() -> PersistenceBoundary.requireAllowed("INSERT INTO " + table + " (reg_user_id) VALUES (?)")).doesNotThrowAnyException();
        }
        for (String table : new String[]{"TB_CO_MON_PL", "TB_CL_CLOSE", "TB_FI_BIZ_PLAN", "TB_CO_PART_SETL"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("INSERT INTO " + table + " (reg_user_id) VALUES (?)"))
                .isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void financialDeletionRequiresSelectedMonthAndExactIdentityOrUploadOrigin() {
        for (String sql : new String[]{
                "DELETE FROM tb_fi_item WHERE fin_ym=? AND item_sn=?",
                "DELETE FROM tb_fi_item WHERE fin_ym=? AND upld_id IS NOT NULL",
                "DELETE FROM tb_co_prev_adj WHERE aply_ym=? AND adj_id=?",
                "DELETE FROM tb_fi_loan WHERE loan_sn=? AND loan_dt>=? AND loan_dt<?",
                "DELETE FROM tb_fi_xfer WHERE xfer_sn=? AND xfer_dt>=? AND xfer_dt<?"}) {
            assertThatCode(() -> PersistenceBoundary.requireAllowed(sql)).doesNotThrowAnyException();
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql + " OR 1=1")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql + "; DELETE FROM tb_fi_item")).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void financialDeletionCannotDeleteManualRowsOrUnboundedMonths() {
        for (String sql : new String[]{
                "DELETE FROM tb_fi_item", "DELETE FROM tb_fi_item WHERE fin_ym=?",
                "DELETE FROM tb_fi_item WHERE item_sn=?", "DELETE FROM tb_fi_item WHERE fin_ym>=? AND item_sn=?",
                "DELETE FROM tb_fi_item WHERE fin_ym=? AND upld_id IS NULL",
                "DELETE FROM tb_fi_item WHERE upld_id IS NOT NULL",
                "DELETE FROM tb_co_prev_adj WHERE adj_id=?", "DELETE FROM tb_co_prev_adj WHERE aply_ym=?",
                "DELETE FROM tb_fi_loan WHERE loan_sn=?", "DELETE FROM tb_fi_loan WHERE loan_dt>=? AND loan_dt<?",
                "DELETE FROM tb_fi_xfer WHERE xfer_sn=?", "DELETE FROM tb_fi_xfer WHERE xfer_dt>=? AND xfer_dt<?"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void financialCardsAndTaxRowsNeverPhysicallyDelete() {
        for (String table : new String[]{"TB_FI_CARD", "TB_FI_CARD_PAY", "TB_FI_CARD_MON", "TB_FI_CORP_TAX", "TB_FI_TAX_PAY"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("DELETE FROM " + table + " WHERE pay_ym=? AND card_id=?"))
                .isInstanceOf(IllegalStateException.class);
        }
    }
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
    @Test void assignmentCreateAndApprovedMutationsPreserveIdentityColumns() {
        for (String sql : new String[]{
                "INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,bill_uprc,input_type_cd,ctrt_dev_id,ext_psbl_cd,ext_cnt,early_wthdr_yn,asgn_stat_cd,reg_user_id,reg_dttm) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "UPDATE tb_pj_asgn SET input_end_dt=?,ext_cnt=ext_cnt+1,mod_user_id=?,mod_dttm=? WHERE asgn_id=?",
                "UPDATE tb_pj_asgn SET input_type_cd=?,ext_psbl_cd=NULL WHERE asgn_id=?",
                "UPDATE tb_pj_asgn SET asgn_stat_cd='CNCL',mod_user_id=?,mod_dttm=? WHERE asgn_id=? AND asgn_stat_cd='ACT'"}) {
            assertThatCode(() -> PersistenceBoundary.requireAllowed(sql)).doesNotThrowAnyException();
        }
        for (String column : new String[]{"asgn_id","proj_id","dev_id","job_cd","input_bgn_dt","ctrt_dev_id","reg_user_id","reg_dttm"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("UPDATE tb_pj_asgn SET " + column + "=? WHERE asgn_id=?")).isInstanceOf(IllegalStateException.class);
        }
        for (String sql : new String[]{"UPDATE tb_pj_asgn SET bill_uprc=? WHERE proj_id=?",
                "UPDATE tb_pj_asgn SET bill_uprc=? WHERE asgn_id=? OR 1=1",
                "INSERT INTO tb_pj_asgn(asgn_id,unknown_column) VALUES(?,?)",
                "INSERT INTO tb_pj_asgn(asgn_id,asgn_id) VALUES(?,?)",
                "INSERT INTO tb_pj_asgn(asgn_id) VALUES(?) ON CONFLICT(asgn_id) DO UPDATE SET dev_id=?"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void onlyGuardedFullKeyCorrectionCanUpdateAssignmentHistory() {
        String allowed="UPDATE tb_pj_asgn_hist SET crct_yn='Y' WHERE asgn_id=? AND hist_sn=? AND crct_yn='N'";
        assertThatCode(() -> PersistenceBoundary.requireAllowed(allowed)).doesNotThrowAnyException();
        for (String sql : new String[]{
                allowed + " OR 1=1",allowed.replace("crct_yn='N'","crct_yn='Y'"),
                allowed.replace("SET crct_yn='Y'","SET crct_yn='N'"),
                allowed.replace("SET crct_yn='Y'","SET crct_yn=?"),
                allowed.replace(" AND hist_sn=?",""),allowed.replace(" AND crct_yn='N'",""),
                allowed.replace("SET crct_yn='Y'","SET crct_yn='Y',new_uprc=?"),
                allowed.replace("SET crct_yn='Y'","SET crct_yn='Y',new_ctrt_mm=?"),
                allowed.replace("SET crct_yn='Y'","SET crct_yn='Y',reg_dttm=?"),
                "DELETE FROM tb_pj_asgn_hist WHERE asgn_id=? AND hist_sn=?"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void monthlyDeletionRequiresExactAssignmentMonthAndUnfixedRow() {
        assertThatCode(() -> PersistenceBoundary.requireAllowed("DELETE FROM tb_pj_asgn_mon WHERE asgn_id=? AND input_ym=? AND fix_yn='N'")).doesNotThrowAnyException();
        for (String where : new String[]{"asgn_id=?", "asgn_id=? AND input_ym=?", "asgn_id=? AND input_ym=? AND fix_yn='Y'",
                "asgn_id=? AND input_ym>=? AND fix_yn='N'", "asgn_id=? AND input_ym=? AND fix_yn=?",
                "asgn_id=? AND input_ym=? AND fix_yn='N' OR 1=1"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("DELETE FROM tb_pj_asgn_mon WHERE " + where)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void payrollReplacementOnlyDeletesOneExplicitMonthOrFullPrimaryKey() {
        assertThatCode(() -> PersistenceBoundary.requireAllowed("DELETE FROM tb_hr_mon_pay WHERE pay_ym=?")).doesNotThrowAnyException();
        assertThatCode(() -> PersistenceBoundary.requireAllowed("DELETE FROM tb_hr_mon_pay WHERE pay_ym=? AND dev_id=?")).doesNotThrowAnyException();
        for (String where : new String[]{"", " WHERE dev_id=?", " WHERE pay_ym>=?", " WHERE pay_ym=? OR 1=1",
                " WHERE pay_ym=? AND dev_id=? OR dev_id=?", " WHERE pay_ym=?; DELETE FROM tb_hr_dev"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("DELETE FROM tb_hr_mon_pay" + where)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void payrollUpdatesAndUpsertsPreservePrimaryKeyAndOriginalRegistrationFields() {
        String insert="INSERT INTO tb_hr_mon_pay(pay_ym,dev_id,pay_amt,alwnc_amt,insr_cmpny_amt,retire_rsv_amt,emp_type_cd,drct_cls_cd,reg_user_id) VALUES(?,?,?,?,?,?,?,?,?)";
        assertThatCode(() -> PersistenceBoundary.requireAllowed(insert)).doesNotThrowAnyException();
        assertThatCode(() -> PersistenceBoundary.requireAllowed(insert + " ON CONFLICT(pay_ym,dev_id) DO UPDATE SET pay_amt=?,upld_id=?,mod_user_id=?,mod_dttm=?")).doesNotThrowAnyException();
        assertThatCode(() -> PersistenceBoundary.requireAllowed(insert + " ON\n CONFLICT(pay_ym,dev_id) DO UPDATE SET pay_amt=EXCLUDED.pay_amt,mod_user_id=EXCLUDED.mod_user_id")).doesNotThrowAnyException();
        assertThatCode(() -> PersistenceBoundary.requireAllowed("UPDATE tb_hr_mon_pay SET alwnc_amt=?,retire_rsv_amt=?,wthld_tax_amt=?,real_pay_amt=?,mod_user_id=?,mod_dttm=? WHERE pay_ym=? AND dev_id=?")).doesNotThrowAnyException();
        for (String column : new String[]{"pay_ym","dev_id","reg_user_id","reg_dttm","fix_yn"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("UPDATE tb_hr_mon_pay SET " + column + "=? WHERE pay_ym=? AND dev_id=?")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(insert + " ON CONFLICT(pay_ym,dev_id) DO UPDATE SET " + column + "=?")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(insert + " ON\n CONFLICT(pay_ym,dev_id) DO UPDATE SET " + column + "=?")).isInstanceOf(IllegalStateException.class);
        }
        for (String sql : new String[]{"UPDATE tb_hr_mon_pay SET pay_amt=?", "UPDATE tb_hr_mon_pay SET pay_amt=? WHERE pay_ym=?",
                "UPDATE tb_hr_mon_pay SET pay_amt=? WHERE pay_ym=? AND dev_id=? OR 1=1",
                insert + " ON CONFLICT(dev_id) DO UPDATE SET pay_amt=?"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void sourceWritesPermitApprovedProjectionAndCostClassificationNeverAmounts() {
        assertThatCode(() -> PersistenceBoundary.requireAllowed("UPDATE tb_pj_asgn SET input_end_dt=?,early_wthdr_yn='Y',mod_user_id=?,mod_dttm=? WHERE asgn_id=? AND asgn_stat_cd='ACT'")).doesNotThrowAnyException();
        assertThatCode(() -> PersistenceBoundary.requireAllowed("UPDATE tb_co_mon_cost SET cost_cls_cd='IDLE',mod_user_id=?,mod_dttm=? WHERE proj_id=? AND cost_cls_cd='PRE' AND fix_yn='N' AND cost_ym=?")).doesNotThrowAnyException();
        assertThatCode(() -> PersistenceBoundary.requireAllowed("UPDATE tb_pj_asgn SET bill_uprc=?,ctrt_mm=?,mod_user_id=?,mod_dttm=? WHERE asgn_id=?")).doesNotThrowAnyException();
        for (String sql : new String[]{"UPDATE tb_pj_asgn SET dev_id=? WHERE asgn_id=?", "UPDATE tb_pj_asgn SET bill_uprc=?",
                "UPDATE tb_co_mon_cost SET sales_amt=? WHERE proj_id=?", "UPDATE tb_co_mon_cost SET cost_cls_cd='IDLE' WHERE proj_id=?",
                "UPDATE tb_co_mon_cost SET cost_cls_cd='IDLE' WHERE proj_id=? AND cost_cls_cd='PRE' AND fix_yn='Y'",
                "INSERT INTO tb_co_mon_cost(cost_cls_cd) VALUES('IDLE')", "UPDATE tb_pj_drct_exps SET exps_amt=? WHERE proj_id=?",
                "UPDATE tb_sy_user_scope SET proj_id=? WHERE user_id=?"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
        }
    }
}
