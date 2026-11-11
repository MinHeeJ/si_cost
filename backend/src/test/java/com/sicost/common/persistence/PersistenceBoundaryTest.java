package com.sicost.common.persistence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PersistenceBoundaryTest {
    @Test void sixTablesOnlyAreWritable() {
        for (String table : new String[]{"TB_SY_CODE_GRP", "TB_SY_CODE", "TB_SY_CODE_MAP", "TB_SY_OPER_STD", "TB_SY_USER_PREF", "TB_SY_CHG_HIST"}) {
            assertThatCode(() -> PersistenceBoundary.requireAllowed("INSERT INTO " + table + " (x) VALUES (?)")).doesNotThrowAnyException();
        }
        for (String table : new String[]{"TB_PJ_PROJ", "TB_CL_CLOSE", "TB_SY_USER", "TB_CO_PART_SETL"}) {
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
    @Test void multiStatementAndIndirectWritesAreRefused() {
        for (String sql : new String[]{"UPDATE tb_sy_code SET code_nm = ?; DELETE FROM tb_sy_user", "WITH x AS (DELETE FROM tb_sy_user) UPDATE tb_sy_code SET code_nm = ?", "TRUNCATE tb_sy_code", "DROP TABLE tb_sy_code"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
        }
    }
}
