package com.sicost.apiops;

import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.automationrunsops.DailyAutomationDtos.Stored;
import com.sicost.notificationsops.NotificationDtos.StoredNotification;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ActivityReadController.class)
@ContextConfiguration(classes=ActivityReadController.class)
@Import({ActivityReadService.class,ActivityReadRepository.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ActivityReadContractTest {
    @Autowired MockMvc mvc;
    @MockBean ActivityReadMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean Clock clock;
    final LocalDateTime now=LocalDateTime.of(2026,7,15,0,0);
    @BeforeEach void setup() {
        Clock fixed=Clock.fixed(Instant.parse("2026-07-14T15:00:00Z"),ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("recipient","임시 관리자",List.of("ADMIN"),true));
        when(mapper.notifications(any())).thenReturn(List.of());when(mapper.automationRuns(any())).thenReturn(List.of());
    }
    @Test void notificationPageUsesServerRecipientAndPreservesNullReadTime() throws Exception {
        var rows=new ArrayList<StoredNotification>();for(int i=45;i>=1;i--)rows.add(new StoredNotification(BigDecimal.valueOf(i),"DUN","실제 알림 "+i,"BOTH",false,null,now,"UI-13","B-202607-001"));
        when(mapper.notifications(any())).thenAnswer(call->{var q=(ActivityReadDtos.RecipientQuery)call.getArgument(0);return rows.stream().skip(q.offset()).limit(q.size()).toList();});
        when(mapper.notificationCount("recipient")).thenReturn(45L);
        mvc.perform(get("/api/notifications")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(45)).andExpect(jsonPath("$.data.items.length()").value(20)).andExpect(jsonPath("$.data.items[0].id").value("45")).andExpect(jsonPath("$.data.items[0].readAt").isEmpty()).andExpect(jsonPath("$.data.hasMore").value(true));
        mvc.perform(get("/api/notifications").param("page","2")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value("25")).andExpect(jsonPath("$.data.items.length()").value(20));
        verify(mapper).notifications(new ActivityReadDtos.RecipientQuery("recipient",20,0));verify(mapper).notifications(new ActivityReadDtos.RecipientQuery("recipient",20,20));
    }
    @Test void emptyNotificationsAreAnActualEmptyPageWithoutReadOrSend() throws Exception {
        for(int i=0;i<2;i++)mvc.perform(get("/api/notifications")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(0));
        verify(mapper,times(2)).notifications(new ActivityReadDtos.RecipientQuery("recipient",20,0));verify(mapper,times(2)).notificationCount("recipient");verifyNoMoreInteractions(mapper);
    }
    @Test void unsafeLinkKeyCannotBecomeNavigation() throws Exception {
        when(mapper.notifications(any())).thenReturn(List.of(new StoredNotification(BigDecimal.ONE,"UPLD_DONE","완료","SYS",true,now,now,"UI-13","javascript:alert(1)")));
        mvc.perform(get("/api/notifications")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].link").isEmpty());
    }
    @Test void deletedLinkRemainsAnOrdinaryNotification() throws Exception {
        when(mapper.notifications(any())).thenReturn(List.of(new StoredNotification(BigDecimal.ONE,"DUN","원문 유지","SYS",false,null,now,null,"removed")));
        mvc.perform(get("/api/notifications")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].content").value("원문 유지")).andExpect(jsonPath("$.data.items[0].link").isEmpty());
    }
    @Test void realJobStatesAndCheckpointsAreReturnedWithoutExecutingAnything() throws Exception {
        when(mapper.automationRuns(any())).thenReturn(List.of(new Stored("DAILY_OPERATIONS",now.toLocalDate(),"FAILED",3,now,now.plusHours(1),"단계 4 · 대상 P · 원천 확인 실패"),new Stored("RECEIVABLE_DUNNING",now.toLocalDate(),"RUNNING",0,now,null,null),new Stored("DAILY_OPERATIONS",now.toLocalDate().minusDays(1),"SUCCEEDED",7,now.minusDays(1),now.minusDays(1).plusMinutes(1),null)));
        when(mapper.automationCount(any())).thenReturn(3L);
        for(int i=0;i<2;i++)mvc.perform(get("/api/automation-runs")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].state").value("FAILED")).andExpect(jsonPath("$.data.items[0].lastStep").value(3)).andExpect(jsonPath("$.data.items[1].endedAt").isEmpty()).andExpect(jsonPath("$.data.items[2].state").value("SUCCEEDED")).andExpect(jsonPath("$.data.manualRunEnabled").value(false));
        verify(mapper,times(2)).automationRuns(any());verify(mapper,times(2)).automationCount(any());verifyNoMoreInteractions(mapper);
    }
    @Test void absentRunRecordsNeverBecomeSyntheticSuccess() throws Exception {
        mvc.perform(get("/api/automation-runs")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void jobFilterAndPageArePassedToBothQueries() throws Exception {
        mvc.perform(get("/api/automation-runs").param("jobCode","RECEIVABLE_DUNNING").param("page","2").param("size","20")).andExpect(status().isOk());
        var expected=new ActivityQueryInput.Query(2,20,"RECEIVABLE_DUNNING",20);verify(mapper).automationRuns(expected);verify(mapper).automationCount(expected);
    }
    @ParameterizedTest @CsvSource({"/api/notifications,page,0","/api/notifications,size,30","/api/notifications,sort,id","/api/automation-runs,page,0","/api/automation-runs,size,30","/api/automation-runs,sort,runDate","/api/automation-runs,jobCode,OTHER"})
    void invalidConditionsHaveLocalizedFieldAndRequestId(String path,String field,String value) throws Exception {
        mvc.perform(get(path).param(field,value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field)).andExpect(jsonPath("$.requestId").isString()).andExpect(header().exists("X-Request-Id"));verifyNoInteractions(mapper);
    }
    @ParameterizedTest @CsvSource({"/api/notifications,userId","/api/notifications,actor","/api/notifications,from","/api/automation-runs,runDate","/api/automation-runs,from","/api/automation-runs,to","/api/automation-runs,userId"})
    void unknownConditionsNeverReachBusinessQueries(String path,String field) throws Exception {
        mvc.perform(get(path).param(field,"injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper);
    }
    @Test void duplicateQueryIsNotSilentlyCollapsed() throws Exception {
        mvc.perform(get("/api/notifications").param("size","20","50")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);
    }
    @Test void internalErrorsDoNotLeakSqlOrSecrets() throws Exception {
        when(mapper.notifications(any())).thenThrow(new IllegalStateException("SELECT private_secret FROM tb_sy_user"));
        mvc.perform(get("/api/notifications")).andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private_secret"))));
    }
}
