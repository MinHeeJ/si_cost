package com.sicost.permission;

import com.sicost.common.TimeConfiguration;
import java.time.*;
import java.util.*;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;

/** Scope at SQL sources, before database count/sum/order/limit and before workbook generation. */
@Component
@Intercepts(@Signature(type=StatementHandler.class,method="prepare",args={java.sql.Connection.class,Integer.class}))
public class PermissionScopeService implements Interceptor {
    private final ObjectProvider<PermissionService> policies;
    private final Clock clock;
    private ObjectProvider<PermissionWriteGuard> writes;
    @org.springframework.beans.factory.annotation.Autowired
    public void setWrites(ObjectProvider<PermissionWriteGuard> writes) {this.writes=writes;}
    public PermissionScopeService(ObjectProvider<PermissionService> policies,Clock clock) {this.policies=policies;this.clock=clock;}
    @Override public Object intercept(Invocation invocation) throws Throwable {
        var attributes=RequestContextHolder.getRequestAttributes();
        var metadata=SystemMetaObject.forObject(invocation.getTarget());
        var statement=(org.apache.ibatis.mapping.MappedStatement)metadata.getValue("delegate.mappedStatement");
        if(!(attributes instanceof ServletRequestAttributes servlet)) {
            if(PermissionAcceptanceContext.active() && statement.getSqlCommandType()==org.apache.ibatis.mapping.SqlCommandType.SELECT && !authority(statement.getId()) && !PermissionCalculationContext.sourceRead()) {
                var policy=policies.getObject();var allowed=policy.capabilities().scope();
                String current=YearMonth.now(clock.withZone(TimeConfiguration.SEOUL)).toString().replace("-","");
                var scope=new PermissionScopeSql.Scope(allowed.all(),allowed.partIds(),allowed.projectIds(),PermissionAcceptanceContext.fromMonth(current),PermissionAcceptanceContext.toMonth(current),policy.userId());
                String sql=(String)metadata.getValue("delegate.boundSql.sql");metadata.setValue("delegate.boundSql.sql",PermissionScopeSql.rewrite(sql,scope));
            }
            return invocation.proceed();
        }
        if(statement.getSqlCommandType()!=org.apache.ibatis.mapping.SqlCommandType.SELECT) {
            writes.getObject().beforeStatement(statement.getId());
            var request=servlet.getRequest();
            if(!authority(statement.getId()) && request.getAttribute(PermissionOperationRegistry.OPERATION) instanceof PermissionOperationRegistry.Operation operation && operation.policy().startsWith("UI-") && !Set.of("UI-15","UI-23","UI-24","UI-25","UI-27").contains(operation.policy()) && request.getAttribute(PermissionWriteGuard.SCOPE) instanceof PermissionService.Scope scope) {
                String current=YearMonth.now(clock.withZone(TimeConfiguration.SEOUL)).toString().replace("-","");
                var predicate=new PermissionScopeSql.Scope(scope.all(),scope.partIds(),scope.projectIds(),current,current,policies.getObject().userId());
                PermissionMutationScope.check((java.sql.Connection)invocation.getArgs()[0],statement,(org.apache.ibatis.mapping.BoundSql)metadata.getValue("delegate.boundSql"),predicate,operation);
            }
        }
        if(statement.getSqlCommandType()!=org.apache.ibatis.mapping.SqlCommandType.SELECT || authority(statement.getId()) || PermissionCalculationContext.sourceRead())return invocation.proceed();
        Object classification=servlet.getRequest().getAttribute(PermissionOperationRegistry.OPERATION);
        // Mutation invariants (month close, settlement locks, source prices) must not be
        // projected as if they were response data. Their row-write guards are separate.
        boolean validation=classification instanceof PermissionOperationRegistry.Operation operation && operation.action().equals("UPLD") && operation.path().endsWith("/validate");
        if(!"GET".equals(servlet.getRequest().getMethod()) && !validation && !(classification instanceof PermissionOperationRegistry.Operation export && export.handler().equals("MasterDataExportController.createMasterDataExport") && export.policy().startsWith("UI-")))return invocation.proceed();
        boolean global=classification instanceof PermissionOperationRegistry.Operation operation && Set.of("UI-23","UI-24","UI-25","UI-27").contains(operation.policy());
        if(global && !statement.getId().startsWith("com.sicost.apiops.MasterHistoryMapper."))return invocation.proceed();
        var authentication=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)return invocation.proceed();
        var policy=policies.getObject();var capabilities=policy.capabilities();
        var request=servlet.getRequest();String current=YearMonth.now(clock.withZone(TimeConfiguration.SEOUL)).toString().replace("-","");
        String from=current,to=current;
        String year=request.getParameter("year");
        if(year!=null && year.matches("[0-9]{4}")) {from=year+"01";to=year+"12";}
        for(String key:List.of("month","baseMonth")) {String value=month(request.getParameter(key));if(value!=null)from=to=value;}
        for(String key:List.of("from","fromMonth")) {String value=month(request.getParameter(key));if(value!=null)from=value;}
        for(String key:List.of("to","toMonth")) {String value=month(request.getParameter(key));if(value!=null)to=value;}
        String sql=(String)metadata.getValue("delegate.boundSql.sql");
        Map<String,String> fieldPredicates=new HashMap<>();
        if(!capabilities.sensitive())fieldPredicates.put("tb_sy_upld_err","coalesce(permission_row.col_nm,'') !~ '(급여|기본급|지급|실수령|보험|세금|소득세|원천세|퇴직|공제|계좌|은행|예금주|개인.?원가)' ");
        String history="upper(coalesce(permission_row.chg_item_nm,'')) !~ '(PWD|PASSWORD|HASH|MFA|TOKEN|SECRET|CIPHER|비밀번호|암호문)'";
        if(!capabilities.sensitive() && classification instanceof PermissionOperationRegistry.Operation op && op.path().equals("/api/financial-history") && op.policy().equals("UI-19"))
            history+=" AND upper(coalesce(permission_row.chg_item_nm,'')) IN ('PART_ID','SETL_YM','DEV_ID','SETL_STAT_CD','PART_LDR_USER_ID','PROJ_ID','MON_MM','BILL_UPRC')";
        if(statement.getId().startsWith("com.sicost.apiops.PeopleHistoryMapper.")) {
            boolean candidate=classification instanceof PermissionOperationRegistry.Operation op && op.policy().equals("UI-33");
            if(candidate && !policy.candidateRateAllowed())history+=" AND upper(coalesce(permission_row.chg_item_nm,'')) !~ '(UPRC|RATE|단가|희망금액)'";
            if(!candidate && !capabilities.sensitive())history+=" AND permission_row.trgt_tbl_nm<>'TB_HR_DEV_PRC' AND upper(coalesce(permission_row.chg_item_nm,'')) !~ '(UPRC|PAY|SALARY|COST|급여|지급|단가|원가|이익)'";
        }
        if(!capabilities.sensitive())history+=" AND upper(coalesce(permission_row.chg_item_nm,'')) !~ '(ACCT|ACCOUNT|계좌)'";
        fieldPredicates.put("tb_sy_chg_hist",history);
        var scope=new PermissionScopeSql.Scope(global || capabilities.scope().all(),capabilities.scope().partIds(),capabilities.scope().projectIds(),from,to,policy.userId());
        if(classification instanceof PermissionOperationRegistry.Operation op && op.policy().equals("UI-19") && !capabilities.scope().all())
            fieldPredicates.put("tb_bs_part",PermissionScopeSql.predicate("tb_bs_part_std","permission_row",scope));
        String scoped=PermissionScopeSql.rewrite(sql,scope,fieldPredicates);
        metadata.setValue("delegate.boundSql.sql",scoped);
        return invocation.proceed();
    }
    private static String month(String value) {if(value==null)return null;value=value.replace("-","");return value.matches("[0-9]{4}(0[1-9]|1[0-2])")?value:null;}
    private static boolean authority(String id) {
        return id.startsWith("com.sicost.permission.") || id.startsWith("com.sicost.authentication.") || id.startsWith("com.sicost.common.context.") || id.startsWith("com.sicost.currentuserops.");
    }
}
