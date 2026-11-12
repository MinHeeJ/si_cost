package com.sicost.b9;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.common.projects.*;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class ProjectImportDatabaseTest {
    PostgreSqlDatabase db;
    B9Service service;
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        db.sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES('HQ','본사','Y','Y','admin')");
        db.sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,use_yn,reg_user_id) VALUES('C-1','CUST','고객','0012345678','Y','admin')");
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var config=new org.apache.ibatis.session.Configuration();config.setArgNameBasedConstructorAutoMapping(true);config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);factory.setPlugins(new PersistenceBoundary());factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());var mapper=session.getMapper(B9Mapper.class);
        var users=new CurrentUserProvider(session.getMapper(CurrentUserMapper.class));var clock=Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));
        var json=JsonMapper.builder().findAndAddModules().build();var revisions=new RevisionService(json);
        var history=new ChangeHistoryWriter(session.getMapper(ChangeHistoryMapper.class),users,clock,json);
        var manager=new DataSourceTransactionManager(db.source);var locks=session.getMapper(AggregateLockMapper.class);
        var coordinator=new WriteCoordinator(manager,locks,revisions,new CommitInvalidation(event->{}));
        var registration=new ProjectRegistrationService(session.getMapper(ProjectRegistrationMapper.class));
        service=new B9Service(new B9Repository(mapper,history,registration,json,revisions,locks),new ExcelImportService(),new B9ImportDefinition(mapper,clock),users,manager,clock,coordinator);
    }
    @Test void validateWritesOnlyCheckedIssuesAndImmutableContextNotBusinessOrOriginalFile() throws Exception {
        var checked=service.validate(ProjectWorkbook.valid(),2026);assertThat(checked.canApply()).isTrue();assertThat(db.count("tb_pj_proj")).isZero();assertThat(db.count("tb_pj_proj_part")).isZero();assertThat(db.count("tb_pj_proj_chg")).isZero();
        var row=db.sql.queryForMap("SELECT * FROM tb_sy_upld WHERE upld_id=?",checked.uploadId());assertThat(row).containsEntry("form_type_cd","B9").containsEntry("scrn_id","UI-01").containsEntry("upld_stat_cd","CHECKED").containsEntry("reg_user_id","admin");
        assertThat(db.sql.queryForObject("SELECT aft_val FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_SY_UPLD' AND trgt_key=?",String.class,Long.toString(checked.uploadId()))).contains("\"year\":2026","contentSha256");
    }
    @Test void reserveAllExplicitIdsThenStoreProjectChildrenOriginalAmountsAndRecoverOriginalNameAfterRename() throws Exception {
        var file=ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);ProjectWorkbook.validRow(b,2);b.getSheetAt(0).getRow(2).getCell(0).setCellValue("P-0100");});
        var check=service.validate(file,2026);var applied=service.apply(file,check.uploadId(),2026);
        assertThat(applied.results()).extracting(B9Dtos.ResultRow::id).containsExactly("P-0101","P-0100");
        assertThat(db.count("tb_pj_proj")).isEqualTo(2);assertThat(db.count("tb_pj_proj_part")).isEqualTo(2);assertThat(db.count("tb_pj_proj_chg")).isEqualTo(2);
        assertThat(db.sql.queryForObject("SELECT orgnl_ctrt_amt=ctrt_amt FROM tb_pj_proj WHERE proj_id='P-0101'",Boolean.class)).isTrue();
        assertThat(db.sql.queryForObject("SELECT chg_item_cd FROM tb_pj_proj_chg WHERE proj_id='P-0101'",String.class)).isEqualTo("CTRT");
        db.sql.update("UPDATE tb_pj_proj SET proj_nm='변경된 이름' WHERE proj_id='P-0101'");
        assertThat(service.apply(file,check.uploadId(),2026)).isEqualTo(applied);
        assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_PJ_PROJ'",Long.class)).isEqualTo(2);
    }
    @Test void changedReferenceCommits422DiagnosticsButNoBusinessAndLeavesChecked() throws Exception {
        var file=ProjectWorkbook.valid();var check=service.validate(file,2026);db.sql.update("UPDATE tb_bs_part SET use_yn='N' WHERE part_id='HQ'");
        assertThatThrownBy(()->service.apply(file,check.uploadId(),2026)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(422));
        assertThat(db.count("tb_pj_proj")).isZero();assertThat(db.count("tb_sy_upld_err")).isEqualTo(1);assertThat(db.sql.queryForObject("SELECT upld_stat_cd FROM tb_sy_upld",String.class)).isEqualTo("CHECKED");
    }
    @Test void persistedContextRejectsChangedYearAndAllowsOriginalYear() throws Exception {
        var file=ProjectWorkbook.valid();var check=service.validate(file,2026);
        assertThatThrownBy(()->service.apply(file,check.uploadId(),2027)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("UPLOAD_CONTEXT_MISMATCH"));assertThat(db.count("tb_pj_proj")).isZero();
        assertThat(service.apply(file,check.uploadId(),2026).results()).hasSize(1);
    }
    @Test void requiredAuditFailureRollsBackEveryProjectPartContractAndAppliedTransition() throws Exception {
        var file=ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);ProjectWorkbook.validRow(b,2);});var check=service.validate(file,2026);
        db.sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_project_audit CHECK(trgt_tbl_nm <> 'TB_PJ_PROJ')");
        assertThatThrownBy(()->service.apply(file,check.uploadId(),2026)).isInstanceOf(RuntimeException.class);
        assertThat(db.count("tb_pj_proj")).isZero();assertThat(db.count("tb_pj_proj_part")).isZero();assertThat(db.count("tb_pj_proj_chg")).isZero();assertThat(db.sql.queryForObject("SELECT upld_stat_cd FROM tb_sy_upld",String.class)).isEqualTo("CHECKED");assertThat(db.count("tb_sy_chg_hist")).isEqualTo(1);
    }
    @Test void simultaneousSameUploadAppliesOnceAndReturnsSameImmutableResults() throws Exception {
        var file=ProjectWorkbook.valid();var check=service.validate(file,2026);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        Callable<B9Dtos.Result> command=()->{assertThat(start.await(10,TimeUnit.SECONDS)).isTrue();return service.apply(file,check.uploadId(),2026);};
        try{var a=pool.submit(command);var b=pool.submit(command);start.countDown();assertThat(a.get(30,TimeUnit.SECONDS)).isEqualTo(b.get(30,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
        assertThat(db.count("tb_pj_proj")).isEqualTo(1);assertThat(db.count("tb_pj_proj_part")).isEqualTo(1);assertThat(db.count("tb_pj_proj_chg")).isEqualTo(1);
    }
    @Test void contextAuditFailureRollsBackCheckedAndIssuesTogether() throws Exception {
        db.sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_upload_context CHECK(trgt_tbl_nm <> 'TB_SY_UPLD')");
        assertThatThrownBy(()->service.validate(ProjectWorkbook.valid(),2026)).isInstanceOf(RuntimeException.class);
        assertThat(db.count("tb_sy_upld")).isZero();assertThat(db.count("tb_sy_upld_err")).isZero();assertThat(db.count("tb_pj_proj")).isZero();
    }
}
