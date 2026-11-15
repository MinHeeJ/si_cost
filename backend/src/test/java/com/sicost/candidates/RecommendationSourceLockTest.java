package com.sicost.candidates;

import com.sicost.common.concurrent.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecommendationSourceLockTest {
    @Test void commonCreateJoinsCompleteSourceLocksWithoutAcquiringThemAgain(){
        var mapper=mock(SettlementSourceLockMapper.class);var advisory=mock(AggregateLockMapper.class);var locks=new SettlementSourceLocks(mapper,advisory);
        when(mapper.partIds(any())).thenReturn(List.of("PART-001"));
        TransactionSynchronizationManager.initSynchronization();
        try{
            locks.lock(List.of("DEV:D-001","OPP:O-001","OPP:O-002","PAY:202607","code-group:JOB"));
            clearInvocations(mapper,advisory);locks.lock(List.of("DEV:D-001","OPP:O-002","PAY:202607"));verifyNoInteractions(mapper,advisory);
            var synchronization=TransactionSynchronizationManager.getSynchronizations().get(0);synchronization.suspend();assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();synchronization.resume();
            locks.lock(List.of("DEV:D-001","OPP:O-002","PAY:202607"));verifyNoInteractions(mapper,advisory);
        }finally{for(var s:TransactionSynchronizationManager.getSynchronizations())s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);TransactionSynchronizationManager.clearSynchronization();}
        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
    }
}
