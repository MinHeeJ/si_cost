package com.sicost.structure;

import com.sicost.businessplansops.*;
import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.AccessLogWriter;
import com.sicost.profitability.*;
import com.sicost.profitability.MonthlyCostDtos.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.nullValue;

@WebMvcTest(ProfitStructureController.class)
@ContextConfiguration(classes=ProfitStructureController.class)
@org.springframework.transaction.annotation.EnableTransactionManagement
@Import({ProfitStructureService.class,ProfitStructureRepository.class,RevenueCompositionCalculation.class,
    BusinessPlanActuals.class,TurnkeyRevenueCalculation.class,ProfitStructureExportService.class,ExcelExportService.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProfitStructureContractTest {
    @Autowired MockMvc mvc;
    @MockBean BusinessPlanPerformanceMapper financial;
    @MockBean ProfitStructureMapper mapper;
    @MockBean MonthlyCostService costs;
    @MockBean CurrentUserProvider users;
    @MockBean AccessLogWriter logs;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    final OffsetDateTime at=OffsetDateTime.parse("2026-07-15T09:00:00+09:00");
    Sources originals;
    @BeforeEach void setup() {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(clock.instant()).thenReturn(at.toInstant());when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(financial.baseMonth()).thenReturn("202601");when(financial.snapshots(any())).thenReturn(List.of());when(financial.closes(any())).thenReturn(List.of());when(financial.annualTax(any())).thenReturn(n("120"));
        when(financial.financialSources(any())).thenReturn(List.of(new Source("2026-07","TB_BL_BILL","bill","tkSales","1000",false,"청구일"),new Source("2026-07","TB_FI_ITEM","item","sgaInputAmt","50",false,null),new Source("2026-07","TB_CO_PREV_ADJ","adj","prevAdjAmt","-20",false,null)));
        when(mapper.customers()).thenReturn(List.of(new ProfitStructureDtos.ProjectCustomer("project","customer","=수식 아님")));
        when(mapper.bills(any())).thenReturn(List.of(new ProfitStructureDtos.ProjectAmount("2026-07","project",n("1000"))));when(mapper.expenses(any())).thenReturn(List.of());
        originals=new Sources(new ProfitabilityPeriod(2026,"202601","202601","202607",at,false),"admin",LocalDate.of(2026,7,15),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        when(costs.loadYear(any())).thenReturn(originals);when(costs.calculate(any())).thenReturn(new MonthlyCostsData(originals.period(),List.of()));
    }
    static BigDecimal n(String value) {return value==null?null:new BigDecimal(value);}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String path,String month,String aggregation) {return get(path).param("year",month.substring(0,4)).param("month",month).param("aggregation",aggregation);}
    ResultActions read(String month,String aggregation)throws Exception {return mvc.perform(request("/api/profit-loss/structure",month,aggregation));}
    @Test void publicReadFourCardsAndAllViewsShareLastBucketWithoutMasking()throws Exception {
        read("2026-07","MONTH").andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.months.length()").value(12)).andExpect(jsonPath("$.data.summary.length()").value(4))
            .andExpect(jsonPath("$.data.context.timezone").value("Asia/Seoul")).andExpect(jsonPath("$.data.summary[0].amount").value("1000"))
            .andExpect(jsonPath("$.data.buckets[6].amounts.salesAmt").value("1000")).andExpect(jsonPath("$.data.numericRows[0].bucketValues[6]").value("1000"))
            .andExpect(jsonPath("$.data.waterfall[0].amount").value("1000")).andExpect(jsonPath("$.data.buckets[6].composition.contractTypes[0].salesAmt").value("1000"))
            .andExpect(jsonPath("$.data.months[7].amounts.salesAmt").value(nullValue()));
        verify(users).currentUser();verifyNoInteractions(logs);
    }
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","REG_USER_ID","REG_DTTM","unknown"})
    void injectedFieldsRejectedForBothOperationsBeforeSourceReads(String key)throws Exception {
        for(String path:List.of("/api/profit-loss/structure","/api/profit-loss/structure/export"))mvc.perform(request(path,"2026-07","MONTH").param(key,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(financial,mapper,costs,logs,users);
    }
    @ParameterizedTest @ValueSource(strings={"2026-00","2026-13","2026-7","0000-01","2025-07"})
    void malformedOrMismatchedMonth(String month)throws Exception {mvc.perform(get("/api/profit-loss/structure").param("year","2026").param("month",month).param("aggregation","MONTH")).andExpect(status().isBadRequest());verifyNoInteractions(financial,costs);}
    @ParameterizedTest @ValueSource(strings={"year","month","aggregation"})
    void duplicateQueriesRejected(String key)throws Exception {mvc.perform(request("/api/profit-loss/structure","2026-07","MONTH").param(key,"2026")).andExpect(status().isBadRequest());verifyNoInteractions(financial);}
    @Test void invalidAggregationRejected()throws Exception {read("2026-07","WEEK").andExpect(status().isBadRequest());verifyNoInteractions(financial);}
    @Test void futureRejectedWithoutCostReads()throws Exception {read("2026-08","MONTH").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERIOD"));verifyNoInteractions(costs);}
    @Test void outsideBasePreviousYearRejected()throws Exception {read("2024-12","MONTH").andExpect(status().isBadRequest());verifyNoInteractions(costs);}
    @Test void historicalReadsNeverInvokePersonalPeriodOrInventZero()throws Exception {read("2025-03","MONTH").andExpect(status().isOk()).andExpect(jsonPath("$.data.buckets[0].amounts.salesAmt").value(nullValue())).andExpect(jsonPath("$.data.months[0].sourceKind").value("MISSING"));verifyNoInteractions(costs);}
    @Test void closeMissingSnapshotNeverUsesLiveBills()throws Exception {when(financial.closes(any())).thenReturn(List.of(new Close("202607","CLOSE")));read("2026-07","MONTH").andExpect(jsonPath("$.data.buckets[6].amounts.salesAmt").value(nullValue())).andExpect(jsonPath("$.data.months[6].completeness.issues[0].reason").value("마감 손익 자료 미확인"));}
    @Test void closedRawValuesWinAndOptionalNullStaysNull()throws Exception {when(financial.closes(any())).thenReturn(List.of(new Close("202607","CLOSE")));when(financial.snapshots(any())).thenReturn(List.of(copy("202607","Y","N")));read("2026-07","MONTH").andExpect(jsonPath("$.data.summary[0].amount").value("500")).andExpect(jsonPath("$.data.summary[1].amount").value(nullValue())).andExpect(jsonPath("$.data.months[6].sourceKind").value("CLOSED_SNAPSHOT"));}
    @Test void unfixedCopyIsNotCalculationSource()throws Exception {when(financial.snapshots(any())).thenReturn(List.of(copy("202607","N","N")));read("2026-07","MONTH").andExpect(jsonPath("$.data.summary[0].amount").value("1000"));}
    @Test void migratedNullDoesNotBackfillAndMissingHistoryIsOmittedWithCoverage()throws Exception {when(financial.snapshots(any())).thenReturn(List.of(copy("202501","Y","Y")));read("2025-03","YTD").andExpect(jsonPath("$.data.buckets[2].amounts.salesAmt").value("500")).andExpect(jsonPath("$.data.buckets[2].amounts.grossPrftAmt").value(nullValue())).andExpect(jsonPath("$.data.buckets[2].completeness.availableMonthCount").value(1)).andExpect(jsonPath("$.data.buckets[2].composition.visible").value(false)).andExpect(jsonPath("$.data.buckets[2].referenceRevenue.visible").value(false));}
    @Test void absentPreviousYearHidesSummaryComparison()throws Exception {read("2026-07","MONTH").andExpect(jsonPath("$.data.summary[0].previousYear").value(nullValue()));}
    @Test void zeroDenominatorProducesNullRates()throws Exception {read("2026-01","MONTH").andExpect(jsonPath("$.data.buckets[0].amounts.grossProfitRate").value(nullValue())).andExpect(jsonPath("$.data.buckets[0].amounts.idleCostShare").value(nullValue()));}
    @Test void negativeAdjustmentsAndTaxKeepSignInWaterfallAndSubtotals()throws Exception {when(financial.annualTax(any())).thenReturn(n("-120"));read("2026-07","MONTH").andExpect(jsonPath("$.data.waterfall[4].amount").value("-20")).andExpect(jsonPath("$.data.waterfall[7].amount").value("10")).andExpect(jsonPath("$.data.waterfall[5].kind").value("SUBTOTAL")).andExpect(jsonPath("$.data.waterfall[5].amount").value("930"));}
    @Test void incompleteQuarterUsesSameNumberOfMonthsInPreviousQuarter()throws Exception {read("2026-07","QUARTER").andExpect(jsonPath("$.data.buckets[2].monthCount").value(1)).andExpect(jsonPath("$.data.buckets[2].comparisons[0].comparisonMonths[0]").value("2026-04"));}
    @Test void ytdHasOnlyPreviousYearComparisons()throws Exception {read("2026-07","YTD").andExpect(jsonPath("$.data.summary[0].previous").value(nullValue())).andExpect(jsonPath("$.data.buckets[6].comparisons[0].comparisonMonths.length()").value(7));}
    @Test void repeatedReadHasNoAuditWritesAndOneSourceReadPerYear()throws Exception {read("2026-07","MONTH");read("2026-07","MONTH");verify(costs,times(2)).loadYear(2026);verifyNoInteractions(logs);verify(manager,atLeastOnce()).getTransaction(argThat(d->d.isReadOnly()&&d.getIsolationLevel()==TransactionDefinition.ISOLATION_REPEATABLE_READ));}
    @Test void publicExportCreatesRealNumericXlsxThenCommitsDownloadLog()throws Exception {
        var response=mvc.perform(request("/api/profit-loss/structure/export","2026-07","MONTH").param("amountUnit","WON")).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn().getResponse();
        assertThat(response.getHeader("Content-Disposition")).contains("202607_MONTH.xlsx");
        try(var workbook=new XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(workbook.getSheet("기간별 손익·참고선").getRow(1).getCell(7).getNumericCellValue()).isEqualTo(1000);
            assertThat(workbook.getSheet("기간별 손익·참고선").getRow(7).getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
            assertThat(workbook.getSheet("고객사 구성").getRow(1).getCell(0).getStringCellValue()).contains("=수식 아님");
            assertThat(workbook.getSheet("고객사 구성").getRow(1).getCell(0).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
        }
        verify(logs).append(argThat(e->"DNLD".equals(e.action())&&"UI-07".equals(e.screenId())),any());
        verify(manager,atLeastOnce()).commit(any());
    }
    @Test void exportAuditFailureNeverTransfersWorkbook()throws Exception {doThrow(new IllegalStateException("private audit SQL")).when(logs).append(any(),any());var response=mvc.perform(request("/api/profit-loss/structure/export","2026-07","MONTH")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn().getResponse();assertThat(response.getHeader("Content-Disposition")).isNull();assertThat(response.getContentAsString()).doesNotContain("private audit SQL");}
    @Test void invalidExportUnitAndDuplicateQueryNeverGenerateOrLog()throws Exception {mvc.perform(request("/api/profit-loss/structure/export","2026-07","MONTH").param("amountUnit","bad")).andExpect(status().isBadRequest());mvc.perform(request("/api/profit-loss/structure/export","2026-07","MONTH").param("month","2026-06")).andExpect(status().isBadRequest());verifyNoInteractions(logs,financial);}
    static Snapshot copy(String month,String fixed,String migrated) {return new Snapshot(month,fixed,migrated,n("500"),null,null,null,n("200"),null,null,null,null,n("100"),null,n("-5"),null,null,n("1"),null);}
}
