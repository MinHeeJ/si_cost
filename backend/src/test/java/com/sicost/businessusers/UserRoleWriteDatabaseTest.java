package com.sicost.businessusers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.pm.*;
import com.sicost.budget.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static com.sicost.businessusers.BusinessUserDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class UserRoleWriteDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    BusinessUserService service;
    ObjectMapper json;
    MockMvc mvc;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();
        configuration.addMapper(AggregateLockMapper.class);
        configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(BusinessUserMapper.class,()->session.getMapper(BusinessUserMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.registerBean(AccessLogMapper.class,()->session.getMapper(AccessLogMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(SettlementSourceLockMapper.class,()->session.getMapper(SettlementSourceLockMapper.class));
        context.registerBean(ProjectPmMapper.class,()->session.getMapper(ProjectPmMapper.class));
        context.registerBean(ProjectBudgetMapper.class,()->session.getMapper(ProjectBudgetMapper.class));
        context.register(SettlementSourceLocks.class,MonthCloseBoundary.class,CommitInvalidation.class,ProjectPmRepository.class,ProjectBudgetRepository.class);
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,AccessLogWriter.class,
            RevisionService.class,BusinessUserRepository.class,BusinessUserService.class);
        context.register(com.sicost.common.LegacyBusinessPolicyFixtures.class);context.refresh();service=context.getBean(BusinessUserService.class);
        mvc=MockMvcBuilders.standaloneSetup(new BusinessUserController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
    }
    @AfterEach void close() {if(context!=null)context.close();}

    Create full(String id,List<String> roles,boolean office,List<ProjectAssignment> assignments) {
        return new Create(id,"검증 "+id,id+"@example.test",null,roles,office,assignments);
    }
    void editUser(User user,List<String> roles,Boolean office) {
        service.update(user.userId(),new Edit(user.name(),user.email(),null,user.revision(),roles,office));
    }
    long n(String table) {return sql.queryForObject("SELECT count(*) FROM "+table,Long.class);}
    void project(String id,String end) {
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2024-01-01',CAST(? AS date),100,0,0,'admin')",id,id,end);
    }
    String projectRevision(String id) {return context.getBean(RevisionService.class).revision(context.getBean(ProjectPmRepository.class).snapshot(id));}
    @Test void wholeRolesAndHeadOfficeAreStoredAndPartialEditPreservesFinance() throws Exception {
        mvc.perform(post("/api/business-users").contentType("application/json").content("{\"userId\":\"full01\",\"name\":\"전체 사용자\",\"email\":\"full@example.test\",\"roleIds\":[\"PM\",\"FIN\"],\"headOffice\":true}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.user.headOffice").value(true));
        User user=service.get("full01");assertThat(user.roleIds()).containsExactly("FIN","PM");
        assertThat(sql.queryForMap("SELECT scope_type_cd,part_id,proj_id FROM tb_sy_user_scope WHERE user_id='full01'"))
            .containsEntry("scope_type_cd","PART").containsEntry("part_id","HQ").containsEntry("proj_id",null);
        var auth=sql.queryForMap("SELECT pwd_hash,temp_pwd_yn,pwd_chg_dttm,mfa_use_yn FROM tb_sy_user WHERE user_id='full01'");
        service.update(user.userId(),new Edit(user.name(),user.email(),List.of("SALES_LDR"),user.revision()));
        user=service.get(user.userId());assertThat(user.roleIds()).containsExactly("FIN","SALES_LDR");
        editUser(user,List.of("EXEC","PM"),false);user=service.get(user.userId());assertThat(user.roleIds()).containsExactly("EXEC","PM");assertThat(user.headOffice()).isFalse();
        assertThat(sql.queryForMap("SELECT pwd_hash,temp_pwd_yn,pwd_chg_dttm,mfa_use_yn FROM tb_sy_user WHERE user_id='full01'")).isEqualTo(auth);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE act_type_cd='AUTH_CHG'",Long.class)).isEqualTo(2);
        long history=n("tb_sy_chg_hist"),access=n("tb_sy_accs_log");
        assertThat(service.update(user.userId(),new Edit(user.name(),user.email(),null,user.revision(),user.roleIds(),false)).changed()).isFalse();
        assertThat(n("tb_sy_chg_hist")).isEqualTo(history);assertThat(n("tb_sy_accs_log")).isEqualTo(access);
        mvc.perform(get("/api/business-users").param("role","PM")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }
    @Test void stopFiltersStatusOrderingAndSearchAreExact() throws Exception {
        for(String id:List.of("user01","user02","user03"))service.create(full(id,List.of("PM","FIN"),false,List.of()),new MockHttpServletRequest());
        sql.update("UPDATE tb_sy_user SET user_stat_cd='LOCK' WHERE user_id='user02'");
        sql.update("UPDATE tb_sy_user SET user_stat_cd='STOP' WHERE user_id='user03'");
        assertThat(service.list(BusinessUserInput.filter(Map.of())).total()).isEqualTo(3);
        assertThat(service.list(BusinessUserInput.filter(Map.of("role","PM","includeStopped","true","sort","status"))).items().stream().map(User::status)).containsExactly("USE","LOCK","STOP");
        assertThat(service.list(BusinessUserInput.filter(Map.of("status","STOP"))).total()).isEqualTo(1);
        assertThat(service.list(BusinessUserInput.filter(Map.of("status","USE","includeStopped","true"))).total()).isEqualTo(2);
        for(String q:List.of("%","_"))assertThat(service.list(BusinessUserInput.filter(Map.of("q",q))).total()).isZero();
        mvc.perform(get("/api/business-users").param("role","PM").param("businessRole","PM")).andExpect(status().isBadRequest());
    }
    @Test void lastAdministratorAndStaleRevisionCannotWrite() {
        User admin=service.get("admin");long history=n("tb_sy_chg_hist");
        assertThatThrownBy(()->editUser(admin,List.of("FIN"),null)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("LAST_ACTIVE_ADMIN"));
        assertThat(n("tb_sy_chg_hist")).isEqualTo(history);
        var user=service.create(full("fresh01",List.of("FIN"),false,List.of()),new MockHttpServletRequest()).user();
        editUser(user,List.of("EXEC"),null);
        assertThatThrownBy(()->editUser(user,List.of("PM"),true)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));
        assertThat(service.get(user.userId()).roleIds()).containsExactly("EXEC");assertThat(service.get(user.userId()).headOffice()).isFalse();
    }
    @Test void multiProjectRegistrationValidatesAllBeforeMutationAndRollsBackAccessFailure() {
        project("QA-P1","2026-06-15");project("QA-P2","2026-06-14");
        var assignments=List.of(new ProjectAssignment("QA-P1",projectRevision("QA-P1"),false),new ProjectAssignment("QA-P2",projectRevision("QA-P2"),false));
        assertThatThrownBy(()->service.create(full("atomic01",List.of("PM","FIN"),true,assignments),new MockHttpServletRequest())).isInstanceOf(ApiException.class);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_user WHERE user_id='atomic01'",Long.class)).isZero();assertThat(n("tb_sy_user_scope")).isZero();
        sql.update("UPDATE tb_pj_proj SET ctrt_end_dt='2026-07-01' WHERE proj_id='QA-P2'");
        var valid=List.of(new ProjectAssignment("QA-P1",projectRevision("QA-P1"),false),new ProjectAssignment("QA-P2",projectRevision("QA-P2"),false));
        sql.execute("ALTER TABLE tb_sy_accs_log ADD CONSTRAINT fail_log CHECK(act_type_cd <> 'USER_REG')");
        try {
            assertThatThrownBy(()->service.create(full("atomic01",List.of("PM","FIN"),true,valid),new MockHttpServletRequest())).isInstanceOf(RuntimeException.class);
            assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_user WHERE user_id='atomic01'",Long.class)).isZero();
            assertThat(n("tb_sy_user_scope")).isZero();assertThat(n("tb_pj_proj_chg")).isZero();assertThat(n("tb_sy_chg_hist")).isZero();
        } finally {sql.execute("ALTER TABLE tb_sy_accs_log DROP CONSTRAINT fail_log");}
        var user=service.create(full("atomic01",List.of("PM","FIN"),true,valid),new MockHttpServletRequest()).user();
        assertThat(n("tb_sy_user_scope")).isEqualTo(3);assertThat(n("tb_pj_proj_chg")).isEqualTo(2);assertThat(n("tb_sy_accs_log")).isEqualTo(1);
        assertThat(sql.queryForList("SELECT scope_sn FROM tb_sy_user_scope WHERE user_id='atomic01' ORDER BY scope_sn",Integer.class)).containsExactly(1,2,3);
        assertThatThrownBy(()->editUser(user,List.of("FIN"),null)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("USER_IS_PROJECT_PM"));
    }
    @Test void administratorHandoffPinsActorForCurrentHttpRequest() throws Exception {
        service.create(full("admin02",List.of("ADMIN"),false,List.of()),new MockHttpServletRequest());
        User admin=service.get("admin");
        mvc.perform(put("/api/business-users/admin").contentType("application/json").content(json.writeValueAsString(
            Map.of("name",admin.name(),"email",admin.email(),"roleIds",List.of("FIN"),"expectedRevision",admin.revision()))))
            .andExpect(status().isOk());
        assertThat(sql.queryForObject("SELECT reg_user_id FROM tb_sy_chg_hist WHERE trgt_key='admin:ADMIN' AND chg_type_cd='DEL'",String.class)).isEqualTo("admin");
        assertThat(sql.queryForObject("SELECT user_id FROM tb_sy_accs_log WHERE act_type_cd='AUTH_CHG' ORDER BY log_id DESC LIMIT 1",String.class)).isEqualTo("admin");
        assertThat(context.getBean(CurrentUserProvider.class).userId()).isEqualTo("admin02");
        assertThat(service.get("admin").roleIds()).containsExactly("FIN");
    }
    @Test void failedAuthLogRollsBackWholeRoleAndOfficeUpdate() {
        var user=service.create(full("rollback01",List.of("FIN"),false,List.of()),new MockHttpServletRequest()).user();
        var before=sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id='rollback01'");long history=n("tb_sy_chg_hist");
        sql.execute("ALTER TABLE tb_sy_accs_log ADD CONSTRAINT fail_auth CHECK(act_type_cd <> 'AUTH_CHG')");
        try {
            assertThatThrownBy(()->editUser(user,List.of("EXEC","PM"),true)).isInstanceOf(RuntimeException.class);
            assertThat(service.get(user.userId()).roleIds()).containsExactly("FIN");assertThat(service.get(user.userId()).headOffice()).isFalse();
            assertThat(sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id='rollback01'")).isEqualTo(before);assertThat(n("tb_sy_chg_hist")).isEqualTo(history);
        } finally {sql.execute("ALTER TABLE tb_sy_accs_log DROP CONSTRAINT fail_auth");}
    }
    @Test void inactiveRoleRejectsNewConnectionAndOfficeSerialLimitRollsBack() {
        sql.update("INSERT INTO tb_sy_role(role_id,role_nm,data_scope_cd,sort_ord,use_yn,reg_user_id) VALUES ('R-099','중지 역할','PROJ',99,'N','admin')");
        assertThatThrownBy(()->service.create(full("stopped01",List.of("R-099"),false,List.of()),new MockHttpServletRequest()))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(409));
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_user WHERE user_id='stopped01'",Long.class)).isZero();
        var user=service.create(full("serial01",List.of("PM"),false,List.of()),new MockHttpServletRequest()).user();
        project("QA-P1","2026-07-01");
        sql.update("INSERT INTO tb_sy_user_scope(user_id,scope_sn,scope_type_cd,proj_id,reg_user_id) VALUES ('serial01',99999,'PROJ','QA-P1','admin')");
        var current=service.get(user.userId());long history=n("tb_sy_chg_hist");
        assertThatThrownBy(()->editUser(current,List.of("PM","FIN"),true)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("SERIAL_LIMIT"));
        assertThat(service.get(user.userId()).roleIds()).containsExactly("PM");assertThat(service.get(user.userId()).headOffice()).isFalse();
        assertThat(n("tb_sy_chg_hist")).isEqualTo(history);assertThat(n("tb_sy_user_scope")).isEqualTo(1);
    }
    @Test void headOfficeRemovalPreservesExistingProjectAndProjectTransferPreservesOldRoles() {
        project("QA-P1","2026-07-01");
        var old=service.create(full("oldpm01",List.of("PM","FIN"),true,List.of(new ProjectAssignment("QA-P1",projectRevision("QA-P1"),false))),new MockHttpServletRequest()).user();
        editUser(old,old.roleIds(),false);assertThat(n("tb_sy_user_scope")).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT proj_id FROM tb_sy_user_scope WHERE user_id='oldpm01'",String.class)).isEqualTo("QA-P1");
        var next=service.create(full("newpm01",List.of("PM"),false,List.of(new ProjectAssignment("QA-P1",projectRevision("QA-P1"),true))),new MockHttpServletRequest()).user();
        assertThat(sql.queryForObject("SELECT user_id FROM tb_sy_user_scope WHERE proj_id='QA-P1'",String.class)).isEqualTo(next.userId());
        assertThat(service.get(old.userId()).roleIds()).containsExactly("FIN","PM");
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_key LIKE 'oldpm01:%' AND chg_type_cd='DEL' AND trgt_tbl_nm='TB_SY_USER_SCOPE'",Long.class)).isEqualTo(2);
    }
    @Test void simultaneousAdministratorRemovalKeepsOneActiveAdministrator() throws Exception {
        var second=service.create(full("admin02",List.of("ADMIN","FIN"),false,List.of()),new MockHttpServletRequest()).user();
        var first=service.get("admin");var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(var user:List.of(first,second))futures.add(pool.submit(()->{gate.await();try {editUser(user,List.of("FIN"),null);return true;}catch(ApiException e){assertThat(e.code()).isEqualTo("LAST_ACTIVE_ADMIN");return false;}}));
            gate.countDown();int changed=0;for(var future:futures)if(future.get(20,TimeUnit.SECONDS))changed++;
            assertThat(changed).isEqualTo(1);assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_user_role WHERE role_id='ADMIN'",Long.class)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
}
