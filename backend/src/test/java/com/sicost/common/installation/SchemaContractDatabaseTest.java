package com.sicost.common.installation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.persistence.SchemaContractVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL", matches=".+")
class SchemaContractDatabaseTest {
    @Test void packagedCatalogAndRepeatableSnapshotMatchEveryConstraintAndIndex() throws Exception {
        PostgreSqlDatabase migrated = new PostgreSqlDatabase();
        PostgreSqlDatabase snapshot = new PostgreSqlDatabase();
        migrated.install();
        var verifier = new SchemaContractVerifier(migrated.sql, new ObjectMapper());
        verifier.verify();
        String ddl = new ClassPathResource("schema.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // PostgreSQL, not Spring's semicolon splitter, parses dollar-quoted DO blocks.
        try (var connection = snapshot.source.getConnection(); var statement = connection.createStatement()) {
            statement.execute(ddl);
            statement.execute(ddl);
        }
        new SchemaContractVerifier(snapshot.sql, new ObjectMapper()).verify();
        assertThat(constraints(migrated)).containsExactlyElementsOf(constraints(snapshot));
        assertThat(indexes(migrated)).containsExactlyElementsOf(indexes(snapshot));
        var before = migrated.sql.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(migrated.flyway().migrate().migrationsExecuted).isZero();
        assertThat(migrated.sql.queryForList("SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(before);
        verifier.verify();
    }

    @Test void reportsMissingColumnWrongTypeNullabilityAndPrimaryKeyWithoutRepairingData() throws Exception {
        PostgreSqlDatabase db = new PostgreSqlDatabase();
        db.install();
        db.sql.execute("ALTER TABLE tb_co_mon_cost DROP COLUMN free_mm");
        db.sql.execute("ALTER TABLE tb_co_mon_cost ALTER COLUMN labor_alloc_amt TYPE numeric(14)");
        db.sql.execute("ALTER TABLE tb_co_mon_cost ALTER COLUMN sales_amt DROP NOT NULL");
        db.sql.execute("ALTER TABLE tb_co_mon_cost DROP CONSTRAINT pk_tb_co_mon_cost");
        var verifier = new SchemaContractVerifier(db.sql, new ObjectMapper());
        assertThatThrownBy(verifier::verify).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("tb_co_mon_cost.free_mm")
            .hasMessageContaining("tb_co_mon_cost.labor_alloc_amt")
            .hasMessageContaining("tb_co_mon_cost.sales_amt")
            .hasMessageContaining("tb_co_mon_cost.PRIMARY_KEY");
        assertThat(db.count("tb_co_mon_cost")).isZero();
    }

    private java.util.List<String> constraints(PostgreSqlDatabase db) {
        return db.sql.queryForList("""
            SELECT t.relname || ':' || c.conname || ':' || c.contype::text || ':' ||
                   replace(pg_get_constraintdef(c.oid), ? || '.', '')
            FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
            JOIN pg_namespace n ON n.oid=t.relnamespace
            WHERE n.nspname=? AND t.relname LIKE 'tb_%' ORDER BY t.relname,c.conname
            """, String.class, db.schema, db.schema);
    }

    private java.util.List<String> indexes(PostgreSqlDatabase db) {
        return db.sql.queryForList("""
            SELECT tablename || ':' || indexname || ':' || replace(indexdef, ? || '.', '')
            FROM pg_indexes WHERE schemaname=? AND tablename LIKE 'tb_%'
            ORDER BY tablename,indexname
            """, String.class, db.schema, db.schema);
    }
}
