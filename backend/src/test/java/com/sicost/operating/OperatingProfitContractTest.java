package com.sicost.operating;

import com.sicost.businessplansops.*;
import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.AccessLogWriter;
import com.sicost.profitability.*;
import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.structure.*;
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

@WebMvcTest(OperatingProfitController.class)
@ContextConfiguration(classes=OperatingProfitController.class)
@org.springframework.transaction.annotation.EnableTransactionManagement
@Import({OperatingProfitService.class,ProfitStructureRepository.class,BusinessPlanActuals.class,
    OperatingProfitExportService.class,ExcelExportService.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class OperatingProfitContractTest {
    @Autowired MockMvc mvc;
    @MockBean BusinessPlanPerformanceMapper financial;
    @MockBean ProfitStructureMapper mapper;
    @MockBean MonthlyCostService costs;
    @MockBean CurrentUserProvider users;
    @MockBean AccessLogWriter logs;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    final OffsetDateTime at=OffsetDateTime.parse("2026-07-15T09:00:00+09:00");
    static final String READ="/api/profit-loss/operating",EXPORT=READ+"/export";
    @BeforeEach void setup() {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","=임시 관리자",List.of("ADMIN"),true));
        when(clock.instant()).thenReturn(at.toInstant());when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(financial.baseMonth()).thenReturn("202601");when(financial.snapshots(any())).thenReturn(List.of());when(financial.closes(any())).thenReturn(List.of());when(financial.annualTax(any())).thenReturn(n("120"));
        when(financial.financialSources(any())).thenReturn(List.of(new Source("2026-07","TB_BL_BILL","bill","tkSales","1000",false,"청구일"),new Source("2026-07","TB_FI_ITEM","item","sgaInputAmt","50",false,null),new Source("2026-07","TB_CO_PREV_ADJ","adj","prevAdjAmt","-20",false,null)));
        when(mapper.customers()).thenReturn(List.of());when(mapper.bills(any())).thenReturn(List.of());when(mapper.expenses(any())).thenReturn(List.of());
        Sources sources=new Sources(new ProfitabilityPeriod(2026,"202601","202601","202607",at,false),"admin",LocalDate.of(2026,7,15),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        when(costs.loadYear(any())).thenReturn(sources);when(costs.calculate(any())).thenReturn(new MonthlyCostsData(sources.period(),List.of()));
    }
    static BigDecimal n(String value) {return value==null?null:new BigDecimal(value);}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String path,String month) {return get(path).param("year",month.substring(0,4)).param("month",month);}
    ResultActions read(String month)throws Exception {return mvc.perform(request(READ,month));}
    @Test void publicUnmaskedStatementUsesOneMaterializedResultInExactAccountingOrder()throws Exception {
        var response=read("2026-07").andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.months.length()").value(12)).andExpect(jsonPath("$.data.summary.length()").value(4))
            .andExpect(jsonPath("$.data.context.timezone").value("Asia/Seoul")).andExpect(jsonPath("$.data.rows.length()").value(13))
            .andExpect(jsonPath("$.data.summary[0].amount").value("1000")).andExpect(jsonPath("$.data.rows[0].monthlyValues[6]").value("1000"))
            .andExpect(jsonPath("$.data.rows[0].cumulativeValue").value("1000")).andExpect(jsonPath("$.data.rows[11].cumulativeValue").value("930"))
            .andExpect(jsonPath("$.data.months[7].sourceKind").value("FUTURE")).andExpect(jsonPath("$.data.rows[0].monthlyValues[7]").value(nullValue())).andReturn();
        var tree=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString());
        assertThat(tree.at("/data/rows").findValuesAsText("metric")).containsExactlyElementsOf(OperatingProfitService.METRICS);
        verify(users).currentUser();verify(costs).loadYear(2026);verifyNoInteractions(logs);
    }
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","REG_USER_ID","REG_DTTM","aggregation","unknown"})
    void injectedAndUndeclaredFieldsRejectedForBothOperations(String key)throws Exception {
        for(String path:List.of(READ,EXPORT))mvc.perform(request(path,"2026-07").param(key,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(financial,mapper,costs,logs,users);
    }
    @ParameterizedTest @ValueSource(strings={"2026-00","2026-13","2026-7","0000-01","2025-07"})
    void malformedOrMismatchedMonthsRejectedBeforeSources(String month)throws Exception {
        for(String path:List.of(READ,EXPORT))mvc.perform(get(path).param("year","2026").param("month",month)).andExpect(status().isBadRequest());verifyNoInteractions(financial,costs,logs);
    }
    @ParameterizedTest @ValueSource(strings={"year","month"})
    void duplicatesRejectedBeforeSources(String key)throws Exception {
        for(String path:List.of(READ,EXPORT))mvc.perform(request(path,"2026-07").param(key,"2026")).andExpect(status().isBadRequest());verifyNoInteractions(financial,logs);
    }
    @Test void futurePeriodRejected()throws Exception {read("2026-08").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERIOD"));verifyNoInteractions(costs,logs);}
    @Test void beforeBasePreviousYearRejected()throws Exception {read("2024-12").andExpect(status().isBadRequest());verifyNoInteractions(costs,logs);}
    @Test void readsRejectAmountUnitNotInReadContract()throws Exception {mvc.perform(request(READ,"2026-07").param("amountUnit","WON")).andExpect(status().isBadRequest());verifyNoInteractions(financial);}
    @Test void exportRejectsInvalidAndDuplicateUnits()throws Exception {mvc.perform(request(EXPORT,"2026-07").param("amountUnit","bad")).andExpect(status().isBadRequest());mvc.perform(request(EXPORT,"2026-07").param("amountUnit","WON","WON")).andExpect(status().isBadRequest());verifyNoInteractions(financial,logs);}
    @Test void bodyInjectionRejectedBeforeSources()throws Exception {for(String path:List.of(READ,EXPORT))mvc.perform(request(path,"2026-07").contentType("application/json").content("{\"actor\":\"forged\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(financial,logs);}
    @Test void closeWithoutSnapshotHasNoLiveFallback()throws Exception {when(financial.closes(any())).thenReturn(List.of(new Close("202607","CLOSE")));read("2026-07").andExpect(jsonPath("$.data.rows[0].monthlyValues[6]").value(nullValue())).andExpect(jsonPath("$.data.months[6].completeness.issues[0].reason").value("마감 손익 자료 미확인"));}
    @Test void invalidFixedFlagHasNoLiveFallback()throws Exception {when(financial.closes(any())).thenReturn(List.of(new Close("202607","CLOSE")));when(financial.snapshots(any())).thenReturn(List.of(copy("202607","N","N")));read("2026-07").andExpect(jsonPath("$.data.rows[0].monthlyValues[6]").value(nullValue()));}
    @Test void closedCopyPreservesKnownTotalsAndOptionalNull()throws Exception {when(financial.closes(any())).thenReturn(List.of(new Close("202607","CLOSE")));when(financial.snapshots(any())).thenReturn(List.of(copy("202607","Y","N")));read("2026-07").andExpect(jsonPath("$.data.rows[0].monthlyValues[6]").value("500")).andExpect(jsonPath("$.data.rows[5].monthlyValues[6]").value(nullValue())).andExpect(jsonPath("$.data.rows[11].monthlyValues[6]").value("-5"));}
    @Test void unfixedOperationalCopyIsIgnored()throws Exception {when(financial.snapshots(any())).thenReturn(List.of(copy("202607","N","N")));read("2026-07").andExpect(jsonPath("$.data.rows[0].monthlyValues[6]").value("1000"));}
    @Test void migratedMissingMonthsOmittedButOptionalNullInvalidatesOnlySelectedMetric()throws Exception {when(financial.snapshots(any())).thenReturn(List.of(copy("202501","Y","Y")));read("2025-03").andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].cumulativeValue").value("500")).andExpect(jsonPath("$.data.rows[5].cumulativeValue").value(nullValue())).andExpect(jsonPath("$.data.completeness.availableMonthCount").value(1)).andExpect(jsonPath("$.data.completeness.requestedMonthCount").value(3)).andExpect(jsonPath("$.data.cumulativeProvisional").value(false));verifyNoInteractions(costs);}
    @Test void absentPreviousYearHidesBothColumns()throws Exception {read("2026-07").andExpect(jsonPath("$.data.showPreviousYear").value(false)).andExpect(jsonPath("$.data.summary[0].previousYear").value(nullValue()));}
    @Test void partialPriorYearNeverComparesDifferentMonthSets()throws Exception {when(financial.snapshots(any())).thenReturn(List.of(copy("202501","Y","Y")));read("2026-07").andExpect(jsonPath("$.data.showPreviousYear").value(true)).andExpect(jsonPath("$.data.rows[0].previousYearCumulativeValue").value("500")).andExpect(jsonPath("$.data.rows[0].comparison.comparable").value(false)).andExpect(jsonPath("$.data.rows[0].comparison.changeRate").value(nullValue())).andExpect(jsonPath("$.data.rows[0].comparison.comparisonMonths.length()").value(7));}
    @Test void januaryComparesCurrentMonthWithAllowedPriorDecember()throws Exception {when(financial.snapshots(any())).thenReturn(List.of(copy("202512","Y","Y")));read("2026-01").andExpect(jsonPath("$.data.summary[3].previous.comparisonMonths[0]").value("2025-12")).andExpect(jsonPath("$.data.summary[3].previous.comparisonValue").value("-5"));}
    @Test void zeroRevenueProducesNullRatesNotArtificialDenominator()throws Exception {read("2026-01").andExpect(jsonPath("$.data.rows[12].cumulativeValue").value(nullValue())).andExpect(jsonPath("$.data.rows[0].cumulativeValue").value("0"));}
    @Test void negativeAdjustmentPreservedAndProvisionalPropagated()throws Exception {read("2026-07").andExpect(jsonPath("$.data.rows[10].cumulativeValue").value("-20")).andExpect(jsonPath("$.data.summary[3].amount").value("930")).andExpect(jsonPath("$.data.cumulativeProvisional").value(true));}
    @Test void repeatedReadsHaveNoAuditAndRepeatableReadBoundary()throws Exception {read("2026-07");read("2026-07");verify(costs,times(2)).loadYear(2026);verifyNoInteractions(logs);verify(manager,atLeastOnce()).getTransaction(argThat(d->d.isReadOnly()&&d.getIsolationLevel()==TransactionDefinition.ISOLATION_REPEATABLE_READ));}
    @Test void publicExportContainsRealNumericWorkbookAndCommittedUi08Log()throws Exception {
        var response=mvc.perform(request(EXPORT,"2026-07").param("amountUnit","WON")).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn().getResponse();
        assertThat(org.springframework.http.ContentDisposition.parse(response.getHeader("Content-Disposition")).getFilename()).isEqualTo("영업이익표_202607.xlsx");
        try(var workbook=new XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet=workbook.getSheet("영업이익표");assertThat(sheet.getRow(1).getCell(7).getNumericCellValue()).isEqualTo(1000);
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isZero();assertThat(sheet.getRow(1).getCell(8).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
            assertThat(sheet.getRow(11).getCell(7).getNumericCellValue()).isEqualTo(-20);assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short)14);
            assertThat(sheet.getRow(0).getCell(7).getStringCellValue()).contains("작업중");assertThat(sheet.getRow(0).getCell(13).getStringCellValue()).contains("잠정");
            assertThat(workbook.getSheet("다운로드정보").getRow(1).getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
        }
        verify(logs).append(argThat(e->"DNLD".equals(e.action())&&"UI-08".equals(e.screenId())&&"영업이익표".equals(e.fileType())),any());
        var order=inOrder(logs,manager);order.verify(logs).append(any(),any());order.verify(manager).commit(any());
    }
    @Test void migratedExportDoesNotFillMissingDetailAndIncludesComparisonOnlyWhenAvailable()throws Exception {
        when(financial.snapshots(any())).thenReturn(List.of(copy("202501","Y","Y")));
        var response=mvc.perform(request(EXPORT,"2025-03").param("amountUnit","WON")).andExpect(status().isOk()).andReturn().getResponse();
        try(var workbook=new XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {assertThat(workbook.getSheet("영업이익표").getRow(6).getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);assertThat(workbook.getSheet("영업이익표").getRow(1).getCell(13).getNumericCellValue()).isEqualTo(500);}
        verifyNoInteractions(costs);
    }
    @ParameterizedTest @ValueSource(strings={"=1+1","+SUM(A1)","-1+1","@SUM(A1)"})
    void userOwnedMetadataStaysTextNeverFormula(String name)throws Exception {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin",name,List.of("ADMIN"),true));
        var response=mvc.perform(request(EXPORT,"2026-07")).andExpect(status().isOk()).andReturn().getResponse();
        try(var workbook=new XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
            var cell=workbook.getSheet("다운로드정보").getRow(1).getCell(1);
            assertThat(cell.getStringCellValue()).isEqualTo(name);assertThat(cell.getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            for(var sheet:workbook)for(var row:sheet)for(var value:row)assertThat(value.getCellType()).isNotEqualTo(org.apache.poi.ss.usermodel.CellType.FORMULA);
        }
    }
    @Test void auditFailureDoesNotSendFileOrLeakDetails()throws Exception {doThrow(new IllegalStateException("private SQL")).when(logs).append(any(),any());var response=mvc.perform(request(EXPORT,"2026-07")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn().getResponse();assertThat(response.getHeader("Content-Disposition")).isNull();assertThat(response.getContentType()).doesNotContain("spreadsheet");assertThat(response.getContentAsString()).doesNotContain("private SQL");}
    @Test void entireCompanyCostsIncludeIndirectLeaveFreePreAndInternalWithoutDoubleCounting()throws Exception {
        var rows=List.of(cost("mo","MO","ALLOC","200","10","20","PAID"),cost("dp","DP","ALLOC","300","11","30","PAID"),cost("tk","TK","ALLOC","9999","12","40","PAID"),cost("free","MO","ALLOC","0","13","50","FREE"),cost("pre","MO","PRE","0","14","60","PAID"),cost("lost","MO","IDLE","0","15","70","PAID"),cost("IDLE",null,"IDLE","0","16","0",null),cost("INDRCT",null,"INDRCT","0","17","0",null),cost("internal","IN","RND","0","18","80","PAID"));
        var people=rows.stream().map(r->new MonthlyPersonCost(r.personId(),r.month(),List.of(r),null,new MonthlyCostCompleteness(true,!"TK".equals(r.contractType()),false,true,List.of()),r.laborAllocated(),r.personalRevenue(),"1.00",false,false)).toList();
        var originals=costs.loadYear(2026);clearInvocations(costs);
        when(costs.calculate(any())).thenReturn(new MonthlyCostsData(originals.period(),people));
        when(financial.financialSources(any())).thenReturn(List.of(new Source("2026-07","TB_BL_BILL","bill","tkSales","1000",false,null),new Source("2026-07","TB_PJ_DRCT_EXPS","extra","directExtra","7",false,null),new Source("2026-07","TB_PJ_DRCT_EXPS","rnd","rndExtra","8",false,null)));
        read("2026-07").andExpect(jsonPath("$.data.months[6].amounts.salesAmt").value(n("200").add(n("300")).add(n("1000")).toPlainString()))
            .andExpect(jsonPath("$.data.months[6].amounts.allocCostAmt").value(n("10").add(n("11")).add(n("12")).add(n("13")).add(n("14")).toPlainString()))
            .andExpect(jsonPath("$.data.months[6].amounts.idleCostAmt").value(n("15").add(n("16")).toPlainString()))
            .andExpect(jsonPath("$.data.months[6].amounts.drctExpsAmt").value(n("20").add(n("30")).add(n("40")).add(n("50")).add(n("60")).add(n("70")).add(n("7")).toPlainString()))
            .andExpect(jsonPath("$.data.months[6].amounts.indrctLaborAmt").value("17")).andExpect(jsonPath("$.data.months[6].amounts.rndAmt").value(n("18").add(n("80")).add(n("8")).toPlainString()))
            .andExpect(jsonPath("$.data.months[6].completeness.revenueComplete").value(true));
    }
    @Test void unallocatedLaborPropagatesUnknownCostButPreservesKnownTkBilling()throws Exception {
        var originals=costs.loadYear(2026);clearInvocations(costs);
        var person=new MonthlyPersonCost("unallocated","202607",List.of(),null,new MonthlyCostCompleteness(false,false,false,false,List.of()),null,null,"0.00",false,false);
        when(costs.calculate(any())).thenReturn(new MonthlyCostsData(originals.period(),List.of(person)));
        read("2026-07").andExpect(jsonPath("$.data.months[6].amounts.salesAmt").value("1000")).andExpect(jsonPath("$.data.months[6].amounts.allocCostAmt").value(nullValue())).andExpect(jsonPath("$.data.months[6].amounts.oprtPrftAmt").value(nullValue()));
    }
    static MonthlyCostRow cost(String project,String type,String cls,String sales,String labor,String expense,String input) {
        return new StoredCost("202607",project,project,project,n("1.00"),n("0.00"),n(sales),n(labor),n("999"),n("999"),n(expense),cls,type,null,"N","admin",null,null,null,project,"DEV",input).toRow();
    }
    static Snapshot copy(String month,String fixed,String migrated) {return new Snapshot(month,fixed,migrated,n("500"),null,null,null,n("200"),null,null,null,null,n("100"),null,n("-5"),null,null,n("1"),null);}
}
