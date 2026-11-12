package com.sicost.uploadsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.b9.*;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.common.projects.ProjectRegistrationService;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class UploadResultDatabaseTest {
    PostgreSqlDatabase db;
    AnnotationConfigApplicationContext context;
    UploadResultService service;
    MockMvc mvc;
    @Configuration @EnableTransactionManagement static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var config=new org.apache.ibatis.session.Configuration();
        config.setArgNameBasedConstructorAutoMapping(true);config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        ObjectMapper json=JsonMapper.builder().findAndAddModules().build();
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(UploadResultMapper.class,()->session.getMapper(UploadResultMapper.class));
        context.registerBean(B9Mapper.class,()->session.getMapper(B9Mapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryWriter.class,()->mock(ChangeHistoryWriter.class));
        context.registerBean(ProjectRegistrationService.class,()->mock(ProjectRegistrationService.class));
        context.registerBean(AggregateLockMapper.class,()->mock(AggregateLockMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,RevisionService.class,B9Repository.class,
            UploadResultRepository.class,UploadResultService.class);
        context.refresh();service=context.getBean(UploadResultService.class);
        mvc=MockMvcBuilders.standaloneSetup(new UploadResultController(service,json))
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
    }
    @AfterEach void close() {if(context!=null)context.close();}
    long upload(String form,String status,int rows,String step) {
        long id=db.sql.queryForObject("INSERT INTO tb_sy_upld(form_type_cd,scrn_id,file_nm,file_size,trgt_proj_id,trgt_dstrb_step_cd,tot_row_cnt,succ_cnt,warn_cnt,err_cnt,upld_stat_cd,aply_dttm,reg_user_id) VALUES(?,'UI-01','결과.xlsx',100,?,?,?,?,0,0,?,CASE WHEN ?='APPLIED' THEN TIMESTAMP '2026-06-15 09:00:00' ELSE NULL END,'admin') RETURNING upld_id",
            Long.class,form,form.equals("B6")?"P-durable":null,step,rows,rows,status,status);
        db.sql.update("INSERT INTO tb_sy_chg_hist(trgt_tbl_nm,trgt_key,chg_type_cd,chg_item_nm,aft_val,chg_rsn,reg_user_id) VALUES('TB_SY_UPLD',?,'NEW','검증 문맥',?,?, 'admin')",
            Long.toString(id),"{\"year\":2026,\"contentSha256\":\"stored-byte-digest\"}",form);
        return id;
    }
    void receipt(long id,int row,String project,String name) throws Exception {
        String json=context.getBean(ObjectMapper.class).writeValueAsString(Map.of("id",project,"name",name));
        db.sql.update("INSERT INTO tb_sy_chg_hist(trgt_tbl_nm,trgt_key,chg_type_cd,chg_item_nm,aft_val,chg_rsn,reg_user_id) VALUES('TB_PJ_PROJ',?,'NEW','등록',?,?,'admin')",
            project,json,"B9 업로드:"+id+";행:"+row);
    }
    void issue(long id,int sequence,String category,String severity) {
        db.sql.update("INSERT INTO tb_sy_upld_err(upld_id,rslt_sn,rslt_type_cd,row_no,col_nm,rsn_cn,reg_user_id) VALUES(?,?,?,1,?,'저장된 진단','admin')",id,sequence,severity,category);
    }
    Map<String,List<Map<String,Object>>> state() {
        var state=new TreeMap<String,List<Map<String,Object>>>();
        for(String table:List.of("tb_sy_upld","tb_sy_upld_err","tb_sy_chg_hist","tb_sy_accs_log","tb_pj_proj","tb_pj_proj_chg","tb_pj_proj_part","tb_pj_mnpw_plan","tb_pj_drct_exps","tb_pj_tk_dstrb","tb_pj_asgn","tb_pj_asgn_mon","tb_pj_asgn_hist","tb_co_mon_cost","tb_co_mon_pl"))
            state.put(table,db.sql.queryForList("SELECT * FROM "+table+" ORDER BY 1,2"));
        return state;
    }
    @Test void b9FullAuditSourceRemainsOriginalAcrossRenameOtherUploadsAndRepeatedGetsWithoutAnyWrites() throws Exception {
        long id=upload("B9","APPLIED",2,null);
        receipt(id,11,"P-final-b","최초 이름 B");receipt(id,3,"P-final-a","최초 이름 A");
        issue(id,1,"[파일]","WARN");issue(id,2,"[표]","WARN");issue(id,3,"[표]","ERR");
        var before=state();
        String first=mvc.perform(get("/api/uploads/"+id+"/result")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.results[0].rowNumber").value(3))
            .andExpect(jsonPath("$.data.results[0].id").value("P-final-a"))
            .andExpect(jsonPath("$.data.results[0].name").value("최초 이름 A"))
            .andExpect(jsonPath("$.data.summary.fileWarningCount").value(1))
            .andExpect(jsonPath("$.data.summary.tableWarningCount").value(1))
            .andReturn().getResponse().getContentAsString();
        assertThat(state()).isEqualTo(before);
        long later=upload("B9","APPLIED",1,null);receipt(later,3,"P-other","다른 업로드");
        db.sql.update("INSERT INTO tb_sy_chg_hist(trgt_tbl_nm,trgt_key,chg_type_cd,chg_item_nm,aft_val,reg_user_id) VALUES('TB_PJ_PROJ','P-final-a','UPD','이름','나중 이름','admin')");
        before=state();
        assertThat(mvc.perform(get("/api/uploads/"+id+"/result")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).isEqualTo(first);
        assertThat(state()).isEqualTo(before);
    }
    @Test void b6OriginalStageSummaryAndRevisionSurviveLaterUploadAndProjectChanges() throws Exception {
        long id=upload("B6","APPLIED",1,"EST");issue(id,1,"[파일]","WARN");issue(id,2,"[표]","WARN");
        var original=service.get(id);upload("B6","APPLIED",2,"PLAN");
        var before=state();
        var recovered=service.get(id);
        assertThat(recovered).isEqualTo(original);assertThat(recovered.projectId()).isEqualTo("P-durable");
        assertThat(recovered.step()).isEqualTo("EST");assertThat(recovered.results()).isEmpty();
        assertThat(recovered.summary().fileWarningCount()).isEqualTo(1);assertThat(recovered.summary().tableWarningCount()).isEqualTo(1);
        assertThat(recovered.revision()).isNotBlank();assertThat(state()).isEqualTo(before);
    }
    @Test void checkedAndEmptyActualSourceReturnNoInventedResult() throws Exception {
        long id=upload("B9","CHECKED",0,null);var before=state();
        mvc.perform(get("/api/uploads/"+id+"/result")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.results").isEmpty()).andExpect(jsonPath("$.data.changed").value(false))
            .andExpect(jsonPath("$.data.revision").isEmpty()).andExpect(jsonPath("$.data.summary.totalRows").value(0));
        assertThat(state()).isEqualTo(before);
    }
    @Test void missingAndLegacyUploadsAre404WithoutChangingLegacyContracts() throws Exception {
        long id=upload("B7","CHECKED",0,null);var before=state();
        mvc.perform(get("/api/uploads/"+id+"/result")).andExpect(status().isNotFound());
        mvc.perform(get("/api/uploads/999999999999/result")).andExpect(status().isNotFound());
        assertThat(state()).isEqualTo(before);
    }
    @Test void rejectedQueriesAndBodiesLeaveEveryBusinessAuditAndAccountingRowUnchanged() throws Exception {
        long id=upload("B6","CHECKED",0,"PLAN");var before=state();
        mvc.perform(get("/api/uploads/"+id+"/result").param("year","2026"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("year"));
        mvc.perform(get("/api/uploads/"+id+"/result").contentType("application/json").content("{\"actor\":\"other\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(state()).isEqualTo(before);
    }
}
