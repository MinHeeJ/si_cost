package com.sicost.common.history;

import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonthCloseAccessLogTest {
    final AccessLogMapper mapper=mock(AccessLogMapper.class);
    final CurrentUserProvider users=mock(CurrentUserProvider.class);
    final AccessLogWriter writer=new AccessLogWriter(mapper,users,Clock.fixed(Instant.parse("2026-06-30T15:00:00Z"),ZoneOffset.UTC));
    final LocalDateTime at=LocalDateTime.of(2026,7,1,0,0);
    @Test void closeAndReopenUseOneCallerIdentityTimeAndHistoryKey() {
        when(mapper.insert(any())).thenReturn(1);
        for(String action:List.of("CLOSE","UNCLOSE"))writer.appendMonthClose(new AccessLogWriter.MonthCloseEvent(action,"202607",99999),"admin",at,new MockHttpServletRequest());
        verify(mapper).insert(argThat(e->e.action().equals("CLOSE")&&e.screenId().equals("UI-14")&&e.description().equals("202607:99999")&&e.userId().equals("admin")&&e.now().equals(at)));
        verify(mapper).insert(argThat(e->e.action().equals("UNCLOSE")&&e.now().equals(at)));verifyNoInteractions(users);
    }
    @Test void invalidActionMonthAndSequenceFailBeforeActorOrStorage() {
        for(var event:List.of(new AccessLogWriter.MonthCloseEvent("DELETE","202607",1),new AccessLogWriter.MonthCloseEvent("CLOSE","2026-07",1),new AccessLogWriter.MonthCloseEvent("CLOSE","202607",0),new AccessLogWriter.MonthCloseEvent("CLOSE","202607",100000)))
            assertThatThrownBy(()->writer.appendMonthClose(event,"admin",at,new MockHttpServletRequest())).isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper,users);
    }
    @Test void failedMandatoryAccessLogRejectsCommand() {
        assertThatThrownBy(()->writer.appendMonthClose(new AccessLogWriter.MonthCloseEvent("CLOSE","202607",1),"admin",at,new MockHttpServletRequest())).isInstanceOf(IllegalStateException.class);
    }
    @Test void clientIpIsValidatedInsteadOfCopiedAsProvided() {
        when(mapper.insert(any())).thenReturn(1);var request=new MockHttpServletRequest();request.setRemoteAddr("bad\r\nvalue");
        writer.appendMonthClose(new AccessLogWriter.MonthCloseEvent("CLOSE","202607",1),"admin",at,request);
        verify(mapper).insert(argThat(e->e.clientIp()==null));
    }
    @Test void cashDownloadRequiresExactKindCountAndServerUser() {
        when(mapper.insert(any())).thenReturn(1);when(users.userId()).thenReturn("admin");
        writer.append(new AccessLogWriter.Event("DNLD","UI-21",null,"자금수지표",0L),new MockHttpServletRequest());
        verify(mapper).insert(argThat(e->e.screenId().equals("UI-21")&&e.userId().equals("admin")&&e.description().equals("자금수지표 0")));
    }
    @Test void cashDownloadRejectsUnknownActionAndInjectedTargetOrFilename() {
        for(var event:List.of(new AccessLogWriter.Event("CLOSE","UI-21",null,"자금수지표",0L),new AccessLogWriter.Event("DNLD","UI-21","admin","자금수지표",0L),new AccessLogWriter.Event("DNLD","UI-21",null,"자금수지표",0L,"evil.xlsx"),new AccessLogWriter.Event("DNLD","UI-21",null,"자금수지표",null)))
            assertThatThrownBy(()->writer.append(event,new MockHttpServletRequest())).isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper,users);
    }
}
