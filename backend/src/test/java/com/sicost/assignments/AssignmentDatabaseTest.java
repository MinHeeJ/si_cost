package com.sicost.assignments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.assignmentsops.*;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.*;
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
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class AssignmentDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;MockMvc mvc;ObjectMapper json;
    String project,developer,part;
    @Configuration @EnableTransactionManagement
    static class Transactions {@Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}}
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(AssignmentActionsMapper.class,()->session.getMapper(AssignmentActionsMapper.class));context.registerBean(AssignmentMapper.class,()->session.getMapper(AssignmentMapper.class));context.registerBean(AssignmentMonthlyMapper.class,()->session.getMapper(AssignmentMonthlyMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));context.registerBean(MasterIdMapper.class,()->session.getMapper(MasterIdMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,CommitInvalidation.class,MasterIdService.class,AssignmentMonthlyService.class,AssignmentActionsRepository.class,AssignmentActionsService.class,AssignmentRepository.class,AssignmentService.class);context.refresh();
        mvc=MockMvcBuilders.standaloneSetup(new AssignmentController(context.getBean(AssignmentService.class))).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        String key=UUID.randomUUID().toString().replace("-","").substring(0,12);project="P-"+key;developer="D-"+key;part="T-"+key;
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,reg_user_id) VALUES (?,?,'admin')",part,part);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2080-01-01','2081-12-31',100,100,0,0,'admin')",project,project);
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FT','BE','DRCT',?,'IDLE','admin')",developer,developer,"Ab_% "+developer,part);
    }
    @AfterEach void close(){if(context!=null)context.close();}
    ObjectNode create(){return json.createObjectNode().put("year",2080).put("projectId",project).put("developerId",developer).put("jobCode","BE").put("startDate","2080-07-01").put("endDate","2080-08-31").put("contractMm","1.00").put("billingUnitPrice","101");}
    String save(ObjectNode body) throws Exception {var result=mvc.perform(post("/api/assignments").contentType("application/json").content(body.toString())).andExpect(status().isCreated()).andReturn();return json.readTree(result.getResponse().getContentAsString()).path("data").path("assignment").path("id").asText();}
    List<Map<String,Object>> all(String table){return sql.queryForList("SELECT * FROM "+table);}
    @Test void createReadbackKeepsSourceInputMonthlyIdentityAndOtherSourcesUntouched() throws Exception {
        var developers=all("tb_hr_dev");var prices=all("tb_hr_dev_prc");var states=all("tb_hr_dev_stat");var parts=all("tb_pj_proj_part");var distribution=all("tb_pj_tk_dstrb");
        String id=save(create());assertThat(id).startsWith("A-").hasSizeLessThanOrEqualTo(20);
        mvc.perform(get("/api/assignments/"+id).param("year","2080")).andExpect(status().isOk()).andExpect(jsonPath("$.data.yearMm").value("2.00")).andExpect(jsonPath("$.data.revision").isNotEmpty());
        assertThat(sql.queryForObject("SELECT reg_user_id FROM tb_pj_asgn WHERE asgn_id=?",String.class,id)).isEqualTo("admin");
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_asgn_hist WHERE asgn_id=? AND asgn_chg_type_cd='INPUT' AND hist_sn=1 AND new_uprc=101 AND new_ctrt_mm=1",Integer.class,id)).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_asgn_mon WHERE asgn_id=? AND dev_id=? AND proj_id=? AND mon_mm=1 AND sales_amt=101",Integer.class,id,developer,project)).isEqualTo(2);
        assertThat(all("tb_hr_dev")).isEqualTo(developers);assertThat(all("tb_hr_dev_prc")).isEqualTo(prices);assertThat(all("tb_hr_dev_stat")).isEqualTo(states);assertThat(all("tb_pj_proj_part")).isEqualTo(parts);assertThat(all("tb_pj_tk_dstrb")).isEqualTo(distribution);
        for(String table:List.of("tb_bl_bill","tb_bl_rcpt","tb_co_prev_adj","tb_sl_tent_asgn","tb_sl_rcmd_log"))assertThat(all(table)).isEmpty();
    }
    @Test void listLiteralSearchFilteringCountAndReadOnlySnapshotsAreConsistent() throws Exception {
        String id=save(create());var source=all("tb_pj_asgn");var history=all("tb_pj_asgn_hist");var monthly=all("tb_pj_asgn_mon");var audit=all("tb_sy_chg_hist");
        mvc.perform(get("/api/assignments").param("year","2080").param("projectId",project).param("q"," aB_% ")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value(id));
        mvc.perform(get("/api/assignments").param("year","2080").param("q","not-present")).andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/assignments/"+id).param("year","2080")).andExpect(status().isOk());
        assertThat(all("tb_pj_asgn")).isEqualTo(source);assertThat(all("tb_pj_asgn_hist")).isEqualTo(history);assertThat(all("tb_pj_asgn_mon")).isEqualTo(monthly);assertThat(all("tb_sy_chg_hist")).isEqualTo(audit);
    }
    @Test void duplicateAndHistoricalPostLeaveEveryRowUnchanged() throws Exception {
        save(create());var source=all("tb_pj_asgn");var history=all("tb_pj_asgn_hist");var monthly=all("tb_pj_asgn_mon");var audit=all("tb_sy_chg_hist");
        mvc.perform(post("/api/assignments").contentType("application/json").content(create().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_ASSIGNMENT"));
        mvc.perform(post("/api/assignments").contentType("application/json").content(create().put("year",2079).toString())).andExpect(status().isConflict());
        assertThat(all("tb_pj_asgn")).isEqualTo(source);assertThat(all("tb_pj_asgn_hist")).isEqualTo(history);assertThat(all("tb_pj_asgn_mon")).isEqualTo(monthly);assertThat(all("tb_sy_chg_hist")).isEqualTo(audit);
    }
    @Test void requiredHistoryFailureRollsBackEntireNewAssignment() throws Exception {
        sql.execute("ALTER TABLE tb_pj_asgn_hist ADD CONSTRAINT reject_input CHECK(asgn_chg_type_cd <> 'INPUT')");
        mvc.perform(post("/api/assignments").contentType("application/json").content(create().toString())).andExpect(status().isInternalServerError());
        assertThat(all("tb_pj_asgn")).isEmpty();assertThat(all("tb_pj_asgn_hist")).isEmpty();assertThat(all("tb_pj_asgn_mon")).isEmpty();assertThat(all("tb_sy_chg_hist")).isEmpty();
    }
    @Test void requiredAuditFailureRollsBackSourceInputAndMonths() throws Exception {
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_assignment CHECK(trgt_tbl_nm <> 'TB_PJ_ASGN')");
        mvc.perform(post("/api/assignments").contentType("application/json").content(create().toString())).andExpect(status().isInternalServerError());
        assertThat(all("tb_pj_asgn")).isEmpty();assertThat(all("tb_pj_asgn_hist")).isEmpty();assertThat(all("tb_pj_asgn_mon")).isEmpty();
    }
    @Test void sameNewAssignmentRaceOnlyOneCommits() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<Integer> work=()->{start.await();return mvc.perform(post("/api/assignments").contentType("application/json").content(create().toString())).andReturn().getResponse().getStatus();};
            Future<Integer> a=pool.submit(work),b=pool.submit(work);start.countDown();assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
            assertThat(all("tb_pj_asgn")).hasSize(1);assertThat(all("tb_pj_asgn_hist")).hasSize(1);assertThat(all("tb_pj_asgn_mon")).hasSize(2);
        } finally {pool.shutdownNow();}
    }
}
