package com.sicost.plans;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.budget.ProjectBudgetDtos;
import com.sicost.budget.ProjectBudgetMapper;
import com.sicost.budget.ProjectBudgetRepository;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.plans.ProjectPlanDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectPlanController.class)
@ContextConfiguration(classes = ProjectPlanController.class)
@Import({ProjectPlanService.class, ProjectPlanRepository.class, ProjectBudgetRepository.class, WriteCoordinator.class,
    RevisionService.class, ChangeHistoryWriter.class, ApiExceptionHandler.class, RequestIdFilter.class, JsonConfiguration.class})
class ProjectPlanContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean ProjectPlanMapper mapper;
    @MockBean ProjectBudgetMapper projects;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    ProjectBudgetDtos.Stored project;
    List<StoredPlan> rows;
    String otherChild;
    @BeforeEach void setup() {
        rows = new ArrayList<>(List.of(new StoredPlan("DEV", "개발", "N", new BigDecimal("5.00"), new BigDecimal("3.25"), "OPP"),
            new StoredPlan("QA", "시험", "Y", null, new BigDecimal("1.00"), null)));
        project = new ProjectBudgetDtos.Stored("PLAN-1", "TK", "ING", LocalDate.of(2027,12,31), BigDecimal.ZERO, null, "N");
        otherChild = "initial";
        when(users.userId()).thenReturn("admin");
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin", "임시 관리자", List.of("ADMIN"), true));
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(projects.find(anyString())).thenAnswer(call -> project);
        when(projects.snapshot(anyString())).thenAnswer(call -> snapshot());
        when(projects.operatingValue("CASH_BASE_YM", "999912")).thenReturn(new BigDecimal("202501"));
        when(mapper.plans(anyString())).thenAnswer(call -> List.copyOf(rows));
        when(mapper.activeJobs()).thenReturn(List.of("QA", "PM", "BA"));
        when(mapper.update(any())).thenAnswer(call -> persist(call.getArgument(0), false));
        when(mapper.insert(any())).thenAnswer(call -> persist(call.getArgument(0), true));
    }
    int persist(PlanMutation value, boolean insert) {
        StoredPlan old = rows.stream().filter(r -> r.jobCode().equals(value.jobCode())).findFirst().orElse(null);
        rows.removeIf(r -> r.jobCode().equals(value.jobCode()));
        rows.add(new StoredPlan(value.jobCode(), old == null ? value.jobCode() : old.jobName(), old == null ? "Y" : old.useYn(),
            value.planMm(), old == null ? BigDecimal.ZERO : old.actualMm(), insert ? "MANUAL" : old.source()));
        return 1;
    }
    String snapshot() { return project == null ? null : project.toString() + rows.toString() + otherChild; }
    ObjectNode body(String job, String amount) {
        ObjectNode body = json.createObjectNode().put("year",2026).put("expectedRevision",revisions.revision(snapshot()));
        body.putArray("items").addObject().put("jobCode",job).put("planMm",amount);
        return body;
    }
    ResultActions save(ObjectNode body) throws Exception {
        return mvc.perform(put("/api/projects/PLAN-1/plans").contentType("application/json").content(body.toString()));
    }
    void noWrites() {
        verify(mapper,never()).insert(any()); verify(mapper,never()).update(any()); verifyNoInteractions(history,invalidation);
    }
    @Test void publicGetReturnsEveryFieldExactTotalsAndNullUnsavedWithoutMutations() throws Exception {
        String before = snapshot();
        mvc.perform(get("/api/projects/PLAN-1/plans")).andExpect(status().isOk()).andExpect(header().exists("X-Request-Id"))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.items[0].jobCode").value("DEV")).andExpect(jsonPath("$.data.items[0].jobName").value("개발"))
            .andExpect(jsonPath("$.data.items[0].useYn").value("N")).andExpect(jsonPath("$.data.items[0].planMm").value("5.00"))
            .andExpect(jsonPath("$.data.items[0].actualMm").value("3.25")).andExpect(jsonPath("$.data.items[0].differenceMm").value("-1.75"))
            .andExpect(jsonPath("$.data.items[0].source").value("OPP")).andExpect(jsonPath("$.data.items[1].planMm").isEmpty())
            .andExpect(jsonPath("$.data.items[1].source").isEmpty()).andExpect(jsonPath("$.data.planMmTotal").value("5.00"))
            .andExpect(jsonPath("$.data.actualMmTotal").value("4.25")).andExpect(jsonPath("$.data.differenceMmTotal").value("-0.75"))
            .andExpect(jsonPath("$.data.revision").value(revisions.revision(before)));
        verify(users).currentUser(); assertThat(snapshot()).isEqualTo(before); noWrites();
    }
    @Test void emptyRealSourcesReturnEmptyArrayAndZeroWithoutVirtualJobs() throws Exception {
        rows.clear(); mvc.perform(get("/api/projects/PLAN-1/plans")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.planMmTotal").value("0.00"))
            .andExpect(jsonPath("$.data.actualMmTotal").value("0.00")).andExpect(jsonPath("$.data.differenceMmTotal").value("0.00")); noWrites();
    }
    @Test void missingProjectIs404ForBothOperations() throws Exception {
        ObjectNode body=body("QA","0"); project=null;
        mvc.perform(get("/api/projects/missing/plans")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        save(body).andExpect(status().isNotFound()); noWrites();
    }
    @Test void adminWithoutPmSavesBoundaryValuesAndLeavesOmittedPlanIntact() throws Exception {
        ObjectNode body=body("QA","0"); body.withArray("items").addObject().put("jobCode","PM").put("planMm","9999.99");
        save(body).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.value.planMmTotal").value("10004.99"));
        assertThat(rows.stream().filter(r->r.jobCode().equals("DEV")).findFirst().orElseThrow().source()).isEqualTo("OPP");
        verify(mapper,times(2)).insert(argThat(m->m.actor().equals("admin") && m.now().equals(LocalDateTime.of(2026,7,1,0,0))));
        verify(history).insert(argThat(h->h.tableName().equals("TB_PJ_MNPW_PLAN") && h.targetKey().equals("PLAN-1:PM")
            && h.type().equals("NEW") && h.item().equals("PLAN_MM") && h.before()==null && h.after().equals("9999.99") && h.userId().equals("admin")));
        mvc.perform(get("/api/projects/PLAN-1/plans")).andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(revisions.revision(snapshot())));
    }
    @Test void blankExistingInactiveOppBecomesZeroPreservingSourceAndWritesOnlyDelta() throws Exception {
        save(body("DEV","")).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        StoredPlan dev=rows.stream().filter(r->r.jobCode().equals("DEV")).findFirst().orElseThrow();
        assertThat(dev.planMm()).isEqualByComparingTo("0"); assertThat(dev.source()).isEqualTo("OPP");
        verify(mapper).update(any()); verify(mapper,never()).insert(any());
        verify(history).insert(argThat(h->h.type().equals("UPD") && h.targetKey().equals("PLAN-1:DEV") && h.before().equals("5.00") && h.after().equals("0.00")));
    }
    @Test void multipleIndividuallyValidValuesAreNeverReducedByTotal() throws Exception {
        ObjectNode body=body("QA","9999.99"); body.withArray("items").addObject().put("jobCode","PM").put("planMm","9999.99");
        save(body).andExpect(status().isOk()).andExpect(jsonPath("$.data.value.planMmTotal").value("20004.98"));
        verify(mapper,times(2)).insert(argThat(m->m.planMm().compareTo(new BigDecimal("9999.99"))==0));
    }
    @Test void equalValueAndEmptyRequestAreNoOpsWithoutTimestampsHistoryOrInvalidation() throws Exception {
        save(body("DEV","5")).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));
        ObjectNode empty=body("DEV","5");empty.withArray("items").removeAll();
        save(empty).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));noWrites();
    }
    @Test void duplicateJobsRejectWholeRequestBeforeWrites() throws Exception {
        ObjectNode body=body("QA","1");body.withArray("items").addObject().put("jobCode"," QA ").put("planMm","2");
        save(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("items"));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"-1","+1","1e2","1.001","10000","1,000","text",".1","1."})
    void invalidMmRejectsEntireBatchWithoutRounding(String amount) throws Exception {
        ObjectNode body=body("QA","1");body.withArray("items").addObject().put("jobCode","PM").put("planMm",amount);
        save(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("items[1].planMm"));noWrites();
    }
    @Test void unknownNewOrInactiveJobIsFieldError() throws Exception {
        save(body("UNKNOWN","0")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("items[0].jobCode"));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"year","expectedRevision","items"})
    void missingRequiredBodyFieldsAreFieldErrors(String field) throws Exception {
        ObjectNode body=body("QA","0");body.remove(field);
        save(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"jobCode","planMm"})
    void missingRequiredItemFieldsAreFieldErrors(String field) throws Exception {
        ObjectNode body=body("QA","0");((ObjectNode)body.withArray("items").get(0)).remove(field);
        save(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("items[0]."+field));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"actualMm","differenceMm","source","id","regUserId"})
    void readonlyAndUnknownItemFieldsAreRejected(String field) throws Exception {
        ObjectNode body=body("QA","0");((ObjectNode)body.withArray("items").get(0)).put(field,"injected");
        save(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("items[0]."+field));noWrites();
    }
    @Test void unknownRootFieldRejected() throws Exception {
        save(body("QA","0").put("revision","injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("revision"));noWrites();
    }
    @Test void numericMmAndStringYearAreNotCoerced() throws Exception {
        ObjectNode body=body("QA","0");((ObjectNode)body.withArray("items").get(0)).put("planMm",0);
        save(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("items[0].planMm"));
        save(body("QA","0").put("year","2026")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("year"));noWrites();
    }
    @Test void malformedBodyIsSafeInvalidInputWithOriginalRequestId() throws Exception {
        mvc.perform(put("/api/projects/PLAN-1/plans").header("X-Request-Id","plans-json").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("body"))
            .andExpect(jsonPath("$.requestId").value("plans-json")).andExpect(header().string("X-Request-Id","plans-json"));noWrites();
    }
    @Test void unknownQueryFieldsRejectedOnBothOperations() throws Exception {
        mvc.perform(get("/api/projects/PLAN-1/plans").param("year","2026")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("year"));
        mvc.perform(put("/api/projects/PLAN-1/plans").param("q","x").contentType("application/json").content(body("QA","0").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("q"));noWrites();
    }
    @Test void longIdAndJobCodeAreRejectedBeforeWrites() throws Exception {
        mvc.perform(get("/api/projects/"+"x".repeat(21)+"/plans")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("id"));
        save(body("x".repeat(11),"0")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("items[0].jobCode"));noWrites();
    }
    @Test void previousYearIsReadOnly() throws Exception {
        save(body("QA","0").put("year",2025)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));noWrites();
    }
    @ParameterizedTest @ValueSource(ints={2024,2028})
    void outsideAllowedYearRangeIsInvalid(int year) throws Exception {
        save(body("QA","0").put("year",year)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("year"));noWrites();
    }
    @Test void nextYearIsAllowedWithoutProrating() throws Exception { save(body("DEV","5").put("year",2027)).andExpect(status().isOk());noWrites(); }
    @ParameterizedTest @ValueSource(strings={"END","LOSE"})
    void lockedProjectPreservesPlansAndSource(String status) throws Exception {
        project=new ProjectBudgetDtos.Stored(project.id(),project.contractType(),status,project.endDate(),project.contractAmount(),null,"N");
        save(body("QA","0")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATUS"));noWrites();
    }
    @Test void elapsedEndDateIsReadOnlyAndGetNeverUpdatesStoredStatus() throws Exception {
        project=new ProjectBudgetDtos.Stored(project.id(),project.contractType(),"ING",LocalDate.of(2026,6,30),project.contractAmount(),null,"N");
        mvc.perform(get("/api/projects/PLAN-1/plans")).andExpect(status().isOk());
        save(body("QA","0")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATUS"));noWrites();
    }
    @Test void staleRevisionCannotWrite() throws Exception {
        save(body("QA","0").put("expectedRevision","stale")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();
    }
    @Test void siblingChildChangesInvalidatePlanRevision() throws Exception {
        ObjectNode body=body("QA","0");otherChild="new-expense";
        save(body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();
    }
    @Test void lockPrecedesSnapshotAndDomainReads() throws Exception {
        save(body("QA","0")).andExpect(status().isOk());var order=inOrder(locks,projects,mapper);
        order.verify(locks).lock("PROJECT:PLAN-1");order.verify(locks).lock("operating-standards:CASH_BASE_YM");
        order.verify(projects).snapshot("PLAN-1");order.verify(projects).find("PLAN-1");order.verify(mapper).plans("PLAN-1");
    }
    @Test void auditFailureRollsBackAndHidesSqlWithoutInvalidation() throws Exception {
        doThrow(new IllegalStateException("SELECT private sql stack")).when(history).insert(any());
        save(body("QA","0")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private sql"))));
        verify(manager).rollback(any());verifyNoInteractions(invalidation);
    }
}
