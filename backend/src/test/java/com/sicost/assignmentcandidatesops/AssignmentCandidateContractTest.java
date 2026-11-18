package com.sicost.assignmentcandidatesops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.assignments.*;
import com.sicost.assignmentsops.*;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.assignments.AssignmentMonthlyService.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.persistence.MasterIdService;
import com.sicost.projectsops.ProjectStatusDtos.ValueHistory;
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
import static com.sicost.assignmentcandidatesops.AssignmentCandidateDtos.*;
import static com.sicost.assignmentsops.AssignmentActionsDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest(AssignmentCandidateController.class)
@org.springframework.transaction.annotation.EnableTransactionManagement
@ContextConfiguration(classes=AssignmentCandidateController.class)
@Import({AssignmentCandidateService.class,AssignmentCandidateRepository.class,AssignmentService.class,AssignmentRepository.class,
    AssignmentActionsService.class,AssignmentActionsRepository.class,AssignmentMonthlyService.class,RevisionService.class,
    ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class AssignmentCandidateContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean AssignmentCandidateMapper candidates;
    @MockBean AssignmentActionsMapper mapper;
    @MockBean AssignmentMapper references;
    @MockBean AssignmentMonthlyMapper months;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper audit;
    @MockBean AggregateLockMapper locks;
    @MockBean SettlementSourceLocks sourceLocks;
    @MockBean CommitInvalidation invalidation;
    @MockBean MasterIdService ids;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    Origin origin;
    Map<String,Source> rows;
    Map<String,List<ValueHistory>> history;
    Map<String,List<StoredMonth>> monthRows;
    List<Event> events;
    Project project;
    LocalDate day=LocalDate.of(2026,7,1);
    @BeforeEach void setup() {
        rows=new LinkedHashMap<>();history=new HashMap<>();monthRows=new HashMap<>();events=new ArrayList<>();
        origin=new Origin("101","O-0101","P-101","D-101","DEV","CNVT","Y","ING",day.plusYears(1),null);
        project=new Project("P-101","프로젝트","ING","MO",day.minusYears(1),day.plusYears(1));
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));when(users.userId()).thenReturn("admin");
        when(candidates.origin("101")).thenAnswer(c->origin);
        when(candidates.snapshot(anyString())).thenAnswer(c->origin.toString());
        when(candidates.transition(any())).thenAnswer(c->{Mutation m=c.getArgument(0);origin=new Origin(origin.id(),origin.opportunityId(),origin.projectId(),origin.developerId(),origin.jobCode(),m.status(),origin.developerUseYn(),origin.projectStatus(),origin.projectEndDate(),m.now());return 1;});
        when(candidates.touchOpportunity(any())).thenReturn(1);when(audit.insert(any())).thenReturn(1);
        when(months.cashBase()).thenReturn("202501");when(months.source(anyString())).thenAnswer(c->rows.get(c.getArgument(0)));
        when(months.histories(anyString())).thenAnswer(c->history.getOrDefault(c.getArgument(0),List.of()));
        when(months.months(anyString())).thenAnswer(c->monthRows.getOrDefault(c.getArgument(0),List.of()));
        when(mapper.project(anyString())).thenAnswer(c->project);
        when(mapper.developer(anyString())).thenReturn(new Developer("D-101","개발자","M-101","FT","DRCT","PART-1","Y",day.minusYears(1),null));
        when(mapper.labels(anyString())).thenReturn(new Labels("프로젝트","개발자","M-101","개발"));
        when(mapper.snapshot(anyString())).thenAnswer(c->rows.get(c.getArgument(0))+"/"+history.get(c.getArgument(0))+"/"+monthRows.get(c.getArgument(0)));
        when(mapper.active(any())).thenReturn(true);when(mapper.price(any())).thenReturn(new BigDecimal("101"));when(mapper.totalMm(any())).thenReturn(BigDecimal.ZERO);when(mapper.mmLimit(anyString())).thenReturn(BigDecimal.ONE);
        when(mapper.insert(any())).thenAnswer(c->{Write w=c.getArgument(0);rows.put(w.source().id(),w.source());return 1;});
        when(mapper.event(any())).thenAnswer(c->{Event e=c.getArgument(0);events.add(e);history.computeIfAbsent(e.id(),k->new ArrayList<>()).add(new ValueHistory(e.serial(),e.type(),e.date(),e.mm(),e.price(),"N",null));return 1;});
        when(months.saveMonth(any())).thenAnswer(c->{MonthWrite w=c.getArgument(0);var v=w.value();monthRows.computeIfAbsent(w.source().id(),k->new ArrayList<>()).add(new StoredMonth(w.month(),v.inputDays(),new BigDecimal(v.monthlyMm()),v.appliedUnitPrice()==null?null:new BigDecimal(v.appliedUnitPrice()),new BigDecimal(v.salesAmount()),"N"));return 1;});
        when(ids.nextAssignmentId()).thenReturn("A-0102");
    }
    String revision(){return revisions.revision(origin.toString());}
    ObjectNode body(){return json.createObjectNode().put("year",2026).put("projectId","P-101").put("developerId","D-101").put("jobCode","DEV").put("startDate","2026-07-01").put("endDate","2026-07-31").put("contractMm","0.50").put("sourceTentId","101").put("expectedRevision",revision());}
    org.springframework.test.web.servlet.ResultActions post(String action,ObjectNode body) throws Exception {return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/assignment-candidates/101/"+action).contentType("application/json").content(body.toString()));}
    void noWrites(){verify(mapper,never()).insert(any());verify(candidates,never()).transition(any());verify(audit,never()).insert(any());}
    void state(String status){origin=new Origin(origin.id(),origin.opportunityId(),origin.projectId(),origin.developerId(),origin.jobCode(),status,origin.developerUseYn(),origin.projectStatus(),origin.projectEndDate(),origin.processedAt());}
    @Test void confirmAuthRejectsForgedActorBeforeAssignmentCreation() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/assignment-candidates/101/confirm").contentType("application/json").content(body().put("actor","other").toString()))
            .andExpect(DurableOpenApi.response("POST","/api/assignment-candidates/{id}/confirm",400))
            .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();
    }
    @Test void dropValidationRequiresRevisionAndKeepsConvertedCandidate() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/assignment-candidates/101/drop").contentType("application/json").content("{}"))
            .andExpect(DurableOpenApi.response("POST","/api/assignment-candidates/{id}/drop",400));
        assertThat(origin.status()).isEqualTo("CNVT");noWrites();
    }
    @Test void publicConfirmUsesExistingCreationWithInputMonthlyAndFieldAudit() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/assignment-candidates/101/confirm").contentType("application/json").content(body().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.assignmentId").value("A-0102")).andExpect(jsonPath("$.data.tentativeId").value("101")).andExpect(jsonPath("$.data.changed").value(true));
        assertThat(origin.status()).isEqualTo("DONE");assertThat(origin.processedAt()).isNotNull();assertThat(rows.get("A-0102").price()).isEqualByComparingTo("101");assertThat(events).singleElement().satisfies(e->{assertThat(e.type()).isEqualTo("INPUT");assertThat(e.actor()).isEqualTo("admin");});assertThat(monthRows.get("A-0102")).hasSize(1);
        verify(audit).insert(argThat(e->"sourceTentId".equals(e.item())&&"101".equals(e.after())));
        verify(audit).insert(argThat(e->"assignmentId".equals(e.item())&&"A-0102".equals(e.after())));
        verify(candidates).touchOpportunity(argThat(m->m.actor().equals("admin")));
    }
    @Test void successfulDoneReplayReturnsLinkedIdBeforeStaleAndDoesNotWrite() throws Exception {
        ObjectNode request=body();state("DONE");when(candidates.confirmedAssignment("101")).thenReturn("A-0102");
        post("confirm",request).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false)).andExpect(jsonPath("$.data.assignmentId").value("A-0102"));noWrites();
    }
    @Test void doneWithoutLinkedAuditCannotCreateAgain() throws Exception {state("DONE");post("confirm",body()).andExpect(status().isConflict());noWrites();}
    @Test void dropIsPublicAndCreatesNoAssignment() throws Exception {mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/assignment-candidates/101/drop").contentType("application/json").content(json.createObjectNode().put("expectedRevision",revision()).toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.opportunityId").value("O-0101"));assertThat(origin.status()).isEqualTo("DROP");verify(mapper,never()).insert(any());verify(candidates,never()).confirmedAssignment(anyString());}
    @Test void latestDropReplayHasNoAuditOrTimestampChange() throws Exception {state("DROP");Origin before=origin;post("drop",json.createObjectNode().put("expectedRevision",revision())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));assertThat(origin).isEqualTo(before);noWrites();}
    @ParameterizedTest @ValueSource(strings={"ACT","REL","DONE"}) void dropOtherStatesAre409(String state) throws Exception {state(state);post("drop",json.createObjectNode().put("expectedRevision",revision())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"ACT","REL","DROP"}) void confirmOtherStatesAre409(String state) throws Exception {state(state);post("confirm",body()).andExpect(status().isConflict());noWrites();}
    @ParameterizedTest @ValueSource(strings={"year","projectId","developerId","jobCode","startDate","endDate","sourceTentId","expectedRevision"}) void eachRequiredFieldIsValidated(String field) throws Exception {ObjectNode b=body();b.remove(field);post("confirm",b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.field").isNotEmpty()).andExpect(jsonPath("$.requestId").isNotEmpty());noWrites();}
    @ParameterizedTest @ValueSource(strings={"year","projectId","developerId","jobCode","startDate","endDate","sourceTentId","expectedRevision","contractMm","billingUnitPrice"}) void wrongTypesAreNotCoerced(String field) throws Exception {post("confirm",body().put(field,true)).andExpect(status().isBadRequest());noWrites();}
    @Test void dropRequiresStringRevision() throws Exception {post("drop",json.createObjectNode()).andExpect(status().isBadRequest());post("drop",json.createObjectNode().put("expectedRevision",23)).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"actor","status","regUserId","assignmentId"}) void serverFieldsAreRejectedForBothOperations(String field) throws Exception {post("confirm",body().put(field,"injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));post("drop",json.createObjectNode().put("expectedRevision",revision()).put(field,"injected")).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"projectId","developerId","jobCode","sourceTentId"}) void candidateIdentityCannotBeChanged(String field) throws Exception {post("confirm",body().put(field,"other")).andExpect(status().isBadRequest());noWrites();}
    @Test void candidateNeverPermitsSubOrContractDeveloper() throws Exception {post("confirm",body().put("inputType","SUB")).andExpect(status().isBadRequest());post("confirm",body().put("contractDeveloperId","D-102")).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"0","0.001","1.01","1e0","-1"}) void invalidMmDoesNotStore(String mm) throws Exception {post("confirm",body().put("contractMm",mm)).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"0","1,00","1a0","1000000000000000","1e2"}) void invalidPriceDoesNotStore(String price) throws Exception {post("confirm",body().put("billingUnitPrice",price)).andExpect(status().isBadRequest());noWrites();}
    @Test void invalidDateAndReversedPeriodDoNotStore() throws Exception {post("confirm",body().put("startDate","2026-02-30")).andExpect(status().isBadRequest());post("confirm",body().put("startDate","2026-08-01")).andExpect(status().isBadRequest());noWrites();}
    @Test void userCanReviewPriceMmAndPeriodWithoutUsingOpportunityPrice() throws Exception {post("confirm",body().put("billingUnitPrice","1,234").put("contractMm","0.75").put("startDate","2026-07-10")).andExpect(status().isOk());assertThat(rows.get("A-0102").price()).isEqualByComparingTo("1234");assertThat(rows.get("A-0102").contractMm()).isEqualByComparingTo("0.75");}
    @Test void protectedMonthsReuseExistingCreationProtection() throws Exception {when(months.closeStatus("202607")).thenReturn("CLOSE");post("confirm",body()).andExpect(status().isOk());verify(months,never()).saveMonth(any());assertThat(events).singleElement().satisfies(e->assertThat(e.closeCross()).isEqualTo("Y"));}
    @Test void stoppedDeveloperIsStateConflictBeforeAssignmentCreate() throws Exception {origin=new Origin("101","O-0101","P-101","D-101","DEV","CNVT","N","ING",day.plusYears(1),null);post("confirm",body()).andExpect(status().isConflict());noWrites();}
    @ParameterizedTest @ValueSource(strings={"END","LOSE"}) void endedProjectIsStateConflict(String state) throws Exception {origin=new Origin("101","O-0101","P-101","D-101","DEV","CNVT","Y",state,day.plusYears(1),null);post("confirm",body()).andExpect(status().isConflict());noWrites();}
    @ParameterizedTest @ValueSource(strings={"confirm","drop"}) void staleRequestDoesNotPartiallyWrite(String action) throws Exception {ObjectNode b=action.equals("confirm")?body():json.createObjectNode();post(action,b.put("expectedRevision","old")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"confirm","drop"}) void missingCandidateReturns404(String action) throws Exception {ObjectNode b=action.equals("confirm")?body():json.createObjectNode().put("expectedRevision",revision());when(candidates.origin("101")).thenReturn(null);post(action,b).andExpect(status().isNotFound());noWrites();}
    @ParameterizedTest @ValueSource(strings={"confirm","drop"}) void auditAffectedZeroRollsBack(String action) throws Exception {when(audit.insert(argThat(e->e!=null&&e.tableName().equals("TB_SL_TENT_ASGN")))).thenReturn(0);post(action,action.equals("confirm")?body():json.createObjectNode().put("expectedRevision",revision())).andExpect(status().isInternalServerError());verify(manager,atLeastOnce()).rollback(any());}
    @ParameterizedTest @ValueSource(strings={"confirm","drop"}) void auditExceptionRollsBackWithoutSqlLeak(String action) throws Exception {when(audit.insert(any())).thenThrow(new IllegalStateException("private SQL credential"));post(action,action.equals("confirm")?body():json.createObjectNode().put("expectedRevision",revision())).andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));verify(manager,atLeastOnce()).rollback(any());}
}
