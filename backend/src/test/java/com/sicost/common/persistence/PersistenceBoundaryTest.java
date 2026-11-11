package com.sicost.common.persistence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PersistenceBoundaryTest {
    @Test void existingAndExplicitMasterTablesOnlyAreWritable() {
        for (String table : new String[]{"TB_SY_CODE_GRP", "TB_SY_CODE", "TB_SY_CODE_MAP", "TB_SY_OPER_STD", "TB_SY_USER_PREF", "TB_SY_CHG_HIST",
                "TB_SY_USER", "TB_SY_USER_ROLE", "TB_BS_CLNT", "TB_BS_PART", "TB_BS_PART_STD", "TB_SY_UPLD", "TB_SY_UPLD_ERR", "TB_SY_ACCS_LOG"}) {
            assertThatCode(() -> PersistenceBoundary.requireAllowed("INSERT INTO " + table + " (x) VALUES (?)")).doesNotThrowAnyException();
        }
        for (String table : new String[]{"TB_PJ_PROJ", "TB_CL_CLOSE", "TB_SY_ROLE", "TB_CO_PART_SETL", "TB_HR_DEV"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("UPDATE " + table + " SET x = ?")).isInstanceOf(IllegalStateException.class);
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
}
