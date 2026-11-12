package com.sicost.payrollops;

import com.sicost.common.api.*;
import com.sicost.common.context.*;
import java.time.LocalDateTime;
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
import static com.sicost.payrollops.PayrollUploadDtos.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PayrollUploadController.class)
@ContextConfiguration(classes=PayrollUploadController.class)
@Import({PayrollUploadService.class,PayrollUploadRepository.class,CurrentUserProvider.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class PayrollUploadContractTest {
    @Autowired MockMvc mvc;
    @MockBean PayrollUploadMapper mapper;
    @MockBean CurrentUserMapper users;
    @BeforeEach void setup() {
        when(users.findAdministrator()).thenReturn("admin");
        when(users.findAdministratorRoles()).thenReturn(List.of("ADMIN"));
        when(mapper.list(any())).thenReturn(List.of());
    }
    @Test void publicRequestUsesServerActorAndStoredCountsWithDefaultPaging() throws Exception {
        Query query=new Query("202607",1,50,0);
        when(mapper.list(query)).thenReturn(List.of(new Stored(71,"202607","급여.xlsx","recorded-actor",
            LocalDateTime.of(2026,7,15,9,0),"APPLIED",LocalDateTime.of(2026,7,15,9,1),12,9,2,1,1,0)));
        when(mapper.count(query)).thenReturn(1L);
        mvc.perform(get("/api/payroll/uploads").param("month"," 2026-07 ").header("X-Request-Id","payroll-history"))
            .andExpect(status().isOk()).andExpect(header().string("X-Request-Id","payroll-history"))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.items[0].uploadId").value(71))
            .andExpect(jsonPath("$.data.items[0].month").value("2026-07"))
            .andExpect(jsonPath("$.data.items[0].actor").value("recorded-actor"))
            .andExpect(jsonPath("$.data.items[0].fileName").value("급여.xlsx"))
            .andExpect(jsonPath("$.data.items[0].registeredAt").value("2026-07-15 09:00"))
            .andExpect(jsonPath("$.data.items[0].appliedAt").value("2026-07-15 09:01"))
            .andExpect(jsonPath("$.data.items[0].summary.totalRows").value(12))
            .andExpect(jsonPath("$.data.items[0].summary.successRows").value(9))
            .andExpect(jsonPath("$.data.items[0].summary.warningRows").value(2))
            .andExpect(jsonPath("$.data.items[0].summary.errorRows").value(1))
            .andExpect(jsonPath("$.data.items[0].summary.fileWarningCount").value(1))
            .andExpect(jsonPath("$.data.items[0].summary.tableWarningCount").value(0))
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(50));
        verify(users).findAdministrator();verify(mapper).list(query);verify(mapper).count(query);verifyNoMoreInteractions(mapper);
    }
    @Test void emptySourceIsAnEmptyPageNotNotFoundOrInventedRows() throws Exception {
        mvc.perform(get("/api/payroll/uploads").param("month","2026-07"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void explicitPageUsesLongOffsetAndSameCountFilter() throws Exception {
        mvc.perform(get("/api/payroll/uploads").param("month","2026-07").param("page","2147483647").param("size","100"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(2147483647));
        var query=new Query("202607",2147483647,100,214748364600L);
        verify(mapper).list(query);verify(mapper).count(query);
    }
    @Test void missingMonthIs400BeforeDatabaseAccess() throws Exception {
        mvc.perform(get("/api/payroll/uploads")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("month"));
        verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @ValueSource(strings={"","2026-00","2026-13","2026-2","202607","0000-01","2026-07-01"})
    void invalidMonthIs400(String month) throws Exception {
        mvc.perform(get("/api/payroll/uploads").param("month",month)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("month"));
        verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","1.5","1e2","2147483648",""})
    void invalidPageIs400(String page) throws Exception {
        mvc.perform(get("/api/payroll/uploads").param("month","2026-07").param("page",page))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("page"));
        verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @ValueSource(strings={"0","1","25","200","-20",""})
    void invalidSizeIs400(String size) throws Exception {
        mvc.perform(get("/api/payroll/uploads").param("month","2026-07").param("size",size))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("size"));
        verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @ValueSource(strings={"actor","REG_USER_ID","sort","q","uploadId"})
    void unknownAndServerFieldsAreRejected(String field) throws Exception {
        mvc.perform(get("/api/payroll/uploads").param("month","2026-07").param(field,"forged"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value(field));
        verifyNoInteractions(mapper,users);
    }
    @Test void repeatedQueryIsRejected() throws Exception {
        mvc.perform(get("/api/payroll/uploads").param("month","2026-07","2026-08"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));
        verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @ValueSource(strings={"{}","[]","broken","{\"actor\":\"forged\"}"})
    void getHasNoBody(String body) throws Exception {
        mvc.perform(get("/api/payroll/uploads").param("month","2026-07").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(mapper,users);
    }
    @Test void failurePreservesSafeEnvelopeAndTrace() throws Exception {
        when(mapper.list(any())).thenThrow(new org.springframework.dao.DataRetrievalFailureException("SELECT secret_payroll"));
        mvc.perform(get("/api/payroll/uploads").param("month","2026-07").header("X-Request-Id","history-failure"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.requestId").value("history-failure"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret_payroll"))));
    }
}
