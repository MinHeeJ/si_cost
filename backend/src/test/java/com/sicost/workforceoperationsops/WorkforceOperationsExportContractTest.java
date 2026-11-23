package com.sicost.workforceoperationsops;

import com.sicost.apiops.*;
import com.sicost.common.api.*;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.forecast.*;
import com.sicost.workforceforecastsops.WorkforceForecastExportService;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest({WorkforceOperationsExportController.class, WorkforceOperationsController.class})
@ContextConfiguration(classes={WorkforceOperationsExportController.class, WorkforceOperationsController.class})
@EnableTransactionManagement
@Import({WorkforceOperationsExportService.class, WorkforceCalendarService.class, WorkforceForecastExportService.class,
    SalesForecastRepository.class, SalesWorkforceProjectionService.class, ExcelExportService.class, AccessLogWriter.class,
    ApiExceptionHandler.class, RequestIdFilter.class, JsonConfiguration.class})
class WorkforceOperationsExportContractTest {
    @Autowired MockMvc mvc;
    @Autowired ExcelExportService excel;
    @MockBean StaffingReadMapper calendar;
    @MockBean SalesForecastMapper forecasts;
    @MockBean AssignmentMonthlyService monthly;
    @MockBean CurrentUserProvider users;
    @MockBean AccessLogMapper audit;
    @MockBean PlatformTransactionManager transactions;
    @MockBean Clock clock;
    @MockBean com.sicost.common.excel.ExportLabels exportLabels;

    @BeforeEach void setup() {
        com.sicost.common.excel.ExportTestLookups.configure(exportLabels);
        com.sicost.common.excel.ExportTestLookups.configure(excel);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-12T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul")); when(clock.withZone(any())).thenReturn(clock);
        when(transactions.getTransaction(any())).thenAnswer(c -> new SimpleTransactionStatus());
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin", "관리자", List.of("ADMIN"), true));
        when(users.userId()).thenReturn("admin"); when(audit.insert(any())).thenReturn(1);
        when(monthly.cashBase()).thenReturn(YearMonth.of(2025, 1));
        when(calendar.people()).thenReturn(List.of(person("0001", "=1+1", "DEV"), person("0002", "다른 직무", "PM")));
        when(forecasts.people()).thenReturn(List.of(new SalesForecastSources.Person("0001", "DEV", "Y", "DRCT", null, null)));
        when(forecasts.standards("202610")).thenReturn(List.of(
            new SalesForecastSources.Standard("SHORT_PERSON", new BigDecimal("0.4")),
            new SalesForecastSources.Standard("SURPLUS_PERSON", BigDecimal.ONE),
            new SalesForecastSources.Standard("HIRE_LEAD_MON", new BigDecimal("2"))));
    }
    private StaffingReadDtos.Person person(String id, String name, String job) {
        return new StaffingReadDtos.Person(id, name, job, null, "FT", "Y", "DRCT", LocalDate.of(2025,1,1), null);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder download(String tab) {
        return get("/api/workforce-operations/export").param("tab", tab);
    }
    private XSSFWorkbook workbook(org.springframework.mock.web.MockHttpServletResponse response) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()));
    }

    @Test void publicCalendarExportsFilteredWholePopulationWithNumbersAndLiteralIds() throws Exception {
        var response = mvc.perform(download("CALENDAR").param("jobCode", "DEV").param("amountUnit", "MILLION"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".xlsx"))).andReturn().getResponse();
        try (var book = workbook(response)) {
            var sheet = book.getSheet("인력 운영 달력");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short)30);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("0001");
            assertThat(sheet.getRow(1).getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("=1+1");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("개발");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("–");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("정규직");
            assertThat(sheet.getRow(1).getCell(7).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(7).getNumericCellValue()).isZero();
            assertThat(book.getSheet("월별 합계").getRow(1).getCell(1).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(book.getSheet("월별 합계").getRow(10).getCell(1).getNumericCellValue()).isEqualTo(1);
            assertThat(book.getSheet("다운로드정보").getRow(4).getCell(1).getStringCellValue()).isEqualTo("백만원");
        }
        verify(audit).insert(argThat(e -> e.action().equals("DNLD") && e.screenId().equals("UI-28") && e.description().contains("인력운영달력 1")));
        var order = inOrder(audit, transactions);
        order.verify(audit).insert(any());
        order.verify(transactions, times(2)).commit(any());
        verify(transactions).getTransaction(argThat(d -> d.isReadOnly() && d.getIsolationLevel()==TransactionDefinition.ISOLATION_REPEATABLE_READ
            && d.getPropagationBehavior()==TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(monthly, never()).synchronize(any(), anyString(), anyString());
    }
    @Test void exportDoesNotStopAtDefaultFiftyRowsAndPreservesListPaging() throws Exception {
        when(calendar.people()).thenReturn(IntStream.range(0, 55).mapToObj(i -> person("D-"+i, "개발자"+i, "DEV")).toList());
        mvc.perform(get("/api/workforce-operations").param("page", "2").param("size", "20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(20))
            .andExpect(jsonPath("$.data.total").value(55)).andExpect(jsonPath("$.data.page").value(2))
            .andExpect(jsonPath("$.data.monthTotals[9].availablePeople").value(55));
        var response = mvc.perform(download("CALENDAR")).andExpect(status().isOk()).andReturn().getResponse();
        try (var book = workbook(response)) {
            assertThat(book.getSheet("인력 운영 달력").getLastRowNum()).isEqualTo(55);
            assertThat(book.getSheet("월별 합계").getRow(10).getCell(1).getNumericCellValue()).isEqualTo(55);
        }
        verify(calendar, times(2)).people();
    }
    @ParameterizedTest @ValueSource(strings={"ASC", "DESC"})
    void nameOrderIsGlobalWithPkTiesAndIdenticalInEveryListPageAndWorkbook(String direction) throws Exception {
        var people = new ArrayList<StaffingReadDtos.Person>();
        for(int i=55;i>=1;i--)people.add(person(String.format("D-%03d",i),i%3==0?null:i%3==1?"가":"나","DEV"));
        when(calendar.people()).thenReturn(people);
        var expected = new ArrayList<String>();
        int[] groups=direction.equals("ASC")?new int[]{1,2,0}:new int[]{2,1,0};
        for(int group:groups)for(int i=1;i<=55;i++)if(i%3==group)expected.add(String.format("D-%03d",i));
        var actual=new ArrayList<String>();
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        for(int page=1;page<=3;page++) {
            var response=mvc.perform(get("/api/workforce-operations").param("sort","name").param("direction",direction)
                .param("page",Integer.toString(page)).param("size","20")).andExpect(status().isOk()).andReturn().getResponse();
            json.readTree(response.getContentAsString()).path("data").path("items").forEach(r->actual.add(r.path("developerId").asText()));
        }
        assertThat(actual).containsExactlyElementsOf(expected);
        var response=mvc.perform(download("CALENDAR").param("sort","name").param("direction",direction))
            .andExpect(status().isOk()).andReturn().getResponse();
        try(var book=workbook(response)) {
            var sheet=book.getSheet("인력 운영 달력");var exported=new ArrayList<String>();
            for(int i=1;i<=sheet.getLastRowNum();i++)exported.add(sheet.getRow(i).getCell(0).getStringCellValue());
            assertThat(exported).containsExactlyElementsOf(expected);
        }
    }
    @Test void emptyFilterProducesValidEmptyWorkbookNotNotFound() throws Exception {
        var response = mvc.perform(download("CALENDAR").param("jobCode", "ABSENT"))
            .andExpect(status().isOk()).andReturn().getResponse();
        try (var book = workbook(response)) {
            assertThat(book.getSheet("인력 운영 달력").getLastRowNum()).isZero();
            assertThat(book.getSheet("요약").getRow(1).getCell(0).getNumericCellValue()).isZero();
        }
    }
    @Test void tentativeEvidenceKeepsIdsTextAndProbabilitiesNumericWithoutReducingSpareMm() throws Exception {
        when(calendar.tentatives()).thenReturn(List.of(new StaffingReadDtos.TentativeSource("0009", "O-0001", 1,
            "0001", "202610", "ACT", "UI-29", null, "202612", new BigDecimal("50"))));
        var response = mvc.perform(download("CALENDAR").param("jobCode", "DEV"))
            .andExpect(status().isOk()).andReturn().getResponse();
        try (var book = workbook(response)) {
            assertThat(book.getSheet("인력 운영 달력").getRow(1).getCell(24).getStringCellValue()).isEqualTo("가배정");
            assertThat(book.getSheet("월별 상세").getRow(10).getCell(6).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(book.getSheet("월별 상세").getRow(10).getCell(6).getNumericCellValue()).isEqualTo(50);
            assertThat(book.getSheet("가배정 근거").getRow(1).getCell(2).getStringCellValue()).isEqualTo("0009");
            assertThat(book.getSheet("월별 합계").getRow(10).getCell(1).getNumericCellValue()).isZero();
            assertThat(book.getSheet("월별 합계").getRow(10).getCell(2).getNumericCellValue()).isEqualTo(1);
        }
    }
    @Test void supplyIsNextSixMonthsAcrossYearBoundaryRegardlessOfCalendarYearAndFilters() throws Exception {
        var response = mvc.perform(download("SUPPLY_DEMAND").param("year", "2025").param("jobCode", "PM")
                .param("gradeCode", "HIGH").param("employeeType", "OS").param("remainingOnly", "true"))
            .andExpect(status().isOk()).andReturn().getResponse();
        try (var book = workbook(response)) {
            var sheet = book.getSheet("월별 가용 vs 필요");
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short)7);
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("2026-11");
            assertThat(sheet.getRow(0).getCell(6).getStringCellValue()).isEqualTo("2027-04");
            assertThat(sheet.getRow(2).getCell(1).getNumericCellValue()).isEqualTo(1);
            assertThat(book.getSheet("직무별 계산근거").getRow(1).getCell(0).getStringCellValue()).isEqualTo("개발");
        }
        verifyNoInteractions(calendar);
        verify(forecasts).standards("202610");
        verify(audit).insert(argThat(e -> e.screenId().equals("UI-28") && e.description().contains("인력운영수요공급")));
    }
    @Test void supplyRetainsMissingSourceReasons() throws Exception {
        when(forecasts.standards(anyString())).thenReturn(List.of());
        var response = mvc.perform(download("SUPPLY_DEMAND")).andExpect(status().isOk()).andReturn().getResponse();
        try (var book = workbook(response)) { assertThat(book.getSheet("미확인").getLastRowNum()).isEqualTo(3); }
    }
    @ParameterizedTest @ValueSource(strings={"actor", "page", "size", "from", "months", "includeUnfilled"})
    void unknownQueryIsRejectedBeforeReads(String field) throws Exception {
        mvc.perform(download("CALENDAR").param(field, "bad")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value(field))
            .andExpect(jsonPath("$.details.violations[0].field").value(field)).andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(calendar, forecasts, monthly, audit);
    }
    @ParameterizedTest @CsvSource({"year,0", "year,10000", "year,2028", "year,2026.0", "year,2026-01", "employeeType,BAD", "remainingOnly,yes", "amountUnit,won", "gradeCode,TOOLONGCODE1", "jobCode,TOOLONGCODE1", "sort,bad", "direction,bad"})
    void invalidFiltersReturnFieldErrors(String key, String value) throws Exception {
        mvc.perform(download("CALENDAR").param(key, value)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.field").value(key)).andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(calendar, forecasts, monthly, audit);
    }
    @Test void requiresTabAndRejectsInvalidTabDuplicateAndGetBody() throws Exception {
        mvc.perform(get("/api/workforce-operations/export")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("tab"));
        mvc.perform(download("INVALID")).andExpect(status().isBadRequest());
        mvc.perform(download("CALENDAR").param("year", "2026", "2025")).andExpect(status().isBadRequest());
        mvc.perform(download("CALENDAR").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(calendar, forecasts, monthly, audit);
    }
    @Test void rejectsYearBeforeCashBaseWithoutAudit() throws Exception {
        mvc.perform(download("CALENDAR").param("year", "2024")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PERIOD")).andExpect(jsonPath("$.field").value("year"));
        verifyNoInteractions(calendar, forecasts, audit);
    }
    @Test void failedAccessLogRollsBackBeforeAnyBinaryHeadersOrBodyAreSent() throws Exception {
        when(audit.insert(any())).thenReturn(0);
        mvc.perform(download("CALENDAR")).andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(header().doesNotExist("Content-Disposition")).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        verify(transactions, atLeastOnce()).rollback(any());
    }
    @Test void sourceFailureHidesSqlAndDoesNotLogOrSendPartialWorkbook() throws Exception {
        when(calendar.people()).thenThrow(new IllegalStateException("private SQL SELECT"));
        mvc.perform(download("CALENDAR")).andExpect(status().isInternalServerError())
            .andExpect(header().doesNotExist("Content-Disposition"))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
        verifyNoInteractions(audit);
    }
    @Test void auditCommitFailureDoesNotSendWorkbook() throws Exception {
        doAnswer(c -> {
            if (mockingDetails(audit).getInvocations().stream().anyMatch(i -> i.getMethod().getName().equals("insert")))
                throw new IllegalStateException("private commit detail");
            return null;
        }).when(transactions).commit(any());
        mvc.perform(download("SUPPLY_DEMAND")).andExpect(status().isInternalServerError())
            .andExpect(header().doesNotExist("Content-Disposition"))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private commit detail"))));
    }
}
