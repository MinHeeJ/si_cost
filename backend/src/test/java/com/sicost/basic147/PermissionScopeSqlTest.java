package com.sicost.basic147;

import com.sicost.permission.PermissionScopeSql;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PermissionScopeSqlTest {
    final PermissionScopeSql.Scope scope=new PermissionScopeSql.Scope(false,Set.of("PART-001","HQ"),Set.of("P-001"),"202601","202612","User.Case");
    @Test void scopesSourcesBeforeAggregationAndPaginationWithAliasesIntact() {
        String scoped=PermissionScopeSql.rewrite("SELECT p.proj_id, SUM(c.sales_amt) FROM tb_pj_proj p JOIN tb_co_mon_cost c ON c.proj_id=p.proj_id WHERE c.cost_ym=? GROUP BY p.proj_id ORDER BY p.proj_id LIMIT ?",scope);
        assertThat(scoped).contains("(SELECT * FROM tb_pj_proj permission_row WHERE", ") p JOIN", ") c ON", "permission_row.cost_ym", "'P-001'", "'PART-001'", "'HQ'", "LIMIT ?");
        assertThat(scoped.chars().filter(c->c=='?').count()).isEqualTo(2);
    }
    @Test void addsTheOriginalTableNameWhenThereIsNoAliasAndNeverRewritesAStringOrComment() {
        String scoped=PermissionScopeSql.rewrite("SELECT 'FROM tb_pj_proj' AS note,proj_id FROM tb_pj_proj WHERE proj_id=? -- JOIN tb_hr_dev\n",scope);
        assertThat(scoped).contains("'FROM tb_pj_proj'", ") tb_pj_proj WHERE", "-- JOIN tb_hr_dev");
    }
    @Test void projectAndPartScopesUnionWithoutInventingAHeadOfficeScopeType() {
        String predicate=PermissionScopeSql.predicate("tb_bl_bill","bill",scope);
        assertThat(predicate).contains("bill.proj_id IN ('P-001')", "permission_part.aply_bgn_ym<=bill.bill_ym", "'HQ'", "'PART-001'");
        assertThat(PermissionScopeSql.predicate("tb_co_part_setl","s",new PermissionScopeSql.Scope(false,Set.of(),Set.of(),"202601","202612","u"))).isEqualTo("FALSE");
    }
    @Test void allDoesNotBypassQnaNotificationAndUploadOwnership() {
        var all=new PermissionScopeSql.Scope(true,Set.of(),Set.of(),"202601","202612","u");
        assertThat(PermissionScopeSql.predicate("tb_pj_proj","p",all)).isNull();
        assertThat(PermissionScopeSql.predicate("tb_sy_ai_qna","q",all)).isEqualTo("q.user_id='u'");
        assertThat(PermissionScopeSql.predicate("tb_sy_noti","n",all)).isEqualTo("n.rcvr_user_id='u'");
        assertThat(PermissionScopeSql.predicate("tb_sy_upld","f",all)).isEqualTo("f.reg_user_id='u'");
    }
}
