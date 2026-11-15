package com.sicost.common.history;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutomationDeliveryHistoryTest {
    final ChangeHistoryMapper mapper=mock(ChangeHistoryMapper.class);
    final CurrentUserProvider users=mock(CurrentUserProvider.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneId.of("Asia/Seoul"));
    final ChangeHistoryWriter writer=new ChangeHistoryWriter(mapper,users,clock,JsonMapper.builder().findAndAddModules().build());
    final String task=UUID.randomUUID().toString();
    ChangeHistoryWriter.Change task(String type,String field,String before,String after) {
        return new ChangeHistoryWriter.Change("TB_SY_DELIVERY_TASK",task,type,field,before,after,null);
    }
    @Test void systemTaskStateUsesMandatoryAffectedRowCheckAndServerActor() {
        when(mapper.insert(any())).thenReturn(1);
        writer.appendAsSystem(task("UPD","STATE_CD","PENDING","SENDING"));
        verify(mapper).insert(argThat(e->e.userId().equals("SYSTEM")&&e.before().equals("PENDING")&&e.after().equals("SENDING")&&e.occurredAt().toString().equals("2026-01-01T09:00")));
        verifyNoInteractions(users);
    }
    @Test void zeroAffectedRowsRejectsSuccess() {
        assertThatThrownBy(()->writer.appendAsSystem(task("UPD","STATE_CD","PENDING","SENDING"))).isInstanceOf(IllegalStateException.class);
    }
    @Test void longBodyAuditReferencesCompleteImmutableTaskEvidenceWithoutHashOrTruncation() {
        when(mapper.insert(any())).thenReturn(1);
        writer.appendAsSystem(task("NEW","MAIL_BODY",null,"본문".repeat(1000)));
        verify(mapper).insert(argThat(e->e.before()==null&&e.after().equals(task+":MAIL_BODY")));
    }
    @Test void bodyCannotBeMutatedAfterReservation() {
        assertThatThrownBy(()->writer.appendAsSystem(task("UPD","MAIL_BODY","이전","변경"))).isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper);
    }
    @Test void immutableEventRecipientAndOriginalRunStartCannotBeChanged() {
        for(String field:new String[]{"EVENT_KEY","RCVR_NM","RCVR_USER_ID","MAIL_TO","BILL_ID","DUN_SN","NOTI_CN"}) {
            assertThatThrownBy(()->writer.appendAsSystem(task("UPD",field,"전","후"))).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(()->writer.appendAsSystem(new ChangeHistoryWriter.Change("TB_SY_AUTOMATION_RUN","DAILY_OPERATIONS:2026-01-01","UPD","START_DTTM","전","후",null))).isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper);
    }
    @Test void notificationFirstReadAndCleanupHaveNarrowFieldPolicy() {
        when(mapper.insert(any())).thenReturn(1);
        writer.appendAsSystem(new ChangeHistoryWriter.Change("TB_SY_NOTI","123","UPD","READ_YN","N","Y",null));
        writer.appendAsSystem(new ChangeHistoryWriter.Change("TB_SY_NOTI","123","DEL","NOTI_CN","보관 기한 경과",null,null));
        assertThatThrownBy(()->writer.appendAsSystem(new ChangeHistoryWriter.Change("TB_SY_NOTI","123","UPD","NOTI_CN","전","후",null))).isInstanceOf(ApiException.class);
        verify(mapper,times(2)).insert(any());
    }
    @Test void unchangedStateProducesNoAuditAndNullIsNotZero() {
        writer.appendAsSystem(task("UPD","ERROR_CN",null,null));
        verifyNoInteractions(mapper);
        when(mapper.insert(any())).thenReturn(1);
        writer.appendAsSystem(new ChangeHistoryWriter.Change("TB_SY_AUTOMATION_RUN","DAILY_OPERATIONS:2026-01-01","UPD","LAST_STEP",null,"0",null));
        verify(mapper).insert(argThat(e->e.before()==null&&e.after().equals("0")));
    }
    @Test void shortEvidenceRetainsExactWidthAndRejectsOverflow() {
        assertThatThrownBy(()->writer.appendAsSystem(task("NEW","NOTI_CN",null,"가".repeat(501)))).isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper);
    }
    @Test void onlyProjectEndAndCurrentAssignmentPointersExtendSystemAuthority() {
        when(mapper.insert(any())).thenReturn(1);
        writer.appendAsSystem(new ChangeHistoryWriter.Change("TB_PJ_PROJ","P-1","UPD","PROJ_STAT_CD","ING","END",null));
        writer.appendAsSystem(new ChangeHistoryWriter.Change("TB_PJ_ASGN","A-0001","UPD","CTRT_MM","1.00","0.50",null));
        assertThatThrownBy(()->writer.appendAsSystem(new ChangeHistoryWriter.Change("TB_PJ_PROJ","P-1","UPD","CTRT_AMT","1","2",null))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->writer.appendAsSystem(task("DEL","STATE_CD","FAILED",null))).isInstanceOf(ApiException.class);
        verifyNoInteractions(users);
    }
}
