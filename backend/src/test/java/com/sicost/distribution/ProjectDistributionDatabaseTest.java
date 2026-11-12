package com.sicost.distribution;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class ProjectDistributionDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    MockMvc mvc;
    ProjectDistributionService service;
    com.sicost.b6.B6Mapper importMapper;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();
        configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        importMapper=session.getMapper(com.sicost.b6.B6Mapper.class);
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        ObjectMapper json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(ProjectDistributionMapper.class,()->session.getMapper(ProjectDistributionMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,RevisionService.class,ProjectDistributionRepository.class,ProjectDistributionService.class);
        context.refresh();service=context.getBean(ProjectDistributionService.class);
        mvc=MockMvcBuilders.standaloneSetup(new ProjectDistributionController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES ('P-DIST','배분시험','TK','ING','2026-06-01','2026-07-31',101,0,0,'admin')");
    }
    @AfterEach void close() { if(context!=null)context.close(); }
    void item(String step,int row,String month,String amount) {
        sql.update("INSERT INTO tb_pj_tk_dstrb(proj_id,dstrb_step_cd,row_sn,dstrb_ym,row_type_cd,item_nm,dstrb_amt,reg_user_id) VALUES ('P-DIST',?,?,?,'ITEM','같은 항목',?::numeric,'admin')",step,row,month,amount);
    }
    long upload(String step,String date) {
        return sql.queryForObject("INSERT INTO tb_sy_upld(form_type_cd,scrn_id,file_nm,file_size,trgt_proj_id,trgt_dstrb_step_cd,tot_row_cnt,succ_cnt,warn_cnt,err_cnt,upld_stat_cd,reg_user_id,reg_dttm) VALUES ('B6','UI-01','검사.xlsx',20,'P-DIST',?,1,1,0,0,'CHECKED','admin',?::timestamp) RETURNING upld_id",Long.class,step,date);
    }
    Map<String,Object> snapshot() {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String table:List.of("tb_pj_proj","tb_pj_tk_dstrb","tb_sy_upld","tb_sy_upld_err","tb_sy_chg_hist","tb_pj_proj_chg","tb_pj_asgn","tb_pj_asgn_mon","tb_co_mon_cost"))
            result.put(table,sql.queryForList("SELECT row_to_json(t)::text AS body FROM "+table+" t ORDER BY row_to_json(t)::text"));
        return result;
    }
    @Test void realMatrixPreservesAllZeroStageAndDuplicateItemsAndExactTotals() throws Exception {
        item("EST",1,"202606","100");item("PLAN",3,"202606","0");item("PLAN",4,"202606","0");
        mvc.perform(get("/api/projects/P-DIST/distribution").param("step","PLAN")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rows.length()").value(2)).andExpect(jsonPath("$.data.rows[0].rowSn").value(3))
            .andExpect(jsonPath("$.data.rows[0].cells[0].amount").value("0")).andExpect(jsonPath("$.data.selectedStep").value("PLAN"));
        mvc.perform(get("/api/projects/P-DIST/distribution/comparison")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAmount").value("0"));
        assertThat(service.get("P-DIST","EST").totalAmount()).isEqualTo("100");
    }
    @Test void uploadOrderUsesRegistrationThenIdAndPastStageSurvivesLaterUpload() throws Exception {
        long first=upload("EST","2026-06-14 12:00:00"),second=upload("CTRT","2026-06-15 12:00:00"),third=upload("PLAN","2026-06-15 12:00:00");
        var before=sql.queryForMap("SELECT * FROM tb_sy_upld WHERE upld_id=?",first);
        item("PLAN",1,"202606","0");
        mvc.perform(get("/api/projects/P-DIST/distribution/uploads")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].uploadId").value(third)).andExpect(jsonPath("$.data.items[1].uploadId").value(second))
            .andExpect(jsonPath("$.data.items[2].step").value("EST"));
        assertThat(sql.queryForMap("SELECT * FROM tb_sy_upld WHERE upld_id=?",first)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"","/comparison","/uploads"})
    void everyReadLeavesBusinessHistoryAccountingAndAuditTimestampsUnchanged(String suffix) throws Exception {
        item("EST",1,"202606","100");upload("EST","2026-06-15 12:00:00");var before=snapshot();
        var request=get("/api/projects/P-DIST/distribution"+suffix);if(suffix.isEmpty())request.param("step","EST");
        mvc.perform(request).andExpect(status().isOk());assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"","/comparison","/uploads"})
    void invalidQueryAndMissingTargetNeverChangeStoredValues(String suffix) throws Exception {
        var before=snapshot();var invalid=get("/api/projects/P-DIST/distribution"+suffix).param("month","2026-13");
        if(suffix.isEmpty())invalid.param("step","EST");
        mvc.perform(invalid).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));
        var missing=get("/api/projects/absent/distribution"+suffix);if(suffix.isEmpty())missing.param("step","EST");
        mvc.perform(missing).andExpect(status().isNotFound());assertThat(snapshot()).isEqualTo(before);
    }
    @Test void realPeopleJobsAndMultipleAssignmentsUseOnePersonMonthComparison() throws Exception {
        for(String id:List.of("D-A","D-B","D-C")) {
            sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FT','BE','DRCT','HQ','IDLE','admin')",id,"M-"+id,id);
        }
        for(int i=1;i<=3;i++) {
            String dev=i==1?"D-B":"D-C";
            sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES (?,'P-DIST',?,?,'2025-01-01','2027-01-31',1,'NEW',0,'ACT','admin')","A-"+i,dev,i==2?"PM":"BE");
            sql.update("INSERT INTO tb_pj_asgn_mon(asgn_id,input_ym,dev_id,proj_id,input_day_cnt,mon_mm,sales_amt,reg_user_id) VALUES (?,'202606',?,'P-DIST',20,0.50,0,'admin')","A-"+i,dev);
        }
        sql.update("INSERT INTO tb_pj_tk_dstrb(proj_id,dstrb_step_cd,row_sn,dstrb_ym,row_type_cd,dev_id,dstrb_amt,reg_user_id) VALUES ('P-DIST','PLAN',1,'202606','DEV','D-A',100,'admin'),('P-DIST','PLAN',2,'202606','DEV','D-B',0,'admin')");
        var before=snapshot();
        mvc.perform(get("/api/projects/P-DIST/distribution").param("step","PLAN")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rows[0].jobNames[0]").value("백엔드 개발"));
        mvc.perform(get("/api/projects/P-DIST/distribution/comparison")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mismatchCount").value(2)).andExpect(jsonPath("$.data.items[1].actualMm").value("1.00"));
        assertThat(service.badge("P-DIST").mismatchCount()).isEqualTo(2);assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"project","developer"})
    void mismatchedMonthlyIdentityCannotHideMissingAssignmentInReadOrImport(String mismatch) throws Exception {
        for(String id:List.of("D-VALID","D-OTHER")) {
            sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FT','BE','DRCT','HQ','IDLE','admin')",id,"M-"+id,id);
        }
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES ('P-OTHER','다른 프로젝트','TK','ING','2026-06-01','2026-07-31',0,0,0,'admin')");
        sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES ('A-VALID','P-DIST','D-VALID','BE','2026-06-01','2026-07-31',1,'NEW',0,'ACT','admin')");
        sql.update("INSERT INTO tb_pj_asgn_mon(asgn_id,input_ym,dev_id,proj_id,input_day_cnt,mon_mm,sales_amt,reg_user_id) VALUES ('A-VALID','202606',?,?,20,1,0,'admin')",
            mismatch.equals("developer")?"D-OTHER":"D-VALID",mismatch.equals("project")?"P-OTHER":"P-DIST");
        sql.update("INSERT INTO tb_pj_tk_dstrb(proj_id,dstrb_step_cd,row_sn,dstrb_ym,row_type_cd,dev_id,dstrb_amt,reg_user_id) VALUES ('P-DIST','PLAN',1,'202606','DEV','D-VALID',100,'admin')");
        var before=snapshot();
        mvc.perform(get("/api/projects/P-DIST/distribution/comparison"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.mismatchCount").value(1))
            .andExpect(jsonPath("$.data.items[0].developerId").value("D-VALID"))
            .andExpect(jsonPath("$.data.items[0].type").value("NO_ASSIGNMENT"))
            .andExpect(jsonPath("$.data.items[0].actualMm").value("0.00"));
        assertThat(service.badge("P-DIST").mismatchCount()).isEqualTo(1);
        assertThat(importMapper.actuals("P-DIST")).isEmpty();
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void uploadHistoryExcludesOtherFormsScreensAndProjectsWithoutChangingReceipts() throws Exception {
        long visible=upload("EST","2026-06-14 12:00:00");
        long otherForm=upload("PLAN","2026-06-15 12:00:00");
        long otherScreen=upload("PLAN","2026-06-15 12:00:00");
        long otherProject=upload("PLAN","2026-06-15 12:00:00");
        sql.update("UPDATE tb_sy_upld SET form_type_cd='B9' WHERE upld_id=?",otherForm);
        sql.update("UPDATE tb_sy_upld SET scrn_id='UI-02' WHERE upld_id=?",otherScreen);
        sql.update("UPDATE tb_sy_upld SET trgt_proj_id=NULL WHERE upld_id=?",otherProject);
        var before=snapshot();
        mvc.perform(get("/api/projects/P-DIST/distribution/uploads"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].uploadId").value(visible))
            .andExpect(jsonPath("$.data.items[0].step").value("EST"));
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void noTableAndEffectiveEndAreDerivedWithoutChangingStoredStatus() throws Exception {
        sql.update("UPDATE tb_pj_proj SET ctrt_end_dt='2026-06-14' WHERE proj_id='P-DIST'");var before=snapshot();
        mvc.perform(get("/api/projects/P-DIST/distribution").param("step","EST")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.project.status").value("END")).andExpect(jsonPath("$.data.rows").isEmpty());
        assertThat(service.comparison("P-DIST").selectedStep()).isNull();assertThat(snapshot()).isEqualTo(before);
    }
}
