package com.sicost.common.concurrent;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Covers direct transactions and imports as well as WriteCoordinator, before the first SQL. */
@Component
@Intercepts({
    @Signature(type=Executor.class, method="update", args={MappedStatement.class,Object.class}),
    @Signature(type=Executor.class, method="query", args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class}),
    @Signature(type=Executor.class, method="query", args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class,CacheKey.class,BoundSql.class}),
    @Signature(type=Executor.class, method="queryCursor", args={MappedStatement.class,Object.class,RowBounds.class})
})
public class MonthCloseBoundaryInterceptor implements Interceptor {
    private final MonthCloseBoundary boundary;
    public MonthCloseBoundaryInterceptor(MonthCloseBoundary boundary) { this.boundary = boundary; }
    @Override public Object intercept(Invocation invocation) throws Throwable {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) boundary.shared();
        return invocation.proceed();
    }
}
