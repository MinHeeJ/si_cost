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
    @Test void financialTemplateAndIssuesRequireStructuredDownloadEvents() {
        when(users.userId()).thenReturn("admin");when(mapper.insert(any())).thenReturn(1);
        var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");
        writer.append(new AccessLogWriter.Event("DNLD","UI-10",null,"재무_등록양식",0L),request);
        writer.append(new AccessLogWriter.Event("DNLD","UI-10","999999999999","재무_업로드검사",2L),request);
        verify(mapper).insert(argThat(e -> e.userId().equals("admin") && e.screenId().equals("UI-10")
            && e.action().equals("DNLD") && e.description().equals("999999999999 재무_업로드검사 2")
            && e.clientIp().equals("127.0.0.1") && e.now().toString().equals("2026-01-02T08:59")));
        verify(mapper,times(2)).insert(any());
    }
    @Test void financialAuditRejectsOtherEventsMissingCountAndWrongTargetKind() {
        for (var event : new AccessLogWriter.Event[]{
                new AccessLogWriter.Event("ACCT_VIEW","UI-10",null,"재무_등록양식",0L),
                new AccessLogWriter.Event("USER_REG","UI-10",null,"재무_등록양식",0L),
                new AccessLogWriter.Event("DNLD","UI-10",null,"재무_등록양식",null),
                new AccessLogWriter.Event("DNLD","UI-10","123","재무_등록양식",0L),
                new AccessLogWriter.Event("DNLD","UI-10",null,"재무_업로드검사",0L),
                new AccessLogWriter.Event("DNLD","UI-10","0","재무_업로드검사",0L),
                new AccessLogWriter.Event("DNLD","UI-10","1\r\nunsafe","재무_업로드검사",0L),
                new AccessLogWriter.Event("DNLD","UI-10","1000000000000","재무_업로드검사",0L),
                new AccessLogWriter.Event("DNLD","UI-10",null,"개발자",0L)}) {
            assertThatThrownBy(() -> writer.append(event,new MockHttpServletRequest())).isInstanceOf(ApiException.class);
        }
        verifyNoInteractions(mapper,users);
    }
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
    @Test void peopleDownloadsAreBoundToTheirOwnScreenAndKind() {
        when(users.userId()).thenReturn("admin");when(mapper.insert(any())).thenReturn(1);
        for (String kind : new String[]{"개발자","개발자_등록양식","개발자_업로드검사"}) {
            writer.append(new AccessLogWriter.Event("DNLD","UI-02",kind.endsWith("검사")?"123":null,kind,2L),new MockHttpServletRequest());
        }
        writer.append(new AccessLogWriter.Event("DNLD","UI-33",null,"외부인력후보",2L),new MockHttpServletRequest());
        verify(mapper,times(3)).insert(argThat(entry -> entry.screenId().equals("UI-02") && entry.action().equals("DNLD")));
        verify(mapper).insert(argThat(entry -> entry.screenId().equals("UI-33") && entry.description().contains("외부인력후보")));
    }
    @Test void peopleScreensCannotAcquireOtherAccessEventsOrMismatchedKinds() {
        for (String action : new String[]{"USER_REG","ACCT_VIEW"}) {
            assertThatThrownBy(() -> writer.append(new AccessLogWriter.Event(action,"UI-02",null,"개발자",1L),new MockHttpServletRequest())).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> writer.append(new AccessLogWriter.Event("DNLD","UI-33",null,"개발자",1L),new MockHttpServletRequest())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> writer.append(new AccessLogWriter.Event("DNLD","UI-02",null,null,1L),new MockHttpServletRequest())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> writer.append(new AccessLogWriter.Event("DNLD","UI-02",null,"개발자\r\nunsafe",1L),new MockHttpServletRequest())).isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper);
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
    @Test void projectDownloadsRetainStructuredAuditAndRejectOtherActionsAndKinds() {
        when(users.userId()).thenReturn("admin");when(mapper.insert(any())).thenReturn(1);
        for (String kind : new String[]{"프로젝트", "턴키배분표", "프로젝트_등록양식", "턴키배분표_등록양식", "프로젝트_업로드검사", "턴키배분표_업로드검사"}) {
            writer.append(new AccessLogWriter.Event("DNLD","UI-01",kind.endsWith("검사")?"123":null,kind,0L),new MockHttpServletRequest());
        }
        verify(mapper,times(6)).insert(argThat(entry -> entry.screenId().equals("UI-01") && entry.action().equals("DNLD")));
        for (AccessLogWriter.Event event : new AccessLogWriter.Event[]{
                new AccessLogWriter.Event("USER_REG","UI-01",null,"프로젝트",0L),
                new AccessLogWriter.Event("DNLD","UI-01",null,null,0L),
                new AccessLogWriter.Event("DNLD","UI-01",null,"개발자",0L),
                new AccessLogWriter.Event("DNLD","UI-01","1\r\nunsafe","프로젝트_업로드검사",0L)}) {
            assertThatThrownBy(() -> writer.append(event,new MockHttpServletRequest())).isInstanceOf(ApiException.class);
        }
        verify(mapper,times(6)).insert(any());
    }
}
