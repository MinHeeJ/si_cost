package com.sicost.b5;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.assignmentsops.*;
import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.ApiException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.mock.web.MockMultipartFile;
import static com.sicost.b5.B5Dtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class B5DatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;B5Service service;B5Mapper mapper;AssignmentActionsRepository assignments;
    String project,developer,contract,part,job,newType,subType,reason;
    @Configuration @EnableTransactionManagement
    static class Transactions {@Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}}
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var config=new org.apache.ibatis.session.Configuration();config.addMapper(AggregateLockMapper.class);config.setArgNameBasedConstructorAutoMapping(true);config.setMapUnderscoreToCamelCase(true);config.addInterceptor(new PersistenceBoundary());factory.setConfiguration(config);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);context.registerBean(ObjectMapper.class,()->JsonMapper.builder().findAndAddModules().build());context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(B5Mapper.class,()->session.getMapper(B5Mapper.class));context.registerBean(AssignmentActionsMapper.class,()->session.getMapper(AssignmentActionsMapper.class));context.registerBean(AssignmentMonthlyMapper.class,()->session.getMapper(AssignmentMonthlyMapper.class));context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));context.registerBean(MasterIdMapper.class,()->session.getMapper(MasterIdMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,CommitInvalidation.class,MasterIdService.class,AssignmentMonthlyService.class,AssignmentActionsRepository.class,AssignmentActionsService.class,ExcelImportService.class,B5ImportDefinition.class,B5Repository.class,B5Service.class);context.refresh();service=context.getBean(B5Service.class);mapper=context.getBean(B5Mapper.class);assignments=context.getBean(AssignmentActionsRepository.class);
        String key=UUID.randomUUID().toString().replace("-","").substring(0,10);project="P-"+key;developer="D-"+key;contract="C-"+key;part="T-"+key;
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,reg_user_id) VALUES (?,?,'admin')",part,part);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2026-01-01','2027-12-31',100,100,0,0,'admin')",project,project);
        for(String id:List.of(developer,contract))sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FT','BE','DRCT',?,'IDLE','admin')",id,id,id,part);
        job=codeName("JOB","BE");newType=codeName("INPUT_TYPE","NEW");subType=codeName("INPUT_TYPE","SUB");reason=sql.queryForObject("SELECT code FROM tb_sy_code WHERE code_grp_id='ASGN_CHG_RSN' AND use_yn='Y' AND del_yn='N' ORDER BY sort_ord LIMIT 1",String.class);
    }
    String codeName(String group,String code){return sql.queryForObject("SELECT code_nm FROM tb_sy_code WHERE code_grp_id=? AND code=?",String.class,group,code);}
    @AfterEach void close(){if(context!=null)context.close();}
    MockMultipartFile file() throws Exception {return B5Workbook.file(b->B5Workbook.row(b,1,project,developer,job,"2026-06-01","2026-06-30","","101",newType));}
    Request initial(){return new Request(2026,null,null,List.of());}
    Request apply(Validation checked){return new Request(2026,checked.uploadId(),checked.previewToken(),List.of());}
    List<Map<String,Object>> source(){return sql.queryForList("SELECT * FROM tb_pj_asgn WHERE proj_id=? ORDER BY asgn_id",project);}
    @Test void seededActorAppliesWithInputMonthsAuditAndImmutableReplayReceipt() throws Exception {
        var file=file();var checked=service.validate(file,initial());assertThat(checked.summary().errorRows()).isZero();assertThat(source()).isEmpty();assertThat(mapper.upload(checked.uploadId()).actor()).isEqualTo("admin");
        var result=service.apply(file,apply(checked));String id=result.results().get(0).assignmentId();assertThat(result.results().get(0).rowNumber()).isEqualTo(2);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_asgn_hist WHERE asgn_id=? AND asgn_chg_type_cd='INPUT'",Integer.class,id)).isEqualTo(1);
        assertThat(sql.queryForMap("SELECT ctrt_mm,ext_cnt,early_wthdr_yn,asgn_stat_cd FROM tb_pj_asgn WHERE asgn_id=?",id)).containsEntry("early_wthdr_yn","N").containsEntry("asgn_stat_cd","ACT");
        assertThat(sql.queryForObject("SELECT mon_mm::text FROM tb_pj_asgn_mon WHERE asgn_id=?",String.class,id)).isEqualTo("1.00");
        assertThat(sql.queryForObject("SELECT chg_rsn FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_PJ_ASGN' AND trgt_key=? AND chg_type_cd='NEW'",String.class,id)).isEqualTo("B5 업로드:"+checked.uploadId()+";행:2");
        sql.update("UPDATE tb_pj_asgn SET bill_uprc=999 WHERE asgn_id=?",id);var before=source();long audit=sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist",Long.class);
        assertThat(service.apply(file,apply(checked))).isEqualTo(result);assertThat(service.result(result.uploadId())).isEqualTo(result);assertThat(source()).isEqualTo(before);assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist",Long.class)).isEqualTo(audit);
    }
    @Test void duplicateAfterValidationCommitsOnlyCheckedDiagnosticsWithoutRollbackOnlyLeak() throws Exception {
        var file=file();var checked=service.validate(file,initial());sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,bill_uprc,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES ('A-concurrent',?,?,'BE','2026-06-01','2026-06-30',1,101,'NEW',0,'ACT','admin')",project,developer);var before=source();
        assertThatThrownBy(()->service.apply(file,apply(checked))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(422));assertThat(source()).isEqualTo(before);assertThat(mapper.upload(checked.uploadId()).status()).isEqualTo("CHECKED");assertThat(mapper.issues(checked.uploadId())).isNotEmpty();
    }
    @Test void auditFailureRollsBackAllNewSourcesInputMonthsAndApplied() throws Exception {
        var file=file();var checked=service.validate(file,initial());sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_b5_audit CHECK(trgt_tbl_nm <> 'TB_PJ_ASGN')");assertThatThrownBy(()->service.apply(file,apply(checked))).isInstanceOf(RuntimeException.class);assertThat(source()).isEmpty();assertThat(sql.queryForList("SELECT * FROM tb_pj_asgn_hist")).isEmpty();assertThat(sql.queryForList("SELECT * FROM tb_pj_asgn_mon")).isEmpty();assertThat(mapper.upload(checked.uploadId()).status()).isEqualTo("CHECKED");
    }
    @Test void sameDateWithdrawalAndNewRowsCommitTogetherAndProtectFixedMonth() throws Exception {
        String old="A-old";sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,bill_uprc,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES (?, ?,?,'BE','2026-01-01','2026-12-31',1,101,'NEW',0,'ACT','admin')",old,project,contract);
        sql.update("INSERT INTO tb_pj_asgn_hist(asgn_id,hist_sn,asgn_chg_type_cd,aply_dt,new_ctrt_mm,new_uprc,reg_user_id) VALUES (?,1,'INPUT','2026-01-01',1,101,'admin')",old);
        sql.update("INSERT INTO tb_pj_asgn_mon(asgn_id,input_ym,dev_id,proj_id,input_day_cnt,mon_mm,aply_uprc,sales_amt,fix_yn,reg_user_id) VALUES (?,'202606',?,?,30,1,101,101,'Y','admin')",old,contract,project);var fixed=sql.queryForList("SELECT * FROM tb_pj_asgn_mon WHERE asgn_id=? AND input_ym='202606'",old);
        var file=B5Workbook.file(b->{B5Workbook.row(b,1,project,developer,job,"2026-06-01","2026-06-30","0.50","101",subType,contract);B5Workbook.row(b,2,project,developer,job,"2026-06-01","2026-07-31","0.50","101",subType,contract);});var first=service.validate(file,initial());
        var w=new com.sicost.assignmentsops.AssignmentActionsDtos.Withdrawal(old,assignments.revision(old),LocalDate.of(2026,5,31),reason,null);var selections=List.of(new B5Dtos.Selection(2,List.of(w)),new B5Dtos.Selection(3,List.of(w)));var checked=service.validate(file,new Request(2026,first.uploadId(),null,selections));assertThat(checked.summary().errorRows()).isZero();assertThat(checked.previewToken()).isNotEqualTo(first.previewToken());
        var result=service.apply(file,new Request(2026,checked.uploadId(),checked.previewToken(),selections));assertThat(result.results()).hasSize(2);assertThat(result.withdrawnIds()).containsExactly(old);assertThat(sql.queryForObject("SELECT count(*) FROM tb_pj_asgn_hist WHERE asgn_id=? AND asgn_chg_type_cd='EARLY'",Integer.class,old)).isEqualTo(1);assertThat(sql.queryForList("SELECT * FROM tb_pj_asgn_mon WHERE asgn_id=? AND input_ym='202606'",old)).isEqualTo(fixed);
    }
}
