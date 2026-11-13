package com.sicost.b6;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.b7.ExcelImportService;
import com.sicost.budget.*;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static com.sicost.b6.B6Dtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class DistributionImportDatabaseTest {
    PostgreSqlDatabase db;
    B6Service service;
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        db.sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES('P-TEST','시험 프로젝트','TK','ING','2026-06-01','2026-07-31',100,0,0,'admin')");
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var config=new org.apache.ibatis.session.Configuration();config.addMapper(AggregateLockMapper.class);config.setArgNameBasedConstructorAutoMapping(true);config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);factory.setPlugins(new PersistenceBoundary());factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());var mapper=session.getMapper(B6Mapper.class);
        var users=new CurrentUserProvider(session.getMapper(CurrentUserMapper.class));var clock=Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));
        var json=JsonMapper.builder().findAndAddModules().build();var revisions=new RevisionService(json);
        var history=new ChangeHistoryWriter(session.getMapper(ChangeHistoryMapper.class),users,clock,json);
        var manager=new DataSourceTransactionManager(db.source);var locks=session.getMapper(AggregateLockMapper.class);
        var coordinator=new WriteCoordinator(manager,locks,revisions,new CommitInvalidation(event->{}));
        var projects=new ProjectBudgetRepository(session.getMapper(ProjectBudgetMapper.class));
        service=new B6Service(new B6Repository(mapper,projects,history,json,revisions,locks),mapper,new B6ImportDefinition(new ExcelImportService()),users,coordinator,revisions,clock,manager);
    }
    private Request validateRequest(String step) {return new Request(2026,"P-TEST",step,null,null);}
    private Request applyRequest(String step,Validation checked) {return new Request(2026,"P-TEST",step,checked.uploadId(),checked.revision());}
    private void stage(String step,int row,String name,int june,int july) {
        for(String month:new String[]{"202606","202607"})db.sql.update("INSERT INTO tb_pj_tk_dstrb(proj_id,dstrb_step_cd,row_sn,dstrb_ym,row_type_cd,item_nm,dstrb_amt,reg_user_id) VALUES('P-TEST',?,?,?,'ITEM',?,?,'admin')",step,row,month,name,month.equals("202606")?june:july);
    }
    @Test void checkedMetadataIssuesContextDoNotReplaceAnyBusinessRows() throws Exception {
        stage("EST",1,"이전",7,8);var checked=service.validate(DistributionWorkbook.valid(),validateRequest("PLAN"));
        assertThat(checked.canApply()).isTrue();assertThat(db.count("tb_pj_tk_dstrb")).isEqualTo(2);
        var row=db.sql.queryForMap("SELECT * FROM tb_sy_upld WHERE upld_id=?",checked.uploadId());assertThat(row).containsEntry("form_type_cd","B6").containsEntry("trgt_proj_id","P-TEST").containsEntry("trgt_dstrb_step_cd","PLAN").containsEntry("upld_stat_cd","CHECKED");
        assertThat(db.sql.queryForObject("SELECT aft_val FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_SY_UPLD'",String.class)).contains("contentSha256","\"year\":2026");
    }
    @Test void wholeStageReplacementRetainsOtherStageAndAllZeroCellsAndReplayReturnsImmutableOriginal() throws Exception {
        stage("EST",1,"견적",30,40);stage("PLAN",8,"과거",10,20);
        var file=DistributionWorkbook.valid();var checked=service.validate(file,validateRequest("PLAN"));var request=applyRequest("PLAN",checked);var result=service.apply(file,request);
        assertThat(result.status()).isEqualTo("APPLIED");assertThat(result.results()).isEmpty();assertThat(result.projectId()).isEqualTo("P-TEST");
        assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_pj_tk_dstrb WHERE dstrb_step_cd='EST'",Long.class)).isEqualTo(2);
        assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_pj_tk_dstrb WHERE dstrb_step_cd='PLAN' AND row_sn=1 AND upld_id=?",Long.class,checked.uploadId())).isEqualTo(2);
        assertThat(db.sql.queryForObject("SELECT dstrb_amt::text FROM tb_pj_tk_dstrb WHERE dstrb_step_cd='PLAN' AND dstrb_ym='202607'",String.class)).isEqualTo("0");
        long audit=db.count("tb_sy_chg_hist");db.sql.update("UPDATE tb_pj_proj SET proj_nm='나중 이름' WHERE proj_id='P-TEST'");
        assertThat(service.apply(file,request)).isEqualTo(result);assertThat(db.count("tb_sy_chg_hist")).isEqualTo(audit);
    }
    @Test void protectedZeroPresenceStageChangeAllowedButOneWonRollsBackAllStages() throws Exception {
        stage("EST",1,"항목",0,0);db.sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES('202606','CLOSE','admin')");
        var one=DistributionWorkbook.file(b->DistributionWorkbook.item(b,1,"항목","1","0"));var checked=service.validate(one,validateRequest("PLAN"));
        assertThatThrownBy(()->service.apply(one,applyRequest("PLAN",checked))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("CLOSED_MONTH"));
        assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_pj_tk_dstrb WHERE dstrb_step_cd='PLAN'",Long.class)).isZero();
        assertThat(db.sql.queryForObject("SELECT upld_stat_cd FROM tb_sy_upld WHERE upld_id=?",String.class,checked.uploadId())).isEqualTo("CHECKED");
        var zero=DistributionWorkbook.file(b->DistributionWorkbook.item(b,1,"다른 0 항목","0","99"));var z=service.validate(zero,validateRequest("PLAN"));
        assertThat(service.apply(zero,applyRequest("PLAN",z)).status()).isEqualTo("APPLIED");
        assertThat(db.count("tb_pj_tk_dstrb")).isEqualTo(4);
    }
    @Test void latestPeriodHeaderRevalidationCommits422DiagnosticsWithoutReplacement() throws Exception {
        stage("EST",1,"원본",3,4);var file=DistributionWorkbook.valid();var checked=service.validate(file,validateRequest("PLAN"));
        db.sql.update("UPDATE tb_pj_proj SET ctrt_end_dt='2026-08-31' WHERE proj_id='P-TEST'");
        assertThatThrownBy(()->service.apply(file,applyRequest("PLAN",checked))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(422));
        assertThat(db.count("tb_pj_tk_dstrb")).isEqualTo(2);assertThat(db.count("tb_sy_upld_err")).isEqualTo(1);assertThat(db.sql.queryForObject("SELECT upld_stat_cd FROM tb_sy_upld",String.class)).isEqualTo("CHECKED");
    }
    @Test void requiredBusinessAuditFailureRollsBackDeleteInsertAndAppliedTogether() throws Exception {
        stage("PLAN",1,"원본",3,4);var file=DistributionWorkbook.valid();var checked=service.validate(file,validateRequest("PLAN"));
        db.sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_distribution_audit CHECK(trgt_tbl_nm <> 'TB_PJ_PROJ')");
        assertThatThrownBy(()->service.apply(file,applyRequest("PLAN",checked))).isInstanceOf(RuntimeException.class);
        assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_pj_tk_dstrb WHERE item_nm='원본'",Long.class)).isEqualTo(2);
        assertThat(db.sql.queryForObject("SELECT upld_stat_cd FROM tb_sy_upld",String.class)).isEqualTo("CHECKED");
    }
    @Test void sameUploadConcurrentApplyReturnsOneResultAndOneReplacement() throws Exception {
        var file=DistributionWorkbook.valid();var checked=service.validate(file,validateRequest("PLAN"));var request=applyRequest("PLAN",checked);
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<com.sicost.b9.B9Dtos.Result> command=()->{assertThat(start.await(10,TimeUnit.SECONDS)).isTrue();return service.apply(file,request);};
        try {var a=pool.submit(command);var b=pool.submit(command);start.countDown();assertThat(a.get(30,TimeUnit.SECONDS)).isEqualTo(b.get(30,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
        assertThat(db.count("tb_pj_tk_dstrb")).isEqualTo(2);assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE chg_item_nm='배분표 업로드 PLAN'",Long.class)).isEqualTo(1);
    }
}
