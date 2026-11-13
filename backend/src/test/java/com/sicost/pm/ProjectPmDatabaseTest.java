package com.sicost.pm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.budget.*;
import com.sicost.businessusers.*;
import com.sicost.businessusersops.*;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static com.sicost.pm.ProjectPmDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class ProjectPmDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    ProjectPmService service;
    ObjectMapper json;
    MockMvc mvc;
    String id,other,chosen,old,part;
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
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(ProjectPmMapper.class,()->session.getMapper(ProjectPmMapper.class));
        context.registerBean(ProjectBudgetMapper.class,()->session.getMapper(ProjectBudgetMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.registerBean(BusinessUserMapper.class,()->session.getMapper(BusinessUserMapper.class));
        context.registerBean(AccessLogMapper.class,()->session.getMapper(AccessLogMapper.class));
        context.registerBean(BusinessUserProjectsMapper.class,()->session.getMapper(BusinessUserProjectsMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,WriteCoordinator.class,
            CommitInvalidation.class,ProjectBudgetRepository.class,ProjectPmRepository.class,ProjectPmService.class,
            AccessLogWriter.class,BusinessUserRepository.class,BusinessUserService.class,BusinessUserProjectsRepository.class,BusinessUserProjectsService.class);
        context.refresh();service=context.getBean(ProjectPmService.class);
        mvc=MockMvcBuilders.standaloneSetup(new ProjectPmController(service),new BusinessUserProjectsController(context.getBean(BusinessUserProjectsService.class)))
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,10);
        id="P-"+suffix;other="Q-"+suffix;chosen="pm"+suffix;old="old"+suffix;part="part"+suffix;
        project(id,"ING","2027-12-31");project(other,"END","2024-12-31");
        actor(chosen);actor(old);
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES (?,?,'N','Y','admin')",part,part);
        scope(chosen,10,other,"PROJ",null);scope(chosen,11,null,"PART",part);
    }
    @AfterEach void close() {if(context!=null)context.close();}
    void project(String key,String status,String end) {
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO',?,'2024-01-01',CAST(? AS date),999999999999999,0,0,'admin')",key,key,status,end);
    }
    void actor(String user) {
        sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,reg_user_id) SELECT ?,?,'pm@example.org',pwd_hash,0,'USE',0,'admin' FROM tb_sy_user WHERE user_id='admin'",user,user);
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,'PM','admin'),(?,'SALES_LDR','admin')",user,user);
    }
    void scope(String user,int sn,String project,String type,String partId) {
        sql.update("INSERT INTO tb_sy_user_scope(user_id,scope_sn,scope_type_cd,part_id,proj_id,reg_user_id) VALUES (?,?,?,?,?,'admin')",user,sn,type,partId,project);
    }
    SaveProjectPmRequest save(boolean confirmed) {return new SaveProjectPmRequest(chosen,confirmed,service.getProjectPm(id).revision());}
    List<Map<String,Object>> scopes() {return sql.queryForList("SELECT * FROM tb_sy_user_scope ORDER BY user_id,scope_sn");}
    List<Map<String,Object>> events() {return sql.queryForList("SELECT * FROM tb_pj_proj_chg WHERE proj_id=? ORDER BY hist_sn",id);}
    List<Map<String,Object>> audit() {return sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id");}
    @Test void publicRealDbAssignmentUsesNullPartAndAtomicNewAuditAndContractEvent() throws Exception {
        var otherRows=scopes();var roles=sql.queryForList("SELECT * FROM tb_sy_user_role ORDER BY user_id,role_id");
        String response=mvc.perform(put("/api/projects/{id}/pm",id).contentType("application/json").content(json.writeValueAsString(save(false))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.value.items[0].userId").value(chosen)).andReturn().getResponse().getContentAsString();
        var row=sql.queryForMap("SELECT * FROM tb_sy_user_scope WHERE proj_id=?",id);assertThat(row.get("part_id")).isNull();assertThat(row.get("scope_type_cd")).isEqualTo("PROJ");
        assertThat(((Number)row.get("scope_sn")).intValue()).isEqualTo(12);assertThat(row.get("reg_user_id")).isEqualTo("admin");
        assertThat(scopes()).containsAll(otherRows);assertThat(sql.queryForList("SELECT * FROM tb_sy_user_role ORDER BY user_id,role_id")).isEqualTo(roles);
        assertThat(events()).singleElement().satisfies(e -> {assertThat(e.get("chg_item_cd")).isEqualTo("ETC");assertThat(e.get("chg_item_nm")).isEqualTo("PM");});
        assertThat(audit()).singleElement().satisfies(e -> {assertThat(e.get("chg_type_cd")).isEqualTo("NEW");assertThat(e.get("trgt_tbl_nm")).isEqualTo("TB_SY_USER_SCOPE");});
        assertThat(json.readTree(response).at("/data/revision").asText()).isEqualTo(service.getProjectPm(id).revision());
        mvc.perform(get("/api/business-users/{userId}/projects",chosen)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2));
    }
    @Test void sameUserDoesNotChangeTimestampsOrAnyHistory() {
        service.saveProjectPm(id,save(false));var rows=scopes();var events=events();var audit=audit();
        assertThat(service.saveProjectPm(id,save(false)).changed()).isFalse();assertThat(scopes()).isEqualTo(rows);assertThat(events()).isEqualTo(events);assertThat(audit()).isEqualTo(audit);
    }
    @Test void replacementCollapsesLegacyDuplicatesAndPreservesOtherProjectPartAndRoles() {
        scope(old,1,id,"PROJ",null);scope(old,2,id,"PROJ",null);scope(chosen,3,id,"PROJ",null);
        var unrelated=scopes().stream().filter(r -> !id.equals(r.get("proj_id"))).toList();var roles=sql.queryForList("SELECT * FROM tb_sy_user_role ORDER BY user_id,role_id");
        assertThat(service.getProjectPm(id).items()).hasSize(3);assertThatThrownBy(()->service.saveProjectPm(id,save(false))).isInstanceOf(ApiException.class);
        service.saveProjectPm(id,save(true));assertThat(service.getProjectPm(id).items()).singleElement().extracting(ProjectPm::userId).isEqualTo(chosen);
        assertThat(scopes()).containsAll(unrelated);assertThat(sql.queryForList("SELECT * FROM tb_sy_user_role ORDER BY user_id,role_id")).isEqualTo(roles);
        assertThat(events()).hasSize(1);assertThat(audit()).hasSize(4);assertThat(audit().stream().filter(r -> r.get("chg_type_cd").equals("DEL"))).hasSize(3);
    }
    @Test void pastEndRemovalOnlyDeletesRequestedUsersProjectAndAssignedListRefreshes() throws Exception {
        sql.update("UPDATE tb_pj_proj SET proj_stat_cd='END',ctrt_end_dt='2024-12-31' WHERE proj_id=?",id);
        scope(chosen,1,id,"PROJ",null);scope(old,1,id,"PROJ",null);
        sql.update("UPDATE tb_sy_user SET user_stat_cd='STOP' WHERE user_id=?",chosen);
        var before=scopes();var target=before.stream().filter(r -> chosen.equals(r.get("user_id")) && id.equals(r.get("proj_id"))).findFirst().orElseThrow();
        var input=new DeleteProjectPmRequest(chosen,service.getProjectPm(id).revision());
        mvc.perform(delete("/api/projects/{id}/pm",id).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        before=new ArrayList<>(before);before.remove(target);assertThat(scopes()).isEqualTo(before);
        assertThat(audit()).singleElement().satisfies(e -> assertThat(e.get("chg_type_cd")).isEqualTo("DEL"));assertThat(events()).hasSize(1);
        mvc.perform(get("/api/business-users/{userId}/projects",chosen)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].id").value(other));
    }
    @Test void qualificationUsesActualUseAndRoleAfterLockAndNotAdminRole() {
        sql.update("UPDATE tb_sy_user SET user_stat_cd='STOP' WHERE user_id=?",chosen);
        assertThatThrownBy(()->service.saveProjectPm(id,save(false))).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("PM_QUALIFICATION_REQUIRED"));assertThat(events()).isEmpty();
        sql.update("UPDATE tb_sy_user SET user_stat_cd='USE' WHERE user_id=?",chosen);sql.update("DELETE FROM tb_sy_user_role WHERE user_id=? AND role_id='PM'",chosen);
        assertThatThrownBy(()->service.saveProjectPm(id,save(false))).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("PM_QUALIFICATION_REQUIRED"));assertThat(service.getProjectPm(id).items()).isEmpty();
    }
    @Test void staleChildWriteAndScopeWriteBothRejectWithoutEffects() {
        var input=save(false);sql.update("INSERT INTO tb_pj_mnpw_plan(proj_id,job_cd,plan_mm,src_cd,reg_user_id,reg_dttm) VALUES (?,'TEST',1.23,'MANUAL','admin',timestamp '2026-06-15 01:02:03.123456')",id);
        assertThatThrownBy(()->service.saveProjectPm(id,input)).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("STALE_WRITE"));
        assertThat(context.getBean(ProjectPmRepository.class).snapshot(id)).contains("1.23","123456");
        var remove=new DeleteProjectPmRequest(chosen,service.getProjectPm(id).revision());scope(chosen,1,id,"PROJ",null);var before=scopes();
        assertThatThrownBy(()->service.deleteProjectPm(id,remove)).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("STALE_WRITE"));assertThat(scopes()).isEqualTo(before);assertThat(events()).isEmpty();assertThat(audit()).isEmpty();
    }
    @Test void readLeavesBodyScopeHistoryAccountingAndAuditTimesUnchanged() throws Exception {
        scope(chosen,1,id,"PROJ",null);var rows=scopes();var history=events();var audits=audit();var project=sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?",id);var accounting=sql.queryForList("SELECT * FROM tb_co_mon_cost");
        mvc.perform(get("/api/projects/{id}/pm",id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].userId").value(chosen));
        assertThat(scopes()).isEqualTo(rows);assertThat(events()).isEqualTo(history);assertThat(audit()).isEqualTo(audits);assertThat(sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?",id)).isEqualTo(project);assertThat(sql.queryForList("SELECT * FROM tb_co_mon_cost")).isEqualTo(accounting);
    }
    @Test void contractHistoryFailureRollsBackScopesAndAlreadyAppendedAudit() {
        scope(old,1,id,"PROJ",null);var rows=scopes();var input=save(true);
        sql.execute("ALTER TABLE tb_pj_proj_chg ADD CONSTRAINT reject_pm_contract CHECK(chg_item_nm <> 'PM')");
        assertThatThrownBy(()->service.saveProjectPm(id,input)).isInstanceOf(RuntimeException.class);assertThat(scopes()).isEqualTo(rows);assertThat(events()).isEmpty();assertThat(audit()).isEmpty();
    }
    @Test void auditFailureRollsBackReplacementAndLeavesPriorPm() {
        scope(old,1,id,"PROJ",null);var rows=scopes();var input=save(true);
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_pm_audit CHECK(trgt_tbl_nm <> 'TB_SY_USER_SCOPE')");
        assertThatThrownBy(()->service.saveProjectPm(id,input)).isInstanceOf(RuntimeException.class);assertThat(scopes()).isEqualTo(rows);assertThat(events()).isEmpty();
    }
    @Test void userLockSerializesRoleRemovalAndAssignmentWithNoUnqualifiedConnection() throws Exception {
        var executor=Executors.newSingleThreadExecutor();var entered=new CountDownLatch(1);
        var input=save(false);
        try {
            var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            Future<?>[] future=new Future<?>[1];
            tx.execute(status -> {
                context.getBean(AggregateLockMapper.class).lock("USER:"+chosen);
                sql.update("DELETE FROM tb_sy_user_role WHERE user_id=? AND role_id='PM'",chosen);
                future[0]=executor.submit(()-> {entered.countDown();return service.saveProjectPm(id,input);});
                try {assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();} catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                return null;
            });
            assertThatThrownBy(()->future[0].get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(ApiException.class);
            assertThat(service.getProjectPm(id).items()).isEmpty();assertThat(events()).isEmpty();assertThat(audit()).isEmpty();
        } finally {executor.shutdownNow();}
    }
    @Test void businessUserRoleRemovalUsesSameUserLockAndRejectsRemainingPm() {
        service.saveProjectPm(id,save(false));var business=context.getBean(BusinessUserService.class);var user=business.get(chosen);
        assertThatThrownBy(()->business.update(chosen,new BusinessUserDtos.Edit(user.name(),user.email(),List.of("SALES_LDR"),user.revision())))
            .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("USER_IS_PROJECT_PM"));
        assertThat(service.getProjectPm(id).items()).hasSize(1);
    }
}
