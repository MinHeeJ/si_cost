package com.sicost.automationrunsops;

import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.automationrunsops.DailyAutomationDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest(DailyAutomationController.class)
@ContextConfiguration(classes=DailyAutomationController.class)
@Import({DailyAutomationService.class,DailyAutomationRepository.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
@TestPropertySource(properties="AUTOMATION_MANUAL_RUN_ENABLED=true")
class DailyAutomationContractTest {
    @Autowired MockMvc mvc;
    @Autowired DailyAutomationService service;
    @MockBean DailyAutomationMapper mapper;
    @MockBean DailyAutomationStages stages;
    @MockBean DailyAutomationSessionLocks locks;
    @MockBean MonthCloseBoundary boundary;
    @MockBean SettlementSourceLocks sourceLocks;
    @MockBean ChangeHistoryWriter history;
    @MockBean CurrentUserProvider users;
    @MockBean Clock clock;
    @MockBean PlatformTransactionManager manager;
    @MockBean(name="dailyAutomationExecutor") TaskExecutor executor;
    final LocalDate today=LocalDate.of(2026,7,15);
    final LocalDateTime started=LocalDateTime.of(2026,7,15,2,0);
    final AtomicReference<Stored> stored=new AtomicReference<>();
    final AtomicReference<Runnable> worker=new AtomicReference<>();
    DailyAutomationSessionLocks.Lease lease;
    @org.junit.jupiter.api.AfterEach void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
    @BeforeEach void setup() {
        var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("admin",null,java.util.List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        Clock fixed=Clock.fixed(Instant.parse("2026-07-14T17:00:00Z"),ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(manager.getTransaction(any())).thenAnswer(i->new SimpleTransactionStatus());
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",java.util.List.of("ADMIN"),true));
        stored.set(null);worker.set(null);lease=mock(DailyAutomationSessionLocks.Lease.class);
        when(locks.tryAcquire(any())).thenReturn(lease);
        when(mapper.run(any())).thenAnswer(i->stored.get());
        when(mapper.insertRun(any())).thenAnswer(i->{RunWrite w=i.getArgument(0);stored.set(new Stored(w.key().jobCode(),w.key().runDate(),"RUNNING",0,w.at(),null,null));return 1;});
        when(mapper.updateRun(any())).thenAnswer(i->{RunWrite w=i.getArgument(0);stored.set(new Stored(w.key().jobCode(),w.key().runDate(),w.state(),w.lastStep(),stored.get().startedAt(),w.endedAt(),w.errorMessage()));return 1;});
        doAnswer(i->{worker.set(i.getArgument(0));return null;}).when(executor).execute(any());
    }
    String url(String job) {return "/api/automation-runs/"+job+"/run-today";}
    @Test void authenticatedManualRunReturnsActualRunWithRequiredNulls() throws Exception {
        mvc.perform(post(url("DAILY_OPERATIONS"))).andExpect(status().isAccepted())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.accepted").value(true))
            .andExpect(jsonPath("$.data.completed").value(false)).andExpect(jsonPath("$.data.run.jobCode").value("DAILY_OPERATIONS"))
            .andExpect(jsonPath("$.data.run.runDate").value("2026-07-15")).andExpect(jsonPath("$.data.run.state").value("RUNNING"))
            .andExpect(jsonPath("$.data.run.endedAt").value(org.hamcrest.Matchers.nullValue())).andExpect(header().exists("X-Request-Id"));
        worker.get().run();
        for(int stage=1;stage<=7;stage++)verify(stages).execute(new Key("DAILY_OPERATIONS",today),stage);
        assertThat(stored.get().state()).isEqualTo("SUCCEEDED");verify(lease).close();
        verifyNoInteractions(users);
    }
    @Test void sameDaySuccessReturnsOriginalWithoutAnyMutationOrDispatch() throws Exception {
        Stored original=new Stored("DAILY_OPERATIONS",today,"SUCCEEDED",7,started,started.plusMinutes(1),null);stored.set(original);
        mvc.perform(post(url("DAILY_OPERATIONS"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.completed").value(true)).andExpect(jsonPath("$.data.accepted").value(false));
        assertThat(stored.get()).isSameAs(original);verifyNoInteractions(locks,executor,history,stages);verify(mapper,never()).updateRun(any());
    }
    @Test void activelyLockedRunningReturns202WithNoAdditionalWork() throws Exception {
        stored.set(new Stored("DAILY_OPERATIONS",today,"RUNNING",3,started,null,null));when(locks.tryAcquire(any())).thenReturn(null);
        mvc.perform(post(url("DAILY_OPERATIONS"))).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.run.lastStep").value(3));
        verifyNoInteractions(executor,history,stages);verify(mapper,never()).updateRun(any());
    }
    @ParameterizedTest @ValueSource(strings={"FAILED","RUNNING"})
    void unlockedInterruptedRunResumesOnlyIncompleteStageAndPreservesFirstStart(String state) throws Exception {
        stored.set(new Stored("DAILY_OPERATIONS",today,state,3,started.minusHours(1),started,"단계 4"));
        mvc.perform(post(url("DAILY_OPERATIONS"))).andExpect(status().isAccepted());worker.get().run();
        verify(stages,never()).execute(any(),eq(3));verify(stages).execute(any(),eq(4));
        assertThat(stored.get().startedAt()).isEqualTo(started.minusHours(1));assertThat(stored.get().lastStep()).isEqualTo(7);
    }
    @Test void failedItemPersistsFailedCheckpointAndStopsLaterStagesWithoutSqlLeak() throws Exception {
        doThrow(new DailyAutomationStages.ItemFailure(2,"DEV-001")).when(stages).execute(any(),eq(2));
        mvc.perform(post(url("DAILY_OPERATIONS"))).andExpect(status().isAccepted());worker.get().run();
        assertThat(stored.get().state()).isEqualTo("FAILED");assertThat(stored.get().lastStep()).isEqualTo(1);
        assertThat(stored.get().errorMessage()).contains("단계 2","DEV-001").doesNotContain("SQL","Exception");
        verify(stages,never()).execute(any(),eq(3));verify(lease).close();
    }
    @Test void nineOClockUsesFourStagesOfSameCoordinator() throws Exception {
        mvc.perform(post(url("RECEIVABLE_DUNNING"))).andExpect(status().isAccepted());worker.get().run();
        for(int stage=1;stage<=4;stage++)verify(stages).execute(new Key("RECEIVABLE_DUNNING",today),stage);
        verify(stages,never()).execute(any(),eq(5));assertThat(stored.get().lastStep()).isEqualTo(4);
    }
    @ParameterizedTest @ValueSource(strings={"date","time","force","runDate","actor","userId"})
    void unknownQueriesCannotRunPastDates(String field) throws Exception {
        mvc.perform(post(url("DAILY_OPERATIONS")).param(field,"2020-01-01")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(mapper,executor,locks,stages);
    }
    @ParameterizedTest @ValueSource(strings={"{}","null","{\"force\":true}","{\"date\":\"2020-01-01\"}"," "})
    void anyBodyIsRejected(String body) throws Exception {
        mvc.perform(post(url("DAILY_OPERATIONS")).contentType("application/json").content(body)).andExpect(status().isBadRequest());verifyNoInteractions(mapper,executor,locks);
    }
    @ParameterizedTest @ValueSource(strings={"OTHER","daily_operations","2026-01-01"})
    void jobAllowlistRejectsArbitraryWork(String job) throws Exception {
        mvc.perform(post(url(job))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("jobCode"));verifyNoInteractions(mapper,executor,locks);
    }
    @Test void rejectedExecutorReleasesSessionLockAndLeavesResumableRun() throws Exception {
        doThrow(new org.springframework.core.task.TaskRejectedException("拒否")).when(executor).execute(any());
        mvc.perform(post(url("DAILY_OPERATIONS"))).andExpect(status().isInternalServerError());verify(lease).close();assertThat(stored.get().state()).isEqualTo("RUNNING");
    }
}
