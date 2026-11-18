package com.sicost.roles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
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
import static com.sicost.roles.RoleDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class RoleDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    RoleService service;
    RoleMapper mapper;
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
        configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AggregateLockMapper.class);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());mapper=session.getMapper(RoleMapper.class);
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(RoleMapper.class,()->mapper);
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.registerBean(AccessLogMapper.class,()->session.getMapper(AccessLogMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(SettlementSourceLockMapper.class,()->session.getMapper(SettlementSourceLockMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,AccessLogWriter.class,
            RevisionService.class,CommitInvalidation.class,MonthCloseBoundary.class,SettlementSourceLocks.class,WriteCoordinator.class,RoleRepository.class,RoleService.class);
        context.refresh();service=context.getBean(RoleService.class);
        mvc=MockMvcBuilders.standaloneSetup(new RoleController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
    }
    @AfterEach void close() {if(context!=null)context.close();}
    ManagedRoleDetail create(String name) {return service.create(new Create("NEW",name,"PART",null,null),new MockHttpServletRequest()).value();}
    Edit edit(ManagedRoleDetail value,String scope,boolean sensitive) {return new Edit(scope,sensitive,value.menuPermissions().stream().map(Permission::input).toList(),value.revision());}
    List<Map<String,Object>> roles() {return sql.queryForList("SELECT * FROM tb_sy_role ORDER BY role_id");}
    List<Map<String,Object>> auth() {return sql.queryForList("SELECT * FROM tb_sy_role_menu_auth ORDER BY role_id,menu_id");}
    List<Map<String,Object>> history() {return sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id");}
    List<Map<String,Object>> access() {return sql.queryForList("SELECT * FROM tb_sy_accs_log ORDER BY log_id");}
    @Test void creationRequeriesEveryFalseSubmenuAndPreservesSeedRoles() throws Exception {
        var before=roles();var permissions=auth();
        var value=create("DB 역할");
        assertThat(value.systemRole()).isFalse();assertThat(value.use()).isTrue();assertThat(value.dataScope()).isEqualTo("PART");assertThat(value.sensitiveView()).isFalse();
        assertThat(value.menuPermissions()).hasSize(sql.queryForObject("SELECT count(*) FROM tb_sy_menu WHERE menu_lvl=2",Integer.class));
        assertThat(value.menuPermissions()).allSatisfy(p->assertThat(p.input()).isEqualTo(new PermissionInput(p.menuId(),false,false,false,false,false,false,false)));
        assertThat(sql.queryForList("SELECT * FROM tb_sy_role WHERE role_id<>? ORDER BY role_id",value.roleId())).isEqualTo(before);
        assertThat(sql.queryForList("SELECT * FROM tb_sy_role_menu_auth WHERE role_id<>? ORDER BY role_id,menu_id",value.roleId())).isEqualTo(permissions);
        assertThat(access()).hasSize(1);assertThat(access().get(0)).containsEntry("act_type_cd","AUTH_CHG").containsEntry("scrn_id","UI-24");
        mvc.perform(get("/api/roles/"+value.roleId())).andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("DB 역할"));
    }
    @Test void readsArePureAndMissingPermissionsReadFalseWithoutBackfill() throws Exception {
        var value=create("조회 역할");
        sql.update("DELETE FROM tb_sy_role_menu_auth WHERE role_id=?",value.roleId());
        var before=roles();var permissions=auth();var histories=history();var accesses=access();
        mvc.perform(get("/api/roles")).andExpect(status().isOk());
        var read=service.get(value.roleId());assertThat(read.menuPermissions()).allSatisfy(p->assertThat(p.update()).isFalse());
        assertThat(roles()).isEqualTo(before);assertThat(auth()).isEqualTo(permissions);assertThat(history()).isEqualTo(histories);assertThat(access()).isEqualTo(accesses);
    }
    @Test void independentPermissionSaveAndNoOpPreserveAuditTimestamps() {
        var value=create("설정 역할");var input=edit(value,"PROJ",true);List<PermissionInput> changed=new ArrayList<>(input.menuPermissions());var p=changed.get(0);
        changed.set(0,new PermissionInput(p.menuId(),false,true,true,false,true,true,true));
        var result=service.update(value.roleId(),new Edit("PROJ",true,changed,value.revision()),new MockHttpServletRequest());
        var after=service.get(value.roleId());assertThat(after.dataScope()).isEqualTo("PROJ");assertThat(after.sensitiveView()).isTrue();
        assertThat(after.menuPermissions().get(0).input()).isEqualTo(changed.get(0));assertThat(result.changed()).isTrue();
        var roleRows=roles();var authRows=auth();var histories=history();var accesses=access();
        assertThat(service.update(value.roleId(),edit(after,"PROJ",true),new MockHttpServletRequest()).changed()).isFalse();
        assertThat(roles()).isEqualTo(roleRows);assertThat(auth()).isEqualTo(authRows);assertThat(history()).isEqualTo(histories);assertThat(access()).isEqualTo(accesses);
        assertThat(accesses).hasSize(2);
        var audit=sql.queryForMap("SELECT * FROM tb_sy_chg_hist WHERE trgt_key=? AND chg_type_cd='UPD'",value.roleId()+":"+p.menuId());
        assertThat(audit.get("aft_val")).isEqualTo(RoleService.permissionJson(changed.get(0)));
    }
    @Test void revisionIncludesChildAuditNanosecondsAndIgnoresDisplayName() {
        var value=create("리비전 역할");var first=value.menuPermissions().get(0);
        sql.update("UPDATE tb_sy_menu SET menu_nm='표시명 변경' WHERE menu_id=?",first.menuId());
        assertThat(service.get(value.roleId()).revision()).isEqualTo(value.revision());
        sql.update("UPDATE tb_sy_role_menu_auth SET mod_dttm=TIMESTAMP '2026-01-01 00:00:00.123456' WHERE role_id=? AND menu_id=?",value.roleId(),first.menuId());
        assertThatThrownBy(()->service.update(value.roleId(),edit(value,"PROJ",false),new MockHttpServletRequest())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));
    }
    @Test void copiedRolePreservesSavedSettingsButNotDefaultIdentityAndSourceChangesStayIndependent() {
        var original=create("복사 원본");original=service.update(original.roleId(),edit(original,"ALL",true),new MockHttpServletRequest()).value();
        var copied=service.create(new Create("COPY","복사본",null,original.roleId(),original.revision()),new MockHttpServletRequest()).value();
        assertThat(copied.dataScope()).isEqualTo("ALL");assertThat(copied.sensitiveView()).isTrue();assertThat(copied.systemRole()).isFalse();
        service.update(original.roleId(),edit(original,"PART",false),new MockHttpServletRequest());assertThat(service.get(copied.roleId()).dataScope()).isEqualTo("ALL");
        var pm=service.get("PM");var pmCopy=service.create(new Create("COPY","PM 복사",null,"PM",pm.revision()),new MockHttpServletRequest()).value();
        assertThat(pmCopy.roleId()).isNotEqualTo("PM");assertThat(pmCopy.systemRole()).isFalse();
        sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,reg_user_id) SELECT 'role-copy-user','복사 사용자','copy@example.test',pwd_hash,0,'USE',0,'admin' FROM tb_sy_user WHERE user_id='admin'");
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES ('role-copy-user',?,'admin')",pmCopy.roleId());
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_user_role ur JOIN tb_sy_role r ON r.role_id=ur.role_id WHERE ur.user_id='role-copy-user' AND r.role_id='PM' AND r.use_yn='Y'",Integer.class)).isZero();
    }
    @Test void renamedAdminScreenStillProtectedAndFailureLeavesEverythingUnchanged() {
        String menu=sql.queryForObject("SELECT menu_id FROM tb_sy_menu WHERE scrn_id='UI-24'",String.class);
        sql.update("UPDATE tb_sy_menu SET menu_nm='다른 이름' WHERE menu_id=?",menu);
        var admin=service.get("ADMIN");var before=roles();var permissions=auth();var histories=history();
        var changed=admin.menuPermissions().stream().map(p->p.menuId().equals(menu)?new PermissionInput(menu,false,p.register(),true,p.delete(),p.upload(),p.download(),p.confirm()):p.input()).toList();
        assertThatThrownBy(()->service.update("ADMIN",new Edit("PART",false,changed,admin.revision()),new MockHttpServletRequest())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("ADMIN_PERMISSION_PROTECTED"));
        assertThat(roles()).isEqualTo(before);assertThat(auth()).isEqualTo(permissions);assertThat(history()).isEqualTo(histories);assertThat(access()).isEmpty();
    }
    @Test void newMenuIdentityInvalidatesOldWholeSetBeforeWrite() {
        var value=create("트리 검증");
        String parent=sql.queryForObject("SELECT menu_id FROM tb_sy_menu WHERE menu_lvl=1 ORDER BY menu_id LIMIT 1",String.class);
        sql.update("INSERT INTO tb_sy_menu(menu_id,upper_menu_id,menu_nm,scrn_id,menu_lvl,sort_ord,reg_user_id) VALUES ('ROLE-MENU',?,'역할용 화면','UI-24',2,99999,'admin')",parent);
        var before=roles();assertThatThrownBy(()->service.update(value.roleId(),edit(value,"PART",false),new MockHttpServletRequest())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));assertThat(roles()).isEqualTo(before);
    }
    @Test void failedMenuHistoryRollsBackWholeCreation() {
        var before=roles();var permissions=auth();var histories=history();
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_role_permission_history CHECK(trgt_tbl_nm <> 'TB_SY_ROLE_MENU_AUTH')");
        assertThatThrownBy(()->create("실패 역할")).isInstanceOf(RuntimeException.class);
        assertThat(roles()).isEqualTo(before);assertThat(auth()).isEqualTo(permissions);assertThat(history()).isEqualTo(histories);assertThat(access()).isEmpty();
    }
    @Test void failedAccessRollsBackCreationAndAllChildHistory() {
        var before=roles();var permissions=auth();var histories=history();
        sql.execute("ALTER TABLE tb_sy_accs_log ADD CONSTRAINT reject_role_access CHECK(act_type_cd <> 'AUTH_CHG')");
        assertThatThrownBy(()->create("실패 역할")).isInstanceOf(RuntimeException.class);
        assertThat(roles()).isEqualTo(before);assertThat(auth()).isEqualTo(permissions);assertThat(history()).isEqualTo(histories);assertThat(access()).isEmpty();
    }
    @Test void failedUpdateHistoryRollsBackSettingsAndPermissions() {
        var value=create("실패 수정");var before=roles();var permissions=auth();var histories=history();var accesses=access();
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_role_update CHECK(chg_type_cd <> 'UPD')");
        assertThatThrownBy(()->service.update(value.roleId(),edit(value,"ALL",true),new MockHttpServletRequest())).isInstanceOf(RuntimeException.class);
        assertThat(roles()).isEqualTo(before);assertThat(auth()).isEqualTo(permissions);assertThat(history()).isEqualTo(histories);assertThat(access()).isEqualTo(accesses);
    }
    @Test void failedUpdateAccessRollsBackAllSettingsAndHistory() {
        var value=create("실패 접근");var before=roles();var permissions=auth();var histories=history();var accesses=access();
        sql.execute("ALTER TABLE tb_sy_accs_log ADD CONSTRAINT reject_role_access CHECK(act_type_cd <> 'AUTH_CHG') NOT VALID");
        assertThatThrownBy(()->service.update(value.roleId(),edit(value,"ALL",true),new MockHttpServletRequest())).isInstanceOf(RuntimeException.class);
        assertThat(roles()).isEqualTo(before);assertThat(auth()).isEqualTo(permissions);assertThat(history()).isEqualTo(histories);assertThat(access()).isEqualTo(accesses);
    }
    @Test void concurrentCreationIssuesDistinctIdsAndCompletePermissions() throws Exception {
        var executor=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try {
            Callable<ManagedRoleDetail> first=()->{ready.countDown();start.await();return create("동시 역할 A");};
            Callable<ManagedRoleDetail> second=()->{ready.countDown();start.await();return create("동시 역할 B");};
            var a=executor.submit(new org.springframework.security.concurrent.DelegatingSecurityContextCallable<>(first));var b=executor.submit(new org.springframework.security.concurrent.DelegatingSecurityContextCallable<>(second));assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            var av=a.get(20,TimeUnit.SECONDS);var bv=b.get(20,TimeUnit.SECONDS);assertThat(av.roleId()).isNotEqualTo(bv.roleId());
            assertThat(av.sortOrder()).isNotEqualTo(bv.sortOrder());assertThat(av.menuPermissions()).hasSameSizeAs(bv.menuPermissions());
            assertThat(access()).hasSize(2);
        } finally {executor.shutdownNow();}
    }
}
