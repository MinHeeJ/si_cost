package com.sicost.projectsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.budget.ProjectBudgetRepository;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.shared.SharedQueriesMapper;
import com.sicost.shared.SharedQueryDtos.CloseRow;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.projectsops.ProjectStatusDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectStatusController.class)
@ContextConfiguration(classes=ProjectStatusController.class)
@Import({ProjectStatusService.class,ProjectStatusRepository.class,WriteCoordinator.class,RevisionService.class,
    ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProjectStatusContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean ProjectStatusMapper mapper;
    @MockBean ProjectBudgetRepository projects;
    @MockBean SharedQueriesMapper shared;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    Project row;
    String assignmentSnapshot;
    LocalDate day=LocalDate.of(2026,7,1);
    @BeforeEach void setup() {
        row=project("PRE",LocalDate.of(2027,12,31));assignmentSnapshot="original";
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(users.userId()).thenReturn("admin");
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(mapper.project(anyString())).thenAnswer(c->row);
        when(projects.snapshot(anyString())).thenAnswer(c->{if(row==null)throw new ApiException(404,"NOT_FOUND","없음");return row.toString();});
        when(shared.cashBaseMonth()).thenReturn("202501");when(shared.monthCloses(anyString(),anyString())).thenReturn(List.of());
        when(projects.operatingValue("TGT_MRGN_RT","202607")).thenReturn(new BigDecimal("20"));
        when(mapper.allowance("TEAM_FEE")).thenReturn(new BigDecimal("100"));when(mapper.allowance("PEXP_FEE")).thenReturn(BigDecimal.ZERO);
        when(mapper.reason("ETC")).thenReturn("기타");when(mapper.assignmentSnapshot(anyString())).thenAnswer(c->assignmentSnapshot);
        when(mapper.assignments(anyString())).thenReturn(List.of());when(mapper.assignmentRows(anyString())).thenReturn(List.of());
        when(mapper.costMonths(anyString())).thenReturn(List.of());when(mapper.valueHistory(anyString())).thenReturn(List.of());when(mapper.months(anyString())).thenReturn(List.of());
        when(mapper.updateProject(any())).thenAnswer(c->{ProjectWrite w=c.getArgument(0);row=new Project(row.id(),row.name(),row.customerId(),row.contractType(),w.status(),row.startDate(),row.endDate(),row.workplace(),row.feature(),row.estimateAmount(),row.originalContractAmount(),row.contractAmount(),row.budgetAmount(),row.teamExpenseAmount(),row.projectExpenseAmount(),row.budgetManual(),w.status().equals("LOSE")?w.date():null,w.reason(),w.memo(),row.opportunityId());return 1;});
        when(mapper.insertEvent(any())).thenReturn(1);when(mapper.updateAssignment(any())).thenReturn(1);when(mapper.insertAssignmentEvent(any())).thenReturn(1);when(mapper.saveMonth(any())).thenReturn(1);
    }
    Project project(String status,LocalDate end) {return new Project("STATE-1","프로젝트",null,"TK",status,LocalDate.of(2026,1,1),end,null,null,"120","100","101","80","100","0","N",null,null,null,null);}
    String revision() {return revisions.revision(row.toString());}
    ObjectNode progress() {return json.createObjectNode().put("year",2026).put("expectedRevision",revision());}
    ObjectNode loss() {return progress().put("lossReason","ETC").putNull("lossMemo").put("previewToken",revisions.revision(new PreviewSnapshot(row.id(),day,assignmentSnapshot)));}
    void putBody(String operation,ObjectNode body,int status,String code) throws Exception {
        var request=switch(operation) {
            case "progress" -> put("/api/projects/STATE-1/progress");
            case "loss" -> put("/api/projects/STATE-1/loss");
            default -> throw new IllegalArgumentException("Unknown project operation: "+operation);
        };
        var result=mvc.perform(request.contentType("application/json").content(body.toString()))
            .andExpect(response->assertThat(response.getResponse().getStatus()).as(response.getResponse().getContentAsString()).isEqualTo(status));
        if(code!=null)result.andExpect(jsonPath("$.code").value(code));
    }
    void noWrites() {verify(mapper,never()).updateProject(any());verify(mapper,never()).updateAssignment(any());verify(mapper,never()).insertEvent(any());verify(mapper,never()).insertAssignmentEvent(any());verifyNoInteractions(history,invalidation);}
    @Test void lossWithoutAuthCommitsServerDateAndPreservesAmounts() throws Exception {
        Project before=row;
        mvc.perform(put("/api/projects/STATE-1/loss").contentType("application/json").content(loss().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.value.status").value("LOSE"))
            .andExpect(jsonPath("$.data.value.lossDate").value("2026-07-01")).andExpect(jsonPath("$.data.revision").isString());
        assertThat(row.contractAmount()).isEqualTo(before.contractAmount());assertThat(row.budgetAmount()).isEqualTo(before.budgetAmount());
        verify(mapper).insertEvent(argThat(e->e.before().equals("PRE")&&e.after().equals("LOSE")&&e.actor().equals("admin")));
        verify(manager).commit(any());verify(history).insert(any());
    }
    @Test void defaultsPublicCurrentMonthCalculationAndNoWrites() throws Exception {
        mvc.perform(get("/api/projects/defaults").param("contractAmount","101").param("contractType","TK").header("X-Request-Id","defaults-check"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.budgetAmount").value("80"))
            .andExpect(jsonPath("$.data.teamExpenseAmount").value("100")).andExpect(jsonPath("$.data.projectExpenseAmount").value("0"))
            .andExpect(jsonPath("$.data.basisMonth").value("2026-07")).andExpect(header().string("X-Request-Id","defaults-check"));
        verify(users).currentUser();noWrites();
    }
    @Test void absentStandardsAreNullWithManualGuidanceNeverZero() throws Exception {
        when(projects.operatingValue(anyString(),anyString())).thenReturn(null);when(mapper.allowance(anyString())).thenReturn(null);
        mvc.perform(get("/api/projects/defaults").param("contractAmount","101").param("contractType","TK"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.budgetAmount").isEmpty()).andExpect(jsonPath("$.data.teamExpenseAmount").isEmpty()).andExpect(jsonPath("$.data.warnings.length()").value(3));noWrites();
    }
    @Test void internalBudgetIsNull() throws Exception {
        mvc.perform(get("/api/projects/defaults").param("contractAmount","0").param("contractType","IN")).andExpect(status().isOk()).andExpect(jsonPath("$.data.budgetAmount").isEmpty());noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.1","1%","1e3","","1000000000000000"})
    void invalidDefaultsMoney(String amount) throws Exception {
        mvc.perform(get("/api/projects/defaults").param("contractAmount",amount).param("contractType","TK"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("contractAmount"));noWrites();
    }
    @Test void optionsEmptySourceIsAnEmptyPublicList() throws Exception {
        when(mapper.options(any())).thenReturn(List.of());mvc.perform(get("/api/projects/options").param("purpose","ASSIGNMENT"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());verify(users).currentUser();noWrites();
    }
    @Test void optionsUsesActualIdsAndSeoulDateAndPurpose() throws Exception {
        var option=new Option("STATE-1","프로젝트","TK",day,day,"101","PRE","고객사",2,"파트",null);
        when(mapper.options(any())).thenReturn(List.of(option));mvc.perform(get("/api/projects/options").param("purpose","BILLING").param("q","프로젝트"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value("STATE-1"));
        verify(mapper).options(new Selection("BILLING","프로젝트",day,"202607"));noWrites();
    }
    @Test void optionsRejectsPmPurposeAndOversizeSearch() throws Exception {
        mvc.perform(get("/api/projects/options").param("purpose","PM")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("purpose"));
        mvc.perform(get("/api/projects/options").param("purpose","BILLING").param("q","가".repeat(101))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("q"));noWrites();
    }
    @Test void previewSeparatesPeopleAndAssignmentsAndHasNoBusinessWrites() throws Exception {
        var a=new Assignment("AS-1","DEV-PK","M-77","개발자","DEV","개발",day,day,"1.00","1.00","ACT");
        var b=new Assignment("AS-2","DEV-PK","M-77","개발자","DEV","개발",day,day,"1.00","1.00","ACT");
        when(mapper.assignments(anyString())).thenReturn(List.of(a,b));mvc.perform(get("/api/projects/STATE-1/loss-preview"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentPersonCount").value(1)).andExpect(jsonPath("$.data.currentAssignmentCount").value(2))
            .andExpect(jsonPath("$.data.basisDate").value("2026-07-01")).andExpect(jsonPath("$.data.currentAssignments[0].developerId").value("DEV-PK"))
            .andExpect(jsonPath("$.data.currentAssignments[0].managementNo").value("M-77")).andExpect(jsonPath("$.data.previewToken").isString());noWrites();
    }
    @Test void successfulProgressOnlyChangesStateAndCreatesOneEtcStatusHistory() throws Exception {
        Project before=row;putBody("progress",progress(),200,null);assertThat(row.status()).isEqualTo("ING");
        assertThat(row.contractAmount()).isEqualTo(before.contractAmount());assertThat(row.budgetAmount()).isEqualTo(before.budgetAmount());assertThat(row.originalContractAmount()).isEqualTo(before.originalContractAmount());
        verify(mapper).insertEvent(argThat(e->e.before().equals("PRE")&&e.after().equals("ING")&&e.serial()==1&&e.actor().equals("admin")));
        verify(mapper,never()).updateAssignment(any());verify(history).insert(any());
    }
    @ParameterizedTest @ValueSource(strings={"ING","END","LOSE"})
    void nonPreStateRejectsPreviewProgressAndLoss(String state) throws Exception {
        row=project(state,row.endDate());ObjectNode input=loss();mvc.perform(get("/api/projects/STATE-1/loss-preview")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATUS"));
        putBody("progress",progress(),409,"INVALID_PROJECT_STATUS");putBody("loss",input,409,"INVALID_PROJECT_STATUS");noWrites();
    }
    @Test void elapsedPreIsEndAndReadsDoNotPersistIt() throws Exception {
        row=project("PRE",day.minusDays(1));mvc.perform(get("/api/projects/STATE-1/loss-preview")).andExpect(status().isConflict());
        putBody("progress",progress(),409,"INVALID_PROJECT_STATUS");noWrites();
    }
    @Test void missingProjectIs404OnPreviewAndWrites() throws Exception {
        ObjectNode p=progress(),l=loss();row=null;mvc.perform(get("/api/projects/STATE-1/loss-preview")).andExpect(status().isNotFound());
        putBody("progress",p,404,"NOT_FOUND");putBody("loss",l,404,"NOT_FOUND");noWrites();
    }
    @Test void pastYearAndOutOfRangeAreDifferentErrors() throws Exception {
        putBody("progress",progress().put("year",2025),409,"READ_ONLY_YEAR");putBody("loss",loss().put("year",2028),400,"INVALID_INPUT");noWrites();
    }
    @Test void staleRevisionRejectsBothWritesBeforeMutation() throws Exception {
        putBody("progress",progress().put("expectedRevision","old"),409,"STALE_WRITE");putBody("loss",loss().put("expectedRevision","old"),409,"STALE_WRITE");noWrites();
    }
    @Test void changedAssignmentSnapshotAndChangedDayRejectLoss() throws Exception {
        ObjectNode input=loss();assignmentSnapshot="new-assignment";putBody("loss",input,409,"STALE_WRITE");
        input=loss();when(clock.instant()).thenReturn(Instant.parse("2026-07-01T15:00:00Z"));putBody("loss",input,409,"STALE_WRITE");noWrites();
    }
    @Test void closedMonthRefusesPreviewAndCommitWithMonthDetails() throws Exception {
        when(shared.monthCloses("202607","202607")).thenReturn(List.of(new CloseRow("202607","CLOSE")));
        mvc.perform(get("/api/projects/STATE-1/loss-preview")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH")).andExpect(jsonPath("$.details.month").value("2026-07"));
        putBody("loss",loss(),409,"CLOSED_MONTH");noWrites();
    }
    @Test void transferredMonthRefusesLoss() throws Exception {
        when(shared.cashBaseMonth()).thenReturn("202608");putBody("loss",loss(),409,"CLOSED_MONTH");noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"expectedRevision","year"})
    void missingRequiredProgressField(String field) throws Exception {
        ObjectNode body=progress();body.remove(field);putBody("progress",body,400,"INVALID_INPUT");noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"lossReason","previewToken","expectedRevision","year"})
    void missingRequiredLossField(String field) throws Exception {
        ObjectNode body=loss();body.remove(field);putBody("loss",body,400,"INVALID_INPUT");noWrites();
    }
    @Test void invalidInactiveReasonAndLongMemoAreFieldErrors() throws Exception {
        putBody("loss",loss().put("lossReason","STOP"),400,"INVALID_INPUT");putBody("loss",loss().put("lossMemo","가".repeat(501)),400,"INVALID_INPUT");noWrites();
    }
    @Test void unknownFieldsRejectedOnAllFiveOperations() throws Exception {
        for(String path:List.of("/api/projects/defaults","/api/projects/options","/api/projects/STATE-1/loss-preview"))
            mvc.perform(get(path).param("unknown","x")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("unknown"));
        putBody("progress",progress().put("status","ING"),400,"INVALID_INPUT");putBody("loss",loss().put("basisDate","2026-07-01"),400,"INVALID_INPUT");noWrites();
    }
    @Test void successfulLossProcessesCurrentFutureButNotPastAndReclassifiesOnlyOpenCosts() throws Exception {
        var current=new AssignmentRow("A1","D1",day,day,BigDecimal.ONE,new BigDecimal("100"),"ACT");
        var future=new AssignmentRow("A2","D1",day.plusDays(1),day.plusMonths(1),BigDecimal.ONE,new BigDecimal("100"),"ACT");
        var past=new AssignmentRow("A3","D2",day.minusMonths(1),day.minusDays(1),BigDecimal.ONE,null,"ACT");
        when(mapper.assignmentRows(anyString())).thenReturn(List.of(current,future,past));when(mapper.months("A1")).thenReturn(List.of(new MonthRow("202608","N"),new MonthRow("202609","Y")));
        when(mapper.months("A2")).thenReturn(List.of(new MonthRow("202607","N"),new MonthRow("202608","Y")));
        when(mapper.costMonths(anyString())).thenReturn(List.of("202606","202607"));when(shared.monthCloses("202606","202606")).thenReturn(List.of(new CloseRow("202606","CLOSE")));
        ObjectNode body=loss();putBody("loss",body,200,null);assertThat(row.status()).isEqualTo("LOSE");assertThat(row.lossDate()).isEqualTo(day);
        verify(mapper).insertAssignmentEvent(argThat(e->e.id().equals("A1")&&e.type().equals("EARLY")&&e.before().equals(e.after())));
        verify(mapper).insertAssignmentEvent(argThat(e->e.id().equals("A2")&&e.type().equals("CNCL")));
        verify(mapper,never()).updateAssignment(argThat(w->w.id().equals("A3")));verify(mapper).deleteMonth("A1","202608");verify(mapper,never()).deleteMonth("A1","202609");
        verify(mapper).saveMonth(argThat(w->w.month().equals("202607")&&w.days()==1&&w.mm().toPlainString().equals("0.03")));
        verify(mapper).classifyCost(argThat(w->w.month().equals("202607")));verify(mapper,never()).classifyCost(argThat(w->w.month().equals("202606")));
        clearInvocations(mapper,history,invalidation);putBody("loss",body,409,"INVALID_PROJECT_STATUS");noWrites();
    }
    @Test void sortedLocksPrecedeSnapshotAndMandatoryHistoryFailureRollsBack() throws Exception {
        doThrow(new IllegalStateException("private SQL stack")).when(history).insert(any());
        mvc.perform(put("/api/projects/STATE-1/progress").contentType("application/json").content(progress().toString()))
            .andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
        verify(manager).rollback(any());verifyNoInteractions(invalidation);var ordered=inOrder(locks,projects);
        ordered.verify(locks).lock("PROJECT:STATE-1");ordered.verify(locks).lock("operating-standards:CASH_BASE_YM");ordered.verify(projects).snapshot("STATE-1");
    }
}
