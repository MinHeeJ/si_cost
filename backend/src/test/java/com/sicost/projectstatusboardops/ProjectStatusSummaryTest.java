package com.sicost.projectstatusboardops;

import com.sicost.apiops.*;
import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.profitability.TurnkeyRevenueCalculation;
import com.sicost.shared.SharedQueriesService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static com.sicost.apiops.ProjectAnalysisDtos.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProjectStatusSummaryTest {
    @Test void realCalculationReturnsWholeFiveCardsIndependentOfIssueTabFilters()throws Exception {
        var day=LocalDate.of(2026,7,1);var clock=Clock.fixed(Instant.parse("2026-06-30T15:00:00Z"),ZoneId.of("Asia/Seoul"));
        var repository=mock(ProjectAnalysisRepository.class);var users=mock(CurrentUserProvider.class);
        var period=new ProfitabilityPeriod(2026,"202501","202501","202607",day.atStartOfDay().atOffset(ZoneOffset.ofHours(9)),false);
        var projects=new ArrayList<Project>();for(String id:List.of("first","second","third"))projects.add(new Project(id,id,"IN","ING",null,day.minusDays(19),day.plusDays(80),BigDecimal.ZERO,null,BigDecimal.ZERO,BigDecimal.ZERO));
        projects.add(new Project("ended","종료","IN","ING",null,day.minusDays(50),day.minusDays(1),BigDecimal.ZERO,null,BigDecimal.ZERO,BigDecimal.ZERO));
        projects.add(new Project("lost","실주","IN","LOSE",null,day.minusDays(19),day.plusDays(80),BigDecimal.ZERO,null,BigDecimal.ZERO,BigDecimal.ZERO));
        var source=new Sources(period,"admin",day,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),projects,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        var issues=List.of(new Issue("first","HIGH",day.minusDays(1),"OPEN"),new Issue("first","HIGH",day,"ACT"),new Issue("second","MID",day.plusDays(1),"WAIT"),new Issue("third","LOW",day.plusDays(1),"OPEN"));
        var snapshot=new Snapshot(source,new MonthlyCostsData(period,List.of()),projects.stream().map(p->new ProjectEvidence(p.projectId(),null,null,p.fromDate())).toList(),List.of(),List.of(),List.of(new Memo("first",1,day,BigDecimal.ZERO)),issues,List.of(),List.of(),List.of());
        when(repository.load(any())).thenReturn(snapshot);
        var board=new ProjectAnalysisService(repository,new ProjectCostCalculation(new TurnkeyRevenueCalculation()),new ProjectStatusCalculation(),clock,users);
        var reads=new ProjectStatusReadService(mock(ProjectStatusIssueRepository.class),board,mock(SharedQueriesService.class),users,clock);
        var mvc=MockMvcBuilders.standaloneSetup(new ProjectStatusController(reads,mock(ProjectStatusExportService.class))).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/api/project-status-board/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.data.projectCount").value(3)).andExpect(jsonPath("$.data.dangerCount").value(3)).andExpect(jsonPath("$.data.openIssueCount").value(4)).andExpect(jsonPath("$.data.openHighIssueCount").value(2)).andExpect(jsonPath("$.data.overdueIssueCount").value(1)).andExpect(jsonPath("$.data.staleProjectCount").value(2));
        verify(repository).load(YearMonth.of(2026,7));
    }
}
