package com.sicost.permission;

import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PermissionRowMapper {
    enum Entity {
        PROJECT("tb_pj_proj","proj_id"), ASSIGNMENT("tb_pj_asgn","asgn_id"), BILL("tb_bl_bill","bill_id"),
        RECEIPT("tb_bl_rcpt","rcpt_id"), DEVELOPER("tb_hr_dev","dev_id"), OPPORTUNITY("tb_sl_opp","opp_id"), TENTATIVE("tb_sl_tent_asgn","tent_id"), PART("tb_bs_part","part_id");
        final String table,key;
        Entity(String table,String key) {this.table=table;this.key=key;}
    }
    record Target(Entity entity,String id) {}
    @SelectProvider(type=Sql.class,method="permitted")
    boolean permitted(@Param("target") Target target,@Param("scope") PermissionScopeSql.Scope scope);
    final class Sql {
        private Sql() {}
        public static String permitted(Map<String,Object> parameters) {
            Target target=(Target)parameters.get("target");var scope=(PermissionScopeSql.Scope)parameters.get("scope");
            String predicate=PermissionScopeSql.predicate(target.entity().table,"permission_row",scope);
            return "SELECT EXISTS (SELECT 1 FROM "+target.entity().table+" permission_row WHERE permission_row."+target.entity().key+(target.entity()==Entity.TENTATIVE || target.entity()==Entity.RECEIPT?"::text":"")+"=#{target.id}"+(predicate==null?"":" AND ("+predicate+")")+")";
        }
    }
}
