package com.sicost.permission;

import com.sicost.common.api.ApiException;
import java.sql.Connection;
import java.util.*;
import java.util.regex.*;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;

/** Check the complete SQL write set; do not rewrite DML to silently perform a partial write. */
public final class PermissionMutationScope {
    private PermissionMutationScope() {}
    private static final Pattern TARGET=Pattern.compile("(?is)^\\s*(UPDATE\\s+|DELETE\\s+FROM\\s+|INSERT\\s+INTO\\s+)(tb_[a-z0-9_]+)\\b");
    public static void check(Connection connection,MappedStatement statement,BoundSql bound,PermissionScopeSql.Scope scope,PermissionOperationRegistry.Operation operation) throws java.sql.SQLException {
        if(scope.all())return;
        String sql=bound.getSql(),masked=PermissionScopeSql.mask(sql);
        // These statements coordinate the original business transaction, but have no row
        // write set. Their following INSERT/UPDATE/DELETE still undergo the complete check.
        if(masked.matches("(?is)\\s*LOCK\\s+TABLE\\s+tb_[a-z0-9_]+\\s+IN\\s+(?:ACCESS\\s+SHARE|ROW\\s+SHARE|ROW\\s+EXCLUSIVE|SHARE(?:\\s+ROW\\s+EXCLUSIVE)?|EXCLUSIVE|ACCESS\\s+EXCLUSIVE)\\s+MODE\\s*;?\\s*")
            || sql.matches("(?is)\\s*SET\\s+LOCAL\\s+lock_timeout\\s*=\\s*'[0-9]+(?:ms|s)'\\s*;?\\s*"))return;
        var target=TARGET.matcher(masked);
        if(!target.find())throw new ApiException(503,"POLICY_INTEGRATION_REQUIRED","이 쓰기 원천의 범위 검증을 확인해야 합니다.");
        String table=target.group(2).toLowerCase(Locale.ROOT);
        // These are server-owned publication/authentication metadata, not business row ownership.
        if(table.startsWith("tb_sy_"))return;
        String verb=target.group(1).strip().toUpperCase(Locale.ROOT);
        boolean insert=verb.startsWith("INSERT");
        String alias=insert?"permission_row":table;
        String remainder=masked.substring(target.end());
        if(!insert) {
            var explicit=Pattern.compile("(?is)^\\s+(?:AS\\s+)?([a-z_][a-z0-9_]*)\\s+(?:SET|WHERE)\\b").matcher(remainder);
            if(explicit.find())alias=explicit.group(1);
        }
        var writeScope=table.equals("tb_hr_dev")?new PermissionScopeSql.Scope(scope.all(),scope.parts(),Set.of(),scope.fromMonth(),scope.toMonth(),scope.userId()):scope;
        String predicate=PermissionScopeSql.predicate(table,alias,writeScope);
        if(predicate==null)return;
        // New projects acquire their first part row in this same transaction. The part-row
        // check below validates the incoming owner, not a nonexistent prior ownership row.
        if(insert && table.equals("tb_pj_proj") && (operation.action().equals("REG") || operation.action().equals("UPLD")))return;
        if(insert && table.equals("tb_pj_proj_part"))predicate=scope.parts().isEmpty()?"FALSE":"permission_row.part_id IN ("+String.join(",",scope.parts().stream().sorted().map(value->"'"+value.replace("'","''")+"'").toList())+")";
        String query;int first=0,last;
        if(insert) {
            int open=masked.indexOf('(',target.end()),close=open<0?-1:masked.indexOf(')',open);
            if(open<0 || close<0)throw unsupported();
            String columns=sql.substring(open+1,close);
            if(!columns.matches("[a-zA-Z0-9_,\\s]+"))throw unsupported();
            var values=Pattern.compile("(?is)\\bVALUES\\b").matcher(masked);if(!values.find(close))throw unsupported();
            int end=masked.length();var tail=Pattern.compile("(?is)\\b(?:ON\\s+CONFLICT|RETURNING)\\b").matcher(masked);if(tail.find(values.end()))end=tail.start();
            String rows=sql.substring(values.start(),end).strip().replaceFirst(";\\s*$","");
            query="SELECT EXISTS (SELECT 1 FROM ("+rows+") permission_row("+columns+") WHERE ("+predicate+") IS NOT TRUE)";
            last=questions(masked.substring(0,end));
        } else {
            var where=Pattern.compile("(?is)\\bWHERE\\b").matcher(masked);boolean found=false;while(where.find())if(where.start()>target.end() && depth(masked,where.start())==0) {found=true;break;}if(!found)throw unsupported();
            int begin=where.end(),end=masked.length();var returning=Pattern.compile("(?is)\\bRETURNING\\b").matcher(masked);if(returning.find(begin))end=returning.start();
            first=questions(masked.substring(0,begin));last=questions(masked.substring(0,end));
            String condition=sql.substring(begin,end).strip().replaceFirst(";\\s*$","");
            query="SELECT EXISTS (SELECT 1 FROM "+table+(alias.equals(table)?"":" "+alias)+" WHERE ("+condition+") AND ("+predicate+") IS NOT TRUE)";
        }
        if(last>bound.getParameterMappings().size())throw unsupported();
        BoundSql check=new BoundSql(statement.getConfiguration(),query,bound.getParameterMappings().subList(first,last),bound.getParameterObject());
        bound.getAdditionalParameters().forEach(check::setAdditionalParameter);
        try(var prepared=connection.prepareStatement(query)) {
            new DefaultParameterHandler(statement,bound.getParameterObject(),check).setParameters(prepared);
            try(var result=prepared.executeQuery()) {if(!result.next() || result.getBoolean(1))throw new ApiException(404,"NOT_FOUND","요청한 자료를 찾을 수 없습니다.");}
        }
    }
    private static int questions(String sql) {return (int)sql.chars().filter(c->c=='?').count();}
    private static int depth(String sql,int end) {int depth=0;for(int i=0;i<end;i++) {if(sql.charAt(i)=='(')depth++;else if(sql.charAt(i)==')')depth--;}return depth;}
    private static ApiException unsupported() {return new ApiException(503,"POLICY_INTEGRATION_REQUIRED","이 쓰기 원천의 전량 범위 검증을 확인해야 합니다.");}
}
