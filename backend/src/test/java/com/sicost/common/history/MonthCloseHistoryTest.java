package com.sicost.common.history;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sicost.common.history.ChangeHistoryWriter.Change;

class MonthCloseHistoryTest {
    final ChangeHistoryMapper mapper=mock(ChangeHistoryMapper.class);
    final CurrentUserProvider users=mock(CurrentUserProvider.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"),ZoneOffset.UTC);
    final ChangeHistoryWriter writer=new ChangeHistoryWriter(mapper,users,clock,JsonMapper.builder().findAndAddModules().build());
    final LocalDateTime at=LocalDateTime.of(2026,7,1,9,0,0,123456000);
    Change command(String item,String value) { return new Change("TB_CL_CLOSE_HIST","202607:99999","NEW",item,null,value,null); }
    void successfulStorage() {
        when(mapper.insert(any())).thenReturn(1);
        when(mapper.monthCloseEntries(any())).thenAnswer(c->List.of(c.<ChangeHistoryEntry>getArgument(0)));
    }
    @Test void logicalCommandIsStoredLosslesslyWithCallerActorAndTimestampAndReadBack() {
        successfulStorage();String id=UUID.randomUUID().toString();
        writer.appendMonthClose(command("operationId",id),"admin",at);
        var order=inOrder(mapper);order.verify(mapper).insert(argThat(e->e.tableName().equals("TB_CL_CLOSE_HIST")
            &&e.targetKey().equals("202607:99999")&&e.item().equals("operationId")&&e.after().equals(id)
            &&e.reason()==null&&e.userId().equals("admin")&&e.occurredAt().equals(at)));
        order.verify(mapper).monthCloseEntries(any());verifyNoInteractions(users);
    }
    @Test void snapshotsUseSeparatePhysicalWidthSafeCountFieldsAndNeverJsonBlob() {
        successfulStorage();
        for(String table:List.of("TB_PJ_ASGN_MON","TB_CO_MON_COST","TB_CO_MON_PL","TB_CO_PART_SETL","TB_CO_PART_SETL_DTL")) {
            writer.appendMonthClose(command(table+".rowCount","0"),"admin",at);
            writer.appendMonthClose(command(table+".preservedIndependentFixedCount","2147483647"),"admin",at);
        }
        verify(mapper,times(10)).insert(argThat(e->e.item().length()<=50));
    }
    @ParameterizedTest @ValueSource(strings={"operationId|bad","ACT_CD|DELETE","status|CNFM","acceptWarnings|1","acceptAdjustments|null","snapshots|{}","reason|복제 금지","TB_CO_MON_PL.rowCount|-1","TB_CO_MON_PL.rowCount|2147483648","unknown|value"})
    void invalidLogicalValuesAreRejectedBeforeWriting(String pair) {
        var parts=pair.split("\\|",2);
        assertThatThrownBy(()->writer.appendMonthClose(command(parts[0],parts[1]),"admin",at)).isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper,users);
    }
    @Test void ownershipUsesActualPhysicalPkBeforeAfterAndMatchingHistoryMonth() {
        successfulStorage();
        writer.appendMonthClose(new Change("TB_PJ_ASGN_MON","0001:202607","UPD","FIX_YN","N","Y","202607:1"),"admin",at);
        writer.appendMonthClose(new Change("TB_CO_MON_COST","202607:0001:0002:0003","UPD","FIX_YN","Y","N","202607:2"),"admin",at);
        writer.appendMonthClose(new Change("TB_CO_MON_PL","202607","UPD","FIX_YN","N","Y","202607:1"),"admin",at);
        verify(mapper,times(3)).insert(any());
    }
    @Test void ownershipCannotUseAnotherMonthOrPartialIdentityOrArbitraryField() {
        for(var change:List.of(
            new Change("TB_CO_MON_COST","202607:0001","UPD","FIX_YN","N","Y","202607:1"),
            new Change("TB_PJ_ASGN_MON","0001:202607","UPD","FIX_YN","N","Y","202608:1"),
            new Change("TB_CO_MON_PL","202607","UPD","UNKNOWN_FIELD","N","Y","202607:1"),
            new Change("TB_CO_MON_PL","202607","DEL","FIX_YN","N","Y","202607:1")))
            assertThatThrownBy(()->writer.appendMonthClose(change,"admin",at)).isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper);
    }
    @Test void historySequenceZeroOrOverflowAndDuplicatedReasonAreRejected() {
        for(String key:List.of("202607:0","202607:100000","202613:1","2026-07:1"))
            assertThatThrownBy(()->writer.appendMonthClose(new Change("TB_CL_CLOSE_HIST",key,"NEW","ACT_CD",null,"OPEN",null),"admin",at)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->writer.appendMonthClose(new Change("TB_CL_CLOSE_HIST","202607:1","NEW","ACT_CD",null,"OPEN","사유"),"admin",at)).isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper);
    }
    @Test void widthOverflowDoesNotTruncateOrSplitValue() {
        assertThatThrownBy(()->writer.appendMonthClose(command("expectedRevision","가".repeat(501)),"admin",at)).isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper);
    }
    @Test void failedInsertFailsOwningTransactionBeforeReadback() {
        assertThatThrownBy(()->writer.appendMonthClose(command("ACT_CD","CLOSE"),"admin",at)).isInstanceOf(IllegalStateException.class);
        verify(mapper,never()).monthCloseEntries(any());
    }
    @Test void missingOrDuplicateOrDifferentReadbackFailsOwningTransaction() {
        when(mapper.insert(any())).thenReturn(1);
        for(List<ChangeHistoryEntry> rows:List.of(Collections.<ChangeHistoryEntry>emptyList(),
                List.of(new ChangeHistoryEntry("TB_CL_CLOSE_HIST","202607:99999","NEW","ACT_CD",null,"OPEN",null,"admin",at)))) {
            when(mapper.monthCloseEntries(any())).thenReturn(rows);
            assertThatThrownBy(()->writer.appendMonthClose(command("ACT_CD","CLOSE"),"admin",at)).isInstanceOf(IllegalStateException.class);
        }
        when(mapper.monthCloseEntries(any())).thenAnswer(c->List.of(c.getArgument(0),c.getArgument(0)));
        assertThatThrownBy(()->writer.appendMonthClose(command("ACT_CD","CLOSE"),"admin",at)).isInstanceOf(IllegalStateException.class);
    }
    @Test void ordinaryAppendCannotBypassCommandPolicy() {
        assertThatThrownBy(()->writer.append(command("ACT_CD","CLOSE"))).isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper);
    }
}
