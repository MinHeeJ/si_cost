package com.sicost.apiops;

import com.sicost.assignmentcandidatesops.AssignmentCandidateRepository;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.forecast.*;
import com.sicost.staffingplancomparisonops.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.sicost.apiops.StaffingReadDtos.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest({AssignmentCandidateQueryController.class,StaffingPlanComparisonController.class,WorkforceForecastController.class,WorkforceOperationsController.class})
@ContextConfiguration(classes={AssignmentCandidateQueryController.class,StaffingPlanComparisonController.class,WorkforceForecastController.class,WorkforceOperationsController.class})
@Import({AssignmentCandidateQueryService.class,StaffingPlanComparisonService.class,WorkforceForecastService.class,WorkforceCalendarService.class,
    StaffingComparisonRepository.class,SalesForecastRepository.class,SalesWorkforceProjectionService.class,AssignmentMonthlyService.class,ChangeHistoryWriter.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class StaffingReadContractTest {
    @Autowired MockMvc mvc;
    @MockBean StaffingReadMapper mapper;
    @MockBean StaffingComparisonMapper comparisons;
    @MockBean SalesForecastMapper forecasts;
    @MockBean AssignmentMonthlyMapper months;
    @MockBean AssignmentCandidateRepository revisions;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper audit;
    @MockBean Clock clock;
    final LocalDate day=LocalDate.of(2026,7,1);
    @BeforeEach void setup(){
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        when(months.cashBase()).thenReturn("202501");when(revisions.revision(anyString())).thenReturn("revision");
        when(forecasts.standards(anyString())).thenReturn(List.of(new SalesForecastSources.Standard("SHORT_PERSON",new BigDecimal("0.4")),new SalesForecastSources.Standard("SURPLUS_PERSON",BigDecimal.ONE),new SalesForecastSources.Standard("HIRE_LEAD_MON",new BigDecimal("2"))));
    }
    @AfterEach void readOnly(){verifyNoInteractions(audit);verify(months,never()).saveMonth(any());verify(months,never()).project(any());}
    @Test void candidateListIsPublicYearIndependentAndDecimalStrings()throws Exception{
        when(mapper.candidates("202607")).thenReturn(List.of(new CandidateSource("10","O-1","P-1","프로젝트","D-1","개발자","DEV",day.plusYears(2),day.plusYears(3),new BigDecimal("0.50"),null),new CandidateSource("2","O-2","P-2","프로젝트2","D-2","개발자2","DEV",day,day.plusMonths(2),BigDecimal.ONE,new BigDecimal("100"))));
        mvc.perform(get("/api/assignment-candidates")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.items[0].id").value("2")).andExpect(jsonPath("$.data.items[1].contractMm").value("0.50")).andExpect(jsonPath("$.data.items[1].revision").value("revision"));
    }
    @Test void candidateOverflowPageIsEmptyButTotalIsWhole()throws Exception{
        when(mapper.candidates(anyString())).thenReturn(List.of(new CandidateSource("2","O-2","P-2","프로젝트","D-2","개발자","DEV",day,day,BigDecimal.ONE,null)));
        mvc.perform(get("/api/assignment-candidates?page=2&size=20")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items").isEmpty());
    }
    @Test void candidateAuthRejectsActorBeforeReadingNeedOrTentativeSource() throws Exception {
        mvc.perform(get("/api/assignment-candidates").param("actor","other"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value("actor"));
        verifyNoInteractions(mapper,revisions,users);
    }
    @Test void candidateValidationRejectsInvalidSizeWithoutReadingOrWriting() throws Exception {
        mvc.perform(get("/api/assignment-candidates").param("size","30"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("size"))
            .andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(mapper,revisions);
    }
    @Test void comparisonProratesContractAndCountsWholeFilteredTotals()throws Exception{
        when(comparisons.projects(any())).thenReturn(List.of(new StaffingComparisonDtos.Project("P-1","프로젝트",LocalDate.of(2025,7,1),LocalDate.of(2026,6,30),"ING","MO")));
        when(comparisons.plans("P-1")).thenReturn(List.of(new StaffingComparisonDtos.Plan("P-1","DEV",new BigDecimal("12.00"))));
        when(comparisons.assignmentIds("P-1")).thenReturn(List.of("A-1"));
        when(months.source("A-1")).thenReturn(source("A-1",day.minusMonths(6),day.plusMonths(1).minusDays(1),"1.00"));
        mvc.perform(get("/api/staffing-plan-comparison?year=2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].planMm").value("6.00")).andExpect(jsonPath("$.data.items[0].actualMm").value("7.00")).andExpect(jsonPath("$.data.totals.differenceMm").value("1.00"));
        mvc.perform(get("/api/staffing-plan-comparison?year=2026&difference=SHORT")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.totals.planMm").value("0.00"));
    }
    @Test void forecastUsesSharedCalculationWithoutTentativeDeduction()throws Exception{
        when(forecasts.people()).thenReturn(List.of(new SalesForecastSources.Person("D-1","DEV","Y","DRCT",day,null)));
        when(forecasts.opportunities()).thenReturn(List.of(new SalesForecastSources.Opportunity("O-1","기회",null,"ING","202607",2,BigDecimal.ONE,new BigDecimal("50"),null)));
        when(forecasts.needs()).thenReturn(List.of(new SalesForecastSources.Need("O-1",1,"DEV","202607",2,2,new BigDecimal("0.5"))));
        mvc.perform(get("/api/workforce-forecasts?from=2026-07&months=2&includeUnfilled=false")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totals[0].supply").value("1.0")).andExpect(jsonPath("$.data.totals[0].opportunityDemand").value("0.5")).andExpect(jsonPath("$.data.totals[0].unfilledDemand").value("0.0")).andExpect(jsonPath("$.data.jobDiagnostics[0].month.rawOpportunityDemand").value("0.500"));
        verify(mapper,never()).tentatives();
    }
    @Test void forecastThresholdIncludesAllTenPeopleAtAnExactTenPercent()throws Exception{
        when(forecasts.opportunities()).thenReturn(List.of(
            new SalesForecastSources.Opportunity("O-1","포함",null,"ING","202607",2,BigDecimal.ONE,new BigDecimal("10"),null),
            new SalesForecastSources.Opportunity("O-2","미달",null,"ING","202607",2,BigDecimal.ONE,new BigDecimal("9.99"),null),
            new SalesForecastSources.Opportunity("O-3","종료",null,"STOP","202607",2,BigDecimal.ONE,new BigDecimal("50"),null)));
        when(forecasts.needs()).thenReturn(List.of(
            new SalesForecastSources.Need("O-1",1,"DEV","202607",2,10,BigDecimal.ONE),
            new SalesForecastSources.Need("O-2",1,"DEV","202607",2,5,BigDecimal.ONE),
            new SalesForecastSources.Need("O-3",1,"DEV","202607",2,8,BigDecimal.ONE)));
        String url="/api/workforce-forecasts?from=2026-07&months=1";
        mvc.perform(get(url+"&includeOpportunities=false")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totals[0].opportunityDemand").value("0.0"));
        mvc.perform(get(url+"&includeOpportunities=true&minimumWinProbability=10")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totals[0].opportunityDemand").value("10.0"));
        mvc.perform(get(url+"&includeOpportunities=true&minimumWinProbability=10.01")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totals[0].opportunityDemand").value("0.0"));
    }
    @Test void forecastAnyLeaveOverlapExcludesSupplyWithoutPayrollDependency()throws Exception{
        when(forecasts.people()).thenReturn(List.of(new SalesForecastSources.Person("D-1","DEV","Y","DRCT",day,null)));
        when(forecasts.leaves()).thenReturn(List.of(new SalesForecastSources.Leave("D-1",day.plusDays(10),day.plusDays(10))));
        mvc.perform(get("/api/workforce-forecasts?from=2026-07&months=1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totals[0].supply").value("0.0"));
    }
    @Test void calendarActualWinsTentativeAndHistoricalEmptyTotalsStayNull()throws Exception{
        when(mapper.people()).thenReturn(List.of(new Person("D-1","개발자","DEV",null,"FT","Y","DRCT",day.minusYears(1),null)));
        when(mapper.personDisplays(List.of("D-1"))).thenReturn(List.of(new PersonDisplay("D-1","K-0042")));
        when(forecasts.projects()).thenReturn(List.of(new SalesForecastSources.Project("P-1","프로젝트","MO","ING",day,day.plusMonths(3),BigDecimal.ONE,BigDecimal.ZERO,null)));
        when(forecasts.assignmentIds()).thenReturn(List.of("A-1"));when(months.source("A-1")).thenReturn(source("A-1",day,day.plusMonths(1).minusDays(1),"0.50"));
        when(mapper.tentatives()).thenReturn(List.of(new TentativeSource("1","O-1",1,"D-1","202607","ACT","UI-29",null,"202608",new BigDecimal("50"))));
        mvc.perform(get("/api/workforce-operations?year=2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].cells[0].status").value("PAST_EMPTY")).andExpect(jsonPath("$.data.monthTotals[0].remainingMm").doesNotExist()).andExpect(jsonPath("$.data.items[0].cells[6].status").value("ASSIGNED")).andExpect(jsonPath("$.data.items[0].cells[6].tentativeAssignments[0].id").value("1")).andExpect(jsonPath("$.data.summary.remainingMm").value("0.50")).andExpect(jsonPath("$.data.items[0].managementNo").value("K-0042")).andExpect(jsonPath("$.data.items[0].cells[6].projects[0].projectName").value("프로젝트"));
    }
    @Test void calendarLeavePriorityAndFilteredSummary()throws Exception{
        when(mapper.people()).thenReturn(List.of(new Person("D-1","개발자","DEV",null,"FT","Y","DRCT",day,null)));
        when(forecasts.leaves()).thenReturn(List.of(new SalesForecastSources.Leave("D-1",day,day)));
        mvc.perform(get("/api/workforce-operations?year=2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].cells[6].status").value("LEAVE")).andExpect(jsonPath("$.data.summary.remainingMm").value("0.00"));
        mvc.perform(get("/api/workforce-operations?year=2026&jobCode=PM")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }
    @ParameterizedTest @CsvSource({"assignment-candidates,year,2026","assignment-candidates,page,0","assignment-candidates,size,30","assignment-candidates,sort,name","staffing-plan-comparison,difference,BAD","staffing-plan-comparison,year,10000","workforce-forecasts,from,2026-06","workforce-forecasts,months,13","workforce-forecasts,includeUnfilled,yes","workforce-operations,employeeType,BAD","workforce-operations,remainingOnly,yes","workforce-operations,gradeCode,TOOLONGCODE"})
    void invalidFiltersReturnFieldErrorWithoutWrites(String path,String key,String value)throws Exception{
        var request=get("/api/"+path);if(path.equals("workforce-forecasts")){if(!key.equals("from"))request.param("from","2026-07");if(!key.equals("months"))request.param("months","1");}
        mvc.perform(request.param(key,value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.field").isNotEmpty()).andExpect(jsonPath("$.requestId").isNotEmpty());
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"assignment-candidates","staffing-plan-comparison","workforce-forecasts","workforce-operations"})
    void actorAndGetBodyAreRejected(String path)throws Exception{
        mvc.perform(get("/api/"+path).param("actor","injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        mvc.perform(get("/api/"+path).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }
    AssignmentMonthlyService.Source source(String id,LocalDate start,LocalDate end,String mm){return new AssignmentMonthlyService.Source(id,"P-1","D-1","DEV",start,end,new BigDecimal(mm),BigDecimal.TEN,"NEW",null,null,0,"N","ACT");}
}
