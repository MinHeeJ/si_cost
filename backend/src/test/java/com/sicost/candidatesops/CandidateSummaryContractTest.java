package com.sicost.candidatesops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.candidatesops.CandidateSummaryDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CandidateSummaryController.class)
@ContextConfiguration(classes = CandidateSummaryController.class)
@Import({CandidateSummaryService.class, CandidateSummaryRepository.class,
        ApiExceptionHandler.class, RequestIdFilter.class, JsonConfiguration.class})
class CandidateSummaryContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean CandidateSummaryMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean Clock clock;

    @BeforeEach void setup() {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin", "관리자", List.of("ADMIN"), true));
        fixed("2026-11-30T16:00:00Z");
        when(mapper.summary(any())).thenReturn(new Summary(12, 8, 3, 4, 2));
    }
    void fixed(String instant) {
        doAnswer(call -> Clock.fixed(Instant.parse(instant), call.getArgument(0))).when(clock).withZone(any());
    }
    @Test void publicSummaryUsesAdminContextAndExactFiveIntegerFields() throws Exception {
        var response = mvc.perform(get("/api/candidates/summary"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.total").value(12))
            .andExpect(jsonPath("$.data.inProgress").value(8))
            .andExpect(jsonPath("$.data.interviewScheduled").value(3))
            .andExpect(jsonPath("$.data.availableByNextMonthStart").value(4))
            .andExpect(jsonPath("$.data.overdueContact").value(2)).andReturn().getResponse();
        var data = json.readTree(response.getContentAsString()).get("data");
        assertThat(data.size()).isEqualTo(5);
        data.elements().forEachRemaining(value -> assertThat(value.isIntegralNumber()).isTrue());
        verify(users).currentUser();
        verify(mapper).summary(new Basis(LocalDate.parse("2027-01-01"), LocalDate.parse("2026-11-01")));
        verifyNoMoreInteractions(mapper);
    }
    @ParameterizedTest
    @CsvSource({"2026-11-30T14:59:59Z,2026-12-01,2026-10-31",
                "2026-11-30T15:00:00Z,2027-01-01,2026-11-01",
                "2024-02-29T14:59:59Z,2024-03-01,2024-01-30",
                "2026-12-31T15:00:00Z,2027-02-01,2026-12-02"})
    void boundariesUseSeoulClockNotUtcOrMonthEnd(String instant, String nextStart, String overdueBefore) throws Exception {
        fixed(instant);
        mvc.perform(get("/api/candidates/summary")).andExpect(status().isOk());
        verify(mapper).summary(new Basis(LocalDate.parse(nextStart), LocalDate.parse(overdueBefore)));
    }
    @Test void emptyDatabaseReturnsExplicitZeroCounts() throws Exception {
        when(mapper.summary(any())).thenReturn(new Summary(0, 0, 0, 0, 0));
        mvc.perform(get("/api/candidates/summary")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.inProgress").value(0))
            .andExpect(jsonPath("$.data.interviewScheduled").value(0))
            .andExpect(jsonPath("$.data.availableByNextMonthStart").value(0))
            .andExpect(jsonPath("$.data.overdueContact").value(0));
    }
    @Test void actorInjectionIsRejectedBeforeAnyDatabaseAccess() throws Exception {
        reject("actor", "other");
    }
    @ParameterizedTest @ValueSource(strings = {"regUserId", "modUserId", "regDttm", "modDttm", "contactStatus", "userId", "expectedRevision"})
    void systemFieldsAndStatusCopiesAreRejected(String field) throws Exception { reject(field, "injected"); }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc", "1"})
    void paginationIsNotAcceptedEvenWhenValidForOtherLists(String page) throws Exception { reject("page", page); }
    @ParameterizedTest @ValueSource(strings = {"INVALID", "NEXT_MONTH", "THREE_MONTHS", ""})
    void listAvailabilityDoesNotFilterTheGlobalSummary(String availability) throws Exception { reject("availability", availability); }
    @ParameterizedTest @ValueSource(strings = {"q", "size", "sort", "direction", "jobCode", "gradeCode", "employeeType"})
    void allOtherQueryKeysAreUnknown(String field) throws Exception { reject(field, "value"); }
    @Test void duplicateQueryKeysCannotBypassValidation() throws Exception {
        mvc.perform(get("/api/candidates/summary").param("actor", "admin", "other"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(mapper, users);
    }
    void reject(String field, String value) throws Exception {
        var response = mvc.perform(get("/api/candidates/summary").param(field, value))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value(field))
            .andReturn().getResponse();
        assertThat(json.readTree(response.getContentAsString()).get("requestId").asText()).isEqualTo(response.getHeader("X-Request-Id"));
        verifyNoInteractions(mapper, users);
    }
    @Test void consecutiveReadsDoNotCacheCounts() throws Exception {
        when(mapper.summary(any())).thenReturn(new Summary(0, 0, 0, 0, 0), new Summary(1, 1, 0, 1, 0));
        mvc.perform(get("/api/candidates/summary")).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/candidates/summary")).andExpect(jsonPath("$.data.total").value(1));
        verify(mapper, times(2)).summary(any());
    }
    @Test void databaseErrorUsesExistingSafeEnvelopeAndRequestId() throws Exception {
        when(mapper.summary(any())).thenThrow(new DataAccessResourceFailureException("SELECT telno FROM tb_hr_cand; private-person"));
        var response = mvc.perform(get("/api/candidates/summary"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.success").value(false)).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("SELECT", "telno", "tb_hr_cand", "private-person", "Exception");
        assertThat(json.readTree(response.getContentAsString()).get("requestId").asText()).isEqualTo(response.getHeader("X-Request-Id"));
    }
}
