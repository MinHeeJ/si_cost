package com.sicost.common.installation;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class PostgreSqlDatabase {
    final String schema = "sicost_test_" + UUID.randomUUID().toString().replace("-", "");
    final String url = System.getenv("SICOST_TEST_DB_URL");
    final String user = System.getenv("SICOST_TEST_DB_USER");
    final String password = System.getenv("SICOST_TEST_DB_PASSWORD");
    public final DriverManagerDataSource source;
    public final JdbcTemplate sql;

    public PostgreSqlDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            try (var statement = connection.createStatement()) { statement.execute("CREATE SCHEMA " + schema); }
        }
        String scoped = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + "&options=-c%20TimeZone=Asia%2FSeoul";
        source = new DriverManagerDataSource(scoped, user, password);
        sql = new JdbcTemplate(source);
    }
    Flyway flyway() {
        return Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").group(true).baselineOnMigrate(false).cleanDisabled(true).load();
    }
    public void install() { flyway().migrate(); }
    public long count(String table) { return sql.queryForObject("SELECT count(*) FROM " + table, Long.class); }
}
