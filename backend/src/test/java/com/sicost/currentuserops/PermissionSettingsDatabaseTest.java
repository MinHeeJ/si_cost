package com.sicost.currentuserops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class PermissionSettingsDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    PermissionSettingsService service;
    PermissionSettingsMapper mapper;
    MockMvc mvc;
    String user;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();
        configuration.addMapper(com.sicost.permission.PermissionMapper.class);
        configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());mapper=session.getMapper(PermissionSettingsMapper.class);
        user="p"+UUID.randomUUID().toString().replace("-","").substring(0,15);
        sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,reg_user_id) VALUES(?,?,?,'not-a-login-value',0,'USE',0,'admin')",user,"권한 시험",user+"@example.org");
        CurrentUserProvider users=mock(CurrentUserProvider.class);
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser(user,"권한 시험",List.of("RP","RJ"),false));
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        context.registerBean(ObjectMapper.class,()->JsonMapper.builder().findAndAddModules().build());
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-30T15:00:00Z"),ZoneOffset.UTC));
        context.registerBean(CurrentUserProvider.class,()->users);
        context.registerBean(PermissionSettingsMapper.class,()->mapper);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(user,null,List.of()));
        context.registerBean(com.sicost.permission.PermissionMapper.class,()->session.getMapper(com.sicost.permission.PermissionMapper.class));
        context.register(com.sicost.permission.PermissionService.class);
        context.register(Transactions.class,RevisionService.class,PermissionSettingsRepository.class,PermissionSettingsService.class);
        context.refresh();service=context.getBean(PermissionSettingsService.class);
        mvc=MockMvcBuilders.standaloneSetup(new PermissionSettingsController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        role("RP","PART",false,true);role("RJ","PROJ",true,true);role("RX","ALL",true,false);
    }
    @AfterEach void close() {if(context!=null)context.close();}
    void role(String id,String scope,boolean sensitive,boolean active) {
        sql.update("INSERT INTO tb_sy_role(role_id,role_nm,data_scope_cd,snstv_view_yn,sys_role_yn,sort_ord,use_yn,reg_user_id) VALUES(?,?,?,?,'N',99,?,'admin')",id,user+id,scope,sensitive?"Y":"N",active?"Y":"N");
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES(?,?,'admin')",user,id);
    }
    void part(String id,String name,String hq,String start,String end) {
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES(?,?,?,'N','admin')",id,name,hq);
        if(start!=null)sql.update("INSERT INTO tb_bs_part_std(part_id,aply_bgn_ym,aply_end_ym,part_ldr_user_id,prft_dstrb_rt,sales_exps_amt,reg_user_id) VALUES(?,?,?,?,0,0,'admin')",id,start,end,user);
    }
    void scope(int sn,String type,String part,String project) {
        sql.update("INSERT INTO tb_sy_user_scope(user_id,scope_sn,scope_type_cd,part_id,proj_id,reg_user_id) VALUES(?,?,?,?,?,'admin')",user,sn,type,part,project);
    }
    Map<String,Object> snapshot() {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String table:List.of("tb_sy_user","tb_sy_user_role","tb_sy_role","tb_sy_role_menu_auth","tb_sy_menu","tb_sy_user_scope","tb_bs_part","tb_bs_part_std","tb_sy_chg_hist","tb_sy_accs_log"))
            result.put(table,sql.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM "+table+" t",String.class));
        return result;
    }
    @Test void sevenIndependentFlagsAndSensitiveViewUnionExcludeStoppedRoles() {
        // Use the real registered project menu; duplicate screens are not independent permission targets.
        sql.update("INSERT INTO tb_sy_role_menu_auth(role_id,menu_id,inq_yn,reg_yn,upd_yn,del_yn,upld_yn,dnld_yn,cnfm_yn,reg_user_id) VALUES('RP','M004','Y','N','Y','N','Y','N','Y','admin'),('RJ','M004','N','Y','N','Y','N','Y','N','admin')");
        var before=snapshot();var result=service.getCurrentUserPermissionSettings();
        assertThat(result.roleIds()).containsExactly("RJ","RP");assertThat(result.sensitiveView()).isTrue();
        assertThat(result.rowScope().all()).isFalse();
        var permission=result.menuPermissions().stream().filter(p->p.menuId().equals("M004")).findFirst().orElseThrow();
        assertThat(List.of(permission.inquire(),permission.register(),permission.update(),permission.delete(),permission.upload(),permission.download(),permission.confirm())).containsOnly(true);
        assertThat(snapshot()).isEqualTo(before);
        sql.update("UPDATE tb_sy_role SET use_yn='N' WHERE role_id='RJ'");
        var next=service.getCurrentUserPermissionSettings();assertThat(next.sensitiveView()).isFalse();
        assertThat(next.menuPermissions().get(0).register()).isFalse();assertThat(next.settingsRevision()).isNotEqualTo(result.settingsRevision());
    }
    @Test void partAndProjectScopesAreBothPreservedWithCurrentSeoulMonthAndSortedDeduplicatedParts() {
        part("CP1","나 파트","N","202601","202607");part("CP2","가 파트","N","202607",null);
        part("FUT","미래 파트","N","202608",null);part("PAST","과거 파트","N","202601","202606");
        scope(1,"PART","HQ",null);scope(2,"PART","HQ",null);
        String project="PROJTEST";
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES(?,'권한 시험 프로젝트','TK','END','2020-01-01','2020-12-31',0,0,0,'admin')",project);
        scope(3,"PROJ",null,project);scope(4,"PROJ",null,project);
        var before=snapshot();var result=service.getCurrentUserPermissionSettings();
        assertThat(result.assignedParts()).extracting(PermissionSettingsDtos.AssignedPart::partId).containsExactly("CP2","CP1","HQ");
        assertThat(result.rowScope().partIds()).containsExactly("CP1","CP2","HQ");
        assertThat(result.rowScope().projectIds()).containsExactly(project);assertThat(result.assignedProjects()).hasSize(2);
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void allWithNoAssignmentsIsStillAllAndEmptySourceDoesNotGenerateRows() throws Exception {
        role("RA","ALL",false,true);var before=snapshot();
        mvc.perform(get("/api/current-user/permission-settings")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rowScope.all").value(true)).andExpect(jsonPath("$.data.menuPermissions").isEmpty())
            .andExpect(jsonPath("$.data.assignedParts").isEmpty()).andExpect(jsonPath("$.data.assignedProjects").isEmpty());
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void validationAndFailedReadNeverWriteOrSeed() throws Exception {
        var before=snapshot();
        mvc.perform(get("/api/current-user/permission-settings").param("actorId","admin")).andExpect(status().isBadRequest());
        assertThat(snapshot()).isEqualTo(before);
        sql.execute("ALTER TABLE tb_sy_role_menu_auth RENAME COLUMN inq_yn TO unavailable_inquire");
        mvc.perform(get("/api/current-user/permission-settings")).andExpect(status().isInternalServerError());
        sql.execute("ALTER TABLE tb_sy_role_menu_auth RENAME COLUMN unavailable_inquire TO inq_yn");
        assertThat(snapshot()).isEqualTo(before);
    }
}
