package com.sicost.automationrunsops;

import java.sql.*;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;
import static com.sicost.automationrunsops.DailyAutomationDtos.*;

@Component
public class DailyAutomationSessionLocks {
    private final DataSource source;
    public DailyAutomationSessionLocks(DataSource source) { this.source=source; }
    public Lease tryAcquire(Key key) {
        String name="sicost:automation:"+key.jobCode()+":"+key.runDate();
        Connection connection=null;
        try {
            connection=source.getConnection();
            try(var statement=connection.prepareStatement("SELECT pg_try_advisory_lock(hashtextextended(?,0))")) {
                statement.setString(1,name);
                try(var result=statement.executeQuery()) {
                    if(result.next()&&result.getBoolean(1))return new Lease(connection,name);
                }
            }
            connection.close();return null;
        } catch(SQLException error) {
            if(connection!=null)try{connection.close();}catch(SQLException ignored){}
            throw new IllegalStateException("자동 처리 잠금을 확인하지 못했습니다.",error);
        }
    }
    public static class Lease implements AutoCloseable {
        private final Connection connection;
        private final String name;
        private boolean closed;
        Lease(Connection connection,String name) { this.connection=connection;this.name=name; }
        @Override public synchronized void close() {
            if(closed)return;closed=true;
            try(var statement=connection.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?,0))")) {
                statement.setString(1,name);statement.execute();
            } catch(SQLException error) {
                try{connection.abort(Runnable::run);}catch(SQLException ignored){}
            } finally {
                try{connection.close();}catch(SQLException ignored){}
            }
        }
    }
}
