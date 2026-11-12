package com.sicost.budget;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.math.BigDecimal;
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
import static com.sicost.budget.ProjectBudgetDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class ProjectBudgetDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    ProjectBudgetService service;
    ProjectBudgetRepository repository;
    MockMvc mvc;
    ObjectMapper json;
    String id;
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
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(ProjectBudgetMapper.class,()->session.getMapper(ProjectBudgetMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,
            RevisionService.class,WriteCoordinator.class,CommitInvalidation.class,ProjectBudgetRepository.class,ProjectBudgetService.class);
        context.refresh();service=context.getBean(ProjectBudgetService.class);repository=context.getBean(ProjectBudgetRepository.class);
        mvc=MockMvcBuilders.standaloneSetup(new ProjectBudgetController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        id="B-"+UUID.randomUUID().toString().replace("-","").substring(0,16);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,bdgt_manual_yn,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'TK','ING','2080-01-01','2081-12-31',101,80,'N',0,0,'admin')",id,"예산 시험 "+id);
        sql.update("INSERT INTO tb_sy_oper_std(std_cd,aply_bgn_ym,std_nm,std_val,unit_nm,reg_user_id) VALUES ('TGT_MRGN_RT','208007','목표 이익률',20,'%', 'admin')");
    }
    @AfterEach void close() {if(context!=null)context.close();}
    Map<String,Object> project() {return sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?",id);}
    long count(String table) {return sql.queryForObject("SELECT count(*) FROM "+table,Long.class);}
    SaveProjectBudgetRequest request(String amount) {return new SaveProjectBudgetRequest(2080,service.getProjectBudget(id).revision(),new BigDecimal(amount));}
    @Test void getUsesRealSourcesAndDoesNotChangeProjectHistoryOrAccounting() throws Exception {
        var before=project();var accounting=sql.queryForList("SELECT * FROM tb_co_mon_cost ORDER BY cost_ym,dev_id,proj_id,asgn_id");
        long events=count("tb_pj_proj_chg"),audit=count("tb_sy_chg_hist");
        mvc.perform(get("/api/projects/"+id+"/budget")).andExpect(status().isOk()).andExpect(jsonPath("$.data.budgetAmount").value("80"))
            .andExpect(jsonPath("$.data.autoPreviewAmount").value("80")).andExpect(jsonPath("$.data.targetMarginRate").value("20"));
        assertThat(project()).isEqualTo(before);assertThat(count("tb_pj_proj_chg")).isEqualTo(events);assertThat(count("tb_sy_chg_hist")).isEqualTo(audit);
        assertThat(sql.queryForList("SELECT * FROM tb_co_mon_cost ORDER BY cost_ym,dev_id,proj_id,asgn_id")).isEqualTo(accounting);
    }
    @Test void equalAutomaticManualThenNoOpPreservesAuditTimestamp() {
        var result=service.saveProjectBudget(id,request("80"));assertThat(result.changed()).isTrue();assertThat(result.value().budgetManual()).isEqualTo("Y");
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_proj_chg WHERE proj_id=? AND chg_item_cd='BUDGET'",Long.class,id)).isEqualTo(1);
        var history=sql.queryForMap("SELECT * FROM tb_pj_proj_chg WHERE proj_id=?",id);
        assertThat(history.get("bfr_val")).isEqualTo("budgetAmount=80,budgetManual=N");assertThat(history.get("aft_val")).isEqualTo("budgetAmount=80,budgetManual=Y");
        assertThat(history.get("reg_user_id")).isEqualTo("admin");var saved=project();
        assertThat(service.saveProjectBudget(id,request("80")).changed()).isFalse();assertThat(project()).isEqualTo(saved);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_proj_chg WHERE proj_id=?",Long.class,id)).isEqualTo(1);
    }
    @Test void zeroPersistsAndRoundTripsWithSameFinalRevision() {
        var result=service.saveProjectBudget(id,request("0"));assertThat(result.value().budgetAmount()).isEqualTo("0");
        assertThat(service.getProjectBudget(id).revision()).isEqualTo(result.revision());assertThat(project().get("bdgt_manual_yn")).isEqualTo("Y");
    }
    @Test void mandatoryAuditFailureRollsBackBudgetFlagTimestampAndContractHistory() throws Exception {
        var before=project();var request=request("0");
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_budget_history CHECK(trgt_tbl_nm <> 'TB_PJ_PROJ')");
        mvc.perform(put("/api/projects/"+id+"/budget").contentType("application/json").content(json.createObjectNode().put("year",2080).put("expectedRevision",request.expectedRevision()).put("budgetAmount","0").toString()))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertThat(project()).isEqualTo(before);assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_proj_chg WHERE proj_id=?",Long.class,id)).isZero();
    }
    @Test void fullSnapshotRetainsNullAndPrecisionAndDetectsChildAndBodyChanges() throws Exception {
        String original=service.getProjectBudget(id).revision();
        var snapshot=json.readTree(repository.snapshot(id));assertThat(snapshot.get("project").get("lose_dt").isNull()).isTrue();
        sql.update("UPDATE tb_pj_proj SET mod_dttm=timestamp '2080-07-15 01:02:03.123456' WHERE proj_id=?",id);
        assertThat(repository.snapshot(id)).contains("123456");assertThat(service.getProjectBudget(id).revision()).isNotEqualTo(original);
        var request=request("0");
        sql.update("INSERT INTO tb_pj_proj_part(proj_id,aply_bgn_ym,part_id,reg_user_id) VALUES (?,'208001',NULL,'admin')",id);
        assertThatThrownBy(()->service.saveProjectBudget(id,request)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));
    }
    @Test void invalidMoneyHttpPreservesEveryStoredField() throws Exception {
        var before=project();String revision=service.getProjectBudget(id).revision();
        for(String amount:List.of("-1","0.1")) mvc.perform(put("/api/projects/"+id+"/budget").contentType("application/json").content(json.createObjectNode().put("year",2080).put("expectedRevision",revision).put("budgetAmount",amount).toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("budgetAmount"));
        assertThat(project()).isEqualTo(before);assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_proj_chg WHERE proj_id=?",Long.class,id)).isZero();
    }
}
