package com.sicost.common.history;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementHistoryWriterTest {
    final ChangeHistoryMapper mapper = mock(ChangeHistoryMapper.class);
    final CurrentUserProvider users = mock(CurrentUserProvider.class);
    final ChangeHistoryWriter writer = new ChangeHistoryWriter(mapper, users,
        Clock.fixed(Instant.parse("2026-06-30T15:00:00Z"), ZoneId.of("Asia/Seoul")),
        JsonMapper.builder().findAndAddModules().build());

    ChangeHistoryWriter.Change parent(String before, String after) {
        return new ChangeHistoryWriter.Change("TB_CO_PART_SETL", "PART-001:202607", "UPD", "CORP_CARD_AMT", before, after, null);
    }
    ChangeHistoryWriter.Change detail(String before, String after, String type) {
        return new ChangeHistoryWriter.Change("TB_CO_PART_SETL_DTL", "PART-001:202607:DEV-000001", type, "PROJ_NM_LIST", before, after, null);
    }
    @Test void parentFieldsUseCompactMonthServerActorAndSeoulTime() {
        when(users.userId()).thenReturn("admin"); when(mapper.insert(any())).thenReturn(1);
        writer.append(parent("0", "1"));
        verify(mapper).insert(argThat(e -> e.targetKey().equals("PART-001:202607") && e.before().equals("0")
            && e.after().equals("1") && e.userId().equals("admin") && e.occurredAt().equals(LocalDateTime.of(2026,7,1,0,0))));
    }
    @Test void cancellationPreservesDeletedDetailRawBeforeValuesWithoutLiveJoin() {
        when(users.userId()).thenReturn("admin"); when(mapper.insert(any())).thenReturn(1);
        writer.append(detail("[\"쉼표, 포함\"]", null, "DEL"));
        verify(mapper).insert(argThat(e -> e.targetKey().equals("PART-001:202607:DEV-000001")
            && e.before().equals("[\"쉼표, 포함\"]") && e.after() == null && e.type().equals("DEL")));
    }
    @Test void auditLimitCountsUnicodeCodePointsAndNeverTruncates() {
        when(users.userId()).thenReturn("admin"); when(mapper.insert(any())).thenReturn(1);
        String value = "😀".repeat(500);
        writer.append(detail(null, value, "NEW"));
        verify(mapper).insert(argThat(e -> e.after().equals(value)));
    }
    @Test void overWidthAfterValueConflictsBeforeAnyActorOrWrite() {
        assertThatThrownBy(() -> writer.append(detail(null, "가".repeat(501), "NEW")))
            .isInstanceOfSatisfying(ApiException.class, e -> {
                assertThat(e.status()).isEqualTo(409); assertThat(e.code()).isEqualTo("STATE_CONFLICT");
                assertThat(e.field()).isEqualTo("PROJ_NM_LIST"); assertThat(e.details().toString()).contains("DEV-000001", "500");
            });
        verifyNoInteractions(mapper, users);
    }
    @Test void overWidthDeletedBeforeValueConflictsWithoutDigestOrTruncation() {
        assertThatThrownBy(() -> writer.append(detail("가".repeat(1000), null, "DEL")))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(409));
        verifyNoInteractions(mapper, users);
    }
    @Test void wholeCommandPreflightRejectsLaterFieldBeforeEarlierFieldCanBeWritten() {
        assertThatThrownBy(() -> writer.preflightSettlement(List.of(parent("0", "1"), detail(null, "가".repeat(501), "NEW"))))
            .isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper, users);
    }
    @Test void latestSameValueProducesNoAuditAndDoesNotResolveActor() {
        writer.append(parent("0", "0"));
        verifyNoInteractions(mapper, users);
    }
    @Test void parentDeleteDetailUpdateVirtualFieldAndApiMonthAreRejectedSeparately() {
        for (var change : List.of(
            new ChangeHistoryWriter.Change("TB_CO_PART_SETL", "PART-001:202607", "DEL", "CORP_CARD_AMT", "1", null, null),
            detail("[]", "[\"a\"]", "UPD"),
            new ChangeHistoryWriter.Change("TB_CO_PART_SETL", "PART-001:202607", "UPD", "PRFT_DSTRB_RT", "1", "2", null),
            new ChangeHistoryWriter.Change("TB_CO_PART_SETL", "PART-001:2026-07", "UPD", "CORP_CARD_AMT", "1", "2", null))) {
            assertThatThrownBy(() -> writer.append(change)).isInstanceOf(ApiException.class);
        }
        verifyNoInteractions(mapper, users);
    }
    @Test void unsuccessfulAuditInsertFailsTheOwningTransaction() {
        when(users.userId()).thenReturn("admin"); when(mapper.insert(any())).thenReturn(0);
        assertThatThrownBy(() -> writer.append(parent("0", "1"))).isInstanceOf(IllegalStateException.class);
    }
}
