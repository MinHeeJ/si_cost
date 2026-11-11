package com.sicost.common.history;

import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessLogWriterTest {
    final AccessLogMapper mapper=mock(AccessLogMapper.class);
    final CurrentUserProvider users=mock(CurrentUserProvider.class);
    final AccessLogWriter writer=new AccessLogWriter(mapper,users,Clock.fixed(Instant.parse("2026-01-01T23:59:00Z"),ZoneId.of("Asia/Seoul")));
    @Test void successfulActionsUseServerActorSeoulTimeAndObservedIpExactlyOnce() {
        when(users.userId()).thenReturn("admin"); when(mapper.insert(any())).thenReturn(1);
        MockHttpServletRequest request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");request.addHeader("X-Forwarded-For","untrusted");
        for(String action:new String[]{"DNLD","ACCT_VIEW","USER_REG"})writer.append(new AccessLogWriter.Event(action,action.equals("USER_REG")?"UI-23":"UI-15","testuser",null,1L),request);
        verify(mapper,times(3)).insert(argThat(entry->entry.userId().equals("admin") && entry.clientIp().equals("127.0.0.1")
                && entry.now().toString().equals("2026-01-02T08:59") && entry.description().equals("testuser 1")));
    }
    @Test void logFailureIsNotSwallowed() {
        when(users.userId()).thenReturn("admin");when(mapper.insert(any())).thenReturn(0);
        assertThatThrownBy(()->writer.append(new AccessLogWriter.Event("ACCT_VIEW","UI-15","C-001",null,null),new MockHttpServletRequest()))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void descriptionsCannotAcceptUnstructuredSecretsOrUnknownEvents() {
        assertThatThrownBy(()->writer.append(new AccessLogWriter.Event("OTHER","UI-15","C-001",null,null),new MockHttpServletRequest())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->writer.append(new AccessLogWriter.Event("ACCT_VIEW","UI-15","contains spaces and arbitrary payload",null,null),new MockHttpServletRequest())).isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper);
    }
}
