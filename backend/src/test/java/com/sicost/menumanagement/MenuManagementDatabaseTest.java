package com.sicost.menumanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
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
import static com.sicost.menumanagement.MenuManagementDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class MenuManagementDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql; MenuManagementService service; ObjectMapper json; MockMvc mvc;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());context=new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class,()->db.source);json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(MenuManagementMapper.class,()->session.getMapper(MenuManagementMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(SharedQueriesMapper.class,()->session.getMapper(SharedQueriesMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,WriteCoordinator.class,CommitInvalidation.class,
            MenuManagementRepository.class,MenuManagementService.class,MenuService.class);
        context.refresh();service=context.getBean(MenuManagementService.class);
        mvc=MockMvcBuilders.standaloneSetup(new MenuManagementController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        sql.update("INSERT INTO tb_sy_menu(menu_id,menu_nm,menu_lvl,sort_ord,reg_user_id) VALUES ('TEST_ROOT_A','시험 대메뉴 A',1,90000,'admin'),('TEST_ROOT_B','시험 대메뉴 B',1,90001,'admin')");
        sql.update("INSERT INTO tb_sy_menu(menu_id,upper_menu_id,menu_nm,scrn_id,menu_lvl,sort_ord,reg_user_id) VALUES ('TEST_CHILD_A','TEST_ROOT_A','시험 서브 A','UI-01',2,1,'admin'),('TEST_CHILD_B','TEST_ROOT_B','시험 서브 B','UI-02',2,1,'admin')");
    }
    @AfterEach void close() {if(context!=null)context.close();}
    PutTree input(ManagedMenuTree tree,String name) {
        return new PutTree(tree.items().stream().map(row->new MenuInput(row.menuId(),row.menuId().equals("TEST_CHILD_A")?name:row.name(),row.level(),row.parentId(),row.screenId(),row.sortOrder(),row.level()==2?row.use():null,row.level()==2?row.pcOnly():null)).toList(),tree.revision());
    }
    String request(PutTree value) throws Exception {
        var body=json.createObjectNode().put("expectedRevision",value.expectedRevision());var array=body.putArray("items");
        for(MenuInput row:value.items()) {var node=json.valueToTree(row);if(row.level()==1)((com.fasterxml.jackson.databind.node.ObjectNode)node).remove(List.of("use","pcOnly"));array.add(node);}
        return body.toString();
    }
    List<Map<String,Object>> treeRows() {return sql.queryForList("SELECT * FROM tb_sy_menu ORDER BY menu_id");}
    @Test void getReturnsStoredSourceWithoutMutatingAnySourceOrHistory() throws Exception {
        var before=treeRows();var history=sql.queryForList("SELECT * FROM tb_sy_chg_hist");var access=sql.queryForList("SELECT * FROM tb_sy_accs_log");
        mvc.perform(get("/api/menu-management")).andExpect(status().isOk()).andExpect(jsonPath("$.data.developmentMode").value(true));
        assertThat(service.getMenuManagement().items()).hasSize(before.size());assertThat(treeRows()).isEqualTo(before);
        assertThat(sql.queryForList("SELECT * FROM tb_sy_chg_hist")).isEqualTo(history);assertThat(sql.queryForList("SELECT * FROM tb_sy_accs_log")).isEqualTo(access);
    }
    @Test void realMapperWritesAuditAndNoOpPreservesAllRowsAndPermissionPreferenceData() throws Exception {
        var roles=sql.queryForList("SELECT * FROM tb_sy_role_menu_auth ORDER BY role_id,menu_id");var prefs=sql.queryForList("SELECT * FROM tb_sy_user_pref ORDER BY user_id,pref_type_cd,pref_key");long access=sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log",Long.class);
        mvc.perform(put("/api/menu-management").contentType("application/json").content(request(input(service.getMenuManagement(),"변경 이름")))).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        assertThat(sql.queryForObject("SELECT menu_nm FROM tb_sy_menu WHERE menu_id='TEST_CHILD_A'",String.class)).isEqualTo("변경 이름");
        assertThat(sql.queryForList("SELECT bfr_val,aft_val,reg_user_id FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_SY_MENU' AND trgt_key='TEST_CHILD_A' AND chg_item_nm='MENU_NM'"))
            .containsExactly(Map.of("bfr_val","시험 서브 A","aft_val","변경 이름","reg_user_id","admin"));
        var after=treeRows();long audits=sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist",Long.class);
        assertThat(service.putMenuManagement(input(service.getMenuManagement(),"변경 이름")).changed()).isFalse();assertThat(treeRows()).isEqualTo(after);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist",Long.class)).isEqualTo(audits);
        assertThat(sql.queryForList("SELECT * FROM tb_sy_role_menu_auth ORDER BY role_id,menu_id")).isEqualTo(roles);assertThat(sql.queryForList("SELECT * FROM tb_sy_user_pref ORDER BY user_id,pref_type_cd,pref_key")).isEqualTo(prefs);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log",Long.class)).isEqualTo(access);
    }
    @Test void requiredAuditFailureRollsBackEntireTreeAndAuditThroughHttp() throws Exception {
        var before=treeRows();var history=sql.queryForList("SELECT * FROM tb_sy_chg_hist");
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_menu_history CHECK(trgt_tbl_nm <> 'TB_SY_MENU')");
        mvc.perform(put("/api/menu-management").contentType("application/json").content(request(input(service.getMenuManagement(),"롤백 이름")))).andExpect(status().isInternalServerError());
        assertThat(treeRows()).isEqualTo(before);assertThat(sql.queryForList("SELECT * FROM tb_sy_chg_hist")).isEqualTo(history);
    }
    @Test void singleIllegalNodeChangesNothingAndExternalEditInvalidatesRevision() {
        ManagedMenuTree tree=service.getMenuManagement();var before=treeRows();
        List<MenuInput> invalid=new ArrayList<>(input(tree,"수정").items());MenuInput row=invalid.get(0);invalid.set(0,new MenuInput(row.menuId(),row.name(),row.level(),"absent",row.screenId(),row.sortOrder(),row.use(),row.pcOnly()));
        assertThatThrownBy(()->service.putMenuManagement(new PutTree(invalid,tree.revision()))).isInstanceOf(ApiException.class);assertThat(treeRows()).isEqualTo(before);
        sql.update("UPDATE tb_sy_menu SET menu_nm='외부 변경' WHERE menu_id='TEST_CHILD_B'");
        assertThatThrownBy(()->service.putMenuManagement(input(tree,"수정"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));
    }
}
