package com.sicost.businessusers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.RevisionService;
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
class BusinessUserDatabaseTest {
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
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,AccessLogWriter.class,
            RevisionService.class,BusinessUserRepository.class,BusinessUserService.class);
        context.refresh();service=context.getBean(BusinessUserService.class);
        mvc=MockMvcBuilders.standaloneSetup(new BusinessUserController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
    }
    @AfterEach void close() {if(context!=null)context.close();}
    Create input(String id,List<String> roles) {return new Create(id,"사용자 "+id,id+"@example.org",roles);}
    User create(String id,List<String> roles) {return service.create(input(id,roles),new MockHttpServletRequest()).user();}
    Edit edit(User user,String name,List<String> roles) {return new Edit(name,user.email(),roles,user.revision());}
    long count(String table,String id) {return sql.queryForObject("SELECT count(*) FROM "+table+" WHERE "+(table.equals("tb_sy_chg_hist")?"trgt_key":table.equals("tb_sy_accs_log")?"trgt_desc":"user_id")+"=?",Long.class,id);}
    void cleanHistory() {sql.execute("TRUNCATE tb_sy_chg_hist,tb_sy_accs_log");}
    @Test void createCommitsAllFourResourcesAndSystemInitialValuesWithoutChangingExistingActor() throws Exception {
        var actor=sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id='admin'");
        var definitions=sql.queryForList("SELECT * FROM tb_sy_role ORDER BY role_id");
        mvc.perform(post("/api/business-users").contentType("application/json").content(json.writeValueAsString(input("ab12",List.of("PM")))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.user.status").value("USE"));
        assertThat(count("tb_sy_user","ab12")).isEqualTo(1);assertThat(count("tb_sy_user_role","ab12")).isEqualTo(1);
        assertThat(count("tb_sy_chg_hist","ab12")).isEqualTo(1);assertThat(count("tb_sy_accs_log","ab12")).isEqualTo(1);
        var row=sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id='ab12'");
        assertThat(row.get("user_nm")).isEqualTo("사용자 ab12");assertThat(row.get("email_addr")).isEqualTo("ab12@example.org");
        assertThat(row.get("user_stat_cd")).isEqualTo("USE");assertThat(row.get("temp_pwd_yn")).isEqualTo("Y");assertThat(row.get("mfa_use_yn")).isEqualTo("N");
        assertThat(((Number)row.get("pwd_err_cnt")).intValue()).isZero();assertThat(((Number)row.get("mfa_err_cnt")).intValue()).isZero();
        for(String field:List.of("pwd_chg_dttm","last_login_dttm","mfa_code_hash","mfa_expr_dttm"))assertThat(row.get(field)).isNull();
        assertThat(((String)row.get("pwd_hash")).startsWith("$2a$10$")).isTrue();
        assertThat(sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id='admin'").equals(actor)).isTrue();
        assertThat(sql.queryForList("SELECT * FROM tb_sy_role ORDER BY role_id")).isEqualTo(definitions);
        String response=mvc.perform(get("/api/business-users/ab12")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("pwd","mfa","passwordHash");
        assertThat(sql.queryForList("SELECT chg_type_cd,chg_item_nm,aft_val FROM tb_sy_chg_hist")).containsExactly(Map.of("chg_type_cd","NEW","chg_item_nm","등록","aft_val","ab12 사용자 ab12"));
    }
    @Test void accessFailureRollsBackUserRolesAndNewHistoryThroughHttp() throws Exception {
        sql.execute("ALTER TABLE tb_sy_accs_log ADD CONSTRAINT reject_user_registration CHECK(act_type_cd &lt;&gt; 'USER_REG')".replace("&lt;&gt;","<>"));
        mvc.perform(post("/api/business-users").contentType("application/json").content(json.writeValueAsString(input("ab12",List.of("PM")))))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.matchesPattern(".*[가-힣].*")));
        for(String table:List.of("tb_sy_user","tb_sy_user_role","tb_sy_chg_hist","tb_sy_accs_log"))assertThat(count(table,"ab12")).isZero();
    }
    @Test void historyFailureAlsoRollsBackAllResources() {
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_user_history CHECK(trgt_tbl_nm <> 'TB_SY_USER')");
        assertThatThrownBy(()->create("ab12",List.of("PM"))).isInstanceOf(RuntimeException.class);
        for(String table:List.of("tb_sy_user","tb_sy_user_role","tb_sy_chg_hist","tb_sy_accs_log"))assertThat(count(table,"ab12")).isZero();
    }
    @Test void listNameAndIdSubstringCombinedRoleStatusAndCountUseIdenticalPredicates() {
        create("ab12",List.of("PM"));create("ab13",List.of("SALES_LDR"));create("cd12",List.of("PM"));
        sql.update("UPDATE tb_sy_user SET user_stat_cd='LOCK' WHERE user_id='ab12'");sql.update("UPDATE tb_sy_user SET user_stat_cd='STOP' WHERE user_id='cd12'");
        var all=service.list(BusinessUserInput.filter(Map.of()));assertThat(all.items()).extracting(User::status).contains("USE","LOCK","STOP");
        var filtered=service.list(BusinessUserInput.filter(Map.of("q","ab","businessRole","PM","status","LOCK")));
        assertThat(filtered.total()).isEqualTo(1);assertThat(filtered.items()).extracting(User::userId).containsExactly("ab12");
        assertThat(service.list(BusinessUserInput.filter(Map.of("q","사용자 ab1"))).total()).isEqualTo(2);
        assertThat(service.list(BusinessUserInput.filter(Map.of("q","AB"))).total()).isZero();
        assertThat(service.list(BusinessUserInput.filter(Map.of("page","10"))).items()).isEmpty();
    }
    @Test void updatePreservesAuthenticationAndAdminWithExactHistoryThenNoOp() {
        User user=create("ab12",List.of("PM"));
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES ('ab12','ADMIN','admin')");
        user=service.get("ab12");var original=sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id='ab12'");cleanHistory();
        Result changed=service.update("ab12",edit(user,"새 이름",List.of("SALES_LDR")));
        assertThat(changed.changed()).isTrue();assertThat(changed.user().name()).isEqualTo("새 이름");
        assertThat(sql.queryForList("SELECT role_id FROM tb_sy_user_role WHERE user_id='ab12' ORDER BY role_id",String.class)).containsExactly("ADMIN","SALES_LDR");
        var after=sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id='ab12'");
        for(String field:List.of("pwd_hash","pwd_chg_dttm","pwd_err_cnt","mfa_err_cnt","mfa_use_yn","mfa_code_hash","mfa_expr_dttm","last_login_dttm","temp_pwd_yn","user_stat_cd"))
            assertThat(Objects.equals(original.get(field),after.get(field))).isTrue();
        assertThat(sql.queryForList("SELECT chg_item_nm FROM tb_sy_chg_hist ORDER BY chg_item_nm",String.class)).containsExactly("업무 역할","이름");
        assertThat(service.update("ab12",edit(changed.user(),"새 이름",List.of("SALES_LDR"))).changed()).isFalse();
        assertThat(sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id='ab12'").equals(after)).isTrue();assertThat(count("tb_sy_chg_hist","ab12")).isEqualTo(2);
    }
    @Test void unchangedTimestampExternalEditAndFullRoleSetChangeBothInvalidateRevision() {
        User user=create("ab12",List.of("PM"));sql.update("UPDATE tb_sy_user SET user_nm='다른 이름' WHERE user_id='ab12'");cleanHistory();
        assertThatThrownBy(()->service.update("ab12",edit(user,"변경",List.of("PM")))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));
        User next=service.get("ab12");sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES ('ab12','ADMIN','admin')");
        assertThatThrownBy(()->service.update("ab12",edit(next,"변경",List.of("PM")))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));assertThat(count("tb_sy_chg_hist","ab12")).isZero();
    }
    void part(String start,String end) {
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES ('PART-901','중지 파트','N','N','admin')");
        sql.update("INSERT INTO tb_bs_part_std(part_id,aply_bgn_ym,aply_end_ym,part_ldr_user_id,prft_dstrb_rt,sales_exps_amt,reg_user_id) VALUES ('PART-901',?,?,'ab12',40,0,'admin')",start,end);
    }
    @Test void futureStoppedPartBlocksLeaderRemovalEvenBeforeItStarts() {
        User user=create("ab12",List.of("PM","SALES_LDR"));part("202701",null);cleanHistory();
        assertThatThrownBy(()->service.update("ab12",edit(user,user.name(),List.of("PM")))).isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.code()).isEqualTo("USER_IS_PART_LEADER");assertThat(e.getMessage()).contains("중지 파트");});
        assertThat(service.get("ab12").businessRoleIds()).contains("SALES_LDR");assertThat(count("tb_sy_chg_hist","ab12")).isZero();
    }
    @Test void currentMonthEndBlocksLeaderRemoval() {
        User user=create("ab12",List.of("PM","SALES_LDR"));part("202601","202606");
        assertThatThrownBy(()->service.update("ab12",edit(user,user.name(),List.of("PM")))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("USER_IS_PART_LEADER"));
    }
    @Test void pastEndedLeadershipAllowsRemoval() {
        User user=create("ab12",List.of("PM","SALES_LDR"));part("202601","202605");assertThat(service.update("ab12",edit(user,user.name(),List.of("PM"))).changed()).isTrue();
    }
    @Test void duplicateRegistrationNeverMutatesOriginal() {
        create("ab12",List.of("PM"));var before=sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id='ab12'");
        assertThatThrownBy(()->create("ab12",List.of("SALES_LDR"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("DUPLICATE_USER"));
        assertThat(sql.queryForMap("SELECT * FROM tb_sy_user WHERE user_id='ab12'").equals(before)).isTrue();assertThat(count("tb_sy_chg_hist","ab12")).isEqualTo(1);
    }
    @Test void concurrentUpdatesWithOneRevisionCommitExactlyOnce() throws Exception {
        User user=create("ab12",List.of("PM"));cleanHistory();CountDownLatch start=new CountDownLatch(1);
        var a=CompletableFuture.supplyAsync(()->concurrent(start,edit(user,"이름1",List.of("PM"))));
        var b=CompletableFuture.supplyAsync(()->concurrent(start,edit(user,"이름2",List.of("PM"))));start.countDown();
        assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","STALE_WRITE");assertThat(count("tb_sy_chg_hist","ab12")).isEqualTo(1);
    }
    String concurrent(CountDownLatch latch,Edit edit) {
        try {if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");service.update("ab12",edit);return "OK";}
        catch(ApiException e){return e.code();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    }
}
