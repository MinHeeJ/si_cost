package com.sicost.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.forecast.SalesForecastFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest(SalesForecastController.class)
@org.springframework.transaction.annotation.EnableTransactionManagement
@ContextConfiguration(classes=SalesForecastController.class)
@Import({SalesRevenueForecastService.class,SalesForecastRepository.class,SalesWorkforceProjectionService.class,SalesForecastExportService.class,
    ExcelExportService.class,AccessLogWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class SalesForecastContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean SalesForecastMapper mapper;
    @MockBean AssignmentMonthlyService monthly;
    @MockBean CurrentUserProvider users;
    @MockBean AccessLogMapper audit;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    @Autowired com.sicost.common.excel.ExcelExportService excel;
    @MockBean com.sicost.common.excel.ExportLabels exportLabels;
    @BeforeEach void setup() {
        com.sicost.common.excel.ExportTestLookups.configure(exportLabels);
        com.sicost.common.excel.ExportTestLookups.configure(excel);
        when(clock.instant()).thenReturn(Instant.parse("2026-07-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c -> new SimpleTransactionStatus());
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));when(users.userId()).thenReturn("admin");
        when(mapper.projects()).thenReturn(List.of(project("P","TK","ING","2026-10-31","150","50")));
        when(mapper.opportunities()).thenReturn(List.of());when(mapper.needs()).thenReturn(List.of());when(mapper.plans()).thenReturn(List.of());
        when(mapper.people()).thenReturn(List.of());when(mapper.leaves()).thenReturn(List.of());when(mapper.candidates()).thenReturn(List.of());
        when(mapper.assignmentIds()).thenReturn(List.of());when(mapper.jobs()).thenReturn(List.of());
        when(mapper.standards("202607")).thenReturn(standards().entrySet().stream().map(e -> new SalesForecastSources.Standard(e.getKey(),e.getValue())).toList());
        when(audit.insert(any())).thenReturn(1);
    }
    @Test void publicTypedForecastUsesNextSixMonthsWithoutMaskingOrWrites() throws Exception {
        var response=mvc.perform(get("/api/sales-opportunities/forecast")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.from").value("2026-08")).andExpect(jsonPath("$.data.months").value(6))
            .andExpect(jsonPath("$.data.revenueMonths.length()").value(6)).andExpect(jsonPath("$.data.revenueMonths[5].month").value("2027-01"))
            .andExpect(jsonPath("$.data.revenueMonths[0].projectRevenue").value("33")).andExpect(jsonPath("$.data.revenueMonths[2].projectRevenue").value("34"))
            .andExpect(jsonPath("$.data.summary.sixMonthRevenue").value("100")).andReturn();
        var typed=json.treeToValue(json.readTree(response.getResponse().getContentAsString()).get("data"),SalesForecastDtos.Result.class);
        assertThat(typed.workforce().includeUnfilled()).isTrue();verify(users).currentUser();verifyNoInteractions(audit,monthly);
    }
    @ParameterizedTest @ValueSource(strings={"actor","from","months","year","page","amountUnit","REG_USER_ID"})
    void rejectsEveryUnknownForecastQueryBeforeSourceReads(String key) throws Exception {
        mvc.perform(get("/api/sales-opportunities/forecast").param(key,"invalid")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value(key)).andExpect(jsonPath("$.details.violations[0].field").value(key)).andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(mapper,monthly,audit);
    }
    @ParameterizedTest @ValueSource(strings={"actor","from","months","year","page"})
    void rejectsUnknownExportQueryWithoutCreatingFileOrAudit(String key) throws Exception {
        mvc.perform(get("/api/sales-opportunities/forecast/export").param(key,"invalid")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(mapper,monthly,audit);
    }
    @ParameterizedTest @ValueSource(strings={"won","INVALID","","원"}) void rejectsInvalidUnit(String unit) throws Exception {
        mvc.perform(get("/api/sales-opportunities/forecast/export").param("amountUnit",unit)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("amountUnit"));verifyNoInteractions(mapper,audit);
    }
    @Test void duplicateUnitIsNotSilentlyOverwritten() throws Exception {
        mvc.perform(get("/api/sales-opportunities/forecast/export").param("amountUnit","WON","MILLION")).andExpect(status().isBadRequest());verifyNoInteractions(mapper,audit);
    }
    @Test void getBodyCannotInjectActor() throws Exception {
        mvc.perform(get("/api/sales-opportunities/forecast").contentType("application/json").content("{\"actor\":\"other\"}")).andExpect(status().isBadRequest());verifyNoInteractions(mapper,audit);
    }
    @Test void missingStandardsAreIssuesAndDoNotOverwriteRevenueWithZero() throws Exception {
        when(mapper.standards(anyString())).thenReturn(List.of());
        mvc.perform(get("/api/sales-opportunities/forecast")).andExpect(status().isOk()).andExpect(jsonPath("$.data.workforce.issues[0].status").value("UNKNOWN"))
            .andExpect(jsonPath("$.data.summary.sixMonthRevenue").value("100"));verifyNoInteractions(audit,monthly);
    }
    @Test void exportCreatesRealNumericWorkbookCommitsDnldAndThenReturnsBinary() throws Exception {
        when(mapper.projects()).thenReturn(List.of(project("P","TK","ING","2026-10-31","100","0")));
        when(mapper.opportunities()).thenReturn(List.of(opportunity("O","ING","202608",3,"100","50")));
        var response=mvc.perform(get("/api/sales-opportunities/forecast/export").param("amountUnit","MILLION")).andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString(".xlsx"))).andReturn().getResponse();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(book.getSheet("월별 매출").getRow(1).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(book.getSheet("월별 매출").getRow(1).getCell(1).getNumericCellValue()).isEqualTo(33);
            assertThat(book.getSheet("월별 매출").getRow(1).getCell(2).getNumericCellValue()).isEqualTo(16);
            assertThat(book.getSheet("수요공급")).isNotNull();assertThat(book.getSheet("채용 검토")).isNotNull();assertThat(book.getSheet("다운로드정보")).isNotNull();
        }
        var order=inOrder(audit,manager);order.verify(audit).insert(argThat(e -> e.action().equals("DNLD")&&e.screenId().equals("UI-29")));order.verify(manager,times(2)).commit(any());verifyNoInteractions(monthly);
    }
    @Test void zeroAndNullStayNumericZeroAndBlankWhileUserNamesAreNotFormulas() throws Exception {
        var p=project("0001","TK","ING","2026-10-31","0","0");
        when(mapper.projects()).thenReturn(List.of(new SalesForecastSources.Project(p.id(),"=SUM(1,2)",p.type(),p.status(),p.startDate(),p.endDate(),p.amount(),p.billed(),null)));
        var response=mvc.perform(get("/api/sales-opportunities/forecast/export")).andExpect(status().isOk()).andReturn().getResponse();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var row=book.getSheet("매출 원천").getRow(1);assertThat(row.getCell(1).getStringCellValue()).isEqualTo("0001");
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("프로젝트");
            assertThat(row.getCell(6).getStringCellValue()).doesNotContain("05", "preview", "TB_");
            assertThat(row.getCell(2).getCellType()).isEqualTo(CellType.STRING);assertThat(row.getCell(2).getStringCellValue()).isEqualTo("=SUM(1,2)");
            assertThat(row.getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);assertThat(row.getCell(4).getNumericCellValue()).isZero();assertThat(row.getCell(5).getCellType()).isEqualTo(CellType.BLANK);
        }
    }
    @Test void auditFailurePreventsBinaryTransmissionAndHidesSql() throws Exception {
        when(audit.insert(any())).thenReturn(0);
        mvc.perform(get("/api/sales-opportunities/forecast/export")).andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith("application/json")).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));verify(manager,atLeastOnce()).rollback(any());
    }
    @Test void generationFailureDoesNotAppendDnldOrExposeSql() throws Exception {
        when(mapper.projects()).thenThrow(new IllegalStateException("private SQL password"));
        mvc.perform(get("/api/sales-opportunities/forecast/export")).andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));verifyNoInteractions(audit);
    }
}
