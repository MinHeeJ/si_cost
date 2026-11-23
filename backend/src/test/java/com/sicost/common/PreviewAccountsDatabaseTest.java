package com.sicost.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.sicost.common.installation.PostgreSqlDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@EnabledIfEnvironmentVariable(named = "SICOST_TEST_DB_URL", matches = ".+")
class PreviewAccountsDatabaseTest {
    @Test
    void seedsOnlyPreviewAccountsAndPreservesExistingUsersOnRestart() throws Exception {
        var db = new PostgreSqlDatabase();
        String schema = db.sql.queryForObject("SELECT current_schema()", String.class);
        var configuration = Flyway.configure().dataSource(db.source).schemas(schema);
        var customizer = new PreviewAccountsConfiguration();
        customizer.previewAccountMigrations("sicost-production").customize(configuration);
        configuration.load().migrate();
        assertThat(db.sql.queryForList("SELECT user_id FROM tb_sy_user", String.class))
                .containsExactly("admin");

        customizer.previewAccountMigrations("syncops-preview-test").customize(configuration);
        var preview = configuration.load();
        preview.migrate();
        assertThat(db.sql.query("SELECT user_id, role_id FROM tb_sy_user_role WHERE reg_user_id = 'PREVIEW_SEED'",
                (rs, row) -> Map.entry(rs.getString("user_id"), rs.getString("role_id"))))
                .containsExactlyInAnyOrder(Map.entry("qaadmin", "ADMIN"), Map.entry("exec1", "EXEC"),
                        Map.entry("fin1", "FIN"), Map.entry("sales1", "SALES_LDR"), Map.entry("pm1", "PM"));
        var encoder = new BCryptPasswordEncoder();
        assertThat(encoder.matches("finance1234", db.sql.queryForObject(
                "SELECT pwd_hash FROM tb_sy_user WHERE user_id = 'fin1'", String.class))).isTrue();
        assertThat(db.sql.queryForList(
                "SELECT user_id FROM tb_sy_user WHERE reg_user_id = 'PREVIEW_SEED' AND mfa_use_yn = 'Y'",
                String.class)).containsExactly("qaadmin");

        String changedHash = encoder.encode("Changed-Credential!1");
        db.sql.update("UPDATE tb_sy_user SET pwd_hash = ? WHERE user_id = 'fin1'", changedHash);
        db.sql.update("DELETE FROM tb_sy_user_role WHERE user_id = 'fin1'");
        preview.migrate();
        assertThat(db.sql.queryForObject("SELECT pwd_hash FROM tb_sy_user WHERE user_id = 'fin1'", String.class))
                .isEqualTo(changedHash);
        assertThat(db.sql.queryForList("SELECT role_id FROM tb_sy_user_role WHERE user_id = 'fin1'", String.class))
                .isEmpty();
    }
}
