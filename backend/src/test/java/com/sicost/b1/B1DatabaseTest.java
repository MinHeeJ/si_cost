package com.sicost.b1;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.apiops.PayrollMapper;
import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.excel.ExcelExportService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import static com.sicost.b1.B1Dtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class B1DatabaseTest {
    PostgreSqlDatabase db;
    B1Service service;
    B1Controller controller;
    B1Mapper mapper;
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        db.sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES('B1-part','급여시험파트','N','Y','admin')");
        for(String id:List.of("D-one","D-two"))db.sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,use_yn,reg_user_id) VALUES(?,?,?,'FL','DEV','INDRCT','B1-part','RETIRE','N','admin')",id,id.equals("D-one")?"0001":"0002",id);
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);factory.setPlugins(new PersistenceBoundary());factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());mapper=session.getMapper(B1Mapper.class);
        var users=new CurrentUserProvider(session.getMapper(CurrentUserMapper.class));var clock=Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));
        var json=JsonMapper.builder().findAndAddModules().build();var history=new ChangeHistoryWriter(session.getMapper(ChangeHistoryMapper.class),users,clock,json);
        var manager=new DataSourceTransactionManager(db.source);
        service=new B1Service(mapper,new B1Repository(mapper,history,json),new B1ImportDefinition(new ExcelImportService()),users,new RevisionService(json),session.getMapper(PayrollMapper.class),session.getMapper(AggregateLockMapper.class),clock,manager);
        controller=new B1Controller(service,new ExcelExportService(users,new AccessLogWriter(session.getMapper(AccessLogMapper.class),users,clock),clock,manager));
    }
    Request checkRequest() { return new Request("2026-06",null,null,false); }
    Request applyRequest(Validation validation) { return new Request(validation.month(),validation.uploadId(),validation.revision(),true); }
    List<Map<String,Object>> business() { return db.sql.queryForList("SELECT * FROM tb_hr_mon_pay ORDER BY pay_ym,dev_id"); }
    void prior(String month,String dev) {
        db.sql.update("INSERT INTO tb_hr_mon_pay(pay_ym,dev_id,pay_amt,alwnc_amt,insr_cmpny_amt,insr_self_amt,retire_rsv_amt,incm_tax_amt,wthld_tax_amt,real_pay_amt,vndr_sply_amt,emp_type_cd,drct_cls_cd,reg_user_id,reg_dttm) VALUES(?,?,1,0,0,0,0,0,0,1,0,'FL','INDRCT','admin','2026-01-01 00:00:00.123456')",month,dev);
    }
    @Test void emptyMonthValidationStoresOnlyCheckedContextAndProblems() throws Exception {
        var checked=(Validation)service.validate(B1Workbook.valid(),checkRequest());assertThat(checked.summary().errorRows()).isZero();assertThat(checked.revision()).isNotBlank();assertThat(business()).isEmpty();
        assertThat(db.sql.queryForMap("SELECT form_type_cd,scrn_id,trgt_ym,reg_user_id,upld_stat_cd FROM tb_sy_upld WHERE upld_id=?",checked.uploadId())).containsEntry("form_type_cd","B1").containsEntry("scrn_id","UI-02").containsEntry("trgt_ym","202606").containsEntry("reg_user_id","admin").containsEntry("upld_stat_cd","CHECKED");
        assertThat(db.sql.queryForList("SELECT chg_item_nm FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_SY_UPLD'",String.class)).containsExactlyInAnyOrder("파일지문","검증revision");
    }
    @Test void replacePreservesRegistrationDeletesOmissionsAndLeavesOtherMonthsAndMasterAlone() throws Exception {
        prior("202606","D-one");prior("202606","D-two");prior("202605","D-two");
        var old=db.sql.queryForMap("SELECT reg_user_id,reg_dttm FROM tb_hr_mon_pay WHERE pay_ym='202606' AND dev_id='D-one'");
        var other=db.sql.queryForList("SELECT * FROM tb_hr_mon_pay WHERE pay_ym='202605'");var master=db.sql.queryForList("SELECT * FROM tb_hr_dev ORDER BY dev_id");
        var file=B1Workbook.valid();var checked=(Validation)service.validate(file,checkRequest());
        assertThat(checked.replacement().removedDevelopers()).extracting(Removed::developerId).containsExactly("D-two");
        var result=service.apply(file,applyRequest(checked));assertThat(result.status()).isEqualTo("APPLIED");assertThat(result.results()).containsExactly(new ResultRow(2,"D-one"));
        assertThat(db.sql.queryForList("SELECT dev_id FROM tb_hr_mon_pay WHERE pay_ym='202606'",String.class)).containsExactly("D-one");
        assertThat(db.sql.queryForMap("SELECT reg_user_id,reg_dttm FROM tb_hr_mon_pay WHERE pay_ym='202606' AND dev_id='D-one'")).isEqualTo(old);
        assertThat(db.sql.queryForObject("SELECT upld_id FROM tb_hr_mon_pay WHERE pay_ym='202606'",Long.class)).isEqualTo(checked.uploadId());
        assertThat(db.sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_HR_MON_PAY' AND trgt_key='202606:D-two' AND chg_type_cd='DEL'",Long.class)).isPositive();
        assertThat(db.sql.queryForList("SELECT * FROM tb_hr_mon_pay WHERE pay_ym='202605'")).isEqualTo(other);assertThat(db.sql.queryForList("SELECT * FROM tb_hr_dev ORDER BY dev_id")).isEqualTo(master);
    }
    @Test void firstResultSurvivesLaterReplacementAndChangedMasterWithoutRecalculation() throws Exception {
        var firstFile=B1Workbook.valid();var first=(Validation)service.validate(firstFile,checkRequest());var receipt=service.apply(firstFile,applyRequest(first));
        var secondFile=B1Workbook.file(b->B1Workbook.row(b,1,"0002","","222"));var second=(Validation)service.validate(secondFile,checkRequest());service.apply(secondFile,applyRequest(second));
        db.sql.update("UPDATE tb_hr_dev SET emp_type_cd='UNKNOWN' WHERE dev_id='D-one'");var before=business();long audit=db.count("tb_sy_chg_hist");
        assertThat(service.apply(firstFile,applyRequest(first))).isEqualTo(receipt);assertThat(service.result(first.uploadId())).isEqualTo(receipt);assertThat(business()).isEqualTo(before);assertThat(db.count("tb_sy_chg_hist")).isEqualTo(audit);
    }
    @Test void latestInvalidTypeCommitsOnlyReplacementDiagnosticsAndRetainsMonth() throws Exception {
        prior("202606","D-two");var file=B1Workbook.valid();var checked=(Validation)service.validate(file,checkRequest());var before=business();
        db.sql.update("UPDATE tb_hr_dev SET emp_type_cd='UNKNOWN' WHERE dev_id='D-one'");
        assertThatThrownBy(()->service.apply(file,applyRequest(checked))).isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.status()).isEqualTo(422);assertThat(e.code()).isEqualTo("REVALIDATION_FAILED");});
        assertThat(business()).isEqualTo(before);assertThat(mapper.upload(checked.uploadId()).status()).isEqualTo("CHECKED");assertThat(mapper.issues(checked.uploadId())).isNotEmpty();
    }
    @Test void staleMonthIsRejectedBeforeReplacement() throws Exception {
        var file=B1Workbook.valid();var checked=(Validation)service.validate(file,checkRequest());prior("202606","D-two");var before=business();
        assertThatThrownBy(()->service.apply(file,applyRequest(checked))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));assertThat(business()).isEqualTo(before);
    }
    @Test void mandatoryHistoryFailureRollsBackDeleteInsertAndApplied() throws Exception {
        prior("202606","D-two");var file=B1Workbook.valid();var checked=(Validation)service.validate(file,checkRequest());var before=business();
        db.sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_payroll CHECK(trgt_tbl_nm <> 'TB_HR_MON_PAY')");
        assertThatThrownBy(()->service.apply(file,applyRequest(checked))).isInstanceOf(RuntimeException.class);assertThat(business()).isEqualTo(before);assertThat(mapper.upload(checked.uploadId()).status()).isEqualTo("CHECKED");
    }
    @Test void templateCommitsOnlyUi02DownloadAndAuditFailureDoesNotSendFile() throws Exception {
        var request=new MockHttpServletRequest();var response=new org.springframework.mock.web.MockHttpServletResponse();long before=db.count("tb_sy_chg_hist");
        controller.getB1Template(request,response);assertThat(response.getContentAsByteArray()).startsWith((byte)'P',(byte)'K');
        assertThat(db.sql.queryForMap("SELECT act_type_cd,scrn_id,user_id FROM tb_sy_accs_log")).containsEntry("act_type_cd","DNLD").containsEntry("scrn_id","UI-02").containsEntry("user_id","admin");
        assertThat(db.count("tb_sy_chg_hist")).isEqualTo(before);assertThat(business()).isEmpty();
        db.sql.execute("ALTER TABLE tb_sy_accs_log ADD CONSTRAINT reject_download CHECK(act_type_cd <> 'DNLD') NOT VALID");
        var failed=new org.springframework.mock.web.MockHttpServletResponse();assertThatThrownBy(()->controller.getB1Template(new MockHttpServletRequest(),failed)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(500));assertThat(failed.getContentAsByteArray()).isEmpty();
    }
}
