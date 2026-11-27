package com.sicost.apiops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.assignmentcandidatesops.*;
import com.sicost.assignments.AssignmentMapper;
import com.sicost.common.assignments.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.forecast.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class StaffingPersistenceTest {
    PostgreSqlDatabase db;
    SqlSessionTemplate session;
    CurrentUserProvider users;
    AssignmentMonthlyService monthly;
    AssignmentCandidateRepository revisions;
    TentativeSelectionService selections;
    TransactionTemplate tx;
    Clock clock=Clock.fixed(Instant.parse("2026-06-30T15:00:00Z"),ZoneId.of("Asia/Seoul"));
    @BeforeEach void install()throws Exception{
        db=new PostgreSqlDatabase();db.install();
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var config=new org.apache.ibatis.session.Configuration();config.addMapper(AggregateLockMapper.class);config.setArgNameBasedConstructorAutoMapping(true);config.setMapUnderscoreToCamelCase(true);config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);factory.setConfiguration(config);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));session=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        users=new CurrentUserProvider(session.getMapper(CurrentUserMapper.class));var actor=users.userId();
        tx=new TransactionTemplate(new DataSourceTransactionManager(db.source));
        var history=new ChangeHistoryWriter(session.getMapper(ChangeHistoryMapper.class),users,clock,new ObjectMapper());
        monthly=new AssignmentMonthlyService(session.getMapper(AssignmentMonthlyMapper.class),users,history,clock);
        var locks=new SettlementSourceLocks(session.getMapper(SettlementSourceLockMapper.class),session.getMapper(AggregateLockMapper.class));
        var invalidation=new CommitInvalidation(event -> {});
        revisions=new AssignmentCandidateRepository(session.getMapper(AssignmentCandidateMapper.class),locks,session.getMapper(AssignmentMapper.class),new RevisionService(new ObjectMapper()),history,invalidation);
        selections=new TentativeSelectionService(session.getMapper(TentativeSelectionMapper.class),revisions,locks,monthly,history,users,invalidation,clock);
        String part=db.sql.queryForObject("SELECT part_id FROM tb_bs_part WHERE use_yn='Y' ORDER BY part_id LIMIT 1",String.class);
        db.sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES('D-staff','M-staff','인력 검증','FT','DEV','DRCT',?,'WORK',?)",part,actor);
        // The installation seed has no business customers; make this test's opportunity source explicit.
        db.sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_nm,clnt_type_cd,biz_reg_no,use_yn,reg_user_id) VALUES ('C-staff','인력 검증 고객','CUST','0123456789','Y',?)",actor);
        for(String id:List.of("O-staff1","O-staff2")){
            assertThat(db.sql.update("INSERT INTO tb_sl_opp(opp_id,opp_nm,cust_clnt_id,part_id,sales_step_cd,win_prbl_rt,expct_amt,bgn_plan_ym,opp_stat_cd,aprv_stat_cd,reg_user_id) VALUES (?,?,'C-staff',?,'FIND',50,100,'202607','ING','NONE',?)",id,id,part,actor)).isEqualTo(1);
            db.sql.update("INSERT INTO tb_sl_opp_need(opp_id,need_sn,job_cd,need_cnt,bgn_ym,prd_mcnt,mon_mm,reg_user_id) VALUES(?,1,'DEV',2,'202607',3,0.5,?)",id,actor);
        }
    }
    @AfterEach void cleanup(){if(db==null)return;String schema=db.sql.queryForObject("SELECT current_schema()",String.class);if(schema!=null&&schema.matches("sicost_test_[0-9a-f]+"))db.sql.execute("DROP SCHEMA "+schema+" CASCADE");}
    TentativeSelectionInput.Create input(String id){return new TentativeSelectionInput.Create(id,1,"D-staff",YearMonth.of(2026,7),"UI-29",revisions.revision(id),null,false);}
    @Test void realMapperKeepsBothOpportunitiesAndReleasesOnlyTheSelectedOne(){
        long assignments=db.count("tb_pj_asgn"),payroll=db.count("tb_hr_mon_pay");
        var first=tx.execute(s->selections.create(input("O-staff1")));
        assertThat(first.changed()).isTrue();
        tx.executeWithoutResult(s->assertThat(selections.create(input("O-staff1")).changed()).isFalse());
        tx.executeWithoutResult(s->selections.create(input("O-staff2")));
        assertThat(db.sql.queryForList("SELECT opp_id FROM tb_sl_tent_asgn WHERE dev_id='D-staff' AND tent_stat_cd='ACT' ORDER BY opp_id",String.class)).containsExactly("O-staff1","O-staff2");
        db.sql.update("UPDATE tb_sl_tent_asgn SET tent_stat_cd='REL' WHERE tent_id=?::numeric",first.tentativeId());
        assertThat(db.sql.queryForList("SELECT opp_id FROM tb_sl_tent_asgn WHERE dev_id='D-staff' AND tent_stat_cd='ACT'",String.class)).containsExactly("O-staff2");
        assertThat(db.count("tb_pj_asgn")).isEqualTo(assignments);assertThat(db.count("tb_hr_mon_pay")).isEqualTo(payroll);
        assertThat(session.getMapper(StaffingReadMapper.class).tentatives()).anySatisfy(t->{assertThat(t.developerId()).isEqualTo("D-staff");assertThat(t.processedAt()).isNull();});
    }
    @Test void notificationQueueFollowsCommittedAssignmentReleaseAndProbabilityChanges(){
        db.sql.update("UPDATE tb_hr_dev SET email_addr='staff@example.test' WHERE dev_id='D-staff'");
        tx.executeWithoutResult(status -> {
            selections.create(input("O-staff1"));
            status.setRollbackOnly();
        });
        assertThat(db.count("tb_sl_tent_mail_outbox")).isZero();

        var first=tx.execute(status->selections.create(input("O-staff1")));
        tx.executeWithoutResult(status->selections.create(input("O-staff2")));
        db.sql.update("UPDATE tb_sl_opp SET win_prbl_rt=50 WHERE opp_id='O-staff1'");
        db.sql.update("UPDATE tb_sl_opp SET win_prbl_rt=55 WHERE opp_id='O-staff1'");
        db.sql.update("UPDATE tb_sl_tent_asgn SET tent_stat_cd='REL' WHERE tent_id=?::numeric",first.tentativeId());
        db.sql.update("UPDATE tb_sl_opp SET win_prbl_rt=60 WHERE opp_id='O-staff1'");
        db.sql.update("UPDATE tb_sl_tent_asgn SET tent_stat_cd='CNVT' WHERE opp_id='O-staff2'");

        assertThat(db.sql.queryForList("""
            SELECT event_type || ':' || opp_nm || ':' || probability::text
            FROM tb_sl_tent_mail_outbox ORDER BY mail_id
            """,String.class)).containsExactly("ASSIGNED:O-staff1:50.00","ASSIGNED:O-staff2:50.00",
                "PROBABILITY:O-staff1:55.00","RELEASED:O-staff1:55.00");
    }
    @Test void opportunityReadRevisionIsAcceptedByTentativeWrites(){
        var sales=session.getMapper(com.sicost.salesopportunitiesops.SalesCommandMapper.class);
        var candidates=session.getMapper(AssignmentCandidateMapper.class);
        String id="O-staff1";
        assertThat(candidates.snapshot(id)).isEqualTo(sales.snapshot(id));
        String token=new RevisionService(new ObjectMapper()).revision(sales.snapshot(id));
        var result=tx.execute(s->selections.create(new TentativeSelectionInput.Create(id,1,"D-staff",YearMonth.of(2026,7),"UI-29",token,null,false)));
        assertThat(result.changed()).isTrue();
        assertThat(result.revision()).isEqualTo(new RevisionService(new ObjectMapper()).revision(sales.snapshot(id)));
    }
    @Test void auditConstraintFailureRollsBackActualBusinessRows(){
        db.sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_staff_history CHECK(trgt_tbl_nm<>'TB_SL_TENT_ASGN')");
        var before=db.sql.queryForList("SELECT * FROM tb_sl_opp ORDER BY opp_id");long count=db.count("tb_sl_tent_asgn");
        assertThatThrownBy(()->tx.execute(s->selections.create(input("O-staff1")))).isInstanceOf(RuntimeException.class);
        assertThat(db.count("tb_sl_tent_asgn")).isEqualTo(count);assertThat(db.sql.queryForList("SELECT * FROM tb_sl_opp ORDER BY opp_id")).isEqualTo(before);
    }
    @Test void cnvtReadMaterializesNullablePricesAndContractStartWithoutYearFilter(){
        String actor=users.userId();
        db.sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES('P-staff','프로젝트','TK','ING','2027-07-15','2028-06-30',100,0,0,?)",actor);
        db.sql.update("UPDATE tb_sl_opp SET proj_id='P-staff',opp_stat_cd='WIN' WHERE opp_id='O-staff1'");
        db.sql.update("INSERT INTO tb_sl_tent_asgn(opp_id,need_sn,dev_id,bgn_ym,tent_stat_cd,src_scrn_id,reg_user_id) VALUES('O-staff1',1,'D-staff','202707','CNVT','UI-29',?)",actor);
        var mapper=session.getMapper(StaffingReadMapper.class);var before=db.sql.queryForList("SELECT * FROM tb_sl_tent_asgn ORDER BY tent_id");
        var rows=mapper.candidates("202607");assertThat(rows).anySatisfy(r->{assertThat(r.projectId()).isEqualTo("P-staff");assertThat(r.startDate()).isEqualTo(LocalDate.of(2027,7,15));assertThat(r.billingUnitPrice()).isNull();});
        assertThat(mapper.people()).anySatisfy(p->{assertThat(p.id()).isEqualTo("D-staff");assertThat(p.gradeCode()).isNull();});
        assertThat(db.sql.queryForList("SELECT * FROM tb_sl_tent_asgn ORDER BY tent_id")).isEqualTo(before);
    }
}
