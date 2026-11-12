package com.sicost.apiops;

import com.sicost.common.api.*;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.apiops.PayrollDtos.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PayrollController.class)
@ContextConfiguration(classes = PayrollController.class)
@Import({PayrollService.class, PayrollRepository.class, RevisionService.class,
    ApiExceptionHandler.class, RequestIdFilter.class, JsonConfiguration.class})
class PayrollContractTest {
    @Autowired MockMvc mvc;
    @Autowired RevisionService revisions;
    @MockBean PayrollMapper mapper;
    @MockBean CurrentUserProvider users;
    @BeforeEach void setup() {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin", "임시 관리자", List.of("ADMIN"), true));
        when(mapper.snapshot(anyString())).thenReturn("stored whole month");
        when(mapper.rows(any())).thenReturn(List.of());
    }
    Row row() { return new Row("2026-07", "D-PAY", "M-PAY", "급여인력", "FT", "INDRCT",
        "100", "20", "0", "50", "10", "80", "0", "-10", "0", 7L); }
    @Test void publicRequestReturnsStoredSnapshotAndSignedDecimalAmountsWithoutMasking() throws Exception {
        when(mapper.rows(any())).thenReturn(List.of(row())); when(mapper.count(any())).thenReturn(1L);
        mvc.perform(get("/api/payroll").param("month", " 2026-07 ").header("X-Request-Id", "payroll-read"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap()).andExpect(header().string("X-Request-Id", "payroll-read"))
            .andExpect(jsonPath("$.data.items[0].employeeType").value("FT"))
            .andExpect(jsonPath("$.data.items[0].directClass").value("INDRCT"))
            .andExpect(jsonPath("$.data.items[0].payAmount").value("100"))
            .andExpect(jsonPath("$.data.items[0].realPayAmount").value("-10"))
            .andExpect(jsonPath("$.data.items[0].uploadId").value(7))
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(50)).andExpect(jsonPath("$.data.month").value("2026-07"))
            .andExpect(jsonPath("$.data.revision").value(revisions.revision("stored whole month")));
        verify(users).currentUser();verify(mapper).rows(new Query("202607", null, 1, 50, 0));
    }
    @Test void emptySourceHasNoSyntheticRowsAndStillReturnsWholeMonthRevision() throws Exception {
        mvc.perform(get("/api/payroll").param("month", "2026-07"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.revision").isNotEmpty());
    }
    @Test void searchAndPaginationUseIdenticalQueryWhileRevisionIgnoresThem() throws Exception {
        mvc.perform(get("/api/payroll").param("month", "2026-07").param("q", " A_% ").param("page", "2").param("size", "20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(revisions.revision("stored whole month")));
        Query query = new Query("202607", "A_%", 2, 20, 20);
        verify(mapper).rows(query);verify(mapper).count(query);verify(mapper).snapshot("202607");verifyNoMoreInteractions(mapper);
    }
    @Test void outsourceNullNetPayIsNotZero() throws Exception {
        when(mapper.rows(any())).thenReturn(List.of(new Row("2026-07", "D-OS", "M-OS", "외주", "OS", "DRCT",
            "0", "0", "0", "0", "0", "0", "0", null, "123", 7L)));
        mvc.perform(get("/api/payroll").param("month", "2026-07"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].realPayAmount").isEmpty())
            .andExpect(jsonPath("$.data.items[0].payAmount").value("0"));
    }
    @ParameterizedTest @ValueSource(strings = {"2026-13", "2026-00", "202607", "2026-7", "0000-01", "2026-02-30", ""})
    void invalidMonth(String month) throws Exception {
        mvc.perform(get("/api/payroll").param("month", month)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("month"));
        verifyNoInteractions(mapper);
    }
    @Test void missingMonth() throws Exception {
        mvc.perform(get("/api/payroll")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));verifyNoInteractions(mapper);
    }
    @Test void tooLongSearchIsRejectedWithoutTruncation() throws Exception {
        mvc.perform(get("/api/payroll").param("month", "2026-07").param("q", "가".repeat(101)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("q"));verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.5", "abc", "2147483648", ""})
    void invalidPage(String page) throws Exception {
        mvc.perform(get("/api/payroll").param("month", "2026-07").param("page", page))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings = {"30", "0", "-1", "abc", ""})
    void invalidSize(String size) throws Exception {
        mvc.perform(get("/api/payroll").param("month", "2026-07").param("size", size))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("size"));verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings = {"actor", "REG_USER_ID", "employeeType", "sort", "unknown"})
    void serverAndUnknownQueryFieldsAreRejected(String field) throws Exception {
        mvc.perform(get("/api/payroll").param("month", "2026-07").param(field, "forged"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));verifyNoInteractions(mapper);
    }
    @Test void duplicateQueryCannotSmuggleAnotherMonth() throws Exception {
        mvc.perform(get("/api/payroll").param("month", "2026-07", "2026-08")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);
    }
    @Test void getBodyCannotInjectActor() throws Exception {
        mvc.perform(get("/api/payroll").param("month", "2026-07").contentType("application/json").content("{\"actor\":\"forged\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("body"));verifyNoInteractions(mapper);
    }
    @Test void unknownSingleResourceIsNotInvented() throws Exception {
        mvc.perform(get("/api/payroll/missing")).andExpect(status().isNotFound());verifyNoInteractions(mapper);
    }
    @Test void internalFailureDoesNotExposeSqlOrStackAndUsesSafeTrace() throws Exception {
        when(mapper.snapshot(anyString())).thenThrow(new IllegalStateException("SELECT private_salary FROM secret"));
        mvc.perform(get("/api/payroll").param("month", "2026-07").header("X-Request-Id", "x".repeat(65)))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.requestId").isNotEmpty())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private_salary"))));
        verify(mapper).snapshot("202607");verifyNoMoreInteractions(mapper);
    }
}
