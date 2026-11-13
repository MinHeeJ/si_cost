package com.sicost.apiops;

import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.profitability.TurnkeyRevenueCalculation;
import com.sicost.profitability.MonthlyCostDtos.*;
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
import static com.sicost.apiops.ProjectAnalysisDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectAnalysisController.class)
@ContextConfiguration(classes=ProjectAnalysisController.class)
@Import({ProjectAnalysisService.class,ProjectCostCalculation.class,ProjectStatusCalculation.class,
    TurnkeyRevenueCalculation.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProjectAnalysisContractTest {
    @Autowired MockMvc mvc;
    @MockBean ProjectAnalysisRepository repository;
    @MockBean CurrentUserProvider users;
    @MockBean Clock clock;
    final LocalDate today=LocalDate.of(2026,6,30);
    List<Project> projects;
    List<MonthlyPersonCost> people;
    List<Standard> standards;
    List<Memo> memos;
    List<Issue> issues;
    List<Expense> expenses;
    List<Stage> stages;
    Snapshot snapshot;
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        projects=new ArrayList<>();people=new ArrayList<>();standards=new ArrayList<>(List.of(new Standard("TGT_MRGN_RT","202501",n("20")),new Standard("LOW_MRGN_GAP","202501",n("5")),new Standard("BDGT_WARN_RT","202501",n("80"))));
        memos=new ArrayList<>();issues=new ArrayList<>();expenses=new ArrayList<>();stages=new ArrayList<>();
        when(repository.load(any())).thenAnswer(call->{snapshot=snapshot();return snapshot;});
    }
    static BigDecimal n(String value){return new BigDecimal(value);}
    Project project(String id,String type,String state,LocalDate start,LocalDate end,String budget){return new Project(id,id,type,state,null,start,end,n("100"),budget==null?null:n(budget),n("0"),n("0"));}
    void add(String id,String type,String state,LocalDate start,LocalDate end,String budget,String cost,String revenue){
        projects.add(project(id,type,state,start,end,budget));
        var row=new StoredCost("202606","person-"+id,id,"assignment-"+id,n("1.00"),n("0.00"),revenue==null?null:n(revenue),cost==null?null:n(cost),n("0"),n("0"),n("0"),"ALLOC",type,"HQ","N",null,null,null,null,id,"DEV","PAID").toRow();
        var diagnostic=cost==null?List.of(new MonthlyCostIssue(row.personId(),"202606","월원가 누락","TB_CO_MON_COST","UI-03",null)):List.<MonthlyCostIssue>of();
        people.add(new MonthlyPersonCost(row.personId(),"202606",List.of(row),new MonthlyLaborSource(row.personId(),"202606","FT","DRCT","사본",cost,null,null,null,null,null,null,null,null,false),
            new MonthlyCostCompleteness(cost!=null,revenue!=null,false,true,diagnostic),cost,revenue,"1.00",false,false));
    }
    Snapshot snapshot(){
        var period=new ProfitabilityPeriod(2026,"202501","202501","202606",OffsetDateTime.parse("2026-06-30T09:00:00+09:00"),false);
        var sources=new Sources(period,"admin",today,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.copyOf(projects),List.of(),List.of(),List.of(),List.copyOf(standards),List.of(new Part("HQ","본사","Y","Y")),List.of(),List.of());
        return new Snapshot(sources,new MonthlyCostsData(period,List.copyOf(people)),projects.stream().map(p->new ProjectEvidence(p.projectId(),n("100"),null,p.fromDate())).toList(),List.copyOf(expenses),List.copyOf(stages),List.copyOf(memos),List.copyOf(issues),List.of(),List.of(),List.of());
    }
    @Test void endEqualsSelectedMonthEndIncludedPreAndLostExcludedAndSummaryUnpaged() throws Exception {
        for(String state:List.of("ING","PRE","LOSE"))add(state,"MO",state,today.minusDays(19),today,"100","10","100");
        mvc.perform(get("/api/project-costs").param("month","2026-06")).andExpect(status().isOk()).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.summary.total").value(1)).andExpect(jsonPath("$.data.items[0].status").value("ING"));
    }
    @Test void zeroBudgetDoesNotCreateRevenueOrMargin() throws Exception {
        add("zero","TK","ING",today.minusDays(19),today.plusDays(80),"0","25","10");
        mvc.perform(get("/api/project-costs")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].cumulativeRevenue").isEmpty()).andExpect(jsonPath("$.data.items[0].forecastMargin").value("75.0"))
            .andExpect(jsonPath("$.data.items[0].budgetUsage").isEmpty()).andExpect(jsonPath("$.data.summary.unknownRevenueCount").value(1));
    }
    @Test void sourceMissingReasonRetainedWithoutInventedAssignmentAndKnownProjectWorks() throws Exception {
        add("missing","MO","ING",today.minusDays(19),today.plusDays(80),"100",null,"100");add("known","MO","ING",today.minusDays(19),today.plusDays(80),"100","10","100");
        mvc.perform(get("/api/project-costs").param("sort","projectName")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].cumulativeCost").value("10"))
            .andExpect(jsonPath("$.data.items[1].cumulativeCost").isEmpty()).andExpect(jsonPath("$.data.items[1].completeness.issues[0].code").value("TB_CO_MON_COST"))
            .andExpect(jsonPath("$.data.items[1].completeness.issues[0].assignmentId").isEmpty()).andExpect(jsonPath("$.data.summary.cumulativeProfit").isEmpty());
    }
    @Test void exactLowMarginBoundaryIsNotAnAlarmAndBudgetWarningDoesNotPassAlertFilter() throws Exception {
        add("boundary","MO","ING",today.minusDays(19),today.plusDays(80),"100","85","100");
        mvc.perform(get("/api/project-costs")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].targetGap").value("-5.0")).andExpect(jsonPath("$.data.items[0].alertCount").value(0)).andExpect(jsonPath("$.data.items[0].budgetWarning").value(true));
        mvc.perform(get("/api/project-costs").param("alertOnly","true")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void beyondBoundaryProducesOneAlarmPerConditionWithExactFormula() throws Exception {
        add("alarm","MO","ING",today.minusDays(19),today.plusDays(80),"80","86","100");
        mvc.perform(get("/api/project-costs").param("alertOnly","true")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].alertCount").value(2)).andExpect(jsonPath("$.data.summary.lowMarginCount").value(1)).andExpect(jsonPath("$.data.summary.budgetOverCount").value(1));
    }
    @Test void internalFinancialCellsNullAndResearchCostUsesExpensesOnce() throws Exception {
        add("research","IN","ING",today.minusDays(19),today.plusDays(80),null,"10","0");expenses.add(new Expense("research","202606",n("3")));
        mvc.perform(get("/api/project-costs")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].contractAmount").isEmpty()).andExpect(jsonPath("$.data.items[0].cumulativeCost").value("13"))
            .andExpect(jsonPath("$.data.summary.researchCost").value("13")).andExpect(jsonPath("$.data.items[0].costPerMm").isEmpty());
    }
    @Test void scheduleAndCostDangerUseDisplayedValuesAndAllReasons() throws Exception {
        // Period fallback uses elapsed days / (end - start), without inclusive-day counting.
        add("danger","TK","ING",today.minusDays(20),today.plusDays(80),"100","25","100");memos.add(new Memo("danger",1,today,n("10")));issues.add(new Issue("danger","HIGH",today.minusDays(1),"OPEN"));
        mvc.perform(get("/api/project-status-board")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].planProgress").value("20.0"))
            .andExpect(jsonPath("$.data.items[0].actualProgress").value("10.0")).andExpect(jsonPath("$.data.items[0].light").value("위험"))
            .andExpect(jsonPath("$.data.items[0].reasons.length()").value(4)).andExpect(jsonPath("$.data.summary.dangerCount").value(1));
    }
    @Test void scheduleExactlyFiveIsWarning() throws Exception {
        add("warning","TK","ING",today.minusDays(15),today.plusDays(85),"100","10","100");memos.add(new Memo("warning",1,today,n("10")));
        mvc.perform(get("/api/project-status-board")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].planProgress").value("15.0")).andExpect(jsonPath("$.data.items[0].light").value("주의"));
    }
    @Test void todayBoundaryPreIncludedExpiredEndLostExcludedAndFuturePlanZero() throws Exception {
        add("today","MO","ING",today,today,"100","10","100");add("pre","TK","PRE",today.plusDays(1),today.plusDays(50),"0",null,"0");
        add("expired","MO","ING",today.minusDays(10),today.minusDays(1),"100","10","100");add("ended","MO","END",today,today.plusDays(10),"100","10","100");add("lost","MO","LOSE",today,today.plusDays(10),"100","10","100");
        mvc.perform(get("/api/project-status-board").param("sort","projectName")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.items[0].projectId").value("pre"))
            .andExpect(jsonPath("$.data.items[0].planProgress").value("0.0")).andExpect(jsonPath("$.data.items[0].costUnknown").value(true)).andExpect(jsonPath("$.data.items[0].light").value("정상"));
    }
    @Test void paginationRetainsFilterWideSummaryStaleProjectsAndNoSourceChanges() throws Exception {
        for(int i=0;i<23;i++)add("project-"+i,"TK","ING",today.minusDays(19),today.plusDays(80),"100","25","100");
        var original=snapshot();
        mvc.perform(get("/api/project-status-board").param("size","20").param("page","2")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(3)).andExpect(jsonPath("$.data.summary.dangerCount").value(23)).andExpect(jsonPath("$.data.staleProjects.length()").value(23));
        assertThat(snapshot()).isEqualTo(original);
    }
    @Test void strictQueryDuplicatesUnknownBodyAndInvalidMonthAreRejectedBeforeSourceRead() throws Exception {
        mvc.perform(get("/api/project-costs").param("month","2026-06","2026-05")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/project-status-board").param("month","2026-06")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/project-costs").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/project-costs").param("month","2026-13")).andExpect(status().isBadRequest());verifyNoInteractions(repository);
    }
    @ParameterizedTest @CsvSource({"alertOnly,1","alertOnly,TRUE","contractType,OTHER","status,PRE","page,0","size,30","sort,id","direction,desc","month,0000-01","month,2026-07"})
    void costInvalidInput(String field,String value)throws Exception {mvc.perform(get("/api/project-costs").param(field,value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));}
    @ParameterizedTest @CsvSource({"light,UNKNOWN","contractType,OTHER","size,10","page,-1","sort,id","actor,admin","baseDate,2026-06-30","partId,unknown"})
    void boardInvalidInput(String field,String value)throws Exception {mvc.perform(get("/api/project-status-board").param(field,value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));}
    @Test void missingProjectIs404AndPreProjectIs409()throws Exception {
        mvc.perform(get("/api/project-costs").param("projectId","missing")).andExpect(status().isNotFound());
        add("pre","MO","PRE",today,today.plusDays(20),"100","10","100");mvc.perform(get("/api/project-costs").param("projectId","pre")).andExpect(status().isConflict());
    }
}
