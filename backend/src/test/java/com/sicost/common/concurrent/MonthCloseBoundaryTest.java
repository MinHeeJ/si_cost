package com.sicost.common.concurrent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonthCloseBoundaryTest {
    final DataSource source=mock(DataSource.class);
    final Connection connection=mock(Connection.class);
    final PreparedStatement statement=mock(PreparedStatement.class);
    final MonthCloseBoundary boundary=new MonthCloseBoundary(source);
    @BeforeEach void setup() throws Exception {
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }
    @AfterEach void cleanup() {
        for(var sync:TransactionSynchronizationManager.getSynchronizations())sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        TransactionSynchronizationManager.clear();
    }
    @Test void sharedIsTransactionScopedAndIdempotent() throws Exception {
        boundary.shared();boundary.shared();
        verify(connection,times(1)).prepareStatement("SELECT pg_advisory_xact_lock_shared(hashtextextended(?, 0))");
        verify(statement).setString(1,MonthCloseBoundary.LOCK_KEY);
        assertThat(boundary.exclusiveHeld()).isFalse();
    }
    @Test void exclusiveFirstNeverAcquiresShareAndMayBeReenteredByOrdinaryHelpers() throws Exception {
        boundary.exclusive();boundary.shared();boundary.exclusive();
        verify(connection,times(1)).prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))");
        assertThat(boundary.exclusiveHeld()).isTrue();
    }
    @Test void sharePromotionFailsBeforeAnyExclusiveSql() throws Exception {
        boundary.shared();
        assertThatThrownBy(boundary::exclusive).isInstanceOf(IllegalStateException.class).hasMessageContaining("승격");
        verify(connection,times(1)).prepareStatement(anyString());
    }
    @Test void readOnlySnapshotsAcquireNoWriteLock() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        boundary.shared();
        assertThatThrownBy(boundary::exclusive).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(source);
    }
    @Test void missingTransactionIsRejectedWithoutConnection() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertThatThrownBy(boundary::shared).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(source);
    }
    @Test void databaseFailureCannotMarkExclusiveHeld() throws Exception {
        when(statement.execute()).thenThrow(new SQLException("failure"));
        assertThatThrownBy(boundary::exclusive).isInstanceOf(IllegalStateException.class);
        assertThat(boundary.exclusiveHeld()).isFalse();
    }
    @Test void directMapperWriteAcquiresBoundaryBeforeExecutingSql() throws Throwable {
        var executor=mock(Executor.class);var mapped=mock(MappedStatement.class);
        var call=new Invocation(executor,Executor.class.getMethod("update",MappedStatement.class,Object.class),new Object[]{mapped,null});
        new MonthCloseBoundaryInterceptor(boundary).intercept(call);
        var order=inOrder(statement,executor);
        order.verify(statement).execute();order.verify(executor).update(mapped,null);
    }
    @Test void directSourceReadInWriteTransactionAcquiresBoundaryBeforeAggregateLockQuery() throws Throwable {
        var executor=mock(Executor.class);var mapped=mock(MappedStatement.class);
        var call=new Invocation(executor,Executor.class.getMethod("query",MappedStatement.class,Object.class,org.apache.ibatis.session.RowBounds.class,org.apache.ibatis.session.ResultHandler.class),new Object[]{mapped,null,org.apache.ibatis.session.RowBounds.DEFAULT,null});
        new MonthCloseBoundaryInterceptor(boundary).intercept(call);
        var order=inOrder(statement,executor);
        order.verify(statement).execute();order.verify(executor).query(mapped,null,org.apache.ibatis.session.RowBounds.DEFAULT,null);
    }
}
