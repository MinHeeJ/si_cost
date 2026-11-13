package com.sicost.assignmentsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.*;
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
class AssignmentActionsDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;MockMvc mvc;ObjectMapper json;AssignmentActionsRepository repository;
    String project,developer,part,id,reason;
    @Configuration @EnableTransactionManagement
    static class Transactions {@Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}}
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(AssignmentActionsMapper.class,()->session.getMapper(AssignmentActionsMapper.class));context.registerBean(AssignmentMonthlyMapper.class,()->session.getMapper(AssignmentMonthlyMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));context.registerBean(MasterIdMapper.class,()->session.getMapper(MasterIdMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,CommitInvalidation.class,MasterIdService.class,AssignmentMonthlyService.class,AssignmentActionsRepository.class,AssignmentActionsService.class);context.refresh();repository=context.getBean(AssignmentActionsRepository.class);
        mvc=MockMvcBuilders.standaloneSetup(new AssignmentActionsController(context.getBean(AssignmentActionsService.class))).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        String key=UUID.randomUUID().toString().replace("-","").substring(0,12);project="P-"+key;developer="D-"+key;part="T-"+key;id="A-"+key;reason="R"+key.substring(0,8);
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,reg_user_id) VALUES (?,?,'admin')",part,part);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2080-01-01','2081-12-31',100,100,0,0,'admin')",project,project);
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FT','DEV','DRCT',?,'IDLE','admin')",developer,developer,developer,part);
        sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,bill_uprc,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES (?,?,?,'DEV','2080-06-01','2080-08-31',1,101,'NEW',0,'ACT','admin')",id,project,developer);
        sql.update("INSERT INTO tb_pj_asgn_hist(asgn_id,hist_sn,asgn_chg_type_cd,aply_dt,new_ctrt_mm,new_uprc,reg_user_id) VALUES (?,1,'INPUT','2080-06-01',1,101,'admin')",id);
        sql.update("INSERT INTO tb_sy_code(code_grp_id,code,code_nm,sort_ord,reg_user_id) VALUES ('ASGN_CHG_RSN',?,'시험사유',999,'admin')",reason);
    }
    @AfterEach void close(){if(context!=null)context.close();}
    com.fasterxml.jackson.databind.node.ObjectNode change(String type){return json.createObjectNode().put("year",2080).put("expectedRevision",repository.revision(id)).put("type",type).put("reasonCode",reason);}
    List<Map<String,Object>> snapshot(String table){return sql.queryForList("SELECT * FROM "+table+" WHERE asgn_id=?",id);}
    @Test void rateTypedHistoryAndReadbackUseActualSqlAndPreserveFixedRows() throws Exception {
        sql.update("INSERT INTO tb_pj_asgn_mon(asgn_id,input_ym,dev_id,proj_id,input_day_cnt,mon_mm,aply_uprc,sales_amt,fix_yn,reg_user_id) VALUES (?,'208006',?,?,30,1,101,101,'Y','admin')",id,developer,project);
        var protectedRow=snapshot("tb_pj_asgn_mon");
        mvc.perform(post("/api/assignments/"+id+"/changes").contentType("application/json").content(change("RATE").put("applyMonth","2080-07").put("newContractMm","0.50").toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        assertThat(sql.queryForObject("SELECT new_ctrt_mm::text FROM tb_pj_asgn_hist WHERE asgn_id=? AND asgn_chg_type_cd='RATE'",String.class,id)).isEqualTo("0.50");
        assertThat(sql.queryForList("SELECT * FROM tb_pj_asgn_mon WHERE asgn_id=? AND input_ym='208006'",id)).isEqualTo(protectedRow);
        assertThat(sql.queryForObject("SELECT sales_amt::text FROM tb_pj_asgn_mon WHERE asgn_id=? AND input_ym='208007'",String.class,id)).isEqualTo("50");
        var before=snapshot("tb_pj_asgn_mon");mvc.perform(get("/api/assignments/"+id+"/months").param("year","2080")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3));assertThat(snapshot("tb_pj_asgn_mon")).isEqualTo(before);
    }
    @Test void auditFailureRollsBackSourceHistoryAndEveryMonth() throws Exception {
        var before=snapshot("tb_pj_asgn");var history=snapshot("tb_pj_asgn_hist");sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_assignment CHECK(trgt_tbl_nm <> 'TB_PJ_ASGN')");
        mvc.perform(post("/api/assignments/"+id+"/changes").contentType("application/json").content(change("EXT").put("newEndDate","2080-09-30").toString())).andExpect(status().isInternalServerError());assertThat(snapshot("tb_pj_asgn")).isEqualTo(before);assertThat(snapshot("tb_pj_asgn_hist")).isEqualTo(history);assertThat(snapshot("tb_pj_asgn_mon")).isEmpty();
    }
    @Test void cancellationRetainsProtectedMonthAndMatchingRevisionReplayCreatesNoHistory() throws Exception {
        sql.update("INSERT INTO tb_pj_asgn_mon(asgn_id,input_ym,dev_id,proj_id,input_day_cnt,mon_mm,aply_uprc,sales_amt,fix_yn,reg_user_id) VALUES (?,'208006',?,?,30,1,101,101,'Y','admin')",id,developer,project);var protectedRow=snapshot("tb_pj_asgn_mon");
        for(int i=0;i<2;i++){var body=json.createObjectNode().put("year",2080).put("type","CNCL").put("reasonCode",reason);body.putArray("items").addObject().put("id",id).put("expectedRevision",repository.revision(id));mvc.perform(post("/api/assignments/bulk").contentType("application/json").content(body.toString())).andExpect(status().isOk()).andExpect(jsonPath(i==0?"$.data.applied":"$.data.noChange").value(1));}
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_asgn_hist WHERE asgn_id=? AND asgn_chg_type_cd='CNCL'",Integer.class,id)).isEqualTo(1);assertThat(snapshot("tb_pj_asgn_mon")).isEqualTo(protectedRow);assertThat(snapshot("tb_pj_asgn")).hasSize(1);
    }
    @Test void selectionUsesCaseInsensitiveLiteralSearchAndExclusionsWithoutWrites() throws Exception {
        sql.update("UPDATE tb_hr_dev SET dev_nm='Ab_%' WHERE dev_id=?",developer);var before=snapshot("tb_pj_asgn");var body=json.createObjectNode().put("year",2080).put("scope","ALL");body.putObject("filters").put("year",2080).put("projectId",project).put("q","aB_%");
        mvc.perform(post("/api/assignments/selection").contentType("application/json").content(body.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));body.putArray("excludedIds").add(id);mvc.perform(post("/api/assignments/selection").contentType("application/json").content(body.toString())).andExpect(jsonPath("$.data.total").value(0));assertThat(snapshot("tb_pj_asgn")).isEqualTo(before);
    }
}
