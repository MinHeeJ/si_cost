package com.sicost.apiops;

import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class ActivityReadDatabaseTest {
    PostgreSqlDatabase db;
    ActivityReadMapper mapper;
    DashboardReadMapper dashboard;
    TransactionTemplate transaction;
    DataSourceTransactionManager manager;
    ActivityReadService service;
    String actor,other;
    final LocalDateTime at=LocalDateTime.of(2026,7,15,9,0);
    @BeforeEach void setup() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var config=new org.apache.ibatis.session.Configuration();config.setArgNameBasedConstructorAutoMapping(true);config.setMapUnderscoreToCamelCase(true);factory.setConfiguration(config);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new ClassPathResource("mappers/apiops/ActivityReadMapper.xml"),new ClassPathResource("mappers/apiops/DashboardReadMapper.xml"));
        var session=new SqlSessionTemplate(factory.getObject());mapper=session.getMapper(ActivityReadMapper.class);dashboard=session.getMapper(DashboardReadMapper.class);
        manager=new DataSourceTransactionManager(db.source);transaction=new TransactionTemplate(manager);transaction.setReadOnly(true);transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        actor=db.sql.queryForObject("SELECT user_id FROM tb_sy_user ORDER BY user_id LIMIT 1",String.class);
        other="N-"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        db.sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,reg_user_id) VALUES (?,?,?, ?,0,'USE',0,?)",other,"시험 수신자",other+"@example.test",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(UUID.randomUUID().toString()),actor);
        var users=mock(CurrentUserProvider.class);when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser(actor,"임시 관리자",List.of("ADMIN"),true));
        service=new ActivityReadService(new ActivityReadRepository(mapper),users,Clock.fixed(at.atZone(ZoneId.of("Asia/Seoul")).toInstant(),ZoneOffset.UTC),false);
    }
    @AfterEach void cleanup() {
        if(db!=null){String schema=db.sql.queryForObject("SELECT current_schema()",String.class);if(schema==null||!schema.matches("sicost_test_[a-f0-9]{32}"))throw new IllegalStateException("시험 스키마만 정리합니다.");db.sql.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
    BigDecimal notification(String recipient,String method) {
        return db.sql.queryForObject("INSERT INTO tb_sy_noti(rcvr_user_id,noti_type_cd,noti_cn,send_mthd_cd,read_yn,noti_dttm,reg_user_id) VALUES (?,'DUN','시험 알림',?,'N',?,?) RETURNING noti_id",BigDecimal.class,recipient,method,at,actor);
    }
    @Test void fortyFiveOwnSystemNotificationsHaveStableTiesAndCountWithoutReadWrites() {
        var ids=new ArrayList<String>();for(int i=0;i<45;i++)ids.add(notification(actor,i%2==0?"SYS":"BOTH").toPlainString());Collections.reverse(ids);
        notification(other,"SYS");notification(actor,"MAIL");
        var before=db.sql.queryForList("SELECT * FROM tb_sy_noti ORDER BY noti_id");
        var first=transaction.execute(s->service.getNotifications(new ActivityQueryInput.Query(1,20,null,0)));
        var second=transaction.execute(s->service.getNotifications(new ActivityQueryInput.Query(2,20,null,20)));
        assertThat(first.total()).isEqualTo(45);assertThat(second.total()).isEqualTo(45);assertThat(first.items()).extracting(com.sicost.notificationsops.NotificationDtos.Item::id).containsExactlyElementsOf(ids.subList(0,20));assertThat(second.items()).extracting(com.sicost.notificationsops.NotificationDtos.Item::id).containsExactlyElementsOf(ids.subList(20,40));
        assertThat(db.sql.queryForList("SELECT * FROM tb_sy_noti ORDER BY noti_id")).isEqualTo(before);assertThat(db.count("tb_sy_delivery_task")).isZero();assertThat(db.count("tb_sy_automation_run")).isZero();
    }
    @Test void automationRowsFilterCountAndOrderWithoutCreatingMissingRecords() {
        db.sql.update("INSERT INTO tb_sy_automation_run(job_cd,run_dt,state_cd,last_step,start_dttm,error_cn,reg_user_id,reg_dttm) VALUES ('RECEIVABLE_DUNNING','2026-07-15','FAILED',0,?,'대상 확인 실패','SYSTEM',?),('DAILY_OPERATIONS','2026-07-15','RUNNING',2,?,NULL,'SYSTEM',?),('DAILY_OPERATIONS','2026-07-14','SUCCEEDED',7,?,NULL,'SYSTEM',?)",at,at,at,at,at,at);
        var before=db.sql.queryForList("SELECT * FROM tb_sy_automation_run ORDER BY run_dt,job_cd");
        var result=transaction.execute(s->service.getAutomationRuns(new ActivityQueryInput.Query(1,50,null,0)));
        assertThat(result.items()).extracting(com.sicost.automationrunsops.DailyAutomationDtos.Run::state).containsExactly("RUNNING","FAILED","SUCCEEDED");
        var filtered=transaction.execute(s->service.getAutomationRuns(new ActivityQueryInput.Query(1,20,"RECEIVABLE_DUNNING",0)));assertThat(filtered.total()).isEqualTo(1);assertThat(filtered.items()).hasSize(1);
        assertThat(db.sql.queryForList("SELECT * FROM tb_sy_automation_run ORDER BY run_dt,job_cd")).isEqualTo(before);
    }
    @Test void sqlFailureRollsBackToSavepointAndKeepsTheOriginalSnapshotUsable() {
        var repository=new DashboardReadRepository(dashboard,mock(com.sicost.shared.SharedQueriesMapper.class),mock(com.sicost.businessplansops.BusinessPlanPerformanceMapper.class),manager,mock(CurrentUserProvider.class));
        transaction.execute(status->{
            String snapshot=db.sql.queryForObject("SELECT txid_current_snapshot()::text",String.class);
            var broken=repository.section(()->db.sql.queryForObject("SELECT missing_dashboard_column FROM tb_sy_noti",String.class));assertThat(broken.failed()).isTrue();
            var valid=repository.section(()->db.sql.queryForObject("SELECT txid_current_snapshot()::text",String.class));assertThat(valid.failed()).isFalse();assertThat(valid.value()).isEqualTo(snapshot);
            assertThat(db.sql.queryForObject("SHOW transaction_isolation",String.class)).isEqualTo("repeatable read");return null;
        });
    }
    @Test void dashboardCurrentBalanceIncludesFutureAndDueTodayAndCanceledReceiptsDoNotReduceIt() {
        String project="P-"+UUID.randomUUID().toString().replace("-","").substring(0,14);
        db.sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2025-01-01','2027-12-31',1,1,0,0,?)",project,project,actor);
        var baseline=dashboard.receivables(at.toLocalDate());
        var ids=new ArrayList<String>();for(int i=0;i<5;i++) {
            String id="B-"+UUID.randomUUID().toString().replace("-","").substring(0,16);ids.add(id);
            db.sql.update("INSERT INTO tb_bl_bill(bill_id,proj_id,bill_ord,bill_ym,bill_dt,sply_amt,vat_rt,vat_amt,tot_amt,rcpt_sum_amt,unpaid_amt,pay_due_dt,cncl_yn,reg_user_id) VALUES (?,?,1,'202501','2025-01-01',100,0,0,100,0,100,?,?,?)",id,project,i==0?at.toLocalDate().minusDays(1):i==1?at.toLocalDate():at.toLocalDate().plusDays(1),i==3?"Y":"N",actor);
        }
        db.sql.update("INSERT INTO tb_bl_rcpt(bill_id,rcpt_dt,rcpt_amt,cncl_yn,reg_user_id) VALUES (?,'2026-07-15',100,'N',?)",ids.get(4),actor);
        db.sql.update("INSERT INTO tb_bl_rcpt(bill_id,rcpt_dt,rcpt_amt,cncl_yn,reg_user_id) VALUES (?,'2026-07-15',100,'Y',?)",ids.get(2),actor);
        var result=transaction.execute(s->dashboard.receivables(at.toLocalDate()));assertThat(result.balance()).isEqualByComparingTo(baseline.balance().add(new BigDecimal("300")));assertThat(result.overdueCount()).isEqualTo(baseline.overdueCount()+1);assertThat(result.overdueAmount()).isEqualByComparingTo(baseline.overdueAmount().add(new BigDecimal("100")));
    }
}
