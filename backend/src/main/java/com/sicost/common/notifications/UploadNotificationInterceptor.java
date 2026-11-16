package com.sicost.common.notifications;

import java.util.Map;
import java.util.Set;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.executor.Executor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Publish before the owning apply transaction commits; failures propagate through file rollback. */
@Component
@Intercepts(@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}))
public class UploadNotificationInterceptor implements Interceptor {
    private static final Set<String> MAPPERS=Set.of("com.sicost.b1.B1Mapper.markApplied","com.sicost.b2.FinancialImportMapper.markApplied",
        "com.sicost.b5.B5Mapper.markApplied","com.sicost.b6.B6Mapper.markApplied","com.sicost.b7.B7Mapper.markApplied",
        "com.sicost.b8.B8Mapper.markApplied","com.sicost.b9.B9Mapper.markApplied","com.sicost.imports.BusinessPlanImportMapper.markApplied",
        "com.sicost.migration.HistoricalProfitLossImportMapper.markApplied");
    private final ObjectProvider<UploadNotificationService> service;
    public UploadNotificationInterceptor(ObjectProvider<UploadNotificationService> service) {this.service=service;}
    @Override public Object intercept(Invocation invocation) throws Throwable {
        Object result=invocation.proceed();
        if(MAPPERS.contains(((MappedStatement)invocation.getArgs()[0]).getId()) && Integer.valueOf(1).equals(result)) {
            Object parameter=invocation.getArgs()[1];
            Object id=parameter instanceof Map<?,?> map?map.get("uploadId"):parameter.getClass().getMethod("uploadId").invoke(parameter);
            service.getObject().applied(((Number)id).longValue());
        }
        return result;
    }
}
