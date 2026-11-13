package com.sicost.common.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Validate the packaged physical catalog after Flyway, without reading specification inputs or writing business data. */
@Component
public class SchemaContractVerifier implements ApplicationRunner {
    private final JdbcTemplate sql;
    private final ObjectMapper json;

    public SchemaContractVerifier(JdbcTemplate sql, ObjectMapper json) {
        this.sql = sql;
        this.json = json;
    }

    @Override public void run(ApplicationArguments arguments) throws Exception {
        verify();
    }

    public void verify() throws Exception {
        JsonNode catalog;
        try (var input = new ClassPathResource("db/catalog.json").getInputStream()) {
            catalog = json.readTree(input);
        }
        var actualColumns = sql.queryForList("""
            SELECT c.relname AS table_name, a.attname AS column_name,
                   format_type(a.atttypid,a.atttypmod) AS column_type, NOT a.attnotnull AS nullable
            FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid
            JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname=current_schema() AND c.relkind='r' AND a.attnum>0 AND NOT a.attisdropped
            """);
        var actualKeys = sql.queryForList("""
            SELECT t.relname AS table_name, a.attname AS column_name
            FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
            JOIN pg_namespace n ON n.oid=t.relnamespace
            CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY k(attnum,ord)
            JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.attnum
            WHERE n.nspname=current_schema() AND c.contype='p' ORDER BY t.relname,k.ord
            """);
        List<String> violations = new ArrayList<>();
        var tables = catalog.fields();
        while (tables.hasNext()) {
            var table = tables.next();
            String name = table.getKey().toLowerCase(Locale.ROOT);
            for (JsonNode column : table.getValue().path("columns")) {
                String field = column.path("name").asText();
                var actual = actualColumns.stream().filter(row -> name.equals(row.get("table_name"))
                    && field.equals(row.get("column_name"))).findFirst();
                String type = column.path("type").asText().replace("varchar(", "character varying(")
                    .replace("char(", "character(").replace("timestamp", "timestamp without time zone")
                    .replaceAll("numeric\\((\\d+)\\)", "numeric($1,0)");
                if (actual.isEmpty() || !type.equals(actual.get().get("column_type"))
                    || !Boolean.valueOf(column.path("nullable").asBoolean()).equals(actual.get().get("nullable"))) {
                    violations.add(name + "." + field);
                }
            }
            var keys = actualKeys.stream().filter(row -> name.equals(row.get("table_name")))
                .map(row -> row.get("column_name").toString()).toList();
            List<String> expectedKeys = new ArrayList<>();
            table.getValue().path("primaryKey").forEach(key -> expectedKeys.add(key.asText()));
            if (!keys.equals(expectedKeys)) violations.add(name + ".PRIMARY_KEY");
        }
        if (!violations.isEmpty()) {
            // Identifiers only: never include stored values, connection details or credentials.
            throw new IllegalStateException("DB_SCHEMA_CONTRACT_MISMATCH: " + String.join(", ", violations));
        }
    }
}
