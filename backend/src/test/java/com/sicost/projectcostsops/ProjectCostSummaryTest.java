package com.sicost.projectcostsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.apiops.*;
import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.profitability.TurnkeyRevenueCalculation;
import com.sicost.profitability.MonthlyCostDtos.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.apiops.ProjectAnalysisDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ProjectCostExportController.class,ProjectAnalysisController.class})
@ContextConfiguration(classes={ProjectCostExportController.class,ProjectAnalysisController.class})
@Import({ProjectAnalysisService.class,ProjectCostCalculation.class,ProjectStatusCalculation.class,TurnkeyRevenueCalculation.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProjectCostSummaryTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ProjectAnalysisRepository repository;
    @MockBean ProjectCostExportService exports;
    @MockBean CurrentUserProvider users;
    @MockBean Clock clock;
    final LocalDate today=LocalDate.of(2026,6,30);
    List<Project> projects;
    List<MonthlyPersonCost> people;
    List<Expense> expenses;
    @BeforeEach void setup(){
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        projects=new ArrayList<>();people=new ArrayList<>();expenses=new ArrayList<>();
        when(repository.load(any())).thenAnswer(c->snapshot());
    }
    static BigDecimal n(String value){return new BigDecimal(value);}
    void add(String id,String type,String status,String cost,String revenue){
        projects.add(new Project(id,id,type,status,null,today.minusDays(19),today.plusDays(80),n("100"),n("100"),n("0"),n("0")));
        month(id,type,"202606",cost,revenue);
    }
    void month(String id,String type,String month,String cost,String revenue){
        var row=new StoredCost(month,"person-"+id,id,"assignment-"+id,n("1.00"),n("0.00"),revenue==null?null:n(revenue),cost==null?null:n(cost),n("0"),n("0"),n("0"),"ALLOC",type,"HQ","N",null,null,null,null,id,"DEV","PAID").toRow();
        var diagnostic=cost==null?List.of(new MonthlyCostIssue(row.personId(),month,"월원가 누락","TB_CO_MON_COST","UI-03",null)):List.<MonthlyCostIssue>of();
        people.add(new MonthlyPersonCost(row.personId(),month,List.of(row),new MonthlyLaborSource(row.personId(),month,"FT","DRCT","사본",cost,null,null,null,null,null,null,null,null,false),new MonthlyCostCompleteness(cost!=null,revenue!=null,false,true,diagnostic),cost,revenue,"1.00",false,false));
    }
    Snapshot snapshot(){
        var period=new ProfitabilityPeriod(2026,"202501","202501","202606",OffsetDateTime.parse("2026-06-30T09:00:00+09:00"),false);
        var standards=List.of(new Standard("TGT_MRGN_RT","202501",n("20")),new Standard("LOW_MRGN_GAP","202501",n("5")),new Standard("BDGT_WARN_RT","202501",n("80")));
        var sources=new Sources(period,"admin",today,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.copyOf(projects),List.of(),List.of(),List.of(),standards,List.of(new Part("HQ","본사","Y","Y")),List.of(),List.of());
        return new Snapshot(sources,new MonthlyCostsData(period,List.copyOf(people)),projects.stream().map(p->new ProjectEvidence(p.projectId(),n("100"),null,p.fromDate())).toList(),List.copyOf(expenses),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
    }
    @Test void filterWideSummaryEqualsBothPageSizesAndExcludesInternalFinance()throws Exception {
        for(int i=0;i<23;i++)add("client-"+i,"MO","ING","10","100");add("research","IN","ING","7","999");
        add("excluded","MO","PRE","100","100");add("lost","MO","LOSE","100","100");
        var before=snapshot();
        var summary=json.readTree(mvc.perform(get("/api/project-costs/summary").param("month","2026-06")).andExpect(status().isOk()).andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.total").value(24)).andExpect(jsonPath("$.data.cumulativeRevenue").value("2300")).andExpect(jsonPath("$.data.cumulativeProfit").value("2070")).andExpect(jsonPath("$.data.researchCost").value("7")).andExpect(jsonPath("$.data.lowMarginCount").value(0)).andExpect(jsonPath("$.data.budgetOverCount").value(0)).andReturn().getResponse().getContentAsString()).get("data");
        for(String size:List.of("20","50")) {
            var list=json.readTree(mvc.perform(get("/api/project-costs").param("month","2026-06").param("size",size)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
            assertThat(list.get("summary")).isEqualTo(summary);assertThat(list.get("total").intValue()).isEqualTo(24);
        }
        assertThat(snapshot()).isEqualTo(before);verifyNoInteractions(exports);
    }
    @Test void budgetWarningIsNotAnAlarmOrAlertPopulation()throws Exception {
        add("warning","MO","ING","85","100");
        mvc.perform(get("/api/project-costs/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.lowMarginCount").value(0)).andExpect(jsonPath("$.data.budgetOverCount").value(0));
        mvc.perform(get("/api/project-costs/summary").param("alertOnly","true")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void positiveAlarmsCountByKindAndRespectFilters()throws Exception {
        add("alarm","MO","ING","101","100");add("normal","DP","ING","10","100");
        mvc.perform(get("/api/project-costs/summary").param("contractType","MO").param("alertOnly","true")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.lowMarginCount").value(1)).andExpect(jsonPath("$.data.budgetOverCount").value(1));
    }
    @Test void unknownsAreNullNotZeroAndKnownUnaffectedMetricsRemainReadable()throws Exception {
        add("missing","MO","ING",null,"100");add("research","IN","ING","7","0");
        mvc.perform(get("/api/project-costs/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.data.cumulativeProfit").isEmpty()).andExpect(jsonPath("$.data.cumulativeRevenue").isEmpty()).andExpect(jsonPath("$.data.researchCost").value("7")).andExpect(jsonPath("$.data.unknownCostCount").value(1)).andExpect(jsonPath("$.data.completeness.costComplete").value(false)).andExpect(jsonPath("$.data.completeness.issues[0].code").value("TB_CO_MON_COST"));
        mvc.perform(get("/api/project-costs/summary").param("alertOnly","true")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void crossYearCostsAndAdditionalExpenseCountExactlyOnce()throws Exception {
        add("lifetime","MO","ING","10","100");var p=projects.remove(0);projects.add(new Project(p.projectId(),p.name(),p.contractType(),p.status(),null,LocalDate.of(2025,1,1),p.toDate(),p.contractAmount(),p.budget(),p.teamExpense(),p.projectExpense()));
        month("lifetime","MO","202512","20","200");expenses.add(new Expense("lifetime","202512",n("3")));
        mvc.perform(get("/api/project-costs/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.data.cumulativeRevenue").value("300")).andExpect(jsonPath("$.data.cumulativeProfit").value("267"));
    }
    @ParameterizedTest @ValueSource(strings={"?userId=other","?page=1","?size=20","?sort=projectName","?direction=ASC","?order=projectName","?status=PRE","?contractType=OTHER","?alertOnly=TRUE","?month=2026-13","?month=2026-07","?month=0000-01","?month=2026-06&month=2026-06"})
    void invalidQueryIs400(String query)throws Exception {mvc.perform(get("/api/project-costs/summary"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));verifyNoInteractions(repository);}
    @Test void bodyIsRejected()throws Exception {mvc.perform(get("/api/project-costs/summary").contentType("application/json").content("{}")).andExpect(status().isBadRequest());verifyNoInteractions(repository);}
    @Test void unknownProjectIs404()throws Exception {mvc.perform(get("/api/project-costs/summary").param("projectId","missing")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));}
    @Test void excludedProjectIs409()throws Exception {add("pre","MO","PRE","10","100");mvc.perform(get("/api/project-costs/summary").param("projectId","pre")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATUS"));}
}
