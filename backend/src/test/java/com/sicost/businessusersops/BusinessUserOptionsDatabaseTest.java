package com.sicost.businessusersops;

import com.sicost.common.context.CurrentUserMapper;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static com.sicost.businessusersops.BusinessUserOptionsDtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "SICOST_TEST_DB_URL", matches = ".+")
class BusinessUserOptionsDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    BusinessUserOptionsService service;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db = new PostgreSqlDatabase(); db.install(); sql = db.sql;
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean(); factory.setDataSource(db.source);
        var configuration = new org.apache.ibatis.session.Configuration();
        configuration.setArgNameBasedConstructorAutoMapping(true); configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration); factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session = new SqlSessionTemplate(factory.getObject());
        context = new AnnotationConfigApplicationContext(); context.registerBean(DataSource.class, () -> db.source);
        context.registerBean(BusinessUserOptionsMapper.class, () -> session.getMapper(BusinessUserOptionsMapper.class));
        context.registerBean(CurrentUserMapper.class, () -> session.getMapper(CurrentUserMapper.class));
        context.register(Transactions.class, CurrentUserProvider.class, BusinessUserOptionsRepository.class, BusinessUserOptionsService.class);
        context.refresh(); service = context.getBean(BusinessUserOptionsService.class);
    }
    @AfterEach void close() { if (context != null) context.close(); }
    String actor(String name, String status, String... roles) {
        String id = "opt" + UUID.randomUUID().toString().replace("-", "").substring(0, 17);
        sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,reg_user_id) VALUES (?,?,?,'unused-test-value',0,?,0,'admin')", id, name, id + "@example.test", status);
        for (String role : roles) sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,?,'admin')", id, role);
        return id;
    }
    List<Map<String, Object>> snapshot(String table) { return sql.queryForList("SELECT * FROM " + table + " ORDER BY 1,2"); }
    @Test void useAndPurposeFilterSameNameLabelsAndIdTieBreakWithoutWrites() {
        String a = actor("옵션동명", "USE", "PM", "SALES_LDR"), b = actor("옵션동명", "USE", "PM");
        String locked = actor("옵션잠김", "LOCK", "PM"), stopped = actor("옵션중지", "STOP", "PM");
        String leader = actor("옵션파트장", "USE", "SALES_LDR"), admin = actor("옵션관리자", "USE", "ADMIN");
        var users = snapshot("tb_sy_user"); var links = snapshot("tb_sy_user_role");
        var roles = snapshot("tb_sy_role"); var history = snapshot("tb_sy_chg_hist"); var logs = snapshot("tb_sy_accs_log");
        var pm = service.options("PM").items();
        assertThat(pm).extracting(UserOption::userId).contains(a, b).doesNotContain(locked, stopped, leader, admin);
        assertThat(pm.stream().filter(u -> u.name().equals("옵션동명")).toList()).extracting(UserOption::userId)
            .containsExactlyElementsOf(new TreeSet<>(List.of(a, b)));
        assertThat(pm).allSatisfy(u -> assertThat(u.label()).isEqualTo(u.name() + "(" + u.userId() + ")"));
        assertThat(service.options("SALES_LDR").items()).extracting(UserOption::userId).contains(a, leader).doesNotContain(b, locked, stopped, admin);
        assertThat(snapshot("tb_sy_user")).isEqualTo(users); assertThat(snapshot("tb_sy_user_role")).isEqualTo(links);
        assertThat(snapshot("tb_sy_role")).isEqualTo(roles); assertThat(snapshot("tb_sy_chg_hist")).isEqualTo(history); assertThat(snapshot("tb_sy_accs_log")).isEqualTo(logs);
    }
    @Test void inactiveRoleExcludesUsersAndRoleDefinitionsAndNextReadSeesReactivation() {
        String id = actor("옵션역할중지", "USE", "PM");
        sql.update("UPDATE tb_sy_role SET use_yn='N' WHERE role_id='PM'");
        var inactive = service.options("PM");
        assertThat(inactive.items()).isEmpty(); assertThat(inactive.roleOptions()).extracting(RoleOption::roleId).doesNotContain("PM");
        sql.update("UPDATE tb_sy_role SET use_yn='Y',role_nm='갱신된 PM 표시명' WHERE role_id='PM'");
        assertThat(service.options("PM").items()).extracting(UserOption::userId).contains(id);
        assertThat(service.options("PM").roleOptions()).contains(new RoleOption("PM", "갱신된 PM 표시명"));
    }
    @Test void allEligibleValuesAreReturnedWithoutPageCap() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 105; i++) ids.add(actor("옵션전체" + i, "USE", "PM"));
        assertThat(service.options("PM").items()).extracting(UserOption::userId).containsAll(ids);
    }
    @Test void nextReadSeesNewUserAndRoleUnlink() {
        String id = actor("옵션최신", "USE", "SALES_LDR");
        assertThat(service.options("SALES_LDR").items()).extracting(UserOption::userId).contains(id);
        sql.update("DELETE FROM tb_sy_user_role WHERE user_id=? AND role_id='SALES_LDR'", id);
        assertThat(service.options("SALES_LDR").items()).extracting(UserOption::userId).doesNotContain(id);
    }
}
