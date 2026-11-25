package com.sicost.permission;

import com.sicost.common.api.ApiException;
import java.util.*;
import java.util.regex.*;

/** SQL-source projection, before pagination/aggregation. Never filters a materialized result. */
public final class PermissionScopeSql {
    private PermissionScopeSql() {}
    private static final Pattern SOURCE=Pattern.compile("(?i)\\b(FROM|JOIN)\\s+(tb_[a-z0-9_]+)\\b");
    private static final Set<String> KEYWORDS=Set.of("where","join","left","right","inner","outer","cross","full","group","order","limit","offset","fetch","union","except","intersect","having","on","for","window");
    public record Scope(boolean all,Set<String> parts,Set<String> projects,String fromMonth,String toMonth,String userId) {}
    public static String rewrite(String sql,Scope scope) {
        return rewrite(sql,scope,Map.of());
    }
    public static String rewrite(String sql,Scope scope,Map<String,String> extraPredicates) {
        String masked=mask(sql);var matcher=SOURCE.matcher(masked);StringBuilder output=new StringBuilder();int copied=0;
        Map<Integer,String> owners=new HashMap<>();
        while(matcher.find()) {
            String table=matcher.group(2).toLowerCase(Locale.ROOT);
            int depth=0;for(int i=0;i<matcher.start();i++){if(masked.charAt(i)=='(')depth++;else if(masked.charAt(i)==')')depth--;}
            if(matcher.group(1).equalsIgnoreCase("FROM"))owners.put(depth,table);
            // An assignment/bill is owned by its project, not by the descriptive joined
            // developer's home part. A developer-rooted query retains its person policy.
            if(table.equals("tb_hr_dev") && matcher.group(1).equalsIgnoreCase("JOIN")
                && Set.of("tb_pj_asgn","tb_pj_asgn_mon","tb_bl_bill","tb_co_mon_cost","tb_pj_drct_exps").contains(owners.getOrDefault(depth,"")))continue;
            String predicate=predicate(table,"permission_row",scope);
            String extra=extraPredicates.get(table);
            if(extra!=null)predicate=predicate==null?extra:"("+predicate+") AND ("+extra+")";
            if(predicate==null)continue;
            int start=matcher.start(2),end=matcher.end(2);
            output.append(sql,copied,start).append("(SELECT * FROM ").append(table).append(" permission_row WHERE ").append(predicate).append(")");
            String following=sql.substring(end).stripLeading();
            var alias=Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)").matcher(following);
            boolean existingAlias=following.startsWith("\"") || alias.find() && !KEYWORDS.contains(alias.group(1).toLowerCase(Locale.ROOT));
            if(!existingAlias)output.append(" ").append(table);
            copied=end;
        }
        return copied==0?sql:output.append(sql.substring(copied)).toString();
    }
    public static String predicate(String table,String row,Scope scope) {
        if(table.equals("tb_sy_ai_qna"))return row+".user_id="+literal(scope.userId());
        if(table.equals("tb_sy_noti"))return row+".rcvr_user_id="+literal(scope.userId());
        if(table.equals("tb_sy_upld"))return row+".reg_user_id="+literal(scope.userId());
        if(table.equals("tb_sy_upld_err"))return "EXISTS(SELECT 1 FROM tb_sy_upld permission_upload WHERE permission_upload.upld_id="+row+".upld_id AND permission_upload.reg_user_id="+literal(scope.userId())+")";
        if(scope.all())return null;
        return switch(table) {
            case "tb_pj_proj","tb_pj_proj_chg","tb_pj_asgn","tb_pj_mnpw_plan","tb_pj_step","tb_pj_issue","tb_pj_tk_dstrb" -> project(row+".proj_id",null,scope);
            case "tb_pj_week_memo" -> project(row+".proj_id","to_char("+row+".wrt_dt,'YYYYMM')",scope);
            case "tb_pj_proj_part" -> project(row+".proj_id",null,scope);
            case "tb_pj_asgn_mon" -> project(row+".proj_id",row+".input_ym",scope);
            case "tb_co_mon_cost" -> project(row+".proj_id",row+".cost_ym",scope);
            case "tb_pj_drct_exps" -> project(row+".proj_id",row+".exps_ym",scope);
            case "tb_bl_bill" -> project(row+".proj_id",row+".bill_ym",scope);
            case "tb_bl_rcpt","tb_bl_dun_hist" -> "EXISTS(SELECT 1 FROM tb_bl_bill permission_bill WHERE permission_bill.bill_id="+row+".bill_id AND "+project("permission_bill.proj_id","permission_bill.bill_ym",scope)+")";
            case "tb_pj_asgn_hist" -> "EXISTS(SELECT 1 FROM tb_pj_asgn permission_asgn WHERE permission_asgn.asgn_id="+row+".asgn_id AND "+project("permission_asgn.proj_id",null,scope)+")";
            case "tb_hr_dev" -> "("+in(row+".part_id",scope.parts())+" OR EXISTS(SELECT 1 FROM tb_pj_asgn permission_person WHERE permission_person.dev_id="+row+".dev_id AND "+in("permission_person.proj_id",scope.projects())+"))";
            case "tb_hr_dev_prc","tb_hr_dev_skill","tb_hr_dev_stat","tb_hr_mon_pay" -> "EXISTS(SELECT 1 FROM tb_hr_dev permission_dev WHERE permission_dev.dev_id="+row+".dev_id AND "+in("permission_dev.part_id",scope.parts())+")";
            case "tb_co_part_setl","tb_co_part_setl_dtl","tb_bs_part_std" -> in(row+".part_id",scope.parts());
            case "tb_bs_part" -> "("+in(row+".part_id",scope.parts())+" OR EXISTS(SELECT 1 FROM tb_pj_proj_part permission_reference WHERE permission_reference.part_id="+row+".part_id AND "+in("permission_reference.proj_id",scope.projects())+"))";
            case "tb_sl_opp" -> "("+in(row+".part_id",scope.parts())+" OR "+in(row+".proj_id",scope.projects())+")";
            case "tb_sl_opp_need","tb_sl_tent_asgn","tb_sl_aprv_hist" -> "EXISTS(SELECT 1 FROM tb_sl_opp permission_opp WHERE permission_opp.opp_id="+row+".opp_id AND ("+in("permission_opp.part_id",scope.parts())+" OR "+in("permission_opp.proj_id",scope.projects())+"))";
            case "tb_co_mon_pl","tb_fi_item","tb_fi_corp_tax","tb_fi_tax_pay","tb_fi_loan","tb_fi_card","tb_fi_card_pay","tb_fi_card_mon","tb_fi_xfer","tb_co_prev_adj","tb_fi_biz_plan" -> "FALSE";
            default -> {
                if(table.startsWith("tb_pj_") || table.startsWith("tb_co_") || table.startsWith("tb_bl_") || table.startsWith("tb_sl_") || table.startsWith("tb_fi_"))throw new ApiException(503,"POLICY_INTEGRATION_REQUIRED","이 원천의 범위 정책을 확인해야 합니다.");
                if(table.startsWith("tb_hr_") && !Set.of("tb_hr_cand","tb_hr_cand_skill","tb_hr_cand_cntc").contains(table))throw new ApiException(503,"POLICY_INTEGRATION_REQUIRED","이 원천의 범위 정책을 확인해야 합니다.");
                yield null;
            }
        };
    }
    private static String project(String project,String month,Scope scope) {
        String direct=in(project,scope.projects());
        if(scope.parts().isEmpty())return direct;
        String owned="(SELECT permission_part.part_id FROM tb_pj_proj_part permission_part WHERE permission_part.proj_id="+project+" AND permission_part.aply_bgn_ym<=";
        if(month!=null)return "("+direct+" OR "+in(owned+month+" ORDER BY permission_part.aply_bgn_ym DESC LIMIT 1)",scope.parts())+")";
        return "("+direct+" OR EXISTS(SELECT 1 FROM generate_series(to_date("+literal(scope.fromMonth())+",'YYYYMM'),to_date("+literal(scope.toMonth())+",'YYYYMM'),interval '1 month') permission_month WHERE "+in(owned+"to_char(permission_month,'YYYYMM') ORDER BY permission_part.aply_bgn_ym DESC LIMIT 1)",scope.parts())+"))";
    }
    private static String in(String expression,Set<String> values) {return values.isEmpty()?"FALSE":expression+" IN ("+String.join(",",values.stream().sorted().map(PermissionScopeSql::literal).toList())+")";}
    private static String literal(String value) {
        if(value==null || value.length()>100 || value.codePoints().anyMatch(c->c=='\\' || Character.isISOControl(c)))throw new ApiException(503,"SCOPE_CONFIGURATION_INVALID","담당 범위 설정을 확인해 주세요.");
        return "'"+value.replace("'","''")+"'";
    }
    /** Preserve offsets while excluding strings, quoted identifiers and SQL comments from source matching. */
    static String mask(String sql) {
        char[] chars=sql.toCharArray();
        for(int i=0;i<chars.length;i++) {
            char c=chars[i];
            if(c=='\'' || c=='\"') {char quote=c;chars[i]=' ';for(i++;i<chars.length;i++) {c=chars[i];chars[i]=' ';if(c==quote) {if(i+1<chars.length && chars[i+1]==quote)chars[++i]=' ';else break;}}}
            else if(c=='-' && i+1<chars.length && chars[i+1]=='-') {for(;i<chars.length && chars[i]!='\n';i++)chars[i]=' ';}
            else if(c=='/' && i+1<chars.length && chars[i+1]=='*') {chars[i++]=' ';chars[i]=' ';while(i+1<chars.length) {if(chars[i]=='*' && chars[i+1]=='/') {chars[i++]=' ';chars[i]=' ';break;}chars[i++]=' ';}}
        }
        return new String(chars);
    }
}
