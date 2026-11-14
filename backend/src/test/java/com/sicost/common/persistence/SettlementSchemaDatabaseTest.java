package com.sicost.common.persistence;

import com.sicost.common.installation.PostgreSqlDatabase;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL", matches=".+")
class SettlementSchemaDatabaseTest {
    PostgreSqlDatabase db;
    @BeforeEach void installDisposableSchema() throws Exception { db = new PostgreSqlDatabase(); db.install(); }
    @AfterEach void removeOnlyOwnedSchema() {
        if (db != null) {
            String schema = db.sql.queryForObject("SELECT current_schema()", String.class);
            if (schema == null || !schema.matches("sicost_test_[a-f0-9]{32}")) throw new IllegalStateException("시험 스키마만 정리합니다.");
            db.sql.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
    @Test void installedNaturalKeysNullDefaultsWidthsAndIndexesArePreserved() {
        assertThat(primaryKey("tb_co_part_setl")).containsExactly("part_id", "setl_ym");
        assertThat(primaryKey("tb_co_part_setl_dtl")).containsExactly("part_id", "setl_ym", "dev_id");
        var card = column("tb_co_part_setl", "corp_card_amt");
        assertThat(card.get("is_nullable")).isEqualTo("NO"); assertThat(card.get("column_default")).isNull();
        assertThat(((Number) card.get("numeric_precision")).intValue()).isEqualTo(15);
        assertThat(column("tb_co_part_setl", "setl_stat_cd").get("column_default")).isNull();
        assertThat(column("tb_co_part_setl", "part_prft_amt").get("is_nullable")).isEqualTo("YES");
        assertThat(((Number) column("tb_co_part_setl_dtl", "proj_nm_list").get("character_maximum_length")).intValue()).isEqualTo(1000);
        assertThat(((Number) column("tb_sy_chg_hist", "bfr_val").get("character_maximum_length")).intValue()).isEqualTo(500);
        assertThat(((Number) column("tb_sy_chg_hist", "aft_val").get("character_maximum_length")).intValue()).isEqualTo(500);
        for (String table : List.of("tb_co_part_setl", "tb_co_part_setl_dtl")) {
            assertThat(column(table, "reg_user_id").get("is_nullable")).isEqualTo("NO");
            assertThat(column(table, "reg_dttm").get("column_default")).isEqualTo("CURRENT_TIMESTAMP");
            assertThat(column(table, "mod_dttm").get("is_nullable")).isEqualTo("YES");
        }
        assertThat(db.sql.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname=current_schema() AND tablename IN ('tb_co_part_setl','tb_co_part_setl_dtl')", String.class))
            .contains("ix_tb_co_part_setl_01", "ix_tb_co_part_setl_dtl_01", "ix_tb_co_part_setl_dtl_02");
        assertThat(db.sql.queryForList("SELECT conname FROM pg_constraint WHERE contype='f' AND connamespace=current_schema()::regnamespace AND conrelid IN ('tb_co_part_setl'::regclass,'tb_co_part_setl_dtl'::regclass)", String.class))
            .contains("fk_tb_co_part_setl_01", "fk_tb_co_part_setl_02", "fk_tb_co_part_setl_dtl_01", "fk_tb_co_part_setl_dtl_02");
        assertThat(db.sql.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_hr_mon_pay'", String.class)).doesNotContain("fix_yn");
    }
    @Test void restartingMigrationLifecyclePreservesHistoryAndEveryBusinessFieldWithoutExampleInsertion() {
        var before = snapshot();
        db.install();
        assertThat(snapshot()).isEqualTo(before);
    }
    Map<String,List<String>> snapshot() {
        var result = new LinkedHashMap<String,List<String>>();
        for (String table : List.of("flyway_schema_history", "tb_co_part_setl", "tb_co_part_setl_dtl", "tb_bs_part", "tb_bs_part_std",
                "tb_hr_dev", "tb_hr_dev_prc", "tb_hr_mon_pay", "tb_pj_asgn", "tb_pj_asgn_mon", "tb_co_mon_cost", "tb_cl_close")) {
            result.put(table, db.sql.queryForList("SELECT row_to_json(t)::text FROM " + table + " t ORDER BY row_to_json(t)::text", String.class));
        }
        return result;
    }
    List<String> primaryKey(String table) {
        return db.sql.queryForList("SELECT a.attname FROM pg_constraint c CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY k(attnum,ord) JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.attnum WHERE c.contype='p' AND c.conrelid=?::regclass ORDER BY k.ord", String.class, table);
    }
    Map<String,Object> column(String table, String name) {
        return db.sql.queryForMap("SELECT is_nullable,column_default,numeric_precision,character_maximum_length FROM information_schema.columns WHERE table_schema=current_schema() AND table_name=? AND column_name=?", table, name);
    }
}
