package com.sicost.projectstatusboardops;

import com.sicost.apiops.*;
import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.shared.SharedQueriesService;
import com.sicost.shared.SharedQueryDtos.*;
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
import static com.sicost.projectstatusboardops.ProjectStatusDtos.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectStatusController.class)
@ContextConfiguration(classes=ProjectStatusController.class)
@Import({ProjectStatusReadService.class,ProjectStatusIssueRepository.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProjectStatusReadTest {
    @Autowired MockMvc mvc;
    @MockBean ProjectStatusIssueMapper mapper;
    @MockBean ProjectAnalysisService board;
    @MockBean SharedQueriesService shared;
    @MockBean CurrentUserProvider users;
    @MockBean Clock clock;
    @MockBean ProjectStatusExportService exports;
    final LocalDate today=LocalDate.of(2026,7,1);
    @Test void boardAndExistingProjectStatusControllersHaveDistinctBeanNames() {
        var registry=new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        var names=org.springframework.context.annotation.AnnotationBeanNameGenerator.INSTANCE;
        String boardName=names.generateBeanName(new org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition(ProjectStatusController.class),registry);
        String projectName=names.generateBeanName(new org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition(com.sicost.projectsops.ProjectStatusController.class),registry);
        org.assertj.core.api.Assertions.assertThat(boardName).isNotEqualTo(projectName);
    }
    @BeforeEach void setup(){
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(shared.getCodes("IMPR",false)).thenReturn(new Items<>(List.of(new CodeLookup("HIGH","높음",null,null,null,null,null))));
        when(mapper.eligibleIssues(today)).thenReturn(List.of(issue(3,"HIGH","OPEN",today),issue(2,"HIGH","ACT",today.minusDays(1)),issue(1,"MID","OPEN",today.minusDays(2)),issue(4,"HIGH","CLOSE",today.minusDays(3)),issue(5,"LOW","WAIT",today.minusDays(1))));
        when(mapper.projectStatus(anyString(),eq(today))).thenReturn("ELIGIBLE");
    }
    IssueSource issue(long id,String importance,String status,LocalDate due){return new IssueSource(id,"0001","프로젝트","이슈 "+id,importance,importance,due,status,status,null,"author","최초 담당자",today.atStartOfDay(),null,null,null);}
    @Test void defaultIsExactlyOpenAndActWithHighDueAndIdOrder()throws Exception {
        mvc.perform(get("/api/project-status-board/issues")).andExpect(status().isOk()).andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.baseDate").value("2026-07-01"))
            .andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.size").value(50)).andExpect(jsonPath("$.data.openCount").value(4))
            .andExpect(jsonPath("$.data.items[0].issueId").value(2)).andExpect(jsonPath("$.data.items[1].issueId").value(3)).andExpect(jsonPath("$.data.items[2].issueId").value(1))
            .andExpect(jsonPath("$.data.items[0].registeredBy.userName").value("최초 담당자")).andExpect(jsonPath("$.data.items[0].modifiedBy").isEmpty());
        verify(mapper).eligibleIssues(today);verifyNoInteractions(exports,board);
    }
    @Test void overdueIncludesAdditionalNonClosedStatusesButNotToday()throws Exception {
        mvc.perform(get("/api/project-status-board/issues").param("status","OVERDUE")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.items[0].overdue").value(true)).andExpect(jsonPath("$.data.items[2].status").value("WAIT"));
    }
    @Test void closedSelectsOnlyCloseAndNeverMarksItOverdue()throws Exception {
        mvc.perform(get("/api/project-status-board/issues").param("status","CLOSED")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].overdue").value(false));
    }
    @Test void allRetainsRawAdditionalStatusAndFilteredTotalMatchesItems()throws Exception {
        mvc.perform(get("/api/project-status-board/issues").param("status","ALL").param("importance"," HIGH ").param("projectId"," 0001 ")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.items.length()").value(3));
        verify(mapper).projectStatus("0001",today);
    }
    @Test void pageDoesNotChangeFilteredTotalOrOpenCount()throws Exception {
        mvc.perform(get("/api/project-status-board/issues").param("page","2").param("size","20")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.openCount").value(4));
    }
    @Test void unknownImportanceIs400BeforeIssueRead()throws Exception {
        mvc.perform(get("/api/project-status-board/issues").param("importance","UNKNOWN")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("importance"));verify(mapper,never()).eligibleIssues(any());
    }
    @Test void missingProjectIs404()throws Exception {when(mapper.projectStatus("missing",today)).thenReturn(null);mvc.perform(get("/api/project-status-board/issues").param("projectId","missing")).andExpect(status().isNotFound());verify(mapper,never()).eligibleIssues(any());}
    @Test void excludedProjectIs409()throws Exception {when(mapper.projectStatus("ended",today)).thenReturn("EXCLUDED");mvc.perform(get("/api/project-status-board/issues").param("projectId","ended")).andExpect(status().isConflict());verify(mapper,never()).eligibleIssues(any());}
    @ParameterizedTest @CsvSource({"status,OPEN","importance,TOO_LONG_VALUE","page,0","page,1.0","page,2147483648","size,30","sort,title","userId,other","baseDate,2026-07-01"})
    void invalidIssueQuery(String field,String value)throws Exception {mvc.perform(get("/api/project-status-board/issues").param(field,value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));verifyNoInteractions(mapper);}
    @Test void duplicateQueryRejected()throws Exception {mvc.perform(get("/api/project-status-board/issues").param("status","ALL","ALL")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);}
    @Test void bodyRejected()throws Exception {mvc.perform(get("/api/project-status-board/issues").contentType("application/json").content("{}")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);}
    @Test void summaryReusesWholeBoardCalculationWithoutIssueFilters()throws Exception {
        Map<String,Object> summary=Map.of("dangerCount",3,"openIssueCount",4,"openHighIssueCount",2,"overdueIssueCount",1,"staleProjectCount",2);
        when(board.getProjectStatusBoard(any())).thenReturn(new ProjectAnalysisDtos.BoardPage(today,"2026-07",List.of(),23,1,50,summary,List.of(),"안내"));
        mvc.perform(get("/api/project-status-board/summary").param("light","위험")).andExpect(status().isOk()).andExpect(jsonPath("$.data.dangerCount").value(3)).andExpect(jsonPath("$.data.openIssueCount").value(4));
        verify(board).getProjectStatusBoard(argThat(q->q.size()==Integer.MAX_VALUE&&q.value("light").equals("위험")));verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"status","importance","page","size","month","userId"})
    void summaryRejectsIssueAndPageAndActorFields(String field)throws Exception {mvc.perform(get("/api/project-status-board/summary").param(field,"x")).andExpect(status().isBadRequest());verifyNoInteractions(board);}
    @Test void sourceFailureIsSanitized()throws Exception {when(mapper.eligibleIssues(today)).thenThrow(new IllegalStateException("SELECT private"));mvc.perform(get("/api/project-status-board/issues")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));}
}
