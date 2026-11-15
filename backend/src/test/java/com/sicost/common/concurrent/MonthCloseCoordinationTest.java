package com.sicost.common.concurrent;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonthCloseCoordinationTest {
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final AggregateLockMapper locks=mock(AggregateLockMapper.class);
    final MonthCloseBoundary boundary=mock(MonthCloseBoundary.class);
    final CommitInvalidation invalidation=mock(CommitInvalidation.class);
    final RevisionService revisions=new RevisionService(JsonMapper.builder().findAndAddModules().build());
    WriteCoordinator coordinator() {
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        var coordinator=new WriteCoordinator(manager,locks,revisions,invalidation);
        coordinator.setMonthCloseBoundary(boundary);return coordinator;
    }
    @Test void exclusiveBoundaryPrecedesSortedAggregatesAndCommand() {
        var coordinator=coordinator();Runnable command=mock(Runnable.class);
        coordinator.exclusive(List.of("PAY:202607","DEV:01","DEV:01"),()->{command.run();return "stored";});
        var order=inOrder(boundary,locks,command,manager);
        order.verify(manager).getTransaction(any());order.verify(boundary).exclusive();order.verify(boundary).shared();
        order.verify(locks).lock("DEV:01");order.verify(locks).lock("PAY:202607");order.verify(command).run();order.verify(manager).commit(any());
    }
    @Test void ordinaryWriteTakesSharedBoundaryBeforeAnyAggregateAndStaleRejectsDomain() {
        Runnable domain=mock(Runnable.class),write=mock(Runnable.class);var coordinator=coordinator();
        assertThatThrownBy(()->coordinator.write(List.of("PAY:202607"),"old",()->"current",domain,()->true,write)).isInstanceOf(ApiException.class);
        var order=inOrder(boundary,locks,manager);
        order.verify(manager).getTransaction(any());order.verify(boundary).shared();order.verify(locks).lock("PAY:202607");order.verify(manager).rollback(any());
        verifyNoInteractions(domain,write,invalidation);
    }
    @Test void commandFailureRollsBackWholeManagementTransaction() {
        assertThatThrownBy(()->coordinator().exclusive(List.of("PAY:202607"),()->{throw new IllegalStateException("audit");})).isInstanceOf(IllegalStateException.class);
        verify(manager).rollback(any());verifyNoInteractions(invalidation);
    }
    @Test void completeRevisionSortsPhysicalKeysAndDetectsOffPageOrAuditMutation() {
        var a=new RevisionService.SourceRow("TB_HR_MON_PAY","202607:0001",new HashMap<>(Map.of("PAY_AMT","0","REG_USER_ID","admin")));
        var b=new RevisionService.SourceRow("TB_HR_MON_PAY","202607:0002",Map.of("PAY_AMT","-1"));
        String expected=revisions.revisionRows(List.of(b,a));
        assertThat(revisions.revisionRows(List.of(a,b))).isEqualTo(expected);
        var updated=new RevisionService.SourceRow(b.table(),b.primaryKey(),Map.of("PAY_AMT","0"));
        assertThatThrownBy(()->revisions.requireExpected(expected,revisions.revisionRows(List.of(a,updated))))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));
        a.storedValues().put("MOD_DTTM",null);
        assertThat(revisions.revisionRows(List.of(a,b))).isNotEqualTo(expected);
    }
    @Test void duplicatePhysicalIdentityCannotBeHashedAsCompleteSnapshot() {
        var row=new RevisionService.SourceRow("TB_HR_MON_PAY","202607:0001",Map.of("PAY_AMT","0"));
        assertThatThrownBy(()->revisions.revisionRows(List.of(row,row))).isInstanceOf(IllegalArgumentException.class);
    }
}
