package com.sicost.common.notifications;

import com.sicost.salesopportunitiesops.SalesCommandDtos.Notification;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.executor.Executor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@Intercepts(@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}))
public class QuoteNotificationInterceptor implements Interceptor {
    private final ObjectProvider<QuoteNotificationService> service;
    public QuoteNotificationInterceptor(ObjectProvider<QuoteNotificationService> service) {this.service=service;}
    @Override public Object intercept(Invocation invocation) throws Throwable {
        if(((MappedStatement)invocation.getArgs()[0]).getId().equals("com.sicost.salesopportunitiesops.SalesCommandMapper.notification")) {
            service.getObject().rejected(((Notification)invocation.getArgs()[1]).id());
            return 1;
        }
        return invocation.proceed();
    }
}
