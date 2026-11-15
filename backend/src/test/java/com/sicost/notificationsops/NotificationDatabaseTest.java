package com.sicost.notificationsops;

import com.sicost.common.concurrent.CommitInvalidation;
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
import static com.sicost.notificationsops.NotificationDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class NotificationDatabaseTest {
    PostgreSqlDatabase db;
    NotificationMapper mapper;
    TransactionTemplate transaction;
    NotificationService service;
    String actor,other;
    final LocalDateTime now=LocalDateTime.of(2026,7,15,0,0);
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var config=new org.apache.ibatis.session.Configuration();config.setArgNameBasedConstructorAutoMapping(true);config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new ClassPathResource("mappers/notificationsops/NotificationMapper.xml"));
        mapper=new SqlSessionTemplate(factory.getObject()).getMapper(NotificationMapper.class);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(db.source));
        actor=db.sql.queryForObject("SELECT user_id FROM tb_sy_user ORDER BY user_id LIMIT 1",String.class);
        other="N-"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        db.sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,reg_user_id) VALUES (?,?,?, ?,0,'USE',0,?)",
            other,"시험 수신자",other+"@example.test",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(UUID.randomUUID().toString()),actor);
        var users=mock(CurrentUserProvider.class);
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser(actor,"시험 수신자",List.of(),true));
        service=new NotificationService(new NotificationRepository(mapper),users,Clock.fixed(now.atZone(ZoneId.of("Asia/Seoul")).toInstant(),ZoneOffset.UTC),new CommitInvalidation(event->{}));
    }
    @AfterEach void cleanup() {
        if(db!=null) {
            String schema=db.sql.queryForObject("SELECT current_schema()",String.class);
            if(schema==null||!schema.matches("sicost_test_[a-f0-9]{32}"))throw new IllegalStateException("시험 스키마만 정리합니다.");
            db.sql.execute("DROP SCHEMA "+schema+" CASCADE");
        }
    }
    BigDecimal notification(String recipient,String method,boolean read,LocalDateTime timestamp) {
        return db.sql.queryForObject("INSERT INTO tb_sy_noti(rcvr_user_id,noti_type_cd,noti_cn,send_mthd_cd,read_yn,read_dttm,noti_dttm,reg_user_id) VALUES (?,'DUN','시험 알림',?,?,?, ?,?) RETURNING noti_id",BigDecimal.class,recipient,method,read?"Y":"N",read?now.minusDays(1):null,timestamp,actor);
    }
    @Test void countsOnlyCurrentRecipientSystemChannelsAndNeverWritesOnPolling() {
        for(int i=0;i<101;i++)notification(actor,i%2==0?"SYS":"BOTH",false,now.minusMinutes(1));
        notification(other,"SYS",false,now);notification(actor,"MAIL",false,now);notification(actor,"SYS",true,now);
        var before=db.sql.queryForList("SELECT * FROM tb_sy_noti ORDER BY noti_id");
        transaction.setReadOnly(true);transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        for(int i=0;i<3;i++)assertThat(transaction.execute(status->service.getNotificationUnreadCount()).unreadCount()).isEqualTo(101);
        assertThat(db.sql.queryForList("SELECT * FROM tb_sy_noti ORDER BY noti_id")).isEqualTo(before);
        assertThat(db.count("tb_sy_delivery_task")).isZero();assertThat(db.count("tb_bl_dun_hist")).isZero();
    }
    @Test void readAllIsBoundedPreservesFirstReadTimeAndLeavesLaterArrivalsUnread() {
        for(int i=0;i<3;i++)notification(actor,"SYS",false,now.minusSeconds(1));
        BigDecimal read=notification(actor,"BOTH",true,now.minusDays(1));
        BigDecimal foreign=notification(other,"BOTH",false,now.minusSeconds(1));
        BigDecimal later=notification(actor,"SYS",false,now.plusNanos(1000000));
        notification(actor,"MAIL",false,now.minusSeconds(1));
        var beforeOther=db.sql.queryForMap("SELECT * FROM tb_sy_noti WHERE noti_id=?",foreign);
        ReadAllResult result=transaction.execute(status->service.saveNotificationsReadAll());
        assertThat(result.changedCount()).isEqualTo(3);
        assertThat(db.sql.queryForMap("SELECT * FROM tb_sy_noti WHERE noti_id=?",foreign)).isEqualTo(beforeOther);
        assertThat(mapper.lock(actor,later).read()).isFalse();assertThat(mapper.lock(actor,read).readAt()).isEqualTo(now.minusDays(1));
        notification(actor,"SYS",false,now.plusSeconds(1));
        assertThat(transaction.execute(status->service.saveNotificationsReadAll()).changedCount()).isZero();
        assertThat(db.count("tb_sy_delivery_task")).isZero();assertThat(db.count("tb_bl_dun_hist")).isZero();
    }
    @Test void singleReadMaterializesNullLinkAndIsIdempotentWithNoHistoryOrDelivery() {
        BigDecimal id=notification(actor,"BOTH",false,now.minusDays(1));
        long audits=db.count("tb_sy_chg_hist");
        var first=transaction.execute(status->service.saveNotificationRead(id.toPlainString()));
        var second=transaction.execute(status->service.saveNotificationRead(id.toPlainString()));
        assertThat(first.changed()).isTrue();assertThat(first.notification().link()).isNull();assertThat(first.notification().readAt()).isEqualTo(second.notification().readAt());assertThat(second.changed()).isFalse();
        assertThat(db.count("tb_sy_chg_hist")).isEqualTo(audits);assertThat(db.count("tb_sy_delivery_task")).isZero();assertThat(db.count("tb_bl_dun_hist")).isZero();
    }
    @Test void otherRecipientIs404AndUnmodified() {
        BigDecimal id=notification(other,"SYS",false,now);
        assertThatThrownBy(()->transaction.execute(status->service.saveNotificationRead(id.toPlainString())))
            .isInstanceOf(com.sicost.common.api.ApiException.class).extracting(e->((com.sicost.common.api.ApiException)e).status()).isEqualTo(404);
        assertThat(mapper.lock(other,id).read()).isFalse();assertThat(mapper.lock(other,id).readAt()).isNull();
    }
    @Test void failedAtomicReadAllRollsBackEveryRow() {
        for(int i=0;i<3;i++)notification(actor,"SYS",false,now);
        var before=db.sql.queryForList("SELECT * FROM tb_sy_noti ORDER BY noti_id");
        db.sql.execute("CREATE FUNCTION reject_notification_read() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'read rejected'; END $$");
        db.sql.execute("CREATE TRIGGER reject_notification_read AFTER UPDATE ON tb_sy_noti FOR EACH ROW EXECUTE FUNCTION reject_notification_read()");
        assertThatThrownBy(()->transaction.execute(status->service.saveNotificationsReadAll())).isInstanceOf(RuntimeException.class);
        assertThat(db.sql.queryForList("SELECT * FROM tb_sy_noti ORDER BY noti_id")).isEqualTo(before);
    }
    @Test void badgesCountAllPositiveBillsIncludingFutureAndCarryoverButNotCanceledOrSettled() {
        String project="P-"+UUID.randomUUID().toString().replace("-","").substring(0,14);
        db.sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2025-01-01','2027-12-31',1,1,0,0,?)",project,project,actor);
        long baseline=mapper.receivableCount();
        var ids=new ArrayList<String>();
        for(int i=0;i<5;i++) {
            String id="B-"+UUID.randomUUID().toString().replace("-","").substring(0,16);ids.add(id);
            db.sql.update("INSERT INTO tb_bl_bill(bill_id,proj_id,bill_ord,bill_ym,bill_dt,sply_amt,vat_rt,vat_amt,tot_amt,rcpt_sum_amt,unpaid_amt,pay_due_dt,cncl_yn,reg_user_id) VALUES (?,?,1,'202501','2025-01-01',100,0,0,100,0,100,?,?,?)",id,project,i==0?LocalDate.of(2025,1,1):i==1?now.toLocalDate():now.toLocalDate().plusDays(10),i==3?"Y":"N",actor);
        }
        db.sql.update("INSERT INTO tb_bl_rcpt(bill_id,rcpt_dt,rcpt_amt,cncl_yn,reg_user_id) VALUES (?,'2026-07-15',100,'N',?)",ids.get(4),actor);
        db.sql.update("INSERT INTO tb_bl_rcpt(bill_id,rcpt_dt,rcpt_amt,cncl_yn,reg_user_id) VALUES (?,'2026-07-15',100,'Y',?)",ids.get(2),actor);
        assertThat(mapper.receivableCount()).isEqualTo(baseline+3);
        db.sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('202607','CLOSE',?) ON CONFLICT(close_ym) DO UPDATE SET close_stat_cd='CLOSE'",actor);
        assertThat(transaction.execute(status->service.getNotificationUnreadCount()).currentMonthUnclosed()).isFalse();
    }
}
