package com.sicost.common.notifications;

import com.sicost.common.api.ApiException;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.PersistenceBoundary;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static com.sicost.common.notifications.NotificationPublicationMapper.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationPublisherTest {
    final NotificationPublicationMapper mapper=mock(NotificationPublicationMapper.class);
    final ChangeHistoryWriter history=mock(ChangeHistoryWriter.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-07-14T15:00:00Z"),ZoneOffset.UTC);
    final NotificationPublisher publisher=new NotificationPublisher(mapper,history,clock);
    final NotificationRecipients.Recipient recipient=new NotificationRecipients.Recipient("pm","담당",null,List.of("PM"));
    final Event event=new Event("UPLD:123:APPLIED","UPLD_DONE","업로드 반영 완료",new Link("UI-13","123"),null);
    @BeforeEach void setup() {
        when(mapper.eligibleRoles()).thenReturn(List.of(new NotificationRecipients.Role("pm","현재 담당","current@example.test","PM")));
        when(mapper.registeredScreen("UI-13")).thenReturn(true);
        when(mapper.reserveSystem(any())).thenReturn(1);
        when(mapper.insertNotification(any())).thenReturn(new BigDecimal("123"));
        when(mapper.connectNotification(any())).thenReturn(1);
    }
    @Test void publicationUsesCurrentDbNameAndDeduplicatesUserBeforeAtomicSystemEvidence() {
        var created=publisher.publish(event,List.of(recipient,recipient));
        assertThat(created).hasSize(1);
        verify(mapper).reserveSystem(argThat(p->p.recipient().name().equals("현재 담당")&&p.recipient().emailAddress().equals("current@example.test")&&p.at().toString().equals("2026-07-15T00:00")));
        verify(mapper).insertNotification(any());
        verify(history).appendAsSystem(argThat(c->c.tableName().equals("TB_SY_DELIVERY_TASK")&&c.item().equals("STATE_CD")&&c.after().equals("SENT")));
        verify(history).appendAsSystem(argThat(c->c.tableName().equals("TB_SY_NOTI")&&c.item().equals("SEND_MTHD_CD")&&c.after().equals("SYS")));
    }
    @Test void durableDuplicateDoesNotRecreateDeletedNotificationOrAppendAudit() {
        when(mapper.reserveSystem(any())).thenReturn(0);
        assertThat(publisher.publish(event,List.of(recipient))).isEmpty();
        verify(mapper,never()).insertNotification(any());verifyNoInteractions(history);
    }
    @Test void inactiveRecipientIsNotReplacedWithAdministrator() {
        when(mapper.eligibleRoles()).thenReturn(List.of(new NotificationRecipients.Role("admin","관리자",null,"ADMIN")));
        assertThat(publisher.publish(event,List.of(recipient))).isEmpty();
        verify(mapper,never()).reserveSystem(any());verifyNoInteractions(history);
    }
    @Test void mandatoryAuditFailurePropagatesToCallerTransaction() {
        doThrow(new IllegalStateException("audit failure")).when(history).appendAsSystem(any());
        assertThatThrownBy(()->publisher.publish(event,List.of(recipient))).isInstanceOf(IllegalStateException.class);
    }
    @Test void linkingFailureCannotReturnFalseSuccess() {
        when(mapper.connectNotification(any())).thenReturn(0);
        assertThatThrownBy(()->publisher.publish(event,List.of(recipient))).isInstanceOf(IllegalStateException.class);
    }
    @Test void notificationContentOverflowIsRejectedWithoutLosingReason() {
        assertThatThrownBy(()->publisher.publish(new Event(event.key(),event.type(),"가".repeat(301),event.link(),null),List.of(recipient))).isInstanceOf(ApiException.class);
        verify(mapper,never()).reserveSystem(any());
    }
    @Test void externalScriptAndUnregisteredScreenNeverBecomeNavigationTargets() {
        for(var link:List.of(new Link("https://x",null),new Link("UI-13","javascript:alert(1)"),new Link("UI-99","123"))) {
            assertThatThrownBy(()->publisher.publish(new Event(event.key(),event.type(),event.content(),link,null),List.of(recipient))).isInstanceOf(ApiException.class);
        }
        verify(mapper,never()).reserveSystem(any());
    }
    @Test void mapperStatementsUseExactBoundaryGrammarForGeneralAndDunningEvents() throws Exception {
        var configuration=new Configuration();
        var resource=new ClassPathResource("mappers/common/NotificationPublicationMapper.xml");
        try(var input=resource.getInputStream()) {new XMLMapperBuilder(input,configuration,resource.toString(),configuration.getSqlFragments()).parse();}
        for(Event e:List.of(event,new Event("DUN:A:B-202607-001:1","DUN","미수 알림",new Link("UI-13","B-202607-001"),new Dunning("B-202607-001",1,1,false)))) {
            var publication=new Publication(UUID.randomUUID(),e,recipient,LocalDateTime.of(2026,7,15,0,0));
            for(String method:List.of("reserveSystem","insertNotification")) {
                var statement=configuration.getMappedStatement(NotificationPublicationMapper.class.getName()+"."+method);
                assertThatCode(()->PersistenceBoundary.requireAllowed(statement.getBoundSql(publication).getSql())).doesNotThrowAnyException();
            }
        }
        var connection=new NotificationConnection(UUID.randomUUID(),BigDecimal.ONE);
        PersistenceBoundary.requireAllowed(configuration.getMappedStatement(NotificationPublicationMapper.class.getName()+".connectNotification").getBoundSql(connection).getSql());
    }
}
