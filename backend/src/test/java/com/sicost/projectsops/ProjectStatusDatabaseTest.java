package com.sicost.projectsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.budget.*;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.shared.SharedQueriesMapper;
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
import static com.sicost.projectsops.ProjectStatusDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class ProjectStatusDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql; ProjectStatusService service; ProjectStatusRepository repository; ObjectMapper json; MockMvc mvc;
    String id,developer,part;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(ProjectStatusMapper.class,()->session.getMapper(ProjectStatusMapper.class));
        context.registerBean(ProjectBudgetMapper.class,()->session.getMapper(ProjectBudgetMapper.class));
        context.registerBean(SharedQueriesMapper.class,()->session.getMapper(SharedQueriesMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,WriteCoordinator.class,CommitInvalidation.class,
            ProjectBudgetRepository.class,ProjectStatusRepository.class,ProjectStatusService.class);context.refresh();
        service=context.getBean(ProjectStatusService.class);repository=context.getBean(ProjectStatusRepository.class);
        mvc=MockMvcBuilders.standaloneSetup(new ProjectStatusController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        String key=UUID.randomUUID().toString().replace("-","").substring(0,14);id="P-"+key;developer="D-"+key;part="T-"+key;
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'TK','PRE','2080-01-01','2081-12-31',101,80,0,0,'admin')",id,"상태 "+key);
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES (?,?,'N','Y','admin')",part,part);
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FT','DEV','DRCT',?,'IDLE','admin')",developer,"M"+key,developer,part);
        sql.update("INSERT INTO tb_sy_oper_std(std_cd,aply_bgn_ym,std_nm,std_val,unit_nm,reg_user_id) VALUES ('TGT_MRGN_RT','208007','목표',20,'%','admin')");
        sql.update("INSERT INTO tb_sy_code(code_grp_id,code,code_nm,sort_ord,use_yn,del_yn,reg_user_id) VALUES ('LOSE_RSN','TESTLOSS','시험사유',999,'Y','N','admin')");
    }
    @AfterEach void close(){if(context!=null)context.close();}
    String revision(){return context.getBean(RevisionService.class).revision(repository.snapshot(id));}
    Map<String,Object> project(){return sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?",id);}
    String assignment(String suffix,String start,String end) {
        String aid=id+suffix;sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,bill_uprc,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES (?,?,?,'DEV',CAST(? AS date),CAST(? AS date),1,100,'NORMAL',0,'ACT','admin')",aid,id,developer,start,end);return aid;
    }
    Loss loss(Preview preview){return new Loss(2080,preview.revision(),"TESTLOSS",null,preview.previewToken());}
    @Test void readsUseRealDatabaseAndNeverChangeProjectOrHistory() throws Exception {
        assignment("A","2080-07-01","2080-07-31");var before=project();long events=sql.queryForObject("SELECT count(*) FROM tb_pj_proj_chg",Long.class);
        mvc.perform(get("/api/projects/defaults").param("contractAmount","101").param("contractType","TK")).andExpect(status().isOk()).andExpect(jsonPath("$.data.budgetAmount").value("80"));
        mvc.perform(get("/api/projects/options").param("purpose","BILLING").param("q",id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(id));
        mvc.perform(get("/api/projects/"+id+"/loss-preview")).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentAssignments[0].developerId").value(developer));
        assertThat(project()).isEqualTo(before);assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_proj_chg",Long.class)).isEqualTo(events);
    }
    @Test void progressOnlyChangesStateAndOneContractEvent() {
        var before=project();service.progress(id,new Progress(2080,revision()));var after=project();
        for(String field:List.of("ctrt_amt","bdgt_amt","orgnl_ctrt_amt","opp_id"))assertThat(after.get(field)).isEqualTo(before.get(field));
        assertThat(after.get("proj_stat_cd")).isEqualTo("ING");assertThat(sql.queryForMap("SELECT chg_item_cd,chg_item_nm,bfr_val,aft_val FROM tb_pj_proj_chg WHERE proj_id=?",id)).isEqualTo(Map.of("chg_item_cd","ETC","chg_item_nm","상태","bfr_val","PRE","aft_val","ING"));
    }
    @Test void lossKeepsProtectedRowsAndRebuildsOnlyOpenMonthsWithCurrentEarlyAndFutureCancel() {
        String current=assignment("A","2080-06-01","2080-07-15"),future=assignment("B","2080-08-01","2080-08-31"),past=assignment("C","2080-06-01","2080-06-30");
        sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('208006','CLOSE','admin')");
        for(String aid:List.of(current,future))for(String month:List.of("208006","208007","208008"))
            sql.update("INSERT INTO tb_pj_asgn_mon(asgn_id,input_ym,dev_id,proj_id,input_day_cnt,mon_mm,aply_uprc,sales_amt,fix_yn,reg_user_id) VALUES (?,?,?,?,30,1,100,100,?,'admin')",aid,month,developer,id,month.equals("208008")&&aid.equals(current)?"Y":"N");
        var fixed=sql.queryForMap("SELECT * FROM tb_pj_asgn_mon WHERE asgn_id=? AND input_ym='208008'",current);
        var closed=sql.queryForList("SELECT * FROM tb_pj_asgn_mon WHERE input_ym='208006' AND proj_id=? ORDER BY asgn_id",id);
        var preview=service.preview(id);assertThat(preview.currentPersonCount()).isEqualTo(1);assertThat(preview.futurePersonCount()).isEqualTo(1);
        service.loss(id,loss(preview));assertThat(project().get("proj_stat_cd")).isEqualTo("LOSE");
        assertThat(sql.queryForObject("SELECT asgn_stat_cd FROM tb_pj_asgn WHERE asgn_id=?",String.class,future)).isEqualTo("CNCL");
        assertThat(sql.queryForObject("SELECT early_wthdr_yn FROM tb_pj_asgn WHERE asgn_id=?",String.class,current)).isEqualTo("Y");
        assertThat(sql.queryForObject("SELECT asgn_stat_cd FROM tb_pj_asgn WHERE asgn_id=?",String.class,past)).isEqualTo("ACT");
        assertThat(sql.queryForObject("SELECT mon_mm::text FROM tb_pj_asgn_mon WHERE asgn_id=? AND input_ym='208007'",String.class,current)).isEqualTo("0.48");
        assertThat(sql.queryForObject("SELECT sales_amt::text FROM tb_pj_asgn_mon WHERE asgn_id=? AND input_ym='208007'",String.class,current)).isEqualTo("0");
        assertThat(sql.queryForMap("SELECT * FROM tb_pj_asgn_mon WHERE asgn_id=? AND input_ym='208008'",current)).isEqualTo(fixed);
        assertThat(sql.queryForList("SELECT * FROM tb_pj_asgn_mon WHERE input_ym='208006' AND proj_id=? ORDER BY asgn_id",id)).isEqualTo(closed);
        assertThat(sql.queryForObject("SELECT close_cross_yn FROM tb_pj_asgn_hist WHERE asgn_id=?",String.class,current)).isEqualTo("Y");
        assertThat(service.options("ASSIGNMENT",id).items()).isEmpty();
        assertThatThrownBy(()->service.loss(id,loss(preview))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("INVALID_PROJECT_STATUS"));
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_asgn_hist WHERE asgn_id IN (?,?)",Long.class,current,future)).isEqualTo(2);
    }
    @Test void snapshotMembershipChangeNeverSilentlyProcessesNewAssignments() {
        var preview=service.preview(id);assignment("A","2080-07-01","2080-08-01");var before=project();
        assertThatThrownBy(()->service.loss(id,loss(preview))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));
        assertThat(project()).isEqualTo(before);assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_asgn_hist WHERE asgn_id LIKE ?",Long.class,id+"%")).isZero();
    }
    @Test void mandatoryStatusHistoryFailureRollsBackAssignmentsMonthsAndProject() {
        String aid=assignment("A","2080-07-01","2080-08-31");var preview=service.preview(id);var before=project();
        var assignment=sql.queryForMap("SELECT * FROM tb_pj_asgn WHERE asgn_id=?",aid);
        sql.execute("ALTER TABLE tb_pj_proj_chg ADD CONSTRAINT reject_status CHECK(chg_item_nm <> '상태')");
        assertThatThrownBy(()->service.loss(id,loss(preview))).isInstanceOf(RuntimeException.class);
        assertThat(project()).isEqualTo(before);assertThat(sql.queryForMap("SELECT * FROM tb_pj_asgn WHERE asgn_id=?",aid)).isEqualTo(assignment);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_asgn_hist WHERE asgn_id=?",Long.class,aid)).isZero();
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_asgn_mon WHERE asgn_id=?",Long.class,aid)).isZero();
    }
}
