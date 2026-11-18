package com.sicost.menumanagementops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.menumanagement.*;
import com.sicost.shared.*;
import java.time.*;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class MenuRegistrationDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql; MenuRegistrationService service; MenuManagementService trees; ObjectMapper json; MockMvc mvc;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());context=new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class,()->db.source);json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(MenuManagementMapper.class,()->session.getMapper(MenuManagementMapper.class));
        context.registerBean(MenuRegistrationMapper.class,()->session.getMapper(MenuRegistrationMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(SharedQueriesMapper.class,()->session.getMapper(SharedQueriesMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,WriteCoordinator.class,CommitInvalidation.class,
            MenuManagementRepository.class,MenuManagementService.class,MenuRegistrationRepository.class,MenuRegistrationService.class,MenuService.class);
        context.register(com.sicost.common.LegacyBusinessPolicyFixtures.class);context.refresh();service=context.getBean(MenuRegistrationService.class);trees=context.getBean(MenuManagementService.class);
        mvc=MockMvcBuilders.standaloneSetup(new MenuRegistrationController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
    }
    @AfterEach void close() {if(context!=null)context.close();}
    String body(int level,String name,String parent,String screen) {
        var b=json.createObjectNode().put("level",level).put("name",name).put("expectedRevision",trees.getMenuManagement().revision());
        if(level==2)b.put("parentId",parent).put("screenId",screen).put("pcOnly",true);return b.toString();
    }
    org.springframework.test.web.servlet.ResultActions create(String body) throws Exception {return mvc.perform(post("/api/menu-management/menus").contentType("application/json").content(body));}
    String root() throws Exception {
        String name="시험 그룹 "+UUID.randomUUID();create(body(1,name,null,null)).andExpect(status().isCreated());
        return sql.queryForObject("SELECT menu_id FROM tb_sy_menu WHERE menu_nm=?",String.class,name);
    }
    // Every test owns a new PostgreSqlDatabase UUID schema; no shared deployment seed is changed.
    void makeScreenUnlinked() {
        sql.update("DELETE FROM tb_sy_role_menu_auth WHERE menu_id IN (SELECT menu_id FROM tb_sy_menu WHERE scrn_id='UI-03')");
        sql.update("DELETE FROM tb_sy_menu WHERE scrn_id='UI-03'");
    }
    Map<String,List<Map<String,Object>>> snapshot() {
        Map<String,List<Map<String,Object>>> s=new LinkedHashMap<>();
        for(String table:List.of("tb_sy_menu","tb_sy_role_menu_auth","tb_sy_chg_hist","tb_sy_accs_log","tb_sy_user_pref"))
            s.put(table,sql.queryForList("SELECT * FROM "+table+" ORDER BY 1,2"));return s;
    }
    @Test void readUsesRealRegistryAndAllLinkedScreensYieldsNoChoicesAndNoWrites() throws Exception {
        var before=snapshot();mvc.perform(get("/api/menu-management/screens")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void emptyRootIsManagedButNotNavigableAndHasNoRolePermissionOrAccessEvent() throws Exception {
        var access=sql.queryForList("SELECT * FROM tb_sy_accs_log");String id=root();
        var row=sql.queryForMap("SELECT * FROM tb_sy_menu WHERE menu_id=?",id);
        assertThat(row).containsEntry("icon_nm","folder").containsEntry("pc_only_yn","N").containsEntry("lock_yn","N").containsEntry("use_yn","Y");
        assertThat(row.get("scrn_id")).isNull();assertThat(row.get("upper_menu_id")).isNull();
        assertThat(context.getBean(MenuService.class).getMenus().items()).noneMatch(menu->menu.menuId().equals(id));
        assertThat(trees.getMenuManagement().items()).anyMatch(menu->menu.menuId().equals(id));
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_role_menu_auth WHERE menu_id=?",Long.class,id)).isZero();
        assertThat(sql.queryForList("SELECT * FROM tb_sy_accs_log")).isEqualTo(access);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_SY_MENU' AND trgt_key=? AND reg_user_id='admin'",Long.class,id)).isPositive();
    }
    @Test void childAndEveryRoleIncludingStoppedCommitWithOrderedFalseAuditAndSurviveReload() throws Exception {
        makeScreenUnlinked();String parent=root();String role="R"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        sql.update("INSERT INTO tb_sy_role(role_id,role_nm,data_scope_cd,sort_ord,use_yn,reg_user_id) VALUES (?,?,'PROJ',99999,'N','admin')",role,"시험 "+role);
        var before=sql.queryForList("SELECT * FROM tb_sy_accs_log");String name="시험 서브";
        assertThat(service.getMenuManagementScreens().items()).anyMatch(s->s.screenId().equals("UI-03"));
        create(body(2,name,parent,"UI-03")).andExpect(status().isCreated());
        String id=sql.queryForObject("SELECT menu_id FROM tb_sy_menu WHERE menu_nm=?",String.class,name);
        long roleCount=sql.queryForObject("SELECT count(*) FROM tb_sy_role",Long.class);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_role_menu_auth WHERE menu_id=? AND inq_yn='N' AND reg_yn='N' AND upd_yn='N' AND del_yn='N' AND upld_yn='N' AND dnld_yn='N' AND cnfm_yn='N'",Long.class,id)).isEqualTo(roleCount);
        assertThat(sql.queryForObject("SELECT pc_only_yn FROM tb_sy_menu WHERE menu_id=?",String.class,id)).isEqualTo("Y");
        assertThat(sql.queryForObject("SELECT icon_nm FROM tb_sy_menu WHERE menu_id=?",String.class,id)).isNull();
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_SY_ROLE_MENU_AUTH' AND trgt_key=?",Long.class,role+":"+id)).isEqualTo(1);
        assertThat(trees.getMenuManagement().items()).anyMatch(m->m.menuId().equals(id)&&m.screenId().equals("UI-03"));
        assertThat(service.getMenuManagementScreens().items()).noneMatch(s->s.screenId().equals("UI-03"));
        assertThat(sql.queryForList("SELECT * FROM tb_sy_accs_log")).isEqualTo(before);
    }
    @Test void permissionFailureRollsBackMenuEveryPermissionAndEarlierHistory() throws Exception {
        makeScreenUnlinked();String parent=root();var before=snapshot();
        sql.execute("ALTER TABLE tb_sy_role_menu_auth ADD CONSTRAINT reject_new_permission CHECK(menu_id NOT LIKE 'M%') NOT VALID");
        create(body(2,"실패 서브",parent,"UI-03")).andExpect(status().isInternalServerError());assertThat(snapshot()).isEqualTo(before);
    }
    @Test void requiredMenuAuditFailureRollsBackAllRows() throws Exception {
        var before=snapshot();sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_new_menu_history CHECK(trgt_tbl_nm <> 'TB_SY_MENU') NOT VALID");
        create(body(1,"실패 그룹",null,null)).andExpect(status().isInternalServerError());assertThat(snapshot()).isEqualTo(before);
    }
    @Test void requiredPermissionAuditFailureRollsBackMenuAndAllPermissions() throws Exception {
        makeScreenUnlinked();String parent=root();var before=snapshot();
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_new_permission_history CHECK(trgt_tbl_nm <> 'TB_SY_ROLE_MENU_AUTH') NOT VALID");
        create(body(2,"실패 서브",parent,"UI-03")).andExpect(status().isInternalServerError());assertThat(snapshot()).isEqualTo(before);
    }
    @Test void concurrentRegistrationsSerializeWholeTreeAndOnlyOneLatestRevisionCommits() throws Exception {
        String expected=trees.getMenuManagement().revision();
        long before=sql.queryForObject("SELECT count(*) FROM tb_sy_menu",Long.class);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        try {
            var calls=new ArrayList<java.util.concurrent.Future<String>>();
            for(int i=0;i<2;i++) {
                String name="동시 그룹 "+i;
                calls.add(pool.submit(()->{
                    start.await();
                    try { service.createMenu(new MenuRegistrationDtos.Create(1,name,null,null,false,expected));return "CREATED"; }
                    catch(ApiException e) {return e.code();}
                }));
            }
            start.countDown();
            List<String> outcomes=new ArrayList<>();
            for(var call:calls)outcomes.add(call.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder("CREATED","STALE_WRITE");
            assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_menu",Long.class)).isEqualTo(before+1);
        } finally {pool.shutdownNow();}
    }
    @Test void staleAndStoppedDuplicateRejectWithoutAnyBusinessOrAuditChange() throws Exception {
        String parent=root();String stale=body(1,"오래된 요청",null,null);root();var before=snapshot();
        create(stale).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));assertThat(snapshot()).isEqualTo(before);
        String name="시험 중지";sql.update("INSERT INTO tb_sy_menu(menu_id,upper_menu_id,menu_nm,scrn_id,menu_lvl,sort_ord,use_yn,reg_user_id) VALUES ('TEST_STOP',?,?,'UI-99',2,1,'N','admin')",parent,name);
        before=snapshot();create(body(2,"중복",parent,"UI-99")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_SCREEN"));assertThat(snapshot()).isEqualTo(before);
    }
}
