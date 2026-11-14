package com.sicost.net;

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

@WebMvcTest(NetProfitController.class)
@ContextConfiguration(classes=NetProfitController.class)
@org.springframework.transaction.annotation.EnableTransactionManagement
@Import({NetProfitService.class,ProfitStructureRepository.class,BusinessPlanActuals.class,RevenueCompositionCalculation.class,
    NetProfitExportService.class,ExcelExportService.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class NetProfitContractTest {
    @Autowired MockMvc mvc;
    @MockBean BusinessPlanPerformanceMapper financial;
    @MockBean ProfitStructureMapper mapper;
    @MockBean MonthlyCostService costs;
    @MockBean TurnkeyRevenueCalculation turnkey;
    @MockBean CurrentUserProvider users;
    @MockBean AccessLogWriter logs;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    final OffsetDateTime at=OffsetDateTime.parse("2026-07-15T09:00:00+09:00");
    static final String READ="/api/profit-loss/net",EXPORT=READ+"/export";
    @BeforeEach void setup() {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(clock.instant()).thenReturn(at.toInstant());when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(financial.baseMonth()).thenReturn("202601");when(financial.snapshots(any())).thenReturn(List.of());when(financial.closes(any())).thenReturn(List.of());when(financial.annualTax(any())).thenReturn(n("120"));
        when(financial.financialSources(any())).thenReturn(List.of(new Source("2026-07","TB_BL_BILL","bill","tkSales","1000",false,"청구일"),new Source("2026-07","TB_FI_ITEM","item","sgaInputAmt","50",false,null),new Source("2026-07","TB_CO_PREV_ADJ","adj","prevAdjAmt","-20",false,null)));
        when(mapper.customers()).thenReturn(List.of());when(mapper.bills(any())).thenReturn(List.of(new ProfitStructureDtos.ProjectAmount("2026-07","tk",n("1000"))));when(mapper.expenses(any())).thenReturn(List.of());
        Sources sources=new Sources(new ProfitabilityPeriod(2026,"202601","202601","202607",at,false),"admin",LocalDate.of(2026,7,15),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        when(costs.loadYear(any())).thenReturn(sources);when(costs.calculate(any())).thenReturn(new MonthlyCostsData(sources.period(),List.of()));
    }
    static BigDecimal n(String value) {return value==null?null:new BigDecimal(value);}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String path,String month) {return get(path).param("year",month.substring(0,4)).param("month",month);}
    ResultActions read(String month)throws Exception {return mvc.perform(request(READ,month));}
    @Test void publicUnmaskedNetStatementHasTwoCardsAndAllEighteenRowsFromOneRead()throws Exception {
        var response=read("2026-07").andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.months.length()").value(12)).andExpect(jsonPath("$.data.summary.length()").value(2))
            .andExpect(jsonPath("$.data.context.timezone").value("Asia/Seoul")).andExpect(jsonPath("$.data.rows.length()").value(18))
            .andExpect(jsonPath("$.data.summary[1].amount").value(n("1000").subtract(n("50")).subtract(n("20")).subtract(n("10")).toPlainString()))
            .andExpect(jsonPath("$.data.contributionMonth").value("2026-07")).andExpect(jsonPath("$.data.contributions.contractTypes[0].contributionAmt").value("1000"))
            .andExpect(jsonPath("$.data.contributions.contractTypes[0].positiveContributionShare").value("100.0"))
            .andExpect(jsonPath("$.data.rows[16].monthlyValues[7]").value(nullValue())).andReturn();
        var tree=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString());
        assertThat(tree.at("/data/rows").findValuesAsText("metric")).containsExactlyElementsOf(NetProfitService.METRICS);
        assertThat(tree.at("/data/summary/0/amount").asText()).isEqualTo(tree.at("/data/rows/16/cumulativeValue").asText());
        assertThat(tree.at("/data/summary/1/amount").asText()).isEqualTo(tree.at("/data/months/6/amounts/netPrftAmt").asText());
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
    @ParameterizedTest @ValueSource(strings={"2026-08","2024-12"})
    void futureAndOutOfRangeRejectedForBothOperations(String month)throws Exception {for(String path:List.of(READ,EXPORT))mvc.perform(request(path,month)).andExpect(status().isBadRequest());verifyNoInteractions(costs,logs);}
    @Test void readsRejectAmountUnitNotInReadContract()throws Exception {mvc.perform(request(READ,"2026-07").param("amountUnit","WON")).andExpect(status().isBadRequest());verifyNoInteractions(financial);}
    @Test void exportRejectsInvalidAndDuplicateUnits()throws Exception {mvc.perform(request(EXPORT,"2026-07").param("amountUnit","bad")).andExpect(status().isBadRequest());mvc.perform(request(EXPORT,"2026-07").param("amountUnit","WON","WON")).andExpect(status().isBadRequest());verifyNoInteractions(financial,logs);}
    @Test void bodyInjectionRejectedBeforeSources()throws Exception {for(String path:List.of(READ,EXPORT))mvc.perform(request(path,"2026-07").contentType("application/json").content("{\"actor\":\"forged\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(financial,logs);}
    @Test void closeWithoutSnapshotHasNoLiveFallback()throws Exception {when(financial.closes(any())).thenReturn(List.of(new Close("202607","CLOSE")));read("2026-07").andExpect(jsonPath("$.data.rows[16].monthlyValues[6]").value(nullValue())).andExpect(jsonPath("$.data.months[6].completeness.issues[0].reason").value("마감 손익 자료 미확인"));}
    @Test void invalidFixedFlagHasNoLiveFallback()throws Exception {when(financial.closes(any())).thenReturn(List.of(new Close("202607","CLOSE")));when(financial.snapshots(any())).thenReturn(List.of(copy("202607","N","N")));read("2026-07").andExpect(jsonPath("$.data.rows[0].monthlyValues[6]").value(nullValue()));}
    @Test void closedCopyKeepsNegativeNetAndOptionalNullRatherThanDeriving()throws Exception {when(financial.closes(any())).thenReturn(List.of(new Close("202607","CLOSE")));when(financial.snapshots(any())).thenReturn(List.of(copy("202607","Y","N")));read("2026-07").andExpect(jsonPath("$.data.rows[0].monthlyValues[6]").value("500")).andExpect(jsonPath("$.data.rows[5].monthlyValues[6]").value(nullValue())).andExpect(jsonPath("$.data.rows[16].monthlyValues[6]").value("-10")).andExpect(jsonPath("$.data.rows[17].monthlyValues[6]").value("-2.0"));}
    @Test void unfixedOperationalCopyIgnored()throws Exception {when(financial.snapshots(any())).thenReturn(List.of(copy("202607","N","N")));read("2026-07").andExpect(jsonPath("$.data.rows[0].monthlyValues[6]").value("1000"));}
    @Test void migratedMissingMonthsOmittedButOptionalNullOnlyInvalidatesItsMetric()throws Exception {when(financial.snapshots(any())).thenReturn(List.of(copy("202501","Y","Y")));read("2025-03").andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].cumulativeValue").value("500")).andExpect(jsonPath("$.data.rows[5].cumulativeValue").value(nullValue())).andExpect(jsonPath("$.data.rows[16].cumulativeValue").value("-10")).andExpect(jsonPath("$.data.completeness.availableMonthCount").value(1)).andExpect(jsonPath("$.data.completeness.requestedMonthCount").value(3)).andExpect(jsonPath("$.data.cumulativeProvisional").value(false)).andExpect(jsonPath("$.data.contributions.visible").value(false));verifyNoInteractions(costs);}
    @Test void migratedBasisMonthHidesContributionEvenWhenPriorMonthsHaveData()throws Exception {when(financial.snapshots(any())).thenReturn(List.of(copy("202507","Y","Y")));read("2025-07").andExpect(jsonPath("$.data.contributions.visible").value(false)).andExpect(jsonPath("$.data.contributions.contractTypes.length()").value(0));}
    @Test void absentPreviousYearHidesColumns()throws Exception {read("2026-07").andExpect(jsonPath("$.data.showPreviousYear").value(false)).andExpect(jsonPath("$.data.summary[0].previousYear").value(nullValue()));}
    @Test void partialPriorYearNeverComparesDifferentMonthSets()throws Exception {when(financial.snapshots(any())).thenReturn(List.of(copy("202501","Y","Y")));read("2026-07").andExpect(jsonPath("$.data.showPreviousYear").value(true)).andExpect(jsonPath("$.data.rows[16].previousYearCumulativeValue").value("-10")).andExpect(jsonPath("$.data.rows[16].comparison.comparable").value(false)).andExpect(jsonPath("$.data.rows[16].comparison.changeRate").value(nullValue())).andExpect(jsonPath("$.data.rows[16].comparison.comparisonMonths.length()").value(7));}
    @Test void januaryComparesBasisMonthWithAllowedPriorDecember()throws Exception {when(financial.snapshots(any())).thenReturn(List.of(copy("202512","Y","Y")));read("2026-01").andExpect(jsonPath("$.data.summary[1].previous.comparisonMonths[0]").value("2025-12")).andExpect(jsonPath("$.data.summary[1].previous.comparisonValue").value("-10"));}
    @Test void zeroRevenueProducesNullRateAndNegativeNetIsNotClamped()throws Exception {read("2026-01").andExpect(jsonPath("$.data.rows[17].cumulativeValue").value(nullValue())).andExpect(jsonPath("$.data.rows[16].cumulativeValue").value("-10"));}
    @Test void missingTaxOnlyNullsTaxDependentMetrics()throws Exception {when(financial.annualTax(any())).thenReturn(null);read("2026-07").andExpect(jsonPath("$.data.rows[0].monthlyValues[6]").value("1000")).andExpect(jsonPath("$.data.rows[11].monthlyValues[6]").value("930")).andExpect(jsonPath("$.data.rows[16].monthlyValues[6]").value(nullValue())).andExpect(jsonPath("$.data.months[6].completeness.taxComplete").value(false));}
    @Test void repeatedReadsHaveNoAuditAndUseRepeatableRead()throws Exception {read("2026-07");read("2026-07");verify(costs,times(2)).loadYear(2026);verifyNoInteractions(logs);verify(manager,atLeastOnce()).getTransaction(argThat(d->d.isReadOnly()&&d.getIsolationLevel()==TransactionDefinition.ISOLATION_REPEATABLE_READ));}
    @Test void fullCompanyCostClassesAndActualTkBillsReachNetWithoutExpenseOrMmDuplication()throws Exception {
        people(List.of(classified("mo","MO","ALLOC","200","10","20"),classified("dp","DP","ALLOC","300","11","30"),
            classified("tk","TK","ALLOC","9999","12","40"),classified("free","MO","ALLOC","0","13","50"),
            classified("pre","MO","PRE","0","14","60"),classified("lost","MO","IDLE","0","15","70"),
            classified("IDLE",null,"IDLE","0","16","0"),classified("INDRCT",null,"INDRCT","0","17","0"),classified("internal","IN","RND","0","18","80")));
        when(financial.financialSources(any())).thenReturn(List.of(new Source("2026-07","TB_BL_BILL","bill","tkSales","1000",false,null),
            new Source("2026-07","TB_PJ_DRCT_EXPS","extra","directExtra","7",false,null),new Source("2026-07","TB_PJ_DRCT_EXPS","rnd","rndExtra","8",false,null),
            new Source("2026-07","TB_FI_ITEM","income","noprIncmAmt","5",false,null),new Source("2026-07","TB_FI_ITEM","expense","noprExpsAmt","3",false,null)));
        var response=read("2026-07").andExpect(jsonPath("$.data.months[6].amounts.salesAmt").value(n("200").add(n("300")).add(n("1000")).toPlainString()))
            .andExpect(jsonPath("$.data.months[6].amounts.allocCostAmt").value(n("10").add(n("11")).add(n("12")).add(n("13")).add(n("14")).toPlainString()))
            .andExpect(jsonPath("$.data.months[6].amounts.idleCostAmt").value(n("15").add(n("16")).toPlainString()))
            .andExpect(jsonPath("$.data.months[6].amounts.drctExpsAmt").value(n("20").add(n("30")).add(n("40")).add(n("50")).add(n("60")).add(n("70")).add(n("7")).toPlainString()))
            .andExpect(jsonPath("$.data.months[6].amounts.indrctLaborAmt").value("17"))
            .andExpect(jsonPath("$.data.months[6].amounts.rndAmt").value(n("18").add(n("80")).add(n("8")).toPlainString()))
            .andExpect(jsonPath("$.data.months[6].completeness.revenueComplete").value(true)).andReturn();
        var amounts=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).at("/data/months/6/amounts");
        assertThat(amounts.path("netPrftAmt").asText()).isEqualTo(n(amounts.path("oprtPrftAmt").asText()).add(n("5")).subtract(n("3")).subtract(n("10")).toPlainString());
        verifyNoInteractions(logs);
    }
    @Test void missingAllocationPreservesKnownBillingAndOnlyInvalidatesDependentCostsAndProfits()throws Exception {
        var originals=costs.loadYear(2026);clearInvocations(costs);
        when(costs.calculate(any())).thenReturn(new MonthlyCostsData(originals.period(),List.of(new MonthlyPersonCost("unallocated","202607",List.of(),null,new MonthlyCostCompleteness(false,false,false,false,List.of()),null,null,"0.00",false,false))));
        read("2026-07").andExpect(jsonPath("$.data.months[6].amounts.salesAmt").value("1000"))
            .andExpect(jsonPath("$.data.months[6].amounts.allocCostAmt").value(nullValue()))
            .andExpect(jsonPath("$.data.months[6].amounts.netPrftAmt").value(nullValue()));
    }
    @Test void contributionIsBasisMonthNotYearToDateAndPositiveOnly()throws Exception {
        List<MonthlyCostRow> rows=List.of(cost("mo","MO","300","100"),cost("dp","DP","50","100"),cost("zero","TK","9999","1000"));
        people(rows);
        when(mapper.bills(any())).thenReturn(List.of(new ProfitStructureDtos.ProjectAmount("2026-06","tk",n("9000")),new ProfitStructureDtos.ProjectAmount("2026-07","tk",n("1000"))));
        read("2026-07").andExpect(jsonPath("$.data.contributions.contractTypes[?(@.groupId == 'MO')].positiveContributionShare").value(org.hamcrest.Matchers.contains("100.0")))
            .andExpect(jsonPath("$.data.contributions.contractTypes[?(@.groupId == 'DP')].loss").value(org.hamcrest.Matchers.contains(true)))
            .andExpect(jsonPath("$.data.contributions.contractTypes[?(@.groupId == 'DP')].showBar").value(org.hamcrest.Matchers.contains(false)))
            .andExpect(jsonPath("$.data.contributions.contractTypes[?(@.groupId == 'TK')].contributionAmt").value(org.hamcrest.Matchers.contains("0")))
            .andExpect(jsonPath("$.data.contributions.contractTypes[?(@.groupId == 'TK')].showBar").value(org.hamcrest.Matchers.contains(false)));
    }
    @Test void noPositiveContributionsHaveNoArtificialDenominator()throws Exception {people(List.of(cost("tk","TK","9999","2000")));read("2026-07").andExpect(jsonPath("$.data.contributions.contractTypes[0].positiveContributionShare").value(nullValue())).andExpect(jsonPath("$.data.contributions.contractTypes[0].showBar").value(false));}
    @Test void registeredZeroTypeIsVisibleWithZeroAmountNoRateAndNoBar()throws Exception {
        when(mapper.bills(any())).thenReturn(List.of());people(List.of(cost("mo","MO","0","0")));
        read("2026-07").andExpect(jsonPath("$.data.contributions.contractTypes[0].groupId").value("MO"))
            .andExpect(jsonPath("$.data.contributions.contractTypes[0].contributionAmt").value("0"))
            .andExpect(jsonPath("$.data.contributions.contractTypes[0].positiveContributionShare").value(nullValue()))
            .andExpect(jsonPath("$.data.contributions.contractTypes[0].showBar").value(false));
    }
    @Test void unknownContributionDoesNotClaimFullPositiveDistribution()throws Exception {people(List.of(cost("mo","MO","100",null)));read("2026-07").andExpect(jsonPath("$.data.contributions.complete").value(false)).andExpect(jsonPath("$.data.contributions.contractTypes[0].positiveContributionShare").value(nullValue()));}
    @Test void publicExportContainsNumericWorkbookContributionAndCommittedUi09Log()throws Exception {
        var response=mvc.perform(request(EXPORT,"2026-07").param("amountUnit","WON")).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn().getResponse();
        assertThat(org.springframework.http.ContentDisposition.parse(response.getHeader("Content-Disposition")).getFilename()).isEqualTo("순이익표_202607.xlsx");
        try(var workbook=new XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet=workbook.getSheet("순이익표");assertThat(sheet.getRow(1).getCell(7).getNumericCellValue()).isEqualTo(1000);
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isZero();assertThat(sheet.getRow(17).getCell(8).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
            assertThat(sheet.getRow(11).getCell(7).getNumericCellValue()).isEqualTo(-20);assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short)14);
            assertThat(sheet.getRow(0).getCell(7).getStringCellValue()).contains("작업중");assertThat(sheet.getRow(0).getCell(13).getStringCellValue()).contains("잠정");
            assertThat(workbook.getSheet("기준월 기여도").getRow(1).getCell(3).getNumericCellValue()).isEqualTo(1000);
            assertThat(workbook.getSheet("기준월 기여도").getRow(1).getCell(4).getNumericCellValue()).isEqualTo(100);
        }
        verify(logs).append(argThat(e->"DNLD".equals(e.action())&&"UI-09".equals(e.screenId())&&"순이익표".equals(e.fileType())),any());
        var order=inOrder(logs,manager);order.verify(logs).append(any(),any());order.verify(manager).commit(any());verify(costs).loadYear(2026);
    }
    @Test void migratedExportHasNoFabricatedCompositionOrOptionalDetails()throws Exception {
        when(financial.snapshots(any())).thenReturn(List.of(copy("202501","Y","Y")));
        var response=mvc.perform(request(EXPORT,"2025-01").param("amountUnit","WON")).andExpect(status().isOk()).andReturn().getResponse();
        try(var workbook=new XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {assertThat(workbook.getSheet("순이익표").getRow(6).getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);assertThat(workbook.getSheet("순이익표").getRow(17).getCell(1).getNumericCellValue()).isEqualTo(-10);assertThat(workbook.getSheet("기준월 기여도").getLastRowNum()).isZero();}
        verifyNoInteractions(costs);
    }
    @ParameterizedTest @ValueSource(strings={"=1+1","+SUM(A1)","-1+1","@SUM(A1)"})
    void userMetadataRemainsTextNeverFormula(String name)throws Exception {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin",name,List.of("ADMIN"),true));
        var response=mvc.perform(request(EXPORT,"2026-07")).andExpect(status().isOk()).andReturn().getResponse();
        try(var workbook=new XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {var cell=workbook.getSheet("다운로드정보").getRow(1).getCell(1);assertThat(cell.getStringCellValue()).isEqualTo(name);assertThat(cell.getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);for(var sheet:workbook)for(var row:sheet)for(var value:row)assertThat(value.getCellType()).isNotEqualTo(org.apache.poi.ss.usermodel.CellType.FORMULA);}
    }
    @Test void auditFailureSendsNoWorkbookAndNoPrivateError()throws Exception {doThrow(new IllegalStateException("private SQL")).when(logs).append(any(),any());var response=mvc.perform(request(EXPORT,"2026-07")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn().getResponse();assertThat(response.getHeader("Content-Disposition")).isNull();assertThat(response.getContentType()).doesNotContain("spreadsheet");assertThat(response.getContentAsString()).doesNotContain("private SQL");}
    void people(List<MonthlyCostRow> rows) {var originals=costs.loadYear(2026);clearInvocations(costs);when(costs.calculate(any())).thenReturn(new MonthlyCostsData(originals.period(),rows.stream().map(r->new MonthlyPersonCost(r.personId(),r.month(),List.of(r),null,new MonthlyCostCompleteness(r.costComplete(),r.revenueComplete(),false,true,List.of()),r.laborAllocated(),r.personalRevenue(),"1.00",false,false)).toList()));}
    static MonthlyCostRow cost(String project,String type,String sales,String labor) {return new StoredCost("202607",project,project,project,n("1.00"),n("0.00"),n(sales),n(labor),n("0"),n("0"),n("0"),"ALLOC",type,null,"N","admin",null,null,null,project,"DEV","PAID").toRow();}
    static MonthlyCostRow classified(String project,String type,String cls,String sales,String labor,String expense) {return new StoredCost("202607",project,project,project,n("1.00"),n("0.00"),n(sales),n(labor),n("999"),n("999"),n(expense),cls,type,null,"N","admin",null,null,null,project,"DEV","free".equals(project)?"FREE":"PAID").toRow();}
    static Snapshot copy(String month,String fixed,String migrated) {return new Snapshot(month,fixed,migrated,n("500"),null,null,null,n("200"),null,null,null,null,n("100"),null,n("-5"),null,null,n("1"),n("-10"));}
}
