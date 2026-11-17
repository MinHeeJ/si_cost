package com.sicost.common.persistence;

import com.sicost.common.context.CurrentUserMapper;
import com.sicost.common.installation.PostgreSqlDatabase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class UserAdministrationSchemaDatabaseTest {
    PostgreSqlDatabase database;
    JdbcTemplate sql;
    CurrentUserMapper users;
    @BeforeEach void install() throws Exception {
        database=new PostgreSqlDatabase();database.install();sql=database.sql;
        var factory=new SqlSessionFactoryBean();factory.setDataSource(database.source);
        var configuration=new org.apache.ibatis.session.Configuration();
        configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        users=new SqlSessionTemplate(factory.getObject()).getMapper(CurrentUserMapper.class);
    }
    void user(String id,String name,String role) {
        sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,reg_user_id) SELECT ?,?,?||'@example.test',pwd_hash,0,'USE',0,'admin' FROM tb_sy_user WHERE user_id='admin'",id,name,id);
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,?,'admin')",id,role);
    }
    @Test void developmentActorPrefersEligibleAdminThenOrderedFallbackWithActualNameAndActiveRoles() {
        user("aaaadmin","다음 관리자","ADMIN");user("zzzadmin","마지막 관리자","ADMIN");
        assertThat(users.findAdministrator()).isEqualTo("admin");
        sql.update("UPDATE tb_sy_user SET user_stat_cd='STOP' WHERE user_id='admin'");
        assertThat(users.findAdministrator()).isEqualTo("aaaadmin");
        assertThat(users.findName("aaaadmin")).isEqualTo("다음 관리자");
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES ('aaaadmin','PM','admin')");
        assertThat(users.findActiveRoles("aaaadmin")).containsExactly("ADMIN","PM");
        sql.update("UPDATE tb_sy_role SET use_yn='N' WHERE role_id='PM'");
        assertThat(users.findActiveRoles("aaaadmin")).containsExactly("ADMIN");
        sql.update("UPDATE tb_sy_role SET use_yn='N' WHERE role_id='ADMIN'");
        assertThat(users.findAdministrator()).isNull();
    }
    @Test void authenticationColumnsRetainRequiredHashLengthsNullableMfaAndNumericCounters() {
        var columns=sql.queryForList("SELECT column_name,data_type,character_maximum_length,numeric_precision,is_nullable FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_sy_user'");
        Map<String,Object> hash=columns.stream().filter(c->c.get("column_name").equals("pwd_hash")).findFirst().orElseThrow();
        assertThat(hash.get("character_maximum_length")).isEqualTo(200);assertThat(hash.get("is_nullable")).isEqualTo("NO");
        for(String field:List.of("mfa_code_hash","mfa_expr_dttm","pwd_chg_dttm","last_login_dttm"))
            assertThat(columns.stream().filter(c->c.get("column_name").equals(field)).findFirst().orElseThrow().get("is_nullable")).isEqualTo("YES");
        for(String field:List.of("pwd_err_cnt","mfa_err_cnt"))
            assertThat(columns.stream().filter(c->c.get("column_name").equals(field)).findFirst().orElseThrow().get("numeric_precision")).isEqualTo(2);
    }
    @Test void roleAssignmentsKeepCompositeUniqueness() {
        user("roleuser","역할 시험","PM");
        assertThatThrownBy(()->sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES ('roleuser','PM','admin')")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_user_role WHERE user_id='roleuser' AND role_id='PM'",Long.class)).isEqualTo(1);
    }
    @Test void stoppedRolesRemainInNameUniquenessAndPermissionDefaultsDenyAllSevenActions() {
        sql.update("INSERT INTO tb_sy_role(role_id,role_nm,data_scope_cd,snstv_view_yn,sys_role_yn,sort_ord,use_yn,reg_user_id) VALUES ('R-TEST','독립 역할','PROJ','N','N',100,'N','admin')");
        assertThatThrownBy(()->sql.update("INSERT INTO tb_sy_role(role_id,role_nm,data_scope_cd,sort_ord,reg_user_id) VALUES ('R-OTHER','독립 역할','PROJ',101,'admin')")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        String menu=sql.queryForObject("SELECT menu_id FROM tb_sy_menu WHERE menu_lvl=2 ORDER BY menu_id LIMIT 1",String.class);
        sql.update("INSERT INTO tb_sy_role_menu_auth(role_id,menu_id,reg_user_id) VALUES ('R-TEST',?,'admin')",menu);
        var flags=sql.queryForMap("SELECT inq_yn,reg_yn,upd_yn,del_yn,upld_yn,dnld_yn,cnfm_yn FROM tb_sy_role_menu_auth WHERE role_id='R-TEST' AND menu_id=?",menu);
        assertThat(flags.values()).hasSize(7).containsOnly("N");
    }
    @Test void incrementalRestartPreservesSavedMenusPermissionsPreferencesAndAuthentication() {
        String menu=sql.queryForObject("SELECT menu_id FROM tb_sy_menu WHERE menu_lvl=2 AND lock_yn='N' ORDER BY menu_id LIMIT 1",String.class);
        sql.update("UPDATE tb_sy_menu SET use_yn='N',pc_only_yn='N' WHERE menu_id=?",menu);
        sql.update("UPDATE tb_sy_role_menu_auth SET dnld_yn='N' WHERE menu_id=?",menu);
        sql.update("INSERT INTO tb_sy_user_pref(user_id,pref_type_cd,pref_key,pref_val,reg_user_id) VALUES ('admin','FAV','preserve','[]','admin')");
        var menus=sql.queryForList("SELECT * FROM tb_sy_menu ORDER BY menu_id");
        var permissions=sql.queryForList("SELECT * FROM tb_sy_role_menu_auth ORDER BY role_id,menu_id");
        var preferences=sql.queryForList("SELECT * FROM tb_sy_user_pref ORDER BY user_id,pref_type_cd,pref_key");
        var auth=sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id='admin'");
        database.install();
        assertThat(sql.queryForList("SELECT * FROM tb_sy_menu ORDER BY menu_id")).isEqualTo(menus);
        assertThat(sql.queryForList("SELECT * FROM tb_sy_role_menu_auth ORDER BY role_id,menu_id")).isEqualTo(permissions);
        assertThat(sql.queryForList("SELECT * FROM tb_sy_user_pref ORDER BY user_id,pref_type_cd,pref_key")).isEqualTo(preferences);
        assertThat(sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id='admin'").equals(auth)).isTrue();
    }
    @Test void sharedAuditTablesRetainSequenceWidthsAndNoModificationColumns() {
        for(String table:List.of("tb_sy_chg_hist","tb_sy_accs_log")) {
            var columns=sql.queryForList("SELECT column_name,column_default,numeric_precision FROM information_schema.columns WHERE table_schema=current_schema() AND table_name=?",table);
            assertThat(columns).noneMatch(c->c.get("column_name").toString().startsWith("mod_"));
            String key=table.equals("tb_sy_chg_hist")?"hist_id":"log_id";
            var id=columns.stream().filter(c->c.get("column_name").equals(key)).findFirst().orElseThrow();
            assertThat(id.get("numeric_precision")).isEqualTo(table.equals("tb_sy_chg_hist")?12:15);
            assertThat(id.get("column_default").toString()).contains("nextval",table.equals("tb_sy_chg_hist")?"sq_tb_sy_chg_hist":"sq_tb_sy_accs_log");
        }
        assertThat(sql.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_pj_proj' AND column_name='pm_id'",Long.class)).isZero();
    }
}
