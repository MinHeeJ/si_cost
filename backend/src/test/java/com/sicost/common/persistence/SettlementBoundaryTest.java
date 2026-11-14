package com.sicost.common.persistence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SettlementBoundaryTest {
    @Test void parentCreationSuppliesNaturalIdentityExplicitCardStatusAndRegistration() {
        assertThatCode(() -> PersistenceBoundary.requireAllowed("INSERT INTO tb_co_part_setl(part_id,setl_ym,corp_card_amt,setl_stat_cd,reg_user_id,reg_dttm) VALUES(?,?,?,'WORK',?,?)"))
            .doesNotThrowAnyException();
    }
    @Test void parentMutationPreservesFullIdentityAndOriginalRegistration() {
        String sql = "UPDATE tb_co_part_setl SET corp_card_amt=?,part_prft_amt=?,setl_stat_cd='CNFM',cnfm_user_id=?,cnfm_dttm=?,cncl_rsn=NULL,mod_user_id=?,mod_dttm=? WHERE part_id=? AND setl_ym=?";
        assertThatCode(() -> PersistenceBoundary.requireAllowed(sql)).doesNotThrowAnyException();
        for (String field : new String[]{"part_id", "setl_ym", "reg_user_id", "reg_dttm", "profit_rate", "unknown"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed("UPDATE tb_co_part_setl SET " + field + "=? WHERE part_id=? AND setl_ym=?"))
                .isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void detailInsertionAndExactPartMonthReplacementAreAllowed() {
        assertThatCode(() -> PersistenceBoundary.requireAllowed("INSERT INTO tb_co_part_setl_dtl(part_id,setl_ym,dev_id,emp_type_cd,proj_nm_list,mm,uprc_amt,pay_amt,sales_exps_amt,retire_amt,cost_amt,diff_amt,prft_amt,rmrk,reg_user_id,reg_dttm) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"))
            .doesNotThrowAnyException();
        assertThatCode(() -> PersistenceBoundary.requireAllowed("DELETE FROM tb_co_part_setl_dtl WHERE part_id=? AND setl_ym=?"))
            .doesNotThrowAnyException();
    }
    @Test void parentDeletionAndDetailUpdatesStayForbidden() {
        for (String sql : new String[]{"DELETE FROM tb_co_part_setl WHERE part_id=? AND setl_ym=?",
            "UPDATE tb_co_part_setl_dtl SET pay_amt=? WHERE part_id=? AND setl_ym=? AND dev_id=?"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void missingIdentityUnboundedMutationAndPartialDetailDeletionAreRefused() {
        for (String sql : new String[]{"INSERT INTO tb_co_part_setl(reg_user_id) VALUES(?)",
            "INSERT INTO tb_co_part_setl(part_id,setl_ym,setl_stat_cd,reg_user_id) VALUES(?,?,'WORK',?)",
            "INSERT INTO tb_co_part_setl_dtl(part_id,setl_ym,reg_user_id) VALUES(?,?,?)",
            "UPDATE tb_co_part_setl SET corp_card_amt=? WHERE part_id=?",
            "UPDATE tb_co_part_setl SET corp_card_amt=? WHERE setl_ym=?",
            "UPDATE tb_co_part_setl SET corp_card_amt=? WHERE part_id=? AND setl_ym=? OR 1=1",
            "DELETE FROM tb_co_part_setl_dtl WHERE part_id=?",
            "DELETE FROM tb_co_part_setl_dtl WHERE part_id=? AND setl_ym>=?",
            "DELETE FROM tb_co_part_setl_dtl WHERE part_id=? AND setl_ym=? AND dev_id=?",
            "DELETE FROM tb_co_part_setl_dtl WHERE part_id=? AND setl_ym=? OR 1=1"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void upsertsBulkIndirectAndQuotedBusinessIdentifiersStayForbidden() {
        String sql = "INSERT INTO tb_co_part_setl(part_id,setl_ym,corp_card_amt,setl_stat_cd,reg_user_id) VALUES(?,?,?,'WORK',?)";
        for (String rejected : new String[]{sql + " ON CONFLICT(part_id,setl_ym) DO UPDATE SET corp_card_amt=?",
            sql + ",(?,?,?,'WORK',?)", sql + "; DELETE FROM tb_co_part_setl_dtl",
            "INSERT INTO tb_co_part_setl(part_id) SELECT part_id FROM tb_bs_part",
            "UPDATE \"TB_CO_PART_SETL\" SET corp_card_amt=? WHERE part_id=? AND setl_ym=?"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(rejected)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void permissionDoesNotExpandToMonthlyCostOrCloseWritesAndAuditIsAppendOnly() {
        for (String sql : new String[]{"INSERT INTO tb_co_mon_cost(dev_id) VALUES(?)",
            "UPDATE tb_cl_close SET close_stat_cd='CLOSE' WHERE close_ym=?",
            "DELETE FROM tb_sy_chg_hist WHERE trgt_key=?", "UPDATE tb_sy_chg_hist SET aft_val=? WHERE hist_id=?"}) {
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
        }
    }
}
