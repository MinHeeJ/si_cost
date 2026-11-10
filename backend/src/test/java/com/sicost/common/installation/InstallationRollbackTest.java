package com.sicost.common.installation;

import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL", matches=".+")
class InstallationRollbackTest {
    public static class V1__InstallationProbe extends BaseJavaMigration {
        @Override public void migrate(Context context) throws SQLException {
            try (var sql = context.getConnection().createStatement()) {
                sql.execute("CREATE TABLE installation_probe (id integer PRIMARY KEY)");
                sql.execute("INSERT INTO installation_probe VALUES (1)");
                sql.execute("INSERT INTO installation_probe VALUES (1)");
            }
        }
    }
    @Test void failedInstallRollsBackDdlAndDataInOnlyItsOwnedSchema() throws Exception {
        PostgreSqlDatabase db = new PostgreSqlDatabase();
        Flyway flyway = Flyway.configure().dataSource(db.source).schemas(db.schema).defaultSchema(db.schema)
                .locations("classpath:installation-isolated").javaMigrations(new V1__InstallationProbe())
                .baselineOnMigrate(false).cleanDisabled(true).load();
        assertThatThrownBy(flyway::migrate).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
        assertThat(db.sql.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=? AND table_name='installation_probe'",Long.class,db.schema)).isZero();
        assertThat(db.sql.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Long.class)).isZero();
    }
    @Test void mismatchedNonEmptyStructureIsReportedNotBaselinedOrChanged() throws Exception {
        PostgreSqlDatabase db = new PostgreSqlDatabase();
        db.sql.execute("CREATE TABLE tb_sy_code_grp (incorrect_name integer)");
        assertThatThrownBy(() -> db.flyway().migrate()).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
        var columns = db.sql.queryForList("SELECT column_name || ':' || data_type FROM information_schema.columns WHERE table_schema=? AND table_name='tb_sy_code_grp'",String.class,db.schema);
        assertThat(columns).containsExactly("incorrect_name:integer");
        assertThat(db.sql.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=? AND table_name='tb_sy_code'",Long.class,db.schema)).isZero();
    }
    @Test void migrationHistoryChecksumsAreUnchangedAcrossRestarts() throws Exception {
        PostgreSqlDatabase db = new PostgreSqlDatabase(); db.install();
        var before = db.sql.queryForList("SELECT version, checksum FROM flyway_schema_history ORDER BY installed_rank");
        db.flyway().migrate(); db.flyway().validate();
        assertThat(db.sql.queryForList("SELECT version, checksum FROM flyway_schema_history ORDER BY installed_rank")).isEqualTo(before);
    }
}
