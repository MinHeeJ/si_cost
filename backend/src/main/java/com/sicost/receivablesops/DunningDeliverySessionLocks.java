package com.sicost.receivablesops;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
public class DunningDeliverySessionLocks {
    private final DataSource source;
    public DunningDeliverySessionLocks(DataSource source) {this.source=source;}
    public void withLock(String key,Runnable command) {
        // This connection is deliberately NOT enlisted in a Spring financial transaction.
        try(Connection connection=source.getConnection()) {
            boolean acquired=false;
            try {
                try(var statement=connection.prepareStatement("SELECT pg_try_advisory_lock(hashtextextended(?,0))")) {
                    statement.setString(1,"sicost:delivery:"+key);
                    try(var result=statement.executeQuery()){acquired=result.next()&&result.getBoolean(1);}
                }
                if(acquired)command.run();
            } finally {
                if(acquired)try(var statement=connection.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?,0))")) {
                    statement.setString(1,"sicost:delivery:"+key);statement.execute();
                }
            }
        } catch(SQLException e) {throw new IllegalStateException("전달 실행 잠금을 확인하지 못했습니다.");}
    }
}
