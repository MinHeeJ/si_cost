package com.sicost.projects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.budget.*;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.common.projects.*;
import com.sicost.distribution.*;
import com.sicost.plans.*;
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
import static com.sicost.projects.ProjectDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class ProjectDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    ObjectMapper json;
    MockMvc mvc;
    ProjectService service;
    ProjectQueryService queries;
    String customer,part;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(ProjectMapper.class,()->session.getMapper(ProjectMapper.class));
        context.registerBean(ProjectBudgetMapper.class,()->session.getMapper(ProjectBudgetMapper.class));
        context.registerBean(ProjectRegistrationMapper.class,()->session.getMapper(ProjectRegistrationMapper.class));
        context.registerBean(ProjectPlanMapper.class,()->session.getMapper(ProjectPlanMapper.class));
        context.registerBean(ProjectDistributionMapper.class,()->session.getMapper(ProjectDistributionMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,WriteCoordinator.class,CommitInvalidation.class,
            ProjectBudgetRepository.class,ProjectRegistrationService.class,ProjectPlanRepository.class,ProjectPlanService.class,
            ProjectDistributionRepository.class,ProjectDistributionService.class,ProjectRepository.class,ProjectService.class,ProjectQueryService.class);
        context.refresh();service=context.getBean(ProjectService.class);queries=context.getBean(ProjectQueryService.class);
        mvc=MockMvcBuilders.standaloneSetup(new ProjectController(queries,service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        customer="C-"+UUID.randomUUID().toString().substring(0,12);part="S-"+UUID.randomUUID().toString().substring(0,12);
        sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,use_yn,reg_user_id) VALUES (?,'CUST',?,'112-34-56789','Y','admin')",customer,"고객 "+customer);
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES (?,?,'N','Y','admin')",part,"파트 "+part);
        sql.update("INSERT INTO tb_sy_oper_std(std_cd,aply_bgn_ym,std_nm,std_val,unit_nm,reg_user_id) VALUES ('TGT_MRGN_RT','208007','프로젝트 목표율',20,'%', 'admin')");
    }
    @AfterEach void close() {if(context!=null)context.close();}
    ObjectNode input() {return json.createObjectNode().put("year",2080).put("name","Project %_ A").put("contractType","TK").put("customerId",customer).put("partId",part).put("startDate","2080-01-01").put("endDate","2081-12-31").put("contractAmount","100").put("teamExpenseAmount","0").put("projectExpenseAmount","0");}
    SaveResult create() {return service.createProject(ProjectInput.create(input()));}
    ObjectNode edit(SaveResult result) {ObjectNode body=input();body.remove(List.of("contractType","partId"));return body.put("expectedRevision",queries.getProject(result.project().id(),false).revision());}
    long count(String table) {return sql.queryForObject("SELECT count(*) FROM "+table,Long.class);}
    @Test void publicRegistrationRoundTripsRealIdInitialPartContractEventAndAdmin() throws Exception {
        String text=mvc.perform(post("/api/projects").contentType("application/json").content(input().put("contractAmount","0").toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id=json.readTree(text).path("data").path("project").path("id").asText();
        mvc.perform(get("/api/projects/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.project.contractAmount").value("0")).andExpect(jsonPath("$.data.project.originalContractAmount").value("0")).andExpect(jsonPath("$.data.parts[0].effectiveMonth").value("2080-01")).andExpect(jsonPath("$.data.history[0].itemCode").value("CTRT"));
        var p=sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?",id);assertThat(p.get("reg_user_id")).isEqualTo("admin");assertThat(p.get("opp_id")).isNull();assertThat(p.get("lose_dt")).isNull();
        var event=sql.queryForMap("SELECT * FROM tb_pj_proj_chg WHERE proj_id=?",id);assertThat(event.get("bfr_val")).isNull();assertThat(event.get("aft_val")).isEqualTo("0");
    }
    @Test void contractHistoryFailureRollsBackBodyPartAndAudit() throws Exception {
        long body=count("tb_pj_proj"),parts=count("tb_pj_proj_part"),audits=count("tb_sy_chg_hist");
        sql.execute("ALTER TABLE tb_pj_proj_chg ADD CONSTRAINT reject_project_contract CHECK(chg_item_cd <> 'CTRT')");
        mvc.perform(post("/api/projects").contentType("application/json").content(input().toString())).andExpect(status().isInternalServerError());
        assertThat(count("tb_pj_proj")).isEqualTo(body);assertThat(count("tb_pj_proj_part")).isEqualTo(parts);assertThat(count("tb_sy_chg_hist")).isEqualTo(audits);
    }
    @Test void updateHistoryFailureRollsBackBodyAuditAndRevision() throws Exception {
        var result=create();var before=queries.getProject(result.project().id(),false);long events=count("tb_pj_proj_chg");
        sql.execute("ALTER TABLE tb_pj_proj_chg ADD CONSTRAINT reject_project_update CHECK(chg_item_cd <> 'AMT')");
        mvc.perform(put("/api/projects/"+result.project().id()).contentType("application/json").content(edit(result).put("contractAmount","200").toString())).andExpect(status().isInternalServerError());
        assertThat(queries.getProject(result.project().id(),false)).isEqualTo(before);assertThat(count("tb_pj_proj_chg")).isEqualTo(events);
    }
    @Test void realFilteredItemsCountUnassignedCountAndLiteralCaseSensitiveSearchAgree() {
        var first=create();var second=create();sql.update("UPDATE tb_pj_proj_part SET part_id=NULL WHERE proj_id=?",first.project().id());
        var f=ProjectInput.filter(Map.of("year","2080","q","%_","partId",part));var page=queries.getProjects(f);
        assertThat(page.items()).extracting(ProjectListItem::id).containsExactly(second.project().id());assertThat(page.total()).isEqualTo(1);assertThat(page.unassignedCount()).isEqualTo(1);
        assertThat(queries.getProjects(ProjectInput.filter(Map.of("year","2080","q","project"))).items()).isEmpty();
        assertThat(queries.getProjects(ProjectInput.filter(Map.of("year","2079"))).items()).isEmpty();
    }
    @Test void readsNeverTouchBodyHistoryAccountingAndElapsedLoseStatusWins() {
        var result=create();String id=result.project().id();sql.update("UPDATE tb_pj_proj SET ctrt_end_dt='2080-07-14' WHERE proj_id=?",id);
        var before=sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?",id);long events=count("tb_pj_proj_chg"),audits=count("tb_sy_chg_hist");
        assertThat(queries.getProject(id,false).effectiveStatus()).isEqualTo("END");assertThat(queries.getProjects(ProjectInput.filter(Map.of("year","2080","status","END"))).items()).extracting(ProjectListItem::id).contains(id);
        assertThat(sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?",id)).isEqualTo(before);assertThat(count("tb_pj_proj_chg")).isEqualTo(events);assertThat(count("tb_sy_chg_hist")).isEqualTo(audits);assertThat(count("tb_co_mon_cost")).isZero();
        sql.update("UPDATE tb_pj_proj SET proj_stat_cd='LOSE' WHERE proj_id=?",id);assertThat(queries.getProject(id,false).effectiveStatus()).isEqualTo("LOSE");
    }
    @Test void currentPartDoesNotApplyFutureReservationAndUsesFirstFallback() {
        var result=create();String id=result.project().id();sql.update("INSERT INTO tb_pj_proj_part(proj_id,aply_bgn_ym,part_id,reg_user_id) VALUES (?,'208008',NULL,'admin')",id);
        var page=queries.getProjects(ProjectInput.filter(Map.of("year","2080")));assertThat(page.items().get(0).partName()).isEqualTo("파트 "+part);
        assertThat(queries.getProject(id,false).parts()).extracting(ProjectPart::scheduled).containsExactly(false,true);
        var future=service.createProject(ProjectInput.create(input().put("startDate","2081-01-01")));
        assertThat(queries.getProjects(ProjectInput.filter(Map.of("year","2081"))).items()).filteredOn(p->p.id().equals(future.project().id())).extracting(ProjectListItem::partName).containsExactly("파트 "+part);
    }
    @Test void realAmountUpdateChangesOriginalOneAmtAndNoOpKeepsTimestamps() {
        var result=create();String id=result.project().id();var updated=service.putProject(id,ProjectInput.update(edit(result).put("contractAmount","200")));
        assertThat(updated.project().contractAmount()).isEqualTo("200");assertThat(updated.project().originalContractAmount()).isEqualTo("200");
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_proj_chg WHERE proj_id=? AND chg_item_cd='AMT'",Long.class,id)).isEqualTo(1);
        var stored=sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?",id);long events=count("tb_pj_proj_chg");
        assertThat(service.putProject(id,ProjectInput.update(edit(updated).put("contractAmount","200"))).changed()).isFalse();
        assertThat(sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?",id)).isEqualTo(stored);assertThat(count("tb_pj_proj_chg")).isEqualTo(events);
    }
    @Test void allStagePeriodUseBlocksShrinkAndKeepsDistributionUntouched() throws Exception {
        var result=create();String id=result.project().id();sql.update("INSERT INTO tb_pj_tk_dstrb(proj_id,dstrb_step_cd,row_sn,dstrb_ym,row_type_cd,item_nm,dstrb_amt,reg_user_id) VALUES (?,'EST',1,'208001','ITEM','검증 항목',0,'admin')",id);
        var body=edit(result).put("startDate","2080-02-01");var before=queries.getProject(id,false);
        mvc.perform(put("/api/projects/"+id).contentType("application/json").content(body.toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_PERIOD_IN_USE"));
        assertThat(queries.getProject(id,false)).isEqualTo(before);assertThat(count("tb_pj_tk_dstrb")).isEqualTo(1);
    }
    @Test void closedMonthDayChangeRollsBackWithoutAutomaticTruncation() throws Exception {
        var result=create();String id=result.project().id();sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('208001','CLOSE','admin')");
        var before=queries.getProject(id,false);mvc.perform(put("/api/projects/"+id).contentType("application/json").content(edit(result).put("startDate","2080-01-02").toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));assertThat(queries.getProject(id,false)).isEqualTo(before);
    }
}
