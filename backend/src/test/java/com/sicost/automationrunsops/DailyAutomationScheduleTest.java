package com.sicost.automationrunsops;

import java.time.*;
import org.junit.jupiter.api.Test;
import static com.sicost.automationrunsops.DailyAutomationDtos.*;
import static org.mockito.Mockito.*;

class DailyAutomationScheduleTest {
    DailyAutomationService service=mock(DailyAutomationService.class);
    DailyAutomationRepository repository=mock(DailyAutomationRepository.class);
    DailyAutomationStages stages=mock(DailyAutomationStages.class);
    DailyAutomationSchedule schedule(String instant) {
        return new DailyAutomationSchedule(service,repository,stages,Clock.fixed(Instant.parse(instant),ZoneOffset.UTC));
    }
    @Test void startupBeforeTwoNeverStartsAnyJobOrSendsMail() {
        schedule("2026-07-14T16:59:59Z").startup();verify(stages).recoverAbandonedSending();verifyNoInteractions(service,repository);
    }
    @Test void startupAfterTwoResumesOnlyTodaysUnfinishedDailyWork() {
        var key=new Key("DAILY_OPERATIONS",LocalDate.of(2026,7,15));
        when(repository.find(key)).thenReturn(new Stored(key.jobCode(),key.runDate(),"FAILED",4,LocalDateTime.of(2026,7,15,2,0),null,"단계 5"));
        schedule("2026-07-15T01:00:00Z").startup();verify(service).scheduled("DAILY_OPERATIONS");verify(service,never()).scheduled("RECEIVABLE_DUNNING");
    }
    @Test void startupDoesNotCreateMissingRunsOrReplayCompletedWork() {
        var scheduler=schedule("2026-07-15T01:00:00Z");scheduler.startup();
        when(repository.find(any())).thenReturn(new Stored("DAILY_OPERATIONS",LocalDate.of(2026,7,15),"SUCCEEDED",7,LocalDateTime.of(2026,7,15,2,0),null,null));
        scheduler.startup();verifyNoInteractions(service);
    }
    @Test void scheduledAndManualPathsShareTheSameService() {
        var scheduler=schedule("2026-07-15T01:00:00Z");scheduler.daily();scheduler.receivables();
        verify(service).scheduled("DAILY_OPERATIONS");verify(service).scheduled("RECEIVABLE_DUNNING");
    }
}
