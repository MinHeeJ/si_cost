package com.sicost.workforceforecastsops;

import com.sicost.common.api.*;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.forecast.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
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
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest(WorkforceForecastExportController.class)
@org.springframework.transaction.annotation.EnableTransactionManagement
@ContextConfiguration(classes = WorkforceForecastExportController.class)
@Import({WorkforceForecastExportService.class, SalesForecastRepository.class, SalesWorkforceProjectionService.class,
    ExcelExportService.class, AccessLogWriter.class, ApiExceptionHandler.class, RequestIdFilter.class, JsonConfiguration.class})
class WorkforceForecastExportContractTest {
    @Autowired MockMvc mvc;
    @Autowired WorkforceForecastExportService exports;
    @Autowired ExcelExportService excel;
    @MockBean SalesForecastMapper mapper;
    @MockBean AssignmentMonthlyService monthly;
    @MockBean CurrentUserProvider users;
    @MockBean AccessLogMapper audit;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    @MockBean com.sicost.common.excel.ExportLabels exportLabels;
    @BeforeEach void setup() {
        com.sicost.common.excel.ExportTestLookups.configure(exportLabels);
        com.sicost.common.excel.ExportTestLookups.configure(excel);
        when(clock.instant()).thenReturn(Instant.parse("2026-07-15T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul")); when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c -> new SimpleTransactionStatus());
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin", "관리자", List.of("ADMIN"), true));
        when(users.userId()).thenReturn("admin"); when(audit.insert(any())).thenReturn(1);
        when(mapper.standards("202607")).thenReturn(List.of(new SalesForecastSources.Standard("SHORT_PERSON", new BigDecimal("0.4")),
            new SalesForecastSources.Standard("SURPLUS_PERSON", BigDecimal.ONE), new SalesForecastSources.Standard("HIRE_LEAD_MON", new BigDecimal("2"))));
        when(mapper.people()).thenReturn(List.of(new SalesForecastSources.Person("D", "0001", "Y", "DRCT", null, null)));
        when(mapper.opportunities()).thenReturn(List.of(new SalesForecastSources.Opportunity("O", "기회", null, "ING", "202607", null, BigDecimal.TEN, new BigDecimal("50"), null)));
        when(mapper.needs()).thenReturn(List.of(new SalesForecastSources.Need("O", 1, "0001", "202607", 12, 2, BigDecimal.ONE)));
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder download() {
        return get("/api/workforce-forecasts/export").param("from", "2026-07").param("months", "6");
    }
    @Test void publicExportReturnsRealWorkbookWithWholeSelectedYearAndCalculationBasis() throws Exception {
        var response = mvc.perform(download()).andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".xlsx"))).andReturn().getResponse();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var table = book.getSheet("월별 가용 vs 필요");
            assertThat(table.getRow(0).getLastCellNum()).isEqualTo((short) 7);
            assertThat(table.getRow(0).getCell(6).getStringCellValue()).isEqualTo("2026-12");
            assertThat(table.getRow(1).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(table.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(1);
            assertThat(table.getRow(3).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(table.getRow(3).getCell(1).getNumericCellValue()).isZero();
            var basis = book.getSheet("직무별 계산근거");
            assertThat(basis.getLastRowNum()).isEqualTo(6);
            assertThat(basis.getRow(1).getCell(0).getStringCellValue()).isEqualTo("시험 직무");
            assertThat(basis.getRow(1).getCell(7).getStringCellValue()).isEqualTo("보통");
            assertThat(basis.getRow(0).getLastCellNum()).isEqualTo((short)9);
            for(var c:basis.getRow(0)) assertThat(c.toString()).doesNotContain("반올림 전");
            assertThat(book.getSheet("다운로드정보").getRow(4).getCell(1).getStringCellValue()).isEqualTo("천원");
        }
        var order = inOrder(audit, manager);
        order.verify(audit).insert(argThat(e -> e.action().equals("DNLD") && e.screenId().equals("UI-17") && e.userId().equals("admin")));
        order.verify(manager, times(2)).commit(any());
        verify(users, atLeastOnce()).currentUser(); verifyNoInteractions(monthly);
    }
    @Test void nextYearExportsAllTwelveMonthsAndCanExcludeUnfilled() throws Exception {
        var response = mvc.perform(get("/api/workforce-forecasts/export").param("from", "2027-01").param("months", "12").param("includeUnfilled", "false").param("amountUnit", "MILLION"))
            .andExpect(status().isOk()).andReturn().getResponse();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var header = book.getSheetAt(0).getRow(0);
            assertThat(header.getLastCellNum()).isEqualTo((short) 13);
            assertThat(header.getCell(12).getStringCellValue()).isEqualTo("2027-12");
            assertThat(book.getSheet("다운로드정보").getRow(4).getCell(1).getStringCellValue()).isEqualTo("백만원");
        }
        verify(mapper).standards("202607");
    }
    @ParameterizedTest @ValueSource(strings = {"actor", "year", "page", "size", "sort", "REG_USER_ID"})
    void unknownQueryIsRejectedBeforeAnyReadsOrAudit(String key) throws Exception {
        mvc.perform(download().param(key, "bad")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value(key)).andExpect(jsonPath("$.details.violations[0].field").value(key))
            .andExpect(jsonPath("$.requestId").isNotEmpty()).andExpect(header().exists("X-Request-Id"));
        verifyNoInteractions(mapper, audit, monthly);
    }
    @ParameterizedTest @CsvSource({"from,2026-13", "from,2026-7", "from,0000-01", "from,2026-07-01", "from,10000-01", "months,0", "months,13", "months,1.0", "months,-1", "months,999999999999", "includeUnfilled,yes", "includeUnfilled,TRUE", "amountUnit,원", "amountUnit,won"})
    void invalidQueryIsNotCoercedOrTruncated(String key, String value) throws Exception {
        var builder = get("/api/workforce-forecasts/export").param("from", key.equals("from") ? value : "2026-07")
            .param("months", key.equals("months") ? value : "6");
        if (!Set.of("from", "months").contains(key)) builder.param(key, value);
        mvc.perform(builder).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.field").value(key)).andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(mapper, audit, monthly);
    }
    @ParameterizedTest @ValueSource(strings = {"from", "months"}) void missingRequiredQuery(String key) throws Exception {
        var request = get("/api/workforce-forecasts/export");
        request.param(key.equals("from") ? "months" : "from", key.equals("from") ? "6" : "2026-07");
        mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(key)); verifyNoInteractions(mapper, audit);
    }
    @Test void currentMonthBoundaryUsesSeoulNotUtc() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-07-31T15:00:00Z"));
        mvc.perform(download()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERIOD")); verifyNoInteractions(mapper, audit);
    }
    @Test void rejectsEndMonthOverflow() throws Exception {
        mvc.perform(get("/api/workforce-forecasts/export").param("from", "9999-12").param("months", "2"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERIOD")); verifyNoInteractions(mapper, audit);
    }
    @Test void rejectsDuplicateQuery() throws Exception {
        mvc.perform(download().param("months", "6", "12")).andExpect(status().isBadRequest()); verifyNoInteractions(mapper, audit);
    }
    @Test void bodyCannotSupplyActor() throws Exception {
        mvc.perform(download().contentType("application/json").content("{\"actor\":\"someone\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")); verifyNoInteractions(mapper, audit);
    }
    @Test void missingStandardsAreExportedAsUnknownNotSuppressed() throws Exception {
        when(mapper.standards(anyString())).thenReturn(List.of());
        var response = mvc.perform(download()).andExpect(status().isOk()).andReturn().getResponse();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(book.getSheet("미확인").getLastRowNum()).isEqualTo(3);
            assertThat(book.getSheet("직무별 계산근거").getRow(1).getCell(7).getStringCellValue()).isEqualTo("미확인");
        }
    }
    @Test void formulaLikeJobCodesRemainLiteralText() throws Exception {
        when(exportLabels.labels("JOB")).thenReturn(Map.of("=1+1","=1+1","0001","시험 직무"));
        when(mapper.people()).thenReturn(List.of(new SalesForecastSources.Person("D", "=1+1", "Y", "DRCT", null, null)));
        var response = mvc.perform(download()).andExpect(status().isOk()).andReturn().getResponse();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var row = book.getSheet("직무별 계산근거").getRow(7);
            assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("=1+1");
        }
    }
    @Test void auditFailureRollsBackAndTransmitsNoBinary() throws Exception {
        when(audit.insert(any())).thenReturn(0);
        mvc.perform(download()).andExpect(status().isInternalServerError()).andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")); verify(manager, atLeastOnce()).rollback(any());
    }
    @Test void nullRemainsBlankAndZeroRemainsNumericInFullValuesWorkbook() throws Exception {
        when(exportLabels.labels("JOB")).thenReturn(Map.of("@0001","@0001"));
        var month = new SalesForecastDtos.WorkforceMonth("2026-07", null, "0", "0", "0", null, null, "0", "0", "UNKNOWN");
        var data = new SalesForecastDtos.Workforce("2026-07", 1, true, "2026-07-15", "2026-07", List.of(month),
            List.of(new SalesForecastDtos.JobMonth("@0001", month, "=literal basis")), List.of(), List.of(), null, "0", null, null);
        var spec = new ExcelExportService.Specification("인력수요예측", null,
            List.of(new ExcelExportService.Column("metric", "항목", ExcelExportService.CellType.TEXT)), "전체", "원", "UI-17");
        try (var download = excel.generateWorkbook(spec, () -> exports.workbook(data, "WON"), new org.springframework.mock.web.MockHttpServletRequest())) {
            var bytes = new java.io.ByteArrayOutputStream(); download.transferTo(bytes);
            try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
                assertThat(book.getSheetAt(0).getRow(1).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
                assertThat(book.getSheetAt(0).getRow(1).getCell(1).getNumericCellValue()).isZero();
                assertThat(book.getSheetAt(0).getRow(2).getCell(1).getCellType()).isEqualTo(CellType.BLANK);
                assertThat(book.getSheet("직무별 계산근거").getRow(1).getCell(0).getStringCellValue()).isEqualTo("@0001");
                assertThat(book.getSheet("직무별 계산근거").getRow(1).getCell(8).getCellType()).isEqualTo(CellType.STRING);
            }
        }
    }
    @Test void generationFailureHasNoAccessLogAndHidesSql() throws Exception {
        when(mapper.people()).thenThrow(new IllegalStateException("SELECT private SQL"));
        mvc.perform(download()).andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
        verifyNoInteractions(audit);
    }
}
