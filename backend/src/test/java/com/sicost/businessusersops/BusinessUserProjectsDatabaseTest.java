package com.sicost.businessusersops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.*;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class BusinessUserProjectsDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    MockMvc mvc;
    String userId;
    String projectId;
    @Configuration @EnableTransactionManagement static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void install() throws Exception {
        var db=new PostgreSqlDatabase();db.install();sql=db.sql;
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();
        configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        context.registerBean(ObjectMapper.class,()->JsonMapper.builder().findAndAddModules().build());
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(BusinessUserProjectsMapper.class,()->session.getMapper(BusinessUserProjectsMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,RevisionService.class,
            BusinessUserProjectsRepository.class,BusinessUserProjectsService.class);
        context.refresh();
        mvc=MockMvcBuilders.standaloneSetup(new BusinessUserProjectsController(context.getBean(BusinessUserProjectsService.class)))
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,10);
        userId="reader"+suffix;projectId="P-"+suffix;
        sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,reg_user_id) "
            +"SELECT ?,?,'reader@example.org',pwd_hash,0,'STOP',0,'admin' FROM tb_sy_user WHERE user_id='admin'",userId,"조회 사용자");
    }
    @AfterEach void close() { if(context!=null)context.close(); }
    void project(String id,String type,String status,String end) {
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) "
            +"VALUES(?,?,?,?,'2025-01-01',CAST(? AS date),999999999999999,0,0,'admin')",id,"담당 계약 "+id,type,status,end);
    }
    void connect(String id,int serial,String type) {
        sql.update("INSERT INTO tb_sy_user_scope(user_id,scope_sn,scope_type_cd,proj_id,reg_user_id) VALUES(?,?,?,?,'admin')",userId,serial,type,id);
    }
    List<Map<String,Object>> snapshot() {
        return List.of("tb_pj_proj","tb_sy_user_scope","tb_sy_user_role","tb_pj_proj_chg","tb_sy_chg_hist","tb_sy_accs_log","tb_co_mon_cost")
            .stream().map(table->Map.<String,Object>of("table",table,"rows",sql.queryForList("SELECT * FROM "+table))).toList();
    }
    @Test void allLegacyConnectionsIncludingEndedAndLostAreReadWithoutQualificationFiltering() throws Exception {
        project(projectId,"TK","ING","2025-12-31");connect(projectId,1,"PROJ");connect(projectId,2,"PROJ");
        project(projectId+"l","IN","LOSE","2025-12-31");connect(projectId+"l",3,"PROJ");
        project(projectId+"x","TK","PRE","2027-12-31");connect(projectId+"x",4,"PART");
        var before=snapshot();
        mvc.perform(get("/api/business-users/{userId}/projects",userId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].id").value(projectId))
            .andExpect(jsonPath("$.data.items[0].status").value("END"))
            .andExpect(jsonPath("$.data.items[0].contractAmount").value("999999999999999"))
            .andExpect(jsonPath("$.data.items[0].personCount").value(0))
            .andExpect(jsonPath("$.data.items[0].partName").isEmpty())
            .andExpect(jsonPath("$.data.items[0].distribution.hasTable").value(false))
            .andExpect(jsonPath("$.data.items[1].status").value("LOSE"));
        assertThat(snapshot()).isEqualTo(before);
        assertThat(context.getBean(CurrentUserProvider.class).userId()).isEqualTo("admin");
    }
    @Test void noSourceAndUnknownUserAreDifferentWithoutInventedProjects() throws Exception {
        mvc.perform(get("/api/business-users/{userId}/projects",userId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/business-users/{userId}/projects",userId+"missing"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
    @Test void zeroPlanIsWholeSelectedStageRatherThanFallbackToContract() throws Exception {
        project(projectId,"TK","ING","2027-12-31");connect(projectId,1,"PROJ");
        sql.update("INSERT INTO tb_pj_tk_dstrb(proj_id,dstrb_step_cd,row_sn,dstrb_ym,row_type_cd,item_nm,dstrb_amt,reg_user_id) "
            +"VALUES(?,'PLAN',1,'202606','ITEM','경비',0,'admin'),(?,'CTRT',1,'202606','ITEM','경비',100,'admin')",projectId,projectId);
        mvc.perform(get("/api/business-users/{userId}/projects",userId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].distribution.selectedStep").value("PLAN"))
            .andExpect(jsonPath("$.data.items[0].distribution.totalAmount").value("0"))
            .andExpect(jsonPath("$.data.items[0].distribution.contractDifference").value("999999999999999"))
            .andExpect(jsonPath("$.data.items[0].distribution.hasTable").value(true));
    }
    @Test void currentPartUsesLatestStartedRowOrEarliestFutureRowLikeProjectList() throws Exception {
        project(projectId,"TK","ING","2027-12-31");connect(projectId,1,"PROJ");
        sql.update("INSERT INTO tb_pj_proj_part(proj_id,aply_bgn_ym,part_id,reg_user_id) VALUES (?, '202607','HQ','admin'),(?, '202608',NULL,'admin')",projectId,projectId);
        String partName=sql.queryForObject("SELECT part_nm FROM tb_bs_part WHERE part_id='HQ'",String.class);
        mvc.perform(get("/api/business-users/{userId}/projects",userId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].partName").value(partName));
        sql.update("INSERT INTO tb_pj_proj_part(proj_id,aply_bgn_ym,part_id,reg_user_id) VALUES (?, '202605','HQ','admin'),(?, '202606',NULL,'admin')",projectId,projectId);
        mvc.perform(get("/api/business-users/{userId}/projects",userId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].partName").isEmpty());
    }
    @Test void invalidQueryLeavesBusinessHistoryAndAccountingExactlyUnchanged() throws Exception {
        project(projectId,"TK","PRE","2027-12-31");connect(projectId,1,"PROJ");var before=snapshot();
        mvc.perform(get("/api/business-users/{userId}/projects",userId).param("year","2026"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("year"));
        assertThat(snapshot()).isEqualTo(before);
    }
}
