package com.sicost.common.history;

import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementAccessLogTest {
    final AccessLogMapper mapper = mock(AccessLogMapper.class);
    final CurrentUserProvider users = mock(CurrentUserProvider.class);
    final AccessLogWriter writer = new AccessLogWriter(mapper, users,
        Clock.fixed(Instant.parse("2026-06-30T15:00:00Z"), ZoneId.of("Asia/Seoul")));
    @Test void requiredSettlementDownloadsRecordExactScreenFilenameAndCount() {
        when(users.userId()).thenReturn("admin"); when(mapper.insert(any())).thenReturn(1);
        for (String mode : List.of("이번달", "추이")) {
            String filename = "영업파트이익금_PART-001_202607_" + mode + ".xlsx";
            writer.append(new AccessLogWriter.Event("DNLD", "UI-19", null, "영업파트이익금", 0L, filename), new MockHttpServletRequest());
            verify(mapper).insert(argThat(e -> e.screenId().equals("UI-19") && e.description().contains(filename)
                && e.userId().equals("admin") && e.now().equals(LocalDateTime.of(2026,7,1,0,0))));
        }
        writer.append(new AccessLogWriter.Event("DNLD", "UI-32", null, "단가재협상", 2L, "단가재협상_20260701.xlsx"), new MockHttpServletRequest());
        verify(mapper).insert(argThat(e -> e.screenId().equals("UI-32") && e.description().contains("단가재협상_20260701.xlsx")));
    }
    @Test void settlementCannotLogOtherActionsMissingCountWrongKindOrInjectedFilename() {
        for (var event : List.of(
            new AccessLogWriter.Event("ACCT_VIEW", "UI-19", null, "영업파트이익금", 0L, "영업파트이익금_PART-001_202607_이번달.xlsx"),
            new AccessLogWriter.Event("DNLD", "UI-19", null, "영업파트이익금", null, "영업파트이익금_PART-001_202607_이번달.xlsx"),
            new AccessLogWriter.Event("DNLD", "UI-19", null, "단가재협상", 0L, "영업파트이익금_PART-001_202607_이번달.xlsx"),
            new AccessLogWriter.Event("DNLD", "UI-19", null, "영업파트이익금", -1L, "영업파트이익금_PART-001_202607_이번달.xlsx"),
            new AccessLogWriter.Event("DNLD", "UI-32", null, "단가재협상", 0L, "단가재협상_20260701.xlsx\r\nunsafe"),
            new AccessLogWriter.Event("DNLD", "UI-32", null, "단가재협상", 0L))) {
            assertThatThrownBy(() -> writer.append(event, new MockHttpServletRequest())).isInstanceOf(ApiException.class);
        }
        verifyNoInteractions(mapper, users);
    }
    @Test void failedRequiredInsertNeverClaimsLoggedSuccess() {
        when(users.userId()).thenReturn("admin"); when(mapper.insert(any())).thenReturn(0);
        assertThatThrownBy(() -> writer.append(new AccessLogWriter.Event("DNLD", "UI-32", null, "단가재협상", 0L, "단가재협상_20260701.xlsx"),
            new MockHttpServletRequest())).isInstanceOf(IllegalStateException.class);
    }
    @Test void legacyEventConstructorRetainsItsExistingDescription() {
        when(users.userId()).thenReturn("admin"); when(mapper.insert(any())).thenReturn(1);
        writer.append(new AccessLogWriter.Event("DNLD", "UI-03", null, "개인원가수익", 1L), new MockHttpServletRequest());
        verify(mapper).insert(argThat(e -> e.description().equals(" 개인원가수익 1")));
    }
}
