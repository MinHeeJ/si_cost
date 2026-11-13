package com.sicost.common.installation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL", matches=".+")
class SchemaInstallationTest {
    @Test void fullCatalogMatchesEveryOriginalColumnAndKey() throws Exception {
        PostgreSqlDatabase db = new PostgreSqlDatabase(); db.install();
        assertThat(db.sql.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name LIKE 'tb_%'", Long.class, db.schema)).isEqualTo(68);
        assertThat(db.sql.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = ? AND table_name LIKE 'tb_%'", Long.class, db.schema)).isEqualTo(836);
        assertThat(db.sql.queryForObject("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=? AND c.relkind='S'", Long.class, db.schema)).isEqualTo(19);
        for (var expected : java.util.Map.of("f",73L,"c",46L,"u",5L,"p",68L).entrySet()) {
            assertThat(db.sql.queryForObject("SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname=? AND c.contype::text=? AND c.conrelid::regclass::text NOT LIKE '%flyway%'", Long.class, db.schema, expected.getKey())).isEqualTo(expected.getValue());
        }
        assertThat(db.sql.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname=? AND indexname LIKE 'ix_tb_%'", Long.class, db.schema)).isEqualTo(78);
        JsonNode catalog = new ObjectMapper().readTree(new ClassPathResource("db/catalog.json").getInputStream());
        var tables = catalog.fields();
        while (tables.hasNext()) {
            var table = tables.next(); String name = table.getKey().toLowerCase(java.util.Locale.ROOT);
            for (JsonNode column : table.getValue().get("columns")) {
                var actual = db.sql.queryForMap("SELECT format_type(a.atttypid,a.atttypmod) AS type, NOT a.attnotnull AS nullable, pg_get_expr(d.adbin,d.adrelid) AS default_value, col_description(a.attrelid,a.attnum) AS comment FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON n.oid=c.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE n.nspname=? AND c.relname=? AND a.attname=? AND a.attnum>0", db.schema, name, column.get("name").asText());
                String expectedType = column.get("type").asText().replace("varchar(","character varying(").replace("char(","character(").replace("timestamp","timestamp without time zone").replaceAll("numeric\\((\\d+)\\)", "numeric($1,0)");
                assertThat(actual.get("type")).as(name+"."+column.get("name").asText()).isEqualTo(expectedType);
                assertThat(actual.get("nullable")).isEqualTo(column.get("nullable").asBoolean());
                assertThat(actual.get("comment")).isNotNull();
                if (column.get("default").isNull()) assertThat(actual.get("default_value")).isNull();
                else {
                    String expectedDefault = column.get("default").asText();
                    if (expectedDefault.equals("CURRENT_TIMESTAMP")) assertThat(actual.get("default_value").toString()).isEqualTo("CURRENT_TIMESTAMP");
                    else if (expectedDefault.startsWith("nextval")) assertThat(actual.get("default_value").toString().toLowerCase()).contains(expectedDefault.substring(9,expectedDefault.length()-2).toLowerCase());
                    else assertThat(actual.get("default_value").toString()).startsWith(expectedDefault);
                }
            }
            var primaryKey = db.sql.queryForList("SELECT a.attname FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY k(attnum,ord) JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.attnum WHERE n.nspname=? AND t.relname=? AND c.contype='p' ORDER BY k.ord", String.class, db.schema, name);
            assertThat(primaryKey).containsExactlyElementsOf(new ObjectMapper().convertValue(table.getValue().get("primaryKey"), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {}));
        }
        assertThat(catalog.get("TB_SY_CALC_RULE").get("columns").size()).isEqualTo(11);
        assertThat(db.sql.queryForObject("SHOW TIME ZONE", String.class)).isEqualTo("Asia/Seoul");
    }
    @Test void fullSeedAndRestartPreserveAllCounts() throws Exception {
        PostgreSqlDatabase db = new PostgreSqlDatabase(); db.install();
        var counts = java.util.Map.of("tb_sy_code_grp",64L,"tb_sy_code",391L,"tb_sy_oper_std",43L,"tb_sy_role",5L,"tb_sy_menu",40L,"tb_sy_role_menu_auth",150L,"tb_sy_user",1L,"tb_sy_user_role",1L,"tb_sy_calc_rule",94L,"tb_bs_part",1L);
        counts.forEach((table, count) -> assertThat(db.count(table)).as(table).isEqualTo(count));
        assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_sy_code_grp WHERE sys_code_yn='Y'",Long.class)).isEqualTo(44);
        assertThat(db.sql.queryForObject("SELECT std_val::text FROM tb_sy_oper_std WHERE std_cd='CASH_BASE_YM'",String.class)).isEqualTo("202601.0000");
        assertThat(db.sql.queryForObject("SELECT std_val::text FROM tb_sy_oper_std WHERE std_cd='CASH_BASE_AMT'",String.class)).isEqualTo("1150000000.0000");
        var addressCounts = java.util.Map.of("tb_sy_adm_area", db.count("tb_sy_adm_area"), "tb_sy_adm_hdong", db.count("tb_sy_adm_hdong"));
        addressCounts.forEach((table, count) -> assertThat(count).as(table + " installed by V5").isPositive());
        for (String table : new String[]{"tb_sy_code_map","tb_sy_chg_hist","tb_sy_user_pref","tb_cl_close","tb_cl_close_hist","tb_cl_close_chk"}) assertThat(db.count(table)).as(table).isZero();
        assertThat(db.flyway().migrate().migrationsExecuted).isZero();
        counts.forEach((table, count) -> assertThat(db.count(table)).as(table).isEqualTo(count));
        assertThat(db.flyway().validateWithResult().validationSuccessful).isTrue();
        addressCounts.forEach((table, count) -> assertThat(db.count(table)).as(table).isEqualTo(count));
    }
}
