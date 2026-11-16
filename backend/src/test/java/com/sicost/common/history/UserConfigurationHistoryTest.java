package com.sicost.common.history;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserConfigurationHistoryTest {
    final ChangeHistoryMapper mapper=mock(ChangeHistoryMapper.class);
    final CurrentUserProvider users=mock(CurrentUserProvider.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-06-30T15:01:00Z"),ZoneOffset.UTC);
    final ChangeHistoryWriter writer=new ChangeHistoryWriter(mapper,users,clock,JsonMapper.builder().findAndAddModules().build());

    @Test void configurationFieldsUseOriginalOwnerKeysPinnedActorAndSeoulTime() {
        when(users.userId()).thenReturn("admin");when(mapper.insert(any())).thenReturn(1);
        writer.append(new ChangeHistoryWriter.Change("TB_SY_USER_ROLE","ab12:PM","DEL","ROLE_ID","PM",null,null));
        writer.append(new ChangeHistoryWriter.Change("TB_SY_USER_SCOPE","ab12:5","NEW","PART_ID",null,"HQ",null));
        writer.append(new ChangeHistoryWriter.Change("TB_SY_ROLE_MENU_AUTH","R-001:M001","UPD","INQ_YN","N","Y",null));
        writer.append(new ChangeHistoryWriter.Change("TB_SY_ROLE","R-001","STOP","USE_YN","Y","N",null));
        writer.append(new ChangeHistoryWriter.Change("TB_SY_MENU","M001","UPD","MENU_NM","이전","이후",null));
        verify(mapper,times(5)).insert(argThat(e -> e.userId().equals("admin") && e.occurredAt().toString().equals("2026-07-01T00:01")));
        verify(mapper).insert(argThat(e -> e.targetKey().equals("ab12:PM") && e.after()==null));
        verify(mapper).insert(argThat(e -> e.targetKey().equals("R-001:M001") && e.after().equals("Y")));
        verify(users,times(5)).userId();
    }
    @Test void zeroOrMultipleInsertedAuditRowsFailEveryRequiredConfigurationTarget() {
        when(users.userId()).thenReturn("admin");
        for (String table : new String[]{"TB_SY_USER","TB_SY_USER_ROLE","TB_SY_USER_SCOPE","TB_SY_ROLE","TB_SY_ROLE_MENU_AUTH","TB_SY_MENU"}) {
            for (int count : new int[]{0,2}) {
                when(mapper.insert(any())).thenReturn(count);
                assertThatThrownBy(() -> writer.append(new ChangeHistoryWriter.Change(table,"ab12:PM","NEW","등록",null,"새 값",null)))
                    .isInstanceOf(IllegalStateException.class);
            }
        }
    }
    @Test void unchangedConfigurationDoesNotResolveActorOrCreateHistory() {
        for (String table : new String[]{"TB_SY_USER","TB_SY_USER_ROLE","TB_SY_ROLE","TB_SY_MENU","TB_SY_ROLE_MENU_AUTH"})
            writer.append(new ChangeHistoryWriter.Change(table,"ab12","UPD","이름","동일","동일",null));
        verifyNoInteractions(mapper,users);
    }
    @Test void secretsAndOversizedChildValuesCannotEnterHistory() {
        for (String field : new String[]{"PWD_HASH","MFA_CODE_HASH","PASSWORD"})
            assertThatThrownBy(() -> writer.append(new ChangeHistoryWriter.Change("TB_SY_USER","ab12","UPD",field,"before","after",null)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> writer.append(new ChangeHistoryWriter.Change("TB_SY_USER_ROLE","ab12:PM","NEW","역할",null,"가".repeat(501),null)))
            .isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper,users);
    }
    @Test void authChangeAppendsOnlyOneStructuredEventWithServerIpAndActor() {
        var access=mock(AccessLogMapper.class);when(access.insert(any())).thenReturn(1);when(users.userId()).thenReturn("admin");
        var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");request.addHeader("X-Forwarded-For","injected");
        new AccessLogWriter(access,users,clock).append(new AccessLogWriter.Event("AUTH_CHG","UI-24","R-001",null,null),request);
        verify(access).insert(argThat(e -> e.action().equals("AUTH_CHG") && e.description().equals("R-001") && e.userId().equals("admin")
            && e.clientIp().equals("127.0.0.1") && e.now().toString().equals("2026-07-01T00:01")));
    }
    @Test void authChangeRejectsOtherScreensAndUnstructuredValues() {
        var access=mock(AccessLogMapper.class);var logs=new AccessLogWriter(access,users,clock);
        for (var event : new AccessLogWriter.Event[]{new AccessLogWriter.Event("AUTH_CHG","UI-25","M001",null,null),
                new AccessLogWriter.Event("AUTH_CHG","UI-24","R-001\r\nunsafe",null,null),
                new AccessLogWriter.Event("AUTH_CHG","UI-24","R-001","free text",null),
                new AccessLogWriter.Event("AUTH_CHG","UI-24","R-001",null,1L)})
            assertThatThrownBy(() -> logs.append(event,new MockHttpServletRequest())).isInstanceOf(ApiException.class);
        verifyNoInteractions(access,users);
    }
    @Test void authChangeFailureIsRequiredAndPropagates() {
        var access=mock(AccessLogMapper.class);when(users.userId()).thenReturn("admin");
        assertThatThrownBy(() -> new AccessLogWriter(access,users,clock).append(
            new AccessLogWriter.Event("AUTH_CHG","UI-23","ab12",null,null),new MockHttpServletRequest()))
            .isInstanceOf(IllegalStateException.class);
    }
}
