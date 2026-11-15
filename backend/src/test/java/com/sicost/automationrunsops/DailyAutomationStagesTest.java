package com.sicost.automationrunsops;

import com.sicost.common.notifications.ReceivableDunningCoordinator;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import static com.sicost.automationrunsops.DailyAutomationDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DailyAutomationStagesTest {
    final LocalDate today=LocalDate.of(2026,7,15);
    final Clock clock=Clock.fixed(Instant.parse("2026-07-14T17:00:00Z"),ZoneOffset.UTC);
    final DailyAutomationActions actions=mock(DailyAutomationActions.class);
    final DailyAutomationNotifications notifications=mock(DailyAutomationNotifications.class);
    @SuppressWarnings("unchecked") final ObjectProvider<ReceivableDunningCoordinator> provider=mock(ObjectProvider.class);
    final ReceivableDunningCoordinator dunning=mock(ReceivableDunningCoordinator.class);
    DailyAutomationStages stages;
    @BeforeEach void setup() {stages=new DailyAutomationStages(actions,notifications,provider,clock);when(provider.getIfAvailable()).thenReturn(dunning);}
    @Test void primaryKeyOrderIsDeterministicAndFirstFailureStopsRemainingItems() {
        when(actions.keys(1,today)).thenReturn(List.of("DEV-C","DEV-B","DEV-A"));
        doThrow(new IllegalStateException("SQL token=private-value")).when(actions).process(1,"DEV-B",today);
        assertThatThrownBy(()->stages.execute(new Key("DAILY_OPERATIONS",today),1))
            .isInstanceOfSatisfying(DailyAutomationStages.ItemFailure.class,error->assertThat(error.publicMessage()).contains("단계 1","DEV-B").doesNotContain("SQL","private-value"));
        var order=inOrder(actions);order.verify(actions).process(1,"DEV-A",today);order.verify(actions).process(1,"DEV-B",today);verify(actions,never()).process(1,"DEV-C",today);
    }
    @Test void twoOClockNeverCallsReceivableDeliveryOrMail() {
        for(int stage=1;stage<=4;stage++)when(actions.keys(stage,today)).thenReturn(List.of());
        when(notifications.withdrawalKeys(today)).thenReturn(List.of());
        for(int stage=1;stage<=7;stage++)stages.execute(new Key("DAILY_OPERATIONS",today),stage);
        verify(notifications).cash(today);verify(notifications).cleanup(today);verifyNoInteractions(dunning,provider);
    }
    @Test void nineOClockUsesSameDeliveryCoordinatorAndPrimaryKeyOrderForEachStage() {
        for(int stage=1;stage<=4;stage++) {
            when(dunning.businessKeys(stage,today)).thenReturn(List.of("B-B","B-A"));
            stages.execute(new Key("RECEIVABLE_DUNNING",today),stage);
            var order=inOrder(dunning);order.verify(dunning).businessKeys(stage,today);order.verify(dunning).process(stage,"B-A",today);order.verify(dunning).process(stage,"B-B",today);
        }
        verifyNoInteractions(actions,notifications);
    }
    @Test void absentDeliveryIntegrationFailsInsteadOfInventingSuccess() {
        when(provider.getIfAvailable()).thenReturn(null);
        assertThatThrownBy(()->stages.execute(new Key("RECEIVABLE_DUNNING",today),1)).isInstanceOf(DailyAutomationStages.ItemFailure.class);
    }
    @Test void yesterdayCannotBeReplayedAsToday() {
        assertThatThrownBy(()->stages.execute(new Key("DAILY_OPERATIONS",today.minusDays(1)),1)).isInstanceOf(DailyAutomationStages.ItemFailure.class);
        verifyNoInteractions(actions,notifications,dunning);
    }
    @Test void startupRecoveryNeverCallsDispatchStages() {
        stages.recoverAbandonedSending();verify(dunning).recoverAbandonedSending();verify(dunning,never()).process(anyInt(),anyString(),any());
    }
}
