package com.sicost.history;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.assignmentsops.*;
import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static com.sicost.history.AssignmentHistoryDtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class AssignmentHistoryDatabaseTest {
    PostgreSqlDatabase db; AssignmentHistoryService service; AssignmentHistoryRepository repository;
    AssignmentMonthlyService months; AssignmentActionsRepository assignments; RevisionService revisions;
    String id, project, developer;
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var config=new org.apache.ibatis.session.Configuration();config.setArgNameBasedConstructorAutoMapping(true);config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);factory.setPlugins(new PersistenceBoundary());factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());var json=JsonMapper.builder().findAndAddModules().build();
        var users=new CurrentUserProvider(session.getMapper(CurrentUserMapper.class));var clock=Clock.fixed(Instant.parse("2080-07-14T15:00:00Z"),ZoneId.of("Asia/Seoul"));
        var audit=new ChangeHistoryWriter(session.getMapper(ChangeHistoryMapper.class),users,clock,json);revisions=new RevisionService(json);
        var manager=new DataSourceTransactionManager(db.source);var coordinator=new WriteCoordinator(manager,session.getMapper(AggregateLockMapper.class),revisions,new CommitInvalidation(event->{}));
        months=new AssignmentMonthlyService(session.getMapper(AssignmentMonthlyMapper.class),users,audit,clock);
        assignments=new AssignmentActionsRepository(session.getMapper(AssignmentActionsMapper.class),months,revisions);
        repository=new AssignmentHistoryRepository(session.getMapper(AssignmentHistoryMapper.class),assignments,months);
        service=new AssignmentHistoryService(repository,coordinator,users,audit,clock);
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,12);id="A-"+suffix;project="P-"+suffix;developer="D-"+suffix;String part="T-"+suffix;
        db.sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2080-01-01','2081-12-31',100,80,0,0,'admin')",project,project);
        db.sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES (?,?,'N','Y','admin')",part,part);
        db.sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FT','DEV','DRCT',?,'IDLE','admin')",developer,"M"+suffix,developer,part);
        db.sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,bill_uprc,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES (?,?,?,'DEV','2080-07-01','2081-08-31',1,200,'NEW',0,'ACT','admin')",id,project,developer);
        event(1,"INPUT","2080-07-01",100);event(2,"UPRC","2080-07-01",200);
        db.sql.update("INSERT INTO tb_pj_asgn_mon(asgn_id,input_ym,dev_id,proj_id,input_day_cnt,mon_mm,aply_uprc,sales_amt,fix_yn,reg_user_id) VALUES (?,'208007',?,?,31,1,200,200,'N','admin')",id,developer,project);
        db.sql.update("INSERT INTO tb_sy_code(code_grp_id,code,code_nm,sort_ord,use_yn,del_yn,reg_user_id) VALUES ('ASGN_CHG_RSN','HISTTEST','시험',999,'Y','N','admin')");
    }
    void event(int sn,String type,String day,int price) {db.sql.update("INSERT INTO tb_pj_asgn_hist(asgn_id,hist_sn,asgn_chg_type_cd,aply_dt,bfr_val,aft_val,new_uprc,new_ctrt_mm,reg_user_id) VALUES (?,?,?,CAST(? AS date),'100','200',?,1,'admin')",id,sn,type,day,price);}
    Correction input() {return new Correction(2080,assignments.revision(id),"금액 아닌 감사 문장","HISTTEST");}
    List<Map<String,Object>> monthly() {return db.sql.queryForList("SELECT * FROM tb_pj_asgn_mon WHERE asgn_id=? ORDER BY input_ym",id);}
    Map<String,Object> source() {return db.sql.queryForMap("SELECT * FROM tb_pj_asgn WHERE asgn_id=?",id);}
    Map<String,Object> original() {return db.sql.queryForMap("SELECT * FROM tb_pj_asgn_hist WHERE asgn_id=? AND hist_sn=2",id);}
    @Test void realMappingOrderingFilteredCountAndReadHasZeroWrites() {
        var before=repository.snapshot(id);var rows=service.getAssignmentHistory(new Query(id,null,1,50,0),2080);
        assertThat(rows.items()).extracting(Entry::sequence).containsExactly(2,1);assertThat(rows.items().get(0).newUnitPrice()).isEqualTo("200");assertThat(rows.items().get(0).newContractMm()).isEqualTo("1.00");
        assertThat(service.getAssignmentHistory(new Query(id,"CRCT",1,20,0),2080).total()).isZero();assertThat(repository.snapshot(id)).isEqualTo(before);
    }
    @Test void correctionPreservesSourceTypedRegistrationAndMonthsAcrossFreshCalculation() {
        var source=source();var monthly=monthly();var original=original();var before=months.calculate(months.required(id),months.mapper().histories(id),"ING","MO");
        var saved=service.createAssignmentHistoryCorrection(id,2,input());assertThat(saved.original().corrected()).isEqualTo("Y");assertThat(saved.correction().correctionTargetSequence()).isEqualTo(2);assertThat(saved.correction().newUnitPrice()).isNull();assertThat(saved.correction().newContractMm()).isNull();assertThat(saved.correction().corrected()).isEqualTo("N");assertThat(saved.correction().actor()).isEqualTo("admin");
        var after=original();original.put("crct_yn","Y");assertThat(after).isEqualTo(original);assertThat(source()).isEqualTo(source);assertThat(monthly()).isEqualTo(monthly);
        assertThat(months.calculate(months.required(id),months.mapper().histories(id),"ING","MO")).isEqualTo(before);
        assertThatThrownBy(()->service.createAssignmentHistoryCorrection(id,2,input())).isInstanceOf(ApiException.class);assertThat(repository.mapper().count(new Query(id,"CRCT",1,50,0))).isEqualTo(1);
    }
    @Test void mandatoryAuditFailureRollsBackMarkAndAppend() {
        var snapshot=repository.snapshot(id);db.sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_assignment_audit CHECK(trgt_tbl_nm <> 'TB_PJ_ASGN')");
        assertThatThrownBy(()->service.createAssignmentHistoryCorrection(id,2,input())).isInstanceOf(RuntimeException.class);assertThat(repository.snapshot(id)).isEqualTo(snapshot);
    }
    @Test void insertFailureRollsBackOriginalFlag() {
        var snapshot=repository.snapshot(id);db.sql.execute("ALTER TABLE tb_pj_asgn_hist ADD CONSTRAINT reject_correction CHECK(asgn_chg_type_cd <> 'CRCT')");
        assertThatThrownBy(()->service.createAssignmentHistoryCorrection(id,2,input())).isInstanceOf(RuntimeException.class);assertThat(repository.snapshot(id)).isEqualTo(snapshot);
    }
    @Test void staleRevisionLeavesOriginalUnchanged() {
        var request=input();db.sql.update("UPDATE tb_pj_asgn_mon SET mod_dttm=CURRENT_TIMESTAMP WHERE asgn_id=?",id);var snapshot=repository.snapshot(id);
        assertThatThrownBy(()->service.createAssignmentHistoryCorrection(id,2,request)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(409));assertThat(repository.snapshot(id)).isEqualTo(snapshot);
    }
    @Test void futureCancelledTargetRecordsTodayNotStartDate() {
        db.sql.update("UPDATE tb_pj_asgn SET input_bgn_dt='2080-08-01',asgn_stat_cd='CNCL' WHERE asgn_id=?",id);event(3,"CNCL","2080-07-15",200);var source=source();
        var result=service.createAssignmentHistoryCorrection(id,3,input());assertThat(result.correction().applyDate()).isEqualTo("2080-07-15");assertThat(source()).isEqualTo(source);
    }
}
