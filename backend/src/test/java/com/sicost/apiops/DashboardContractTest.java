package com.sicost.apiops;

import com.sicost.common.api.*;
import com.sicost.cashflowops.*;
import com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;
import com.sicost.structure.ProfitStructureRepository;
import com.sicost.forecast.*;
import com.sicost.monthclosesops.MonthCloseDtos;
import com.sicost.shared.SharedQueryDtos.CloseRow;
import com.sicost.profitability.MonthlyCostDtos;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@ContextConfiguration(classes=DashboardController.class)
@Import({DashboardService.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class DashboardContractTest {
    @Autowired MockMvc mvc;
    @MockBean DashboardReadRepository repository;
    @MockBean DashboardFinancialSources financial;
    @MockBean DashboardOperationalSources operations;
    @MockBean CashFlowService cash;
    @MockBean MonthCloseReadService closes;
    @MockBean Clock clock;
    final YearMonth month=YearMonth.of(2026,12);
    final LocalDate day=LocalDate.of(2026,12,15);
    Map<String,Actual> actuals;
    @BeforeEach void setup() {
        Clock fixed=Clock.fixed(day.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),ZoneId.of("Asia/Seoul"));
        when(clock.instant()).thenReturn(fixed.instant());when(clock.getZone()).thenReturn(fixed.getZone());when(clock.withZone(any())).thenReturn(fixed);
        when(repository.base()).thenReturn("202601");when(repository.closes(any(),any())).thenReturn(List.of(new CloseRow("202611","CLOSE")));
        when(repository.section(any())).thenAnswer(call->{try{return new DashboardReadRepository.Read<>(((Supplier<?>)call.getArgument(0)).get(),false);}catch(RuntimeException ex){return new DashboardReadRepository.Read<>(null,true);}});
        when(repository.standard(any(),any())).thenReturn(BigDecimal.ONE);
        when(repository.receivables(day)).thenReturn(new DashboardDtos.ReceivableTotals(new BigDecimal("700"),1,new BigDecimal("200")));
        when(repository.taxes(day)).thenReturn(new DashboardDtos.TaxDue(0));
        when(repository.targets(2026)).thenReturn(java.util.stream.IntStream.rangeClosed(1,12).mapToObj(i->new Target("2026"+String.format("%02d",i),new BigDecimal("100"),new BigDecimal("20"))).toList());
        actuals=new LinkedHashMap<>();for(int i=1;i<=12;i++)actuals.put(YearMonth.of(2026,i).toString(),actual(YearMonth.of(2026,i).toString(),"100","20"));
        when(financial.read(any())).thenAnswer(call->new ProfitStructureRepository.Read(null,List.copyOf(actuals.values()),actuals,Map.of(),List.of(),List.of()));
        when(financial.utilization(any(),any())).thenReturn(new DashboardFinancialSources.Utilization("50.0",1));when(financial.parts(any(),any())).thenReturn(List.of());
        var snapshot=mock(ProjectAnalysisDtos.Snapshot.class);var calculated=mock(MonthlyCostDtos.MonthlyCostsData.class);when(snapshot.costs()).thenReturn(calculated);when(calculated.items()).thenReturn(List.of());
        when(operations.projects(day)).thenReturn(new DashboardOperationalSources.Projects(snapshot,List.of(),List.of()));
        when(operations.withdrawals(day)).thenReturn(new WithdrawalDtos.Guidance(true,0,0,0,"없음"));
        var forecast=mock(SalesForecastSources.Snapshot.class);when(forecast.opportunities()).thenReturn(List.of());
        var workforce=mock(SalesForecastDtos.Workforce.class);when(workforce.totals()).thenReturn(java.util.stream.IntStream.rangeClosed(1,6).mapToObj(i->new SalesForecastDtos.WorkforceMonth(month.plusMonths(i).toString(),"5.0","0.0","0.0","4.0","1.0","5","0","0","AVAILABLE")).toList());
        when(operations.forecast(day)).thenReturn(new DashboardOperationalSources.Forecast(forecast,workforce));
        when(cash.calculateContiguousRange(month,3)).thenReturn(java.util.stream.IntStream.range(0,3).mapToObj(i->new CashFlowDtos.Month(month.plusMonths(i).toString(),"PLANNED","100",List.of(),"0","0","100",true,false,List.of())).toList());
        var checks=java.util.stream.IntStream.rangeClosed(1,6).mapToObj(i->new MonthCloseDtos.Check("CHECK"+i,"검사","OK",0L,"정상",null,"UI-14",month.toString(),null)).toList();
        when(closes.getMonthCloses(any())).thenReturn(new MonthCloseReadDtos.Result(month.toString(),"WORK",null,null,month.toString(),"2026-11",true,true,true,false,"revision",checks,"CURRENT",0,null,List.of()));
    }
    Actual actual(String key,String sales,String profit) {
        var amounts=new HashMap<String,String>();amounts.put("salesAmt",sales);amounts.put("oprtPrftAmt",profit);amounts.put("salesCostAmt","80");
        return new Actual(key,"CALCULATED","WORK",false,false,true,amounts,new Completeness(true,true,true,List.of(),List.of(),List.of(key),List.of(),1,1,List.of()),List.of(),day.atStartOfDay().atOffset(ZoneOffset.ofHours(9)));
    }
    @Test void publicDashboardUsesOriginalValuesAndExactPeriodRanges() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isOk()).andExpect(jsonPath("$.data.scope").value("ALL")).andExpect(jsonPath("$.data.metrics.length()").value(5))
            .andExpect(jsonPath("$.data.metrics[0].value").value("100")).andExpect(jsonPath("$.data.metrics[1].value").value("20")).andExpect(jsonPath("$.data.metrics[2].value").value("240"))
            .andExpect(jsonPath("$.data.metrics[4].value").value("700")).andExpect(jsonPath("$.data.metrics[4].overdueAmount").value("200"))
            .andExpect(jsonPath("$.data.closeProgress.passedCheckCount").value(6)).andExpect(jsonPath("$.data.salesCostTrend.length()").value(12))
            .andExpect(jsonPath("$.data.supplyForecast[5].month").value("2027-06")).andExpect(jsonPath("$.data.cashForecast[2].month").value("2027-02"));
        verify(cash).calculateContiguousRange(month,3);verify(repository).receivables(day);
    }
    @Test void zeroAndNegativePreviousDoNotHaveChangeRates() throws Exception {
        actuals.put("2026-11",actual("2026-11","0","-10"));
        mvc.perform(get("/api/dashboard")).andExpect(status().isOk()).andExpect(jsonPath("$.data.metrics[0].changeRate").isEmpty()).andExpect(jsonPath("$.data.metrics[0].difference").value("100"))
            .andExpect(jsonPath("$.data.metrics[1].changeRate").isEmpty()).andExpect(jsonPath("$.data.metrics[1].difference").value("30"));
    }
    @Test void nonPositiveTargetsHaveNullAchievement() throws Exception {
        when(repository.targets(2026)).thenReturn(List.of(new Target("202612",BigDecimal.ZERO,new BigDecimal("-1"))));
        mvc.perform(get("/api/dashboard")).andExpect(status().isOk()).andExpect(jsonPath("$.data.metrics[0].achievementRate").isEmpty()).andExpect(jsonPath("$.data.metrics[2].achievementRate").isEmpty());
    }
    @Test void zeroUtilizationDenominatorRemainsUnknown() throws Exception {
        when(financial.utilization(any(),any())).thenReturn(new DashboardFinancialSources.Utilization(null,0));
        mvc.perform(get("/api/dashboard")).andExpect(status().isOk()).andExpect(jsonPath("$.data.metrics[3].value").isEmpty()).andExpect(jsonPath("$.data.metrics[3].basis.status").value("UNCONFIRMED"));
    }
    @Test void failingOneSourcePreservesOtherSectionsWithoutZeroing() throws Exception {
        when(operations.forecast(day)).thenThrow(new IllegalStateException("SQL private diagnostics"));
        mvc.perform(get("/api/dashboard")).andExpect(status().isOk()).andExpect(jsonPath("$.data.supplyBasis.status").value("FAILED")).andExpect(jsonPath("$.data.supplyForecast[0].requiredPersons").isEmpty())
            .andExpect(jsonPath("$.data.metrics[0].value").value("100")).andExpect(jsonPath("$.data.actionTypeCount").isEmpty()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private diagnostics"))));
    }
    @Test void successfulEmptyActionsAreZeroNotAnUnknownSource() throws Exception {
        when(repository.receivables(day)).thenReturn(new DashboardDtos.ReceivableTotals(BigDecimal.ZERO,0,BigDecimal.ZERO));
        mvc.perform(get("/api/dashboard")).andExpect(status().isOk()).andExpect(jsonPath("$.data.actions").isEmpty()).andExpect(jsonPath("$.data.actionsBasis.status").value("EMPTY")).andExpect(jsonPath("$.data.actionTypeCount").value(0));
    }
    @Test void selectedQDoesNotChangeCurrentDAndMCashOrWorkforce() throws Exception {
        mvc.perform(get("/api/dashboard").param("month","2026-06")).andExpect(status().isOk()).andExpect(jsonPath("$.data.selectedMonth").value("2026-06")).andExpect(jsonPath("$.data.currentMonth").value("2026-12"))
            .andExpect(jsonPath("$.data.salesCostTrend[6].salesAmount").isEmpty()).andExpect(jsonPath("$.data.salesCostTrend[6].salesTargetAmount").value("100"));
        verify(operations).forecast(day);verify(financial).read(YearMonth.of(2026,6));
    }
    @ParameterizedTest @ValueSource(strings={"2025-12","2027-01"})
    void outOfRangeIs400BeforeSourceAssembly(String value) throws Exception {
        mvc.perform(get("/api/dashboard").param("month",value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERIOD")).andExpect(jsonPath("$.field").value("month")).andExpect(jsonPath("$.requestId").isString());verifyNoInteractions(financial,operations,cash,closes);
    }
    @ParameterizedTest @ValueSource(strings={"2026-00","2026-13","2026-1","0000-01","2026-01-01"})
    void malformedMonthIs400(String value) throws Exception {mvc.perform(get("/api/dashboard").param("month",value)).andExpect(status().isBadRequest());verifyNoInteractions(repository);}
    @ParameterizedTest @ValueSource(strings={"userId","page","sort","actor","asOfDate"})
    void unknownFieldsAreRejected(String field) throws Exception {mvc.perform(get("/api/dashboard").param(field,"injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(repository);}
    @Test void financialPercentageRoundingHasPositivePreviousGuard(){assertThat(DashboardService.change("101","100")).isEqualTo("1.0");assertThat(DashboardService.change("101","0")).isNull();assertThat(DashboardService.change("101","-100")).isNull();}
    @Test void utilizationLinkUsesWorkforceScreenAndUnknownCloseCheckIsNotPassed() throws Exception {
        var result=closes.getMonthCloses(null);
        var checks=new ArrayList<>(result.checks());
        checks.set(5,new MonthCloseDtos.Check("UNKNOWN","검사","UNKNOWN",null,"원천 미확인",null,"UI-14","2026-12",null));
        when(closes.getMonthCloses(any())).thenReturn(new MonthCloseReadDtos.Result("2026-12","WORK",null,null,"2026-12","2026-11",true,true,true,false,"revision",checks,"CURRENT",0,null,List.of()));
        mvc.perform(get("/api/dashboard")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.metrics[3].link.screenId").value("UI-28"))
            .andExpect(jsonPath("$.data.closeProgress.passedCheckCount").value(5))
            .andExpect(jsonPath("$.data.closeProgress.remainingChecks[0].count").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.closeProgress.remainingChecks[0].status").value("UNKNOWN"));
    }
    @Test void everyRequiredLinkRetainsItsSelectedOrCurrentPeriodAndRealIdentifier() throws Exception {
        when(repository.taxes(day)).thenReturn(new DashboardDtos.TaxDue(1));
        var snapshot=mock(ProjectAnalysisDtos.Snapshot.class);
        var costs=mock(MonthlyCostDtos.MonthlyCostsData.class);
        var person=mock(MonthlyCostDtos.MonthlyPersonCost.class);
        var row=mock(MonthlyCostDtos.MonthlyCostRow.class);
        when(row.costClass()).thenReturn("PRE");when(row.assignmentMm()).thenReturn("2.00");
        when(person.personId()).thenReturn("00001");when(person.month()).thenReturn("202612");
        when(person.assignmentMm()).thenReturn("2.00");when(person.rows()).thenReturn(List.of(row));
        when(snapshot.costs()).thenReturn(costs);when(costs.items()).thenReturn(List.of(person));
        var board=new HashMap<String,Object>();board.put("contractType","MO");board.put("partId",null);
        board.put("light","위험");board.put("overdueIssueCount",1);board.put("lastUpdatedDate",null);
        var alert=new HashMap<String,Object>();alert.put("projectId","000012");alert.put("projectName","프로젝트");
        alert.put("contractType","MO");alert.put("monthlyMargin","-1.0");alert.put("budgetUsage","100.0");
        alert.put("alerts",List.of(Map.of("kind","BUDGET_OVER","primaryCause","예산 초과")));
        when(operations.projects(day)).thenReturn(new DashboardOperationalSources.Projects(snapshot,List.of(alert),List.of(board)));
        when(operations.withdrawals(day)).thenReturn(new WithdrawalDtos.Guidance(true,1,1,1,"철수 예정"));
        var source=mock(SalesForecastSources.Snapshot.class);
        when(source.opportunities()).thenReturn(List.of(new SalesForecastSources.Opportunity("000003","기회","MO","ING","202612",1,BigDecimal.TEN,BigDecimal.TEN,null),
            new SalesForecastSources.Opportunity("WAIT","대기","MO","WAIT","202701",1,BigDecimal.TEN,BigDecimal.TEN,null)));
        var workforce=mock(SalesForecastDtos.Workforce.class);
        when(workforce.totals()).thenReturn(List.of(new SalesForecastDtos.WorkforceMonth("2027-01","0.0","2.0","0.0","2.0","-2.0","0","2","0","AVAILABLE")));
        when(operations.forecast(day)).thenReturn(new DashboardOperationalSources.Forecast(source,workforce));
        when(cash.calculateContiguousRange(month,3)).thenReturn(List.of(new CashFlowDtos.Month("2027-01","PLANNED","0",List.of(),"0","0","0",true,true,List.of())));
        var close=closes.getMonthCloses(null);var checks=new ArrayList<>(close.checks());
        checks.set(0,new MonthCloseDtos.Check("BILL_MISS","검사","WARN",1L,"누락",null,"UI-13","2026-06",null));
        when(closes.getMonthCloses(any())).thenReturn(new MonthCloseReadDtos.Result("2026-06","WORK",null,null,"2026-12","2026-11",true,true,true,false,"revision",checks,"CURRENT",0,null,List.of()));
        var response=mvc.perform(get("/api/dashboard").param("month","2026-06")).andExpect(status().isOk()).andReturn();
        var data=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).path("data");
        String[] metricScreens={"UI-08","UI-08","UI-18","UI-28","UI-13"};
        for(int i=0;i<metricScreens.length;i++) {
            assertThat(data.path("metrics").get(i).path("link").path("screenId").asText()).isEqualTo(metricScreens[i]);
            assertThat(data.path("metrics").get(i).path("basis").path("basisMonth").asText()).isEqualTo(i==4?"2026-12":"2026-06");
            if(i<4)assertThat(data.path("metrics").get(i).path("link").path("linkKey").asText()).isEqualTo("2026-06");
        }
        String[] screens={"UI-13","UI-10","UI-21","UI-28","UI-12","UI-12","UI-05","UI-01","UI-31","UI-31","UI-31","UI-13","UI-29"};
        assertThat(data.path("actions").size()).isEqualTo(13);
        for(int i=0;i<screens.length;i++)assertThat(data.path("actions").get(i).path("link").path("screenId").asText()).as("action "+i).isEqualTo(screens[i]);
        assertThat(data.path("actions").get(2).path("link").path("linkKey").asText()).isEqualTo("2027-01");
        assertThat(data.path("actions").get(3).path("link").path("linkKey").asText()).isEqualTo("2027-01");
        assertThat(data.path("actions").get(4).path("basis").path("basisMonth").asText()).isEqualTo("2026-12");
        assertThat(data.path("actions").get(11).path("link").path("linkKey").asText()).isEqualTo("2026-06");
        assertThat(data.path("closeProgress").path("link").path("screenId").asText()).isEqualTo("UI-14");
        assertThat(data.path("closeProgress").path("link").path("linkKey").asText()).isEqualTo("2026-06");
        assertThat(data.path("projectAlerts").get(0).path("link").path("screenId").asText()).isEqualTo("UI-04");
        assertThat(data.path("projectAlerts").get(0).path("link").path("linkKey").asText()).isEqualTo("000012");
        assertThat(data.path("pipeline").path("startingSoon").get(0).path("link").path("screenId").asText()).isEqualTo("UI-29");
        assertThat(data.path("pipeline").path("startingSoon").get(0).path("link").path("linkKey").asText()).isEqualTo("000003");
        for(var cashRow:data.path("cashForecast")) {
            assertThat(cashRow.path("link").path("screenId").asText()).isEqualTo("UI-21");
            assertThat(cashRow.path("link").path("linkKey").asText()).isEqualTo(cashRow.path("month").asText());
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/dashboard-all-links.json"),data.toPrettyString());
    }
    @Test void pipelineTruncatesEachOpportunityAndIncludesOnlyMThroughMPlusThreeInUpcoming() throws Exception {
        var forecast=mock(SalesForecastSources.Snapshot.class);
        when(forecast.opportunities()).thenReturn(List.of(
            new SalesForecastSources.Opportunity("past","과거","MO","ING","202611",1,new BigDecimal("101"),new BigDecimal("50"),null),
            new SalesForecastSources.Opportunity("now","현재","MO","ING","202612",1,new BigDecimal("101"),new BigDecimal("50"),null),
            new SalesForecastSources.Opportunity("edge","경계","MO","ING","202703",1,new BigDecimal("101"),new BigDecimal("50"),null),
            new SalesForecastSources.Opportunity("future","미래","MO","ING","202704",1,new BigDecimal("101"),new BigDecimal("50"),null)));
        var workforce=mock(SalesForecastDtos.Workforce.class);when(workforce.totals()).thenReturn(List.of());
        when(operations.forecast(day)).thenReturn(new DashboardOperationalSources.Forecast(forecast,workforce));
        mvc.perform(get("/api/dashboard")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.pipeline.opportunityCount").value(4))
            .andExpect(jsonPath("$.data.pipeline.weightedExpectedAmount").value("200"))
            .andExpect(jsonPath("$.data.pipeline.startingSoon[*].opportunityId").value(org.hamcrest.Matchers.contains("now","edge")));
    }
    @Test void financialRoundingIsFinalHalfUpWithUnknownAndNonPositiveDenominatorGuards() {
        assertThat(DashboardService.change("3","2")).isEqualTo("50.0");
        assertThat(DashboardService.change("1","3")).isEqualTo("-66.7");
        assertThat(DashboardService.change(null,"3")).isNull();
        assertThat(DashboardService.change("3",null)).isNull();
        assertThat(com.sicost.businessplansops.BusinessPlanActuals.ratio("1","3",true)).isEqualTo("33.3");
        assertThat(com.sicost.businessplansops.BusinessPlanActuals.ratio("1","0",true)).isNull();
    }
}
