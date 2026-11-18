package com.sicost.receivablesops;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.notifications.*;
import com.sicost.common.persistence.PersistenceBoundary;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.sicost.receivablesops.DunningDeliveryDtos.*;
import static com.sicost.common.notifications.NotificationPublicationMapper.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class DunningDeliveryDatabaseTest {
    com.sicost.permission.NotificationRecipientPolicy recipientPolicy;
    PostgreSqlDatabase db;
    DunningDeliveryMapper mapper;
    NotificationPublicationMapper publication;
    ChangeHistoryMapper audit;
    TransactionTemplate transaction;
    String billId,projectId;
    NotificationRecipients.Recipient recipient;
    final Clock clock=Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"),ZoneOffset.UTC);
    @BeforeEach void setup() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        // With no project sales part, the second dunning step falls back to EXEC.
        db.sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES ('admin','EXEC','SYSTEM')");
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(com.sicost.permission.PermissionMapper.class);configuration.addMapper(com.sicost.permission.NotificationTargetMapper.class);
        configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new ClassPathResource("mappers/receivablesops/DunningDeliveryMapper.xml"),new ClassPathResource("mappers/common/NotificationPublicationMapper.xml"),new ClassPathResource("mappers/common/ChangeHistoryMapper.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        // Re-evaluate the recipient against real saved roles, menus and target scope.
        var permissions=new com.sicost.permission.PermissionService(session.getMapper(com.sicost.permission.PermissionMapper.class),clock);
        recipientPolicy=new com.sicost.permission.NotificationRecipientPolicy(permissions,session.getMapper(com.sicost.permission.PermissionMapper.class),session.getMapper(com.sicost.permission.NotificationTargetMapper.class),clock);
        recipientPolicy.setPublication(session.getMapper(NotificationPublicationMapper.class),new DataSourceTransactionManager(db.source));mapper=session.getMapper(DunningDeliveryMapper.class);publication=session.getMapper(NotificationPublicationMapper.class);audit=session.getMapper(ChangeHistoryMapper.class);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(db.source));
        var actor=publication.eligibleRoles().stream().filter(role->role.role().equals("EXEC")).findFirst().orElseThrow();recipient=new NotificationRecipients.Recipient(actor.userId(),actor.name(),actor.emailAddress(),List.of(actor.role()));
        projectId="P-"+UUID.randomUUID().toString().replace("-","").substring(0,14);billId="B-"+UUID.randomUUID().toString().replace("-","").substring(0,14);
        db.sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2026-01-01','2026-12-31',100,100,0,0,?)",projectId,projectId,recipient.userId());
        db.sql.update("INSERT INTO tb_bl_bill(bill_id,proj_id,bill_ord,bill_ym,bill_dt,sply_amt,vat_rt,vat_amt,tot_amt,rcpt_sum_amt,unpaid_amt,pay_due_dt,reg_user_id) VALUES (?,?,1,'202606','2026-06-01',100,10,10,110,0,110,'2026-06-01',?)",billId,projectId,recipient.userId());
    }
    @AfterEach void cleanup() {
        if(db!=null){String schema=db.sql.queryForObject("SELECT current_schema()",String.class);if(schema==null||!schema.matches("sicost_test_[a-f0-9]{32}"))throw new IllegalStateException("시험 스키마만 정리합니다.");db.sql.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
    ChangeHistoryWriter writer(ChangeHistoryMapper target) {return new ChangeHistoryWriter(target,mock(CurrentUserProvider.class),clock,JsonMapper.builder().findAndAddModules().build());}
    NotificationPublisher publisher() {var publisher=new NotificationPublisher(publication,writer(audit),clock);publisher.setRecipientPolicy(recipientPolicy);return publisher;}
    Event event() {return new Event(NotificationEventKeys.manualDunning(billId,UUID.randomUUID()),"DUN","미수 알림",new Link("UI-13",billId),new Dunning(billId,2,1,true));}
    MailReservation mail(Event event) {return new MailReservation(UUID.randomUUID().toString(),event.key(),billId,1,2,true,recipient.userId(),recipient.name(),"test@example.invalid","FAILED","MAIL_NOT_CONFIGURED","메일 미설정","미수 알림","제목","<p>"+"전체 증거".repeat(1000)+"</p>","<fixed@sicost.invalid>",LocalDateTime.of(2026,7,15,9,0));}
    @Test void realPostgresMaterializesFrozenEvidenceAndFinalizesExactlyOnceWithoutChangingMoney() {
        var repository=new DunningDeliveryRepository(mapper,writer(audit),clock);var event=event();var reservation=mail(event);
        String original=mapper.snapshot(billId);
        transaction.executeWithoutResult(s->{repository.lock(billId);publisher().publish(event,List.of(recipient));repository.reserve(reservation);repository.finalizeRound(billId,event.key());repository.finalizeRound(billId,event.key());});
        assertThat(repository.rounds(billId)).hasSize(1);assertThat(repository.rounds(billId).get(0).tasks()).hasSize(2);assertThat(repository.rounds(billId).get(0).history().sendMethodCode()).isEqualTo("SYS");assertThat(db.count("tb_bl_dun_hist")).isEqualTo(1);
        assertThat(mapper.bill(billId).unpaidAmount()).isEqualByComparingTo("110");assertThat(mapper.snapshot(billId)).isNotEqualTo(original);
        String before=mapper.snapshot(billId);repository.rounds(billId);repository.required(billId);assertThat(mapper.snapshot(billId)).isEqualTo(before);
        db.sql.update("DELETE FROM tb_sy_noti WHERE rcvr_user_id=?",recipient.userId());
        assertThat(repository.rounds(billId).get(0).tasks()).allMatch(t->t.notificationId()==null);
        assertThat(db.sql.queryForObject("SELECT mail_body FROM tb_sy_delivery_task WHERE task_id=?::uuid",String.class,reservation.taskId())).isEqualTo(reservation.body());
        assertThat(mapper.nextSerial(billId)).isEqualTo(2);
    }
    @Test void revokedDunningRecipientIsSkippedWithRequiredEvidenceAndNoDisclosureOrDuplicate() {
        var event=event();
        assertThat(recipientPolicy.review(event,recipient.userId())).isEqualTo(event);
        db.sql.update("DELETE FROM tb_sy_user_role WHERE user_id=? AND role_id='EXEC'",recipient.userId());
        var publisher=publisher();
        var skipped=transaction.execute(s->publisher.publish(event,List.of(recipient)));assertThat(skipped).isEmpty();
        var task=db.sql.queryForMap("SELECT * FROM tb_sy_delivery_task");
        assertThat(task).containsEntry("state_cd","SKIPPED").containsEntry("error_cd","PERMISSION_REVOKED")
            .containsEntry("bill_id",billId).containsEntry("dun_step",new java.math.BigDecimal("2"))
            .containsEntry("dun_sn",new java.math.BigDecimal("1")).containsEntry("resend_yn","Y");
        assertThat(task.get("link_scrn_id")).isNull();assertThat(task.get("link_key")).isNull();
        assertThat(task.get("mail_body")).isNull();assertThat(task.get("noti_cn")).isEqualTo("PERMISSION_REVOKED");
        assertThat(db.count("tb_sy_noti")).isZero();assertThat(db.count("tb_bl_dun_hist")).isZero();
        assertThat(mapper.bill(billId).unpaidAmount()).isEqualByComparingTo("110");
        long auditCount=db.count("tb_sy_chg_hist");assertThat(auditCount).isPositive();
        var repeated=transaction.execute(s->publisher.publish(event,List.of(recipient)));assertThat(repeated).isEmpty();
        assertThat(db.count("tb_sy_delivery_task")).isEqualTo(1);assertThat(db.count("tb_sy_chg_hist")).isEqualTo(auditCount);
    }
    @Test void mailAuditFailureRollsBackSystemTaskNotificationAndEveryReservedRow() {
        var failed=mock(ChangeHistoryMapper.class);when(failed.insert(any())).thenReturn(0);var event=event();
        var repository=new DunningDeliveryRepository(mapper,writer(failed),clock);
        String before=mapper.snapshot(billId);
        assertThatThrownBy(()->transaction.executeWithoutResult(s->{repository.lock(billId);publisher().publish(event,List.of(recipient));repository.reserve(mail(event));})).isInstanceOf(IllegalStateException.class);
        assertThat(mapper.snapshot(billId)).isEqualTo(before);assertThat(db.count("tb_sy_noti")).isZero();assertThat(db.count("tb_sy_delivery_task")).isZero();assertThat(db.count("tb_bl_dun_hist")).isZero();assertThat(db.count("tb_sy_chg_hist")).isZero();
    }
}
