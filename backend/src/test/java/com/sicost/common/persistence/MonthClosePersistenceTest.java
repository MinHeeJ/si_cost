package com.sicost.common.persistence;

import java.util.*;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class MonthClosePersistenceTest {
    final String cost="UPDATE tb_co_mon_cost SET labor_alloc_amt=?, fix_yn='Y', mod_user_id=?, mod_dttm=? WHERE cost_ym=? AND dev_id=? AND proj_id=? AND asgn_id=? AND fix_yn='N'";
    BoundSql bound(String sql,String... properties) {
        var config=new Configuration();
        return new BoundSql(config,sql,Arrays.stream(properties).map(p->new ParameterMapping.Builder(config,p,Object.class).build()).toList(),null);
    }
    @Test void fullKeyUnfixedCostReplacementIsManagementOnlyAndDoesNotRelaxOrdinaryPreToIdle() {
        assertThatCode(()->MonthClosePersistence.requireAllowed(cost)).doesNotThrowAnyException();
        assertThatThrownBy(()->PersistenceBoundary.requireAllowed(cost)).isInstanceOf(IllegalStateException.class);
        assertThatCode(()->PersistenceBoundary.requireAllowed("UPDATE tb_co_mon_cost SET cost_cls_cd='IDLE' WHERE proj_id=? AND cost_cls_cd='PRE' AND fix_yn='N'")).doesNotThrowAnyException();
    }
    @Test void newAssignmentAndOperatingProfitSnapshotsUseExplicitFixedAndNonMigratedValues() {
        assertThatCode(()->MonthClosePersistence.requireAllowed("INSERT INTO tb_pj_asgn_mon(asgn_id,input_ym,dev_id,proj_id,input_day_cnt,mon_mm,aply_uprc,sales_amt,fix_yn,reg_user_id,reg_dttm) VALUES(?,?,?,?,?,?,?,?,'Y',?,?)")).doesNotThrowAnyException();
        assertThatCode(()->MonthClosePersistence.requireAllowed("INSERT INTO tb_co_mon_pl(pl_ym,sales_amt,sales_cost_amt,sga_tot_amt,oprt_prft_amt,migr_yn,fix_yn,reg_user_id,reg_dttm) VALUES(?,?,?,?,?,'N','Y',?,?)")).doesNotThrowAnyException();
    }
    @Test void historyAppendAndCheckUpdatesPreserveRegistration() {
        assertThatCode(()->MonthClosePersistence.requireAllowed("INSERT INTO tb_cl_close_hist(close_ym,hist_sn,act_cd,act_rsn,remain_chk_cn,reg_user_id,reg_dttm) VALUES(?,?,?,?,?,?,?)")).doesNotThrowAnyException();
        assertThatCode(()->MonthClosePersistence.requireAllowed("UPDATE tb_cl_close_chk SET chk_stat_cd=?, chk_cnt=?, chk_cn=?, link_scrn_id=?, chk_dttm=?, mod_user_id=?, mod_dttm=? WHERE close_ym=? AND chk_item_cd=?")).doesNotThrowAnyException();
        assertThatCode(()->MonthClosePersistence.requireAllowed("UPDATE tb_cl_close SET close_stat_cd='WORK', close_user_id=NULL, close_dttm=NULL, mod_user_id=?, mod_dttm=? WHERE close_ym=?")).doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(strings={
        "DELETE FROM tb_co_mon_cost", "DELETE FROM tb_co_mon_cost WHERE cost_ym=? AND fix_yn='N'",
        "DELETE FROM tb_cl_close_hist WHERE close_ym=? AND hist_sn=?", "DELETE FROM tb_cl_close_chk WHERE close_ym=? AND chk_item_cd=?",
        "UPDATE tb_cl_close_hist SET act_rsn=? WHERE close_ym=? AND hist_sn=?",
        "UPDATE tb_cl_close SET reg_user_id=? WHERE close_ym=?",
        "UPDATE tb_co_mon_pl SET sales_amt=? WHERE pl_ym=? AND fix_yn='Y' AND migr_yn='N'",
        "UPDATE tb_co_mon_pl SET sales_amt=? WHERE pl_ym=? AND fix_yn='N'",
        "UPDATE tb_co_mon_cost SET cost_cls_cd='IDLE' WHERE proj_id=?",
        "UPDATE tb_co_mon_cost SET fix_yn='N' WHERE cost_ym=? AND dev_id=? AND proj_id=? AND asgn_id=? AND fix_yn='N'",
        "UPDATE tb_co_mon_cost SET fix_yn=? WHERE cost_ym=? AND dev_id=? AND proj_id=? AND asgn_id=? AND fix_yn='N'",
        "INSERT INTO tb_cl_close_hist(close_ym,hist_sn,reg_user_id,reg_dttm) SELECT ?,?,?,?",
        "INSERT INTO tb_co_mon_pl(pl_ym,migr_yn,fix_yn,reg_user_id,reg_dttm) VALUES(?,'Y','Y',?,?)",
        "UPDATE tb_fi_tax_pay SET pay_yn='Y' WHERE tax_yr=? AND pay_type_cd=?"})
    void broadWritesAndSourceMutationsAreForbidden(String sql) {
        assertThatThrownBy(()->MonthClosePersistence.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
    }
    @Test void boundedDeleteOnlyReplacesOneFullUnfixedIdentity() {
        assertThatCode(()->MonthClosePersistence.requireAllowed("DELETE FROM tb_co_mon_cost WHERE cost_ym=? AND dev_id=? AND proj_id=? AND asgn_id=? AND fix_yn='N'")).doesNotThrowAnyException();
        assertThatCode(()->MonthClosePersistence.requireAllowed("DELETE FROM tb_co_mon_pl WHERE pl_ym=? AND fix_yn='N' AND migr_yn='N'")).doesNotThrowAnyException();
    }
    @Test void monthBindingIsCheckedNotMerelyTheScopeLabel() {
        var values=new HashMap<String,Object>(Map.of("month","202607","base","202601","keyMonth","202607"));
        var sql=bound("DELETE FROM tb_co_mon_pl WHERE pl_ym=? AND fix_yn='N' AND migr_yn='N'","keyMonth");
        assertThatCode(()->MonthClosePersistence.requireScope(sql,values)).doesNotThrowAnyException();
        values.put("keyMonth","202608");assertThatThrownBy(()->MonthClosePersistence.requireScope(sql,values)).isInstanceOf(IllegalStateException.class);
        values.put("keyMonth","202607");values.put("base","202608");assertThatThrownBy(()->MonthClosePersistence.requireScope(sql,values)).isInstanceOf(IllegalStateException.class);
    }
    @Test void insertMonthBindingAndInvalidCalendarCannotEscapeScope() {
        var sql=bound("INSERT INTO tb_cl_close_hist(close_ym,hist_sn,act_cd,reg_user_id,reg_dttm) VALUES(?,?,'CLOSE',?,?)","keyMonth","historySequence","actor","at");
        var values=new HashMap<String,Object>(Map.of("month","202607","base","202601","keyMonth","202607"));
        assertThatCode(()->MonthClosePersistence.requireScope(sql,values)).doesNotThrowAnyException();
        values.put("keyMonth","202606");assertThatThrownBy(()->MonthClosePersistence.requireScope(sql,values)).isInstanceOf(IllegalStateException.class);
        values.put("month","202613");assertThatThrownBy(()->MonthClosePersistence.requireScope(sql,values)).isInstanceOf(IllegalStateException.class);
    }
    @Test void reopenRequiresPhysicalOwnershipEvidenceForExactPkAndCloseHistory() {
        String sql="UPDATE tb_co_mon_pl SET fix_yn='N', mod_user_id=?, mod_dttm=? WHERE pl_ym=? AND fix_yn='Y' AND migr_yn='N' AND EXISTS (SELECT 1 FROM tb_sy_chg_hist h WHERE h.trgt_tbl_nm='TB_CO_MON_PL' AND h.trgt_key=? AND h.chg_item_nm='FIX_YN' AND h.bfr_val='N' AND h.aft_val='Y' AND h.chg_rsn=?)";
        assertThatCode(()->MonthClosePersistence.requireAllowed(sql)).doesNotThrowAnyException();
        var bound=bound(sql,"actor","at","month","physicalKey","ownerHistoryKey");
        var values=new HashMap<String,Object>(Map.of("month","202607","base","202601","physicalKey","202607","ownerHistoryKey","202607:1"));
        assertThatCode(()->MonthClosePersistence.requireScope(bound,values)).doesNotThrowAnyException();
        values.put("physicalKey","202608");assertThatThrownBy(()->MonthClosePersistence.requireScope(bound,values)).isInstanceOf(IllegalStateException.class);
        values.put("physicalKey","202607");values.put("ownerHistoryKey","202606:1");assertThatThrownBy(()->MonthClosePersistence.requireScope(bound,values)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->MonthClosePersistence.requireAllowed(sql.substring(0,sql.indexOf(" AND EXISTS")))).isInstanceOf(IllegalStateException.class);
    }
    @Test void runtimeInterceptorSeparatesOperatingProfitFromHistoricalMigrationAndRequiresExclusiveScope() throws Throwable {
        var config=new Configuration();
        String sql="UPDATE tb_co_mon_pl SET sales_amt=? WHERE pl_ym=? AND fix_yn='N' AND migr_yn='N'";
        var mappings=List.of(new ParameterMapping.Builder(config,"amount",Object.class).build(),new ParameterMapping.Builder(config,"month",Object.class).build());
        var statement=new MappedStatement.Builder(config,"com.sicost.monthclose.MonthCloseMapper.updateProfit",
            new org.apache.ibatis.builder.StaticSqlSource(config,sql,mappings),SqlCommandType.UPDATE).build();
        var params=Map.of("month","202607","base","202601","amount","100");
        var executor=org.mockito.Mockito.mock(org.apache.ibatis.executor.Executor.class);
        var call=new org.apache.ibatis.plugin.Invocation(executor,org.apache.ibatis.executor.Executor.class.getMethod("update",MappedStatement.class,Object.class),new Object[]{statement,params});
        var interceptor=new PersistenceBoundary();
        var boundary=org.mockito.Mockito.mock(com.sicost.common.concurrent.MonthCloseBoundary.class);
        interceptor.setMonthCloseBoundary(boundary);
        assertThatThrownBy(()->interceptor.intercept(call)).isInstanceOf(IllegalStateException.class).hasMessageContaining("배타");
        org.mockito.Mockito.verifyNoInteractions(executor);
        org.mockito.Mockito.when(boundary.exclusiveHeld()).thenReturn(true);
        interceptor.intercept(call);
        org.mockito.Mockito.verify(executor).update(statement,params);
        var ordinary=new MappedStatement.Builder(config,"com.sicost.migration.HistoricalProfitLossMapper.updateProfit",
            new org.apache.ibatis.builder.StaticSqlSource(config,sql,mappings),SqlCommandType.UPDATE).build();
        var ordinaryCall=new org.apache.ibatis.plugin.Invocation(executor,org.apache.ibatis.executor.Executor.class.getMethod("update",MappedStatement.class,Object.class),new Object[]{ordinary,params});
        assertThatThrownBy(()->interceptor.intercept(ordinaryCall)).isInstanceOf(IllegalStateException.class).hasMessageContaining("이관");
    }
}
