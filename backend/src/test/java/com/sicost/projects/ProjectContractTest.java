package com.sicost.projects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.projects.ProjectRegistrationService;
import com.sicost.distribution.ProjectDistributionService;
import com.sicost.plans.ProjectPlanService;
import com.sicost.plans.ProjectPlanDtos.ProjectPlansData;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.projects.ProjectDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectController.class)
@ContextConfiguration(classes=ProjectController.class)
@Import({ProjectService.class,ProjectQueryService.class,WriteCoordinator.class,RevisionService.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProjectContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean ProjectRepository repository;
    @MockBean ProjectRegistrationService registration;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryWriter history;
    @MockBean ProjectDistributionService distribution;
    @MockBean ProjectPlanService plans;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    Project row;
    String child;
    @BeforeEach void setup() {
        row=project("ING",LocalDate.of(2027,12,31));child="initial";
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(repository.required(anyString())).thenAnswer(c->{if(row==null)throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");return row;});
        when(repository.snapshot(anyString())).thenAnswer(c->{if(row==null)throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");return row.toString()+child;});
        when(repository.defaults(anyString())).thenReturn(new DefaultsRow(new BigDecimal("202501"),new BigDecimal("20"),new BigDecimal("50000"),new BigDecimal("30000")));
        when(repository.activeType(anyString())).thenReturn(true);
        when(repository.customer(anyString())).thenAnswer(c->new Reference(c.getArgument(0),"고객사",true));
        when(repository.part(anyString())).thenAnswer(c->new Reference(c.getArgument(0),"본사",true));
        when(repository.parts(anyString(),anyString())).thenReturn(List.of(new ProjectPart("2026-01","HQ","본사",null,"관리자",false)));
        when(repository.pm(anyString())).thenReturn(List.of());when(repository.assignments(anyString(),anyBoolean())).thenReturn(List.of());when(repository.history(anyString())).thenReturn(List.of());
        when(repository.actualMmTotal(anyString())).thenReturn("0.00");when(repository.expenseTotal(anyString())).thenReturn("0");
        when(plans.getProjectPlans(anyString())).thenReturn(new ProjectPlansData(List.of(),"0.00","0.00","0.00","revision"));
        when(repository.list(any())).thenAnswer(c->List.of(new ListRow(row.id(),row.name(),row.contractType(),row.startDate(),row.endDate(),row.contractAmount(),ProjectRules.effectiveStatus(row,LocalDate.of(2026,7,1)),"고객사",0,"본사")));
        when(repository.count(any())).thenReturn(31L);when(repository.unassignedCount(any())).thenReturn(7L);
        when(registration.reservedIds(any())).thenReturn(new HashSet<>(Set.of("P-0009","manual-id")));
        doAnswer(c->{row=((Mutation)c.getArgument(0)).project();return null;}).when(repository).update(any());
        doAnswer(c->{String id=c.getArgument(0);ProjectRegistrationService.Project p=c.getArgument(1);
            row=new Project(id,p.name(),p.customerId(),p.contractType(),p.status(),p.startDate(),p.endDate(),p.workplace(),p.feature(),ProjectRules.money(p.estimateAmount()),ProjectRules.money(p.contractAmount()),ProjectRules.money(p.contractAmount()),ProjectRules.money(p.budgetAmount()),ProjectRules.money(p.teamExpenseAmount()),ProjectRules.money(p.projectExpenseAmount()),p.budgetManual(),null,null,null,null);return null;
        }).when(registration).register(anyString(),any(),anyString(),any());
    }
    Project project(String status,LocalDate end) {return new Project("P-0009","원 프로젝트","C-1","TK",status,LocalDate.of(2026,1,1),end,null,null,null,"100","100","80","50000","30000","N",null,null,null,null);}
    ObjectNode create() {return json.createObjectNode().put("year",2026).put("name","등록 프로젝트").put("contractType","TK").put("customerId","C-1").put("partId","HQ").put("startDate","2026-01-01").put("endDate","2027-12-31").put("contractAmount","0");}
    ObjectNode edit() {ObjectNode b=create();b.remove(List.of("contractType","partId"));b.put("name",row.name()).put("contractAmount",row.contractAmount()).put("expectedRevision",revisions.revision(row.toString()+child));return b;}
    org.springframework.test.web.servlet.ResultActions postBody(ObjectNode body) throws Exception {return mvc.perform(post("/api/projects").contentType("application/json").content(body.toString()));}
    org.springframework.test.web.servlet.ResultActions putBody(ObjectNode body) throws Exception {return mvc.perform(put("/api/projects/P-0009").contentType("application/json").content(body.toString()));}
    void noWrites() {verify(repository,never()).update(any());verify(repository,never()).insertHistory(any());verify(registration,never()).register(anyString(),any(),anyString(),any());verifyNoInteractions(history,invalidation);}
    @Test void listUsesPublicAdministratorSeoulClockAndServerCountsWithoutWrites() throws Exception {
        mvc.perform(get("/api/projects").header("X-Request-Id","project-list")).andExpect(status().isOk()).andExpect(header().string("X-Request-Id","project-list"))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.total").value(31))
            .andExpect(jsonPath("$.data.unassignedCount").value(7)).andExpect(jsonPath("$.data.year").value(2026));
        verify(repository).list(argThat(q->q.today().equals(LocalDate.of(2026,7,1))&&q.firstDate().equals(LocalDate.of(2026,1,1))&&q.offset()==0));verify(users).currentUser();noWrites();
    }
    @Test void emptyListIsNotFabricated() throws Exception {when(repository.list(any())).thenReturn(List.of());when(repository.count(any())).thenReturn(0L);when(repository.unassignedCount(any())).thenReturn(0L);mvc.perform(get("/api/projects")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());noWrites();}
    @Test void filtersAreTrimmedAndPassedUnescapedForLiteralStrposSearch() throws Exception {mvc.perform(get("/api/projects").param("q","  A%_  ").param("partId","UNASSIGNED").param("status","PRE").param("page","2").param("size","20")).andExpect(status().isOk());verify(repository).list(argThat(q->q.filter().q().equals("A%_")&&q.filter().partId().equals("UNASSIGNED")&&q.offset()==20));noWrites();}
    @ParameterizedTest @CsvSource({"sort,unexpected","direction,asc","page,0","size,30","year,2028","q,TOO_LONG","contractType,UNKNOWN","status,unknown","includeCancelled,true"})
    void invalidListQueryIsFieldError(String field,String value) throws Exception {if(value.equals("TOO_LONG"))value="x".repeat(101);mvc.perform(get("/api/projects").param(field,value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value(field));noWrites();}
    @Test void detailHasFourRealCollectionsAndNullableBodyWithoutWrites() throws Exception {mvc.perform(get("/api/projects/P-0009")).andExpect(status().isOk()).andExpect(jsonPath("$.data.project.originalContractAmount").value("100")).andExpect(jsonPath("$.data.parts[0].partId").value("HQ")).andExpect(jsonPath("$.data.assignments").isEmpty()).andExpect(jsonPath("$.data.actualMmTotal").value("0.00")).andExpect(jsonPath("$.data.history").isEmpty()).andExpect(jsonPath("$.data.pm").isEmpty());noWrites();}
    @Test void includeCancelledOnlyChangesAssignmentRead() throws Exception {mvc.perform(get("/api/projects/P-0009").param("includeCancelled","true")).andExpect(status().isOk());verify(repository).assignments("P-0009",true);noWrites();}
    @Test void detailDoesNotAcceptYear() throws Exception {mvc.perform(get("/api/projects/P-0009").param("year","2026")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("year"));noWrites();}
    @Test void invalidCancelledBooleanRejected() throws Exception {mvc.perform(get("/api/projects/P-0009").param("includeCancelled","1")).andExpect(status().isBadRequest());noWrites();}
    @Test void missingDetailAndUpdateAre404() throws Exception {ObjectNode b=edit();row=null;mvc.perform(get("/api/projects/missing")).andExpect(status().isNotFound());putBody(b).andExpect(status().isNotFound());noWrites();}
    @Test void createZeroAllocatesMaximumNextAndRegistersWithAdminOnlyAfterLocks() throws Exception {
        postBody(create()).andExpect(status().isOk()).andExpect(jsonPath("$.data.project.id").value("P-0010")).andExpect(jsonPath("$.data.project.originalContractAmount").value("0")).andExpect(jsonPath("$.data.project.lossDate").isEmpty());
        var order=inOrder(locks,repository,registration);order.verify(locks).lock("PROJECT:CREATE");order.verify(locks).lock("operating-standards:CASH_BASE_YM");order.verify(locks).lock("operating-standards:TGT_MRGN_RT");order.verify(repository).defaults("202607");
        verify(registration).register(eq("P-0010"),argThat(p->p.contractAmount().signum()==0&&p.budgetAmount().signum()==0&&p.partId().equals("HQ")),eq("admin"),eq(LocalDateTime.of(2026,7,1,0,0)));
    }
    @ParameterizedTest @ValueSource(strings={"name","contractType","startDate","endDate","year"})
    void missingCreateRequiredIs400(String field) throws Exception {var b=create();b.remove(field);postBody(b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrites();}
    @ParameterizedTest @ValueSource(strings={"id","status","originalContractAmount","modUserId","opportunityId","unknown"})
    void createRejectsServerOwnedOrUnknownFields(String field) throws Exception {postBody(create().put(field,"injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value(field));noWrites();}
    @Test void blankNameRollsBackNoBusinessRows() throws Exception {postBody(create().put("name","  ")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("name"));noWrites();}
    @Test void lastYearCreateIs409() throws Exception {postBody(create().put("year",2025)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));noWrites();}
    @Test void nextYearCreateIsAllowed() throws Exception {postBody(create().put("year",2027)).andExpect(status().isOk());}
    @ParameterizedTest @ValueSource(strings={"-1","1.1","1e3","+1","1%","9999999999999999"})
    void directAmountsNeverRoundedOrCoerced(String value) throws Exception {postBody(create().put("contractAmount",value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("contractAmount"));noWrites();}
    @Test void moneyNormalizationKeepsAllFifteenDigits() throws Exception {postBody(create().put("contractAmount"," 999,999,999,999,999\u00a0원 ")).andExpect(status().isOk()).andExpect(jsonPath("$.data.project.contractAmount").value("999999999999999"));}
    @Test void numericAmountNotCoerced() throws Exception {postBody(create().put("contractAmount",0)).andExpect(status().isBadRequest());noWrites();}
    @Test void stringYearNotCoerced() throws Exception {postBody(create().put("year","2026")).andExpect(status().isBadRequest());noWrites();}
    @Test void missingMarginRequiresManualNotInventedZero() throws Exception {when(repository.defaults(any())).thenReturn(new DefaultsRow(new BigDecimal("202501"),null,null,null));postBody(create()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("budgetAmount"));noWrites();postBody(create().put("budgetMode","MANUAL").put("budgetAmount","0").put("teamExpenseAmount","0").put("projectExpenseAmount","0")).andExpect(status().isOk()).andExpect(jsonPath("$.data.project.budgetManual").value("Y"));}
    @Test void missingAllowanceRequiresExplicitZero() throws Exception {when(repository.defaults(any())).thenReturn(new DefaultsRow(new BigDecimal("202501"),new BigDecimal("20"),null,null));postBody(create()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("teamExpenseAmount"));noWrites();}
    @Test void internalStoresNullHiddenFieldsAndAllowsEstimateAndExpenses() throws Exception {var b=create().put("contractType","IN").put("estimateAmount","99");b.remove(List.of("customerId","partId","contractAmount"));postBody(b).andExpect(status().isOk()).andExpect(jsonPath("$.data.project.customerId").isEmpty()).andExpect(jsonPath("$.data.project.budgetAmount").isEmpty()).andExpect(jsonPath("$.data.project.contractAmount").value("0")).andExpect(jsonPath("$.data.project.estimateAmount").value("99"));}
    @ParameterizedTest @ValueSource(strings={"customerId","partId","workplace","budgetAmount"})
    void internalRejectsNonemptyHiddenField(String field) throws Exception {var b=create().put("contractType","IN");b.remove(List.of("customerId","partId"));b.put(field,field.equals("budgetAmount")?"0":"injected");postBody(b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrites();}
    @Test void inactiveTypeAndNewReferencesAreRejected() throws Exception {when(repository.activeType("TK")).thenReturn(false);postBody(create()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("contractType"));noWrites();}
    @Test void inactiveCustomerRejectedOnCreate() throws Exception {when(repository.customer(any())).thenReturn(new Reference("C-1","중지 고객",false));postBody(create()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("customerId"));noWrites();}
    @Test void inactivePartRejectedOnCreate() throws Exception {when(repository.part(any())).thenReturn(new Reference("HQ","중지",false));postBody(create()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("partId"));noWrites();}
    @Test void updateAmountWithoutBillsChangesOriginalAndOneAmountEventThenNoOp() throws Exception {
        putBody(edit().put("contractAmount","200")).andExpect(status().isOk()).andExpect(jsonPath("$.data.project.originalContractAmount").value("200"));
        verify(repository).insertHistory(argThat(h->h.change().code().equals("AMT")&&h.change().after().contains("원 계약금액: 200")));
        verify(repository).update(argThat(m->m.actor().equals("admin")));clearInvocations(repository,history,invalidation);
        putBody(edit()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));verify(repository,never()).update(any());verify(repository,never()).insertHistory(any());verifyNoInteractions(history,invalidation);
    }
    @Test void activeBillPreservesOriginal() throws Exception {when(repository.hasBills(any())).thenReturn(true);putBody(edit().put("contractAmount","200")).andExpect(status().isOk()).andExpect(jsonPath("$.data.project.originalContractAmount").value("100"));}
    @Test void nullOriginalWithBillUsesPreviousCurrentAmount() throws Exception {row=new Project(row.id(),row.name(),row.customerId(),row.contractType(),row.status(),row.startDate(),row.endDate(),null,null,null,null,"100","80","50000","30000","N",null,null,null,null);when(repository.hasBills(any())).thenReturn(true);putBody(edit().put("contractAmount","200")).andExpect(status().isOk()).andExpect(jsonPath("$.data.project.originalContractAmount").value("100"));}
    @Test void inactiveExistingCustomerCanBePreserved() throws Exception {when(repository.customer(any())).thenReturn(new Reference("C-1","중지 고객",false));putBody(edit().put("name","다른 이름")).andExpect(status().isOk());}
    @Test void changedInactiveCustomerIsRejected() throws Exception {when(repository.customer("C-2")).thenReturn(new Reference("C-2","중지",false));putBody(edit().put("customerId","C-2")).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"contractType","status","partId","id","originalContractAmount","lossDate","regUserId"})
    void updateRejectsImmutableFields(String field) throws Exception {putBody(edit().put(field,"injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrites();}
    @Test void pastYearUpdateRejected() throws Exception {putBody(edit().put("year",2025)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));noWrites();}
    @Test void lossReadableButCannotUpdate() throws Exception {row=project("LOSE",LocalDate.of(2027,12,31));mvc.perform(get("/api/projects/P-0009")).andExpect(status().isOk()).andExpect(jsonPath("$.data.readOnly").value(true));putBody(edit()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATUS"));noWrites();}
    @Test void elapsedStatusReadDoesNotWriteAndExtensionSavesIng() throws Exception {row=project("END",LocalDate.of(2026,6,30));mvc.perform(get("/api/projects/P-0009")).andExpect(jsonPath("$.data.effectiveStatus").value("END"));noWrites();putBody(edit()).andExpect(status().isOk()).andExpect(jsonPath("$.data.project.status").value("ING"));}
    @Test void endTodayKeepsPreWithoutReadSideMutation() throws Exception {row=project("PRE",LocalDate.of(2026,7,1));mvc.perform(get("/api/projects/P-0009")).andExpect(jsonPath("$.data.effectiveStatus").value("PRE"));noWrites();}
    @Test void pastEndOnCreatePersistsEnd() throws Exception {postBody(create().put("endDate","2026-06-30")).andExpect(jsonPath("$.data.project.status").value("END"));}
    @Test void periodInUseRejectsBeforeWritesWithTargets() throws Exception {when(repository.periodUses(any())).thenReturn(List.of(new PeriodUse("ACT:assignment","2026-01")));putBody(edit().put("startDate","2026-02-01")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_PERIOD_IN_USE")).andExpect(jsonPath("$.details.targets[0].month").value("2026-01"));noWrites();}
    @Test void protectedDayRangeChangeRejectedWithoutTruncation() throws Exception {when(repository.protectedMonths(any(),any(),any())).thenReturn(List.of("202601"));putBody(edit().put("startDate","2026-01-02")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));noWrites();}
    @Test void manualEqualAutomaticStillCreatesBudgetHistory() throws Exception {putBody(edit().put("budgetMode","MANUAL").put("budgetAmount","80")).andExpect(jsonPath("$.data.project.budgetManual").value("Y"));verify(repository).insertHistory(argThat(h->h.change().code().equals("BUDGET")));}
    @Test void staleChildRevisionPreventsEveryMutation() throws Exception {var b=edit();child="changed-plan";putBody(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();}
    @Test void historySerialLimitRejectsBeforeBodyWrite() throws Exception {when(repository.greatestHistorySn(any())).thenReturn(99999);putBody(edit().put("name","changed")).andExpect(status().isConflict());noWrites();}
    @Test void unknownQueryRejectedOnWrites() throws Exception {mvc.perform(post("/api/projects").param("unknown","x").contentType("application/json").content(create().toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("unknown"));noWrites();}
    @Test void malformedJsonUsesProjectErrorWithoutGlobalHandlerRewrite() throws Exception {mvc.perform(post("/api/projects").header("X-Request-Id","project-json").contentType("application/json").content("{")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("body")).andExpect(jsonPath("$.requestId").value("project-json"));noWrites();}
    @Test void contractHistoryFailureRollsBackAndDoesNotExposeSql() throws Exception {doThrow(new IllegalStateException("SELECT secret_stack")).when(repository).insertHistory(any());putBody(edit().put("name","changed")).andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret_stack"))));verify(manager).rollback(any());verifyNoInteractions(invalidation);}
}
