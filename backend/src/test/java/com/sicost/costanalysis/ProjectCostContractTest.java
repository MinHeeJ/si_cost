package com.sicost.costanalysis;

import com.sicost.common.api.*;
import com.sicost.projects.ProjectDtos.Project;
import com.sicost.profitability.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static com.sicost.costanalysis.ProjectCostRepository.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectCostController.class)
@ContextConfiguration(classes=ProjectCostController.class)
@Import({ProjectCostService.class,ProjectCostCalculation.class,TurnkeyRevenueCalculation.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProjectCostContractTest {
    @Autowired MockMvc mvc;
    @Autowired ProjectCostService service;
    @Autowired ProjectCostCalculation calculation;
    @MockBean ProjectCostRepository repository;
    Snapshot snapshot;
    static BigDecimal n(String value) {return value==null?null:new BigDecimal(value);}
    @BeforeEach void setup() {
        snapshot=fixture("TK","ING","800",n("1.00"),false);
        when(repository.load(anyString(),nullable(String.class))).thenAnswer(c->snapshot);
    }
    static Snapshot fixture(String type,String status,String budget,BigDecimal plan,boolean missing) {
        var p=new Project("project","계약 원가","customer",type,status,LocalDate.of(2025,12,1),LocalDate.of(2026,3,31),null,null,null,"1000","1000",budget,"7","3","Y",null,null,null,null);
        var period=new ProfitabilityPeriod(2026,"202512","202512","202601",OffsetDateTime.parse("2026-02-01T00:00:00+09:00"),false);
        List<MonthlyPersonCost> items=new ArrayList<>();List<Assignment> assignments=new ArrayList<>();
        for(String month:List.of("202512","202601")) {
            boolean free=month.equals("202512");String input=free?"FREE":"PAID";
            var row=new MonthlyCostRow("actual",p.id(),"assignment-"+month,month,"1.00",free?"1.00":"0.00","200",missing?null:"100","7","3","10",missing?null:"110",null,"ALLOC",type,"HQ","Y",p.name(),"DEV",input,null,false,!missing,true,false,"admin",null,null,null);
            var issues=missing?List.of(new MonthlyCostIssue("actual",month,"급여 원천 없음","TB_HR_MON_PAY","UI-02",null)):List.<MonthlyCostIssue>of();
            var completeness=new MonthlyCostCompleteness(!missing,true,false,true,issues);
            items.add(new MonthlyPersonCost("actual",month,List.of(row),new MonthlyLaborSource("actual",month,"FT","DRCT","월 원가",missing?null:"100",null,null,null,null,null,null,null,null,false),completeness,missing?null:"100","200","1.00",false,false));
            assignments.add(new Assignment(month,"actual",p.id(),row.assignmentId(),n("1.00"),n(month.equals("202512")?"180":"200"),n("200"),"Y","DEV",input,"contract-person","ACT",p.startDate(),p.endDate()));
        }
        Sources sources=new Sources(period,"admin",LocalDate.of(2026,2,1),List.of(),List.of(),List.of(),List.of(),assignments,assignments,
            List.of(new com.sicost.profitability.MonthlyCostDtos.Project(p.id(),p.name(),type,status,null,p.startDate(),p.endDate(),n("1000"),n(budget),n("7"),n("3"))),List.of(),List.of(),List.of(),
            List.of(new Standard("TGT_MRGN_RT","202512",n("20")),new Standard("LOW_MRGN_GAP","202512",n("5")),new Standard("BDGT_WARN_RT","202512",n("80")),new Standard("COST_OVER_UPRC_RT","202512",n("40"))),List.of(),List.of(),List.of());
        return new Snapshot(p,sources,new MonthlyCostsData(period,items),List.of(new ProjectCostMapper.Expense("202512",n("5")),new ProjectCostMapper.Expense("202601",n("5"))),plan,n("25"),List.of(new FutureMonth("actual","2026-02",n("1.00"))),Map.of("DEV","개발"),List.of());
    }
    @Test void commonDetailIsIdenticalForBothScreenConsumersAndReadOnly() throws Exception {
        var first=service.getProjectCostAnalysis("project","2026-01");var second=service.getProjectCostAnalysis("project","2026-01");assertThat(first).isEqualTo(second);
        mvc.perform(get("/api/projects/project/cost-analysis").param("month","2026-01")).andExpect(status().isOk()).andExpect(jsonPath("$.data.row.cumulativeCost").value("230")).andExpect(jsonPath("$.data.row.cumulativeRevenue").value("287")).andExpect(jsonPath("$.data.sourceStatus").value("ING"));
        verify(repository,times(3)).load("project","2026-01");verifyNoMoreInteractions(repository);
    }
    @Test void preDetailShowsPreInputCostZeroRevenueAndNoForecast() throws Exception {
        snapshot=fixture("TK","PRE","800",n("1"),false);
        mvc.perform(get("/api/projects/project/cost-analysis")).andExpect(status().isOk()).andExpect(jsonPath("$.data.row.cumulativeCost").value("230")).andExpect(jsonPath("$.data.row.cumulativeRevenue").value("0")).andExpect(jsonPath("$.data.forecast").isEmpty()).andExpect(jsonPath("$.data.simulationDefaults.enabled").value(false));
    }
    @Test void lostDetailRetainsHistory() throws Exception {snapshot=fixture("TK","LOSE","800",n("1"),false);mvc.perform(get("/api/projects/project/cost-analysis")).andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceStatus").value("LOSE")).andExpect(jsonPath("$.data.row.cumulativeCost").value("230"));}
    @Test void internalHidesAllFinancialCellsExceptResearchCost() throws Exception {snapshot=fixture("IN","ING","800",n("1"),false);mvc.perform(get("/api/projects/project/cost-analysis")).andExpect(jsonPath("$.data.row.cumulativeRevenue").isEmpty()).andExpect(jsonPath("$.data.row.contractAmount").isEmpty()).andExpect(jsonPath("$.data.row.budgetUsage").isEmpty()).andExpect(jsonPath("$.data.row.cumulativeCost").value("230"));}
    @Test void missingProjectIs404WithoutResult() throws Exception {when(repository.load(eq("missing"),nullable(String.class))).thenThrow(new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다."));mvc.perform(get("/api/projects/missing/cost-analysis")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND")).andExpect(jsonPath("$.data").doesNotExist());}
    @Test void monthsCrossYearAndDoNotDoubleAddExpenseDetails() throws Exception {mvc.perform(get("/api/projects/project/cost-analysis/months")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].month").value("2025-12")).andExpect(jsonPath("$.data.items[0].totalCost").value("115")).andExpect(jsonPath("$.data.totalCost").value("230"));}
    @Test void existingSelectedZeroDistributionDoesNotFallBack() throws Exception {
        var s=snapshot.sources();var cells=List.of(new TurnkeyCell("project","PLAN","1","202601","ITEM",null,"명시0",BigDecimal.ZERO));
        snapshot=withSources(new Sources(s.period(),s.actor(),s.asOfDate(),s.people(),s.prices(),s.states(),s.payroll(),s.assignments(),s.projectMonths(),s.projects(),s.projectParts(),cells,s.storedCosts(),s.standards(),s.parts(),s.closes(),s.allowanceCodes()));
        mvc.perform(get("/api/projects/project/cost-analysis/months")).andExpect(jsonPath("$.data.items[1].turnkeyReferenceRevenue").value("0"));
    }
    Snapshot withSources(Sources s) {return new Snapshot(snapshot.project(),s,snapshot.costs(),snapshot.expenses(),snapshot.planMm(),snapshot.actualProgress(),snapshot.future(),snapshot.jobs(),snapshot.prices());}
    @Test void zeroBudgetWithoutTableHasUnknownReference() throws Exception {snapshot=fixture("TK","ING","0",n("1"),false);mvc.perform(get("/api/projects/project/cost-analysis/months")).andExpect(jsonPath("$.data.items[0].turnkeyReferenceRevenue").isEmpty());}
    @Test void missingCostNeverBecomesZeroAndEveryIssueSurvives() throws Exception {snapshot=fixture("TK","ING","800",n("1"),true);mvc.perform(get("/api/projects/project/cost-analysis/months")).andExpect(jsonPath("$.data.totalCost").isEmpty()).andExpect(jsonPath("$.data.items[0].totalCost").isEmpty()).andExpect(jsonPath("$.data.completeness.issues.length()").value(2)).andExpect(jsonPath("$.data.completeness.issues[0].code").value("TB_HR_MON_PAY")).andExpect(jsonPath("$.data.completeness.issues[0].assignmentId").isEmpty());}
    @Test void assignmentsKeepActualPersonDistinctMonthlyAssignmentsAndHistoricPrice() throws Exception {mvc.perform(get("/api/projects/project/cost-analysis/assignments")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2)).andExpect(jsonPath("$.data.items[0].personId").value("actual")).andExpect(jsonPath("$.data.items[0].assignmentId").value("assignment-202512")).andExpect(jsonPath("$.data.items[0].inputType").value("FREE")).andExpect(jsonPath("$.data.items[1].personalPrice").value("200"));}
    @Test void overlappingCausesPreserveNegativeRemainderWithoutShrinkingAmounts() throws Exception {mvc.perform(get("/api/projects/project/cost-analysis/causes")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].kind").value("EXCESS_MM")).andExpect(jsonPath("$.data.items[0].amount").value("172")).andExpect(jsonPath("$.data.progressExcess").value("30")).andExpect(jsonPath("$.data.remainder").value("-262")).andExpect(jsonPath("$.data.showUnexplained").value(false));}
    @Test void missingPlanExplainsUnknownExcessMm() throws Exception {snapshot=fixture("TK","ING","800",null,false);var result=service.getProjectCostCauses("project",null);assertThat(result.items()).anySatisfy(c->{assertThat(c.kind()).isEqualTo("EXCESS_MM");assertThat(c.amount()).isNull();assertThat(c.issues()).isNotEmpty();});}
    @Test void freeRowsAreExcludedFromUnitCostCause() {var result=service.getProjectCostCauses("project",null);assertThat(result.items()).anySatisfy(c->{assertThat(c.kind()).isEqualTo("OVER_UNIT_COST");assertThat(c.amount()).isEqualTo("20");});}
    @ParameterizedTest @ValueSource(strings={"","/months","/assignments","/causes"})
    void duplicateUnknownAndInvalidCalendarQueryRejected(String suffix) throws Exception {
        mvc.perform(get("/api/projects/project/cost-analysis"+suffix).param("month","2026-01","2026-02")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("month"));
        mvc.perform(get("/api/projects/project/cost-analysis"+suffix).param("month","2026-13")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/projects/project/cost-analysis"+suffix).param("actor","other")).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }
    org.springframework.test.web.servlet.ResultActions simulate(String body) throws Exception {return mvc.perform(post("/api/projects/project/cost-analysis/simulation").contentType("application/json").content(body));}
    @Test void turnkeySimulationUsesCompleteForecastAndDoesNotSave() throws Exception {
        Snapshot before=snapshot;
        simulate("{\"n\":1,\"c\":\"10\",\"m\":1}").andExpect(status().isOk()).andExpect(jsonPath("$.data.beforeMargin").value("67.0")).andExpect(jsonPath("$.data.afterMargin").value("66.0")).andExpect(jsonPath("$.data.differencePp").value("-1.0")).andExpect(jsonPath("$.data.saved").value(false));
        assertThat(snapshot).isEqualTo(before);verify(repository).load("project",null);verifyNoMoreInteractions(repository);
    }
    @Test void actorInjectionRejectedBeforeAnySourceRead() throws Exception {simulate("{\"c\":\"10\",\"actor\":\"other\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("actor"));verifyNoInteractions(repository);}
    @ParameterizedTest @CsvSource({"n,1.5","m,0","c,\"1e3\"","c,10","r,\"-1\"","n,2147483648"})
    void invalidSimulationFieldsNeverProduceAResult(String field,String value) throws Exception {String body=field.equals("c")?"{\"c\":"+value+"}":"{\"c\":\"10\",\""+field+"\":"+value+"}";simulate(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field)).andExpect(jsonPath("$.data").doesNotExist());verifyNoInteractions(repository);}
    @Test void negativeVirtualRemainingCostRejected() throws Exception {simulate("{\"n\":-20,\"c\":\"10\",\"m\":1}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("n")).andExpect(jsonPath("$.data").doesNotExist());}
    @Test void monthlySimulationIgnoresMAndPreservesSources() {
        snapshot=fixture("MO","ING","800",n("1"),false);var before=snapshot;
        var first=service.createProjectCostSimulation("project",new ProjectCostDtos.SimulationInput(null,1,"10",1,"10"));
        var second=service.createProjectCostSimulation("project",new ProjectCostDtos.SimulationInput(null,1,"10",100,"10"));
        assertThat(first).isEqualTo(second);assertThat(first.message()).contains("한 달 기준");assertThat(first.saved()).isFalse();assertThat(snapshot).isEqualTo(before);
    }
    @Test void monthlyNonpositiveRevenueRejected() throws Exception {snapshot=fixture("MO","ING","800",n("1"),false);simulate("{\"n\":-20,\"c\":\"0\",\"r\":\"10\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.data").doesNotExist());}
    @Test void monthlyNegativeCostRejected() throws Exception {snapshot=fixture("MO","ING","800",n("1"),false);simulate("{\"n\":-20,\"c\":\"10\",\"r\":\"0\"}").andExpect(status().isBadRequest());}
    @Test void monthlyRequiresR() throws Exception {snapshot=fixture("MO","ING","800",n("1"),false);simulate("{\"c\":\"10\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("r"));}
    @Test void incompleteCostDisablesSimulation() throws Exception {snapshot=fixture("TK","ING","800",n("1"),true);simulate("{\"c\":\"10\"}").andExpect(status().isBadRequest());}
    @Test void strictJsonAndPostQueryAreRejected() throws Exception {simulate("{").andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("body"));mvc.perform(post("/api/projects/project/cost-analysis/simulation").param("month","2026-01").contentType("application/json").content("{\"c\":\"10\"}")).andExpect(status().isBadRequest());}
    @ParameterizedTest @ValueSource(strings={"/months","/assignments","/causes"})
    void detailOperationsReturnMissingProjectContract(String suffix) throws Exception {
        when(repository.load(eq("missing"),nullable(String.class))).thenThrow(new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다."));
        mvc.perform(get("/api/projects/missing/cost-analysis"+suffix))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("NOT_FOUND")).andExpect(jsonPath("$.requestId").isString())
            .andExpect(jsonPath("$.data").doesNotExist());
    }
    @ParameterizedTest @ValueSource(strings={"/months","/assignments","/causes","/simulation"})
    void detailOperationsDoNotLeakPersistenceErrors(String suffix) throws Exception {
        when(repository.load(anyString(),nullable(String.class))).thenThrow(new IllegalStateException("private_sql_connection"));
        var request=suffix.equals("/simulation")?post("/api/projects/project/cost-analysis"+suffix).contentType("application/json").content("{\"c\":\"10\"}")
            :get("/api/projects/project/cost-analysis"+suffix);
        mvc.perform(request).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.requestId").isString()).andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private_sql_connection"))));
    }
}
