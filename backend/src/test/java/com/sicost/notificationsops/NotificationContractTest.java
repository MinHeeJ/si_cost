package com.sicost.notificationsops;

import com.sicost.common.api.*;
import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.context.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.notificationsops.NotificationDtos.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest(NotificationController.class)
@ContextConfiguration(classes=NotificationController.class)
@Import({NotificationService.class,NotificationRepository.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class NotificationContractTest {
    @Autowired MockMvc mvc;
    @MockBean NotificationMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean Clock clock;
    @MockBean CommitInvalidation invalidation;
    final LocalDateTime time=LocalDateTime.of(2026,7,15,0,0);
    @BeforeEach void setup() {
        Clock fixed=Clock.fixed(Instant.parse("2026-07-14T15:00:00Z"),ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(mapper.currentMonthUnclosed("202607")).thenReturn(true);
    }
    StoredNotification row(boolean read,String screen,String key) {
        return new StoredNotification(BigDecimal.ONE,"DUN","미수 알림","BOTH",read,read?time.minusDays(1):null,time.minusDays(2),screen,key);
    }
    @Test void fullIntegerUnreadCountAndCurrentBusinessBadges() throws Exception {
        when(mapper.unreadCount("admin")).thenReturn(101L);when(mapper.receivableCount()).thenReturn(4L);
        mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.unreadCount").value(101)).andExpect(jsonPath("$.data.receivableCount").value(4))
            .andExpect(jsonPath("$.data.currentMonthUnclosed").value(true)).andExpect(jsonPath("$.data.observedAt").value("2026-07-15T00:00:00+09:00"));
        verify(mapper).unreadCount("admin");verifyNoInteractions(invalidation);
    }
    @Test void emptyCountIsRealZero() throws Exception {
        mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(0));
    }
    @Test void usesServerCurrentMonthNotHistoricalSelection() throws Exception {
        when(mapper.currentMonthUnclosed("202607")).thenReturn(false);
        mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isOk()).andExpect(jsonPath("$.data.currentMonthUnclosed").value(false));
        verify(mapper).currentMonthUnclosed("202607");
    }
    @Test void countFailureDoesNotInventZeroOrLeakSql() throws Exception {
        when(mapper.unreadCount("admin")).thenThrow(new IllegalStateException("secret SQL"));
        mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.data").doesNotExist()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL"))))
            .andExpect(jsonPath("$.requestId").isString()).andExpect(header().exists("X-Request-Id"));
    }
    @Test void repeatedPollOnlyReads() throws Exception {
        for(int i=0;i<3;i++)mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isOk());
        verify(mapper,times(3)).unreadCount("admin");verify(mapper,times(3)).receivableCount();verify(mapper,times(3)).currentMonthUnclosed("202607");
        verifyNoMoreInteractions(mapper);verifyNoInteractions(invalidation);
    }
    @ParameterizedTest @ValueSource(strings={"userId","actor","size","page","month","sort"})
    void unknownCountQueryRejectedBeforeReads(String field) throws Exception {
        mvc.perform(get("/api/notifications/unread-count").param(field,"injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(mapper,users);
    }
    @Test void readAllUsesOneAtomicRecipientBoundedWrite() throws Exception {
        when(mapper.markAllRead(new ReadAllWrite("admin",time))).thenReturn(3);
        mvc.perform(post("/api/notifications/read-all")).andExpect(status().isOk()).andExpect(jsonPath("$.data.changedCount").value(3))
            .andExpect(jsonPath("$.data.cutoffAt").value("2026-07-15T00:00:00+09:00"));
        verify(mapper).markAllRead(new ReadAllWrite("admin",time));verifyNoMoreInteractions(mapper);
        verify(invalidation).afterCommit("notifications:admin");
    }
    @Test void readAllDoesNotRequireRecipientRoleOrInventForbidden() throws Exception {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("receiver","수신자",List.of(),true));
        mvc.perform(post("/api/notifications/read-all")).andExpect(status().isOk());
        verify(mapper).markAllRead(new ReadAllWrite("receiver",time));
    }
    @ParameterizedTest @ValueSource(strings={"{}","null","{\"userId\":\"other\"}","{\"readAt\":\"2020-01-01\"}"," "})
    void readAllRejectsAnyBody(String body) throws Exception {
        mvc.perform(post("/api/notifications/read-all").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(mapper,users,invalidation);
    }
    @ParameterizedTest @ValueSource(strings={"userId","cutoffAt","page","ids"})
    void readAllRejectsQuery(String field) throws Exception {
        mvc.perform(post("/api/notifications/read-all").param(field,"injected")).andExpect(status().isBadRequest());verifyNoInteractions(mapper,users,invalidation);
    }
    @Test void readAllFailureDoesNotPublishCommittedChange() throws Exception {
        when(mapper.markAllRead(any())).thenThrow(new IllegalStateException("write failed"));
        mvc.perform(post("/api/notifications/read-all")).andExpect(status().isInternalServerError());verifyNoInteractions(invalidation);
    }
    @Test void readAllNoOpDoesNotPublish() throws Exception {
        mvc.perform(post("/api/notifications/read-all")).andExpect(status().isOk()).andExpect(jsonPath("$.data.changedCount").value(0));verifyNoInteractions(invalidation);
    }
    @Test void readsOwnedNotificationWithServerFirstTimestampAndRegisteredLink() throws Exception {
        when(mapper.lock("admin",BigDecimal.ONE)).thenReturn(row(false,"UI-13","B-202607-001"));
        when(mapper.markRead(any())).thenReturn(1);when(mapper.registeredScreen("UI-13")).thenReturn(true);
        mvc.perform(post("/api/notifications/1/read")).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true))
            .andExpect(jsonPath("$.data.notification.id").value("1")).andExpect(jsonPath("$.data.notification.read").value(true))
            .andExpect(jsonPath("$.data.notification.readAt").value("2026-07-15T00:00:00+09:00"))
            .andExpect(jsonPath("$.data.notification.link.screenId").value("UI-13")).andExpect(jsonPath("$.data.notification.link.linkKey").value("B-202607-001"));
        verify(mapper).markRead(new ReadWrite("admin",BigDecimal.ONE,time,time));verify(invalidation).afterCommit("notifications:admin");
    }
    @Test void repeatedReadKeepsFirstReadTimeAndDoesNotWrite() throws Exception {
        when(mapper.lock("admin",BigDecimal.ONE)).thenReturn(row(true,null,null));
        mvc.perform(post("/api/notifications/1/read")).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false))
            .andExpect(jsonPath("$.data.notification.readAt").value("2026-07-14T00:00:00+09:00"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"link\":null")));
        verify(mapper).lock("admin",BigDecimal.ONE);verifyNoMoreInteractions(mapper);verifyNoInteractions(invalidation);
    }
    @Test void otherRecipientOrMissingIdIs404Not403() throws Exception {
        mvc.perform(post("/api/notifications/1/read")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verify(mapper).lock("admin",BigDecimal.ONE);verifyNoMoreInteractions(mapper);verifyNoInteractions(invalidation);
    }
    @ParameterizedTest @ValueSource(strings={"abc","-1","1.0","1234567890123","1e1"})
    void invalidIdRejectedWithoutWrite(String id) throws Exception {
        mvc.perform(post("/api/notifications/"+id+"/read")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("id"));verifyNoInteractions(mapper,invalidation);
    }
    @Test void readRejectsInjectedBodyAndQuery() throws Exception {
        mvc.perform(post("/api/notifications/1/read").contentType("application/json").content("{\"readAt\":\"2000-01-01\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/notifications/1/read").param("userId","other")).andExpect(status().isBadRequest());verifyNoInteractions(mapper,users,invalidation);
    }
    @ParameterizedTest @ValueSource(strings={"javascript:alert(1)","https://external.test","../secret","bad key"})
    void unsafeLinkIsSuppressedButReadIsKept(String key) throws Exception {
        when(mapper.lock("admin",BigDecimal.ONE)).thenReturn(row(false,"UI-13",key));when(mapper.markRead(any())).thenReturn(1);
        mvc.perform(post("/api/notifications/1/read")).andExpect(status().isOk()).andExpect(jsonPath("$.data.notification.read").value(true))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"link\":null")));
        verify(mapper,never()).registeredScreen(any());
    }
    @Test void removedMenuReturnsNullLinkAfterRead() throws Exception {
        when(mapper.lock("admin",BigDecimal.ONE)).thenReturn(row(false,"UI-13","B-202607-001"));when(mapper.markRead(any())).thenReturn(1);
        mvc.perform(post("/api/notifications/1/read")).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"link\":null")));
    }
    @Test void failedSingleUpdateDoesNotPublish() throws Exception {
        when(mapper.lock("admin",BigDecimal.ONE)).thenReturn(row(false,null,null));
        mvc.perform(post("/api/notifications/1/read")).andExpect(status().isInternalServerError());verifyNoInteractions(invalidation);
    }
}
