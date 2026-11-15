package com.sicost.common.concurrent;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** One transaction-scoped boundary, acquired before any source or aggregate SQL. */
@Component
public class MonthCloseBoundary {
    public static final String LOCK_KEY = "sicost:month-close:boundary";
    private final DataSource source;
    public MonthCloseBoundary(DataSource source) { this.source = source; }

    public void shared() { acquire(false); }
    /** Management commands call this before their first mapper invocation. Never upgrades SHARE. */
    public void exclusive() { acquire(true); }
    public boolean exclusiveHeld() {
        State state = current();
        return state != null && state.exclusive;
    }
    private void acquire(boolean exclusive) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("마감 경계에는 활성 transaction이 필요합니다.");
        }
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            if (exclusive) throw new IllegalStateException("조회 transaction에서는 배타 경계를 획득할 수 없습니다.");
            return;
        }
        State state = current();
        if (state != null) {
            if (exclusive && !state.exclusive) throw new IllegalStateException("공유 경계를 배타 경계로 승격할 수 없습니다.");
            return;
        }
        Connection connection = DataSourceUtils.getConnection(source);
        try (var statement = connection.prepareStatement("SELECT "
                + (exclusive ? "pg_advisory_xact_lock" : "pg_advisory_xact_lock_shared")
                + "(hashtextextended(?, 0))")) {
            statement.setString(1, LOCK_KEY);
            statement.execute();
            TransactionSynchronizationManager.registerSynchronization(new State(exclusive));
        } catch (SQLException error) {
            throw new IllegalStateException("마감 경계를 획득하지 못했습니다.", error);
        } finally {
            DataSourceUtils.releaseConnection(connection, source);
        }
    }
    private State current() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return null;
        return TransactionSynchronizationManager.getSynchronizations().stream()
            .filter(State.class::isInstance).map(State.class::cast).findFirst().orElse(null);
    }
    private static final class State implements TransactionSynchronization {
        final boolean exclusive;
        State(boolean exclusive) { this.exclusive = exclusive; }
    }
}
