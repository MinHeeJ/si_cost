package com.sicost.common.concurrent;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class SettlementDeveloperLockTest {
    @Test void developerStateAndPriceWritesLockPartScopeBeforePhysicalDeveloperRows() {
        var mapper=mock(DeveloperRevisionMapper.class);var locks=mock(SettlementSourceLocks.class);
        when(mapper.developer("D",true)).thenReturn("{}");when(mapper.prices("D",true)).thenReturn(List.of());when(mapper.skills("D",true)).thenReturn(List.of());when(mapper.states("D",true)).thenReturn(List.of());
        var revision=new DeveloperAggregateRevision(mapper,mock(RevisionService.class));revision.settlementLocks(locks);
        revision.snapshot("D",true);
        var order=inOrder(locks,mapper);order.verify(locks).lock(List.of("DEV:D"));order.verify(mapper).developer("D",true);
    }
    @Test void membershipChangeLocksBothCurrentDeveloperAndDestinationPart() {
        var mapper=mock(DeveloperRevisionMapper.class);var locks=mock(SettlementSourceLocks.class);
        var revision=new DeveloperAggregateRevision(mapper,mock(RevisionService.class));revision.settlementLocks(locks);
        revision.lockSettlementSources("D","NEXT");
        verify(locks).lock(List.of("DEV:D","PART:NEXT"));verifyNoInteractions(mapper);
    }
    @Test void ordinaryReadsDoNotAcquireWriteLocks() {
        var mapper=mock(DeveloperRevisionMapper.class);var locks=mock(SettlementSourceLocks.class);when(mapper.developer("D",false)).thenReturn("{}");
        var revision=new DeveloperAggregateRevision(mapper,mock(RevisionService.class));revision.settlementLocks(locks);revision.snapshot("D",false);verifyNoInteractions(locks);
    }
}
