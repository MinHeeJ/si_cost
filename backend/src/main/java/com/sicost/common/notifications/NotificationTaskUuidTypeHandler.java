package com.sicost.common.notifications;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** Explicitly selected by the publication mapper, not globally registered. */
public final class NotificationTaskUuidTypeHandler extends BaseTypeHandler<UUID> {
    @Override public void setNonNullParameter(PreparedStatement statement, int index, UUID value, JdbcType jdbcType) throws SQLException {
        statement.setObject(index,value,Types.OTHER);
    }
    @Override public UUID getNullableResult(ResultSet results,String column) throws SQLException {return results.getObject(column,UUID.class);}
    @Override public UUID getNullableResult(ResultSet results,int column) throws SQLException {return results.getObject(column,UUID.class);}
    @Override public UUID getNullableResult(CallableStatement statement,int column) throws SQLException {return statement.getObject(column,UUID.class);}
}
