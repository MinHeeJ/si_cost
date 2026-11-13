package com.sicost.common.history;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectProgressHistoryTest {
    final ChangeHistoryMapper mapper = mock(ChangeHistoryMapper.class);
    final CurrentUserProvider users = mock(CurrentUserProvider.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00.123456Z"), ZoneId.of("Asia/Seoul"));
    final ChangeHistoryWriter writer = new ChangeHistoryWriter(mapper, users, clock, JsonMapper.builder().findAndAddModules().build());

    @Test void eachFieldUsesFullProductKeyAndServerActorWithExactTimestamp() {
        when(users.userId()).thenReturn("admin");
        writer.append(new ChangeHistoryWriter.Change("TB_PJ_STEP", "P-1000:99", "NEW", "STEP_WGHT_RT", null, "10.00", null));
        writer.append(new ChangeHistoryWriter.Change("TB_PJ_STEP", "P-1000:99", "UPD", "CMPL_DT", null, "2026-01-01", null));
        writer.append(new ChangeHistoryWriter.Change("TB_PJ_WEEK_MEMO", "P-1000:99999", "NEW", "ACTL_PRGRS_RT", null, "0", null));
        writer.append(new ChangeHistoryWriter.Change("TB_PJ_ISSUE", "9999999999", "UPD", "ACTN_CN", "전", "가".repeat(500), null));
        verify(mapper, times(4)).insert(argThat(e -> e.userId().equals("admin")
            && e.occurredAt().toString().equals("2026-01-01T09:00:00.123456")));
        verify(mapper).insert(argThat(e -> e.targetKey().equals("9999999999") && e.after().length() == 500));
    }
    @Test void noOpDoesNotResolveUserOrAppendButNullToZeroDoes() {
        writer.append(new ChangeHistoryWriter.Change("TB_PJ_WEEK_MEMO", "P-1:1", "UPD", "ACTL_PRGRS_RT", "0", "0", null));
        verifyNoInteractions(mapper, users);
        when(users.userId()).thenReturn("admin");
        writer.append(new ChangeHistoryWriter.Change("TB_PJ_WEEK_MEMO", "P-1:1", "UPD", "ACTL_PRGRS_RT", null, "0", null));
        verify(mapper).insert(argThat(e -> e.before() == null && e.after().equals("0")));
    }
    @Test void deleteAndStateEventsCannotBeRecordedAsProgressWrites() {
        for (String type : new String[]{"DEL", "STOP", "RESUME"}) {
            assertThatThrownBy(() -> writer.append(new ChangeHistoryWriter.Change("TB_PJ_ISSUE", "1", type, "ISSUE_STAT_CD", "OPEN", "CLOSE", null)))
                .isInstanceOf(ApiException.class);
        }
        verifyNoInteractions(mapper, users);
    }
    @Test void fullKeysAreRequiredAndBoundsAreNotTruncated() {
        for (String key : new String[]{"P-1", "P-1:0", "P-1:100", "P-1:1:2", "x".repeat(21) + ":1"}) {
            assertThatThrownBy(() -> writer.append(new ChangeHistoryWriter.Change("TB_PJ_STEP", key, "UPD", "CMPL_DT", null, "2026-01-01", null)))
                .isInstanceOf(ApiException.class);
        }
        for (String key : new String[]{"0", "10000000000", "P-1:1"}) {
            assertThatThrownBy(() -> writer.append(new ChangeHistoryWriter.Change("TB_PJ_ISSUE", key, "NEW", "ISSUE_TTL", null, "제목", null)))
                .isInstanceOf(ApiException.class);
        }
        verifyNoInteractions(mapper, users);
    }
    @Test void immutableAndWholeRowFieldsAreRejected() {
        for (String field : new String[]{"REG_USER_ID", "REG_DTTM", "PROJ_ID", "ISSUE_ID", "등록", "ROW_JSON"}) {
            assertThatThrownBy(() -> writer.append(new ChangeHistoryWriter.Change("TB_PJ_ISSUE", "1", "UPD", field, "전", "후", null)))
                .isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> writer.append(new ChangeHistoryWriter.Change("TB_PJ_STEP", "P-1:1", "UPD", "STEP_WGHT_RT", "10", "20", null)))
            .isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper, users);
    }
    @Test void oversizedFieldIsRejectedAndAuditFailurePropagates() {
        assertThatThrownBy(() -> writer.append(new ChangeHistoryWriter.Change("TB_PJ_ISSUE", "1", "UPD", "ACTN_CN", null, "가".repeat(501), null)))
            .isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper);
        when(users.userId()).thenReturn("admin");
        doThrow(new IllegalStateException("audit failure")).when(mapper).insert(any());
        assertThatThrownBy(() -> writer.append(new ChangeHistoryWriter.Change("TB_PJ_ISSUE", "1", "NEW", "ISSUE_TTL", null, "제목", null)))
            .isInstanceOf(IllegalStateException.class);
    }
}
