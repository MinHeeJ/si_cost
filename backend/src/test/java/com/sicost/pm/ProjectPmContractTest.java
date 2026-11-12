package com.sicost.pm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.budget.*;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
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
import static com.sicost.pm.ProjectPmDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectPmController.class)
@ContextConfiguration(classes=ProjectPmController.class)
@Import({ProjectPmService.class,ProjectPmRepository.class,ProjectBudgetRepository.class,WriteCoordinator.class,
    RevisionService.class,ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProjectPmContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean ProjectPmMapper mapper;
    @MockBean ProjectBudgetMapper aggregate;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper audit;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    ProjectSource project;
    List<Scope> scopes;
    List<PmHistory> events;
    String children;
    @BeforeEach void setup() {
        project=new ProjectSource("P-PM","ING",LocalDate.of(2027,12,31));
        scopes=new ArrayList<>();events=new ArrayList<>();children="persisted-body-parts-plans-expenses-distribution";
        when(users.userId()).thenReturn("admin");
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c -> new SimpleTransactionStatus());
        when(mapper.find(anyString())).thenAnswer(c -> project);
        when(mapper.scopes(anyString())).thenAnswer(c -> List.copyOf(scopes));
        when(mapper.eligible(anyString())).thenReturn(true);
        when(mapper.greatestScopeSn(anyString())).thenReturn(12);
        when(aggregate.snapshot(anyString())).thenAnswer(c -> project==null?null:project+children+scopes);
        when(aggregate.greatestHistorySn(anyString())).thenReturn(4);
        when(mapper.insertScope(any())).thenAnswer(c -> { ScopeWrite w=c.getArgument(0);scopes.add(new Scope(w.userId(),w.scopeSn(),w.projectId(),"선택 사용자"));return 1; });
        when(mapper.deleteScope(any())).thenAnswer(c -> { ScopeWrite w=c.getArgument(0);return scopes.removeIf(s -> s.userId().equals(w.userId()) && s.scopeSn()==w.scopeSn() && s.projectId().equals(w.projectId()))?1:0; });
        when(mapper.insertHistory(any())).thenAnswer(c -> {events.add(c.getArgument(0));return 1;});
    }
    String revision() { return revisions.revision(project+children+scopes); }
    ObjectNode body() { return json.createObjectNode().put("userId","chosen").put("expectedRevision",revision()); }
    org.springframework.test.web.servlet.ResultActions save(ObjectNode input) throws Exception {
        return mvc.perform(put("/api/projects/P-PM/pm").contentType("application/json").content(input.toString()));
    }
    org.springframework.test.web.servlet.ResultActions remove(ObjectNode input) throws Exception {
        return mvc.perform(delete("/api/projects/P-PM/pm").contentType("application/json").content(input.toString()));
    }
    void noWrites() {verify(mapper,never()).insertScope(any());verify(mapper,never()).deleteScope(any());verify(mapper,never()).insertHistory(any());verifyNoInteractions(audit,invalidation);}
    void legacy() {scopes.add(new Scope("old-a",2,"P-PM","기존 A"));scopes.add(new Scope("old-b",5,"P-PM","기존 B"));}
    @Test void publicReadReturnsAllLegacyConnectionsAndCompleteRevisionWithoutChanges() throws Exception {
        legacy();String before=revision();
        mvc.perform(get("/api/projects/P-PM/pm").header("X-Request-Id","pm-read"))
            .andExpect(status().isOk()).andExpect(header().string("X-Request-Id","pm-read"))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.items.length()").value(2)).andExpect(jsonPath("$.data.items[1].userId").value("old-b"))
            .andExpect(jsonPath("$.data.items[1].scopeSn").value(5)).andExpect(jsonPath("$.data.revision").value(before));
        verify(users).currentUser();assertThat(revision()).isEqualTo(before);noWrites();
    }
    @Test void emptyReadReturnsNoVirtualRows() throws Exception {mvc.perform(get("/api/projects/P-PM/pm")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());noWrites();}
    @Test void nonexistentProjectIs404() throws Exception {project=null;mvc.perform(get("/api/projects/missing/pm")).andExpect(status().isNotFound());noWrites();}
    @Test void absentProjectOnPutAndDeleteIs404WithoutHistory() throws Exception {
        var input=body();project=null;save(input).andExpect(status().isNotFound());remove(input).andExpect(status().isNotFound());noWrites();
    }
    @Test void unknownGetJsonFieldIsRejected() throws Exception {
        mvc.perform(get("/api/projects/P-PM/pm").contentType("application/json").content("{\"year\":2026}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("year"));noWrites();
    }
    @Test void putAndDeleteRejectUnregisteredQueryFields() throws Exception {
        mvc.perform(put("/api/projects/P-PM/pm").param("year","2026").contentType("application/json").content(body().toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("year"));
        mvc.perform(delete("/api/projects/P-PM/pm").param("year","2024").contentType("application/json").content(body().toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("year"));noWrites();
    }
    @Test void longProjectIdIsFieldError() throws Exception {mvc.perform(get("/api/projects/"+"a".repeat(21)+"/pm")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("id"));verifyNoInteractions(mapper,aggregate);}
    @ParameterizedTest @ValueSource(strings={"year","q","month","purpose"})
    void unknownGetQueryIsInvalidInput(String field) throws Exception {mvc.perform(get("/api/projects/P-PM/pm").param(field,"x")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value(field));noWrites();}
    @Test void temporaryAdminWithoutPmRoleCanAssignEligibleTarget() throws Exception {
        save(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true))
            .andExpect(jsonPath("$.data.value.items[0].userId").value("chosen")).andExpect(jsonPath("$.data.value.items[0].scopeSn").value(13));
        assertThat(events).singleElement().satisfies(e -> {assertThat(e.before()).isNull();assertThat(e.after()).contains("chosen");assertThat(e.historySn()).isEqualTo(5);assertThat(e.actor()).isEqualTo("admin");});
        verify(audit).insert(argThat(h -> h.type().equals("NEW") && h.tableName().equals("TB_SY_USER_SCOPE") && h.userId().equals("admin")));
        mvc.perform(get("/api/projects/P-PM/pm")).andExpect(jsonPath("$.data.revision").value(revision()));
    }
    @Test void preProjectAllowsAssignment() throws Exception {project=new ProjectSource(project.id(),"PRE",project.endDate());save(body()).andExpect(status().isOk());}
    @Test void nonPmTargetIs409NotCaller403AndCreatesNothing() throws Exception {when(mapper.eligible("chosen")).thenReturn(false);save(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PM_QUALIFICATION_REQUIRED"));noWrites();}
    @Test void samePmIsNoOpPreservingHistoryAndAuditTimes() throws Exception {
        scopes.add(new Scope("chosen",7,"P-PM","선택 사용자"));String before=revision();save(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false)).andExpect(jsonPath("$.data.revision").value(before));assertThat(revision()).isEqualTo(before);noWrites();
    }
    @Test void explicitReplacementCollapsesAllLegacyPmAndWritesOneContractEvent() throws Exception {
        legacy();save(body().put("replaceConfirmed",true)).andExpect(status().isOk()).andExpect(jsonPath("$.data.value.items.length()").value(1));
        assertThat(scopes).singleElement().extracting(Scope::userId).isEqualTo("chosen");
        assertThat(events).singleElement().satisfies(e -> {assertThat(e.before()).contains("old-a","old-b");assertThat(e.after()).contains("chosen");});
        verify(audit,times(3)).insert(any());verify(mapper,times(2)).deleteScope(any());
    }
    @Test void multiPmIncludingChosenStillRequiresExplicitReplacement() throws Exception {
        legacy();scopes.add(new Scope("chosen",7,"P-PM","선택 사용자"));save(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.field").value("replaceConfirmed"));noWrites();
    }
    @Test void otherPmWithoutConfirmationIs409AndUnchanged() throws Exception {legacy();String before=revision();save(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PM_REPLACEMENT_REQUIRED"));assertThat(revision()).isEqualTo(before);noWrites();}
    @ParameterizedTest @ValueSource(strings={"END","LOSE"})
    void endedOrLostProjectCannotAssign(String state) throws Exception {project=new ProjectSource(project.id(),state,project.endDate());save(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATUS"));noWrites();}
    @Test void effectiveEndedProjectCannotAssignWithoutUpdatingSource() throws Exception {project=new ProjectSource(project.id(),"ING",LocalDate.of(2026,6,14));save(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATUS"));assertThat(project.status()).isEqualTo("ING");noWrites();}
    @Test void todayEndStillAllowsAssignment() throws Exception {project=new ProjectSource(project.id(),"ING",LocalDate.of(2026,6,15));save(body()).andExpect(status().isOk());}
    @ParameterizedTest @ValueSource(strings={"END","LOSE"})
    void pastEndedOrLostConnectionRemovalNeedsNeitherYearNorTargetQualification(String state) throws Exception {
        project=new ProjectSource(project.id(),state,LocalDate.of(2024,12,31));legacy();scopes.add(new Scope("chosen",7,"P-PM","선택 사용자"));
        remove(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.value.items.length()").value(2));
        verify(mapper,never()).eligible(anyString());verify(mapper,never()).insertScope(any());
        verify(mapper).deleteScope(argThat(w -> w.userId().equals("chosen") && w.scopeSn()==7 && w.projectId().equals("P-PM")));
        verify(audit).insert(argThat(h -> h.type().equals("DEL")));assertThat(events).hasSize(1);
    }
    @Test void removingAbsentUserIsNoOpAndDoesNotClearOthers() throws Exception {legacy();remove(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));assertThat(scopes).hasSize(2);noWrites();}
    @ParameterizedTest @ValueSource(strings={"userId","expectedRevision"})
    void requiredFieldsOnPutAre400(String field) throws Exception {var b=body();b.remove(field);save(b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value(field));noWrites();}
    @ParameterizedTest @ValueSource(strings={"userId","expectedRevision"})
    void requiredFieldsOnDeleteAre400(String field) throws Exception {var b=body();b.remove(field);remove(b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrites();}
    @ParameterizedTest @ValueSource(strings={"year","actor","revision","scopeSn"})
    void unknownPutBodyIs400(String field) throws Exception {save(body().put(field,"x")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrites();}
    @Test void deleteRejectsYearAndReplacementFlag() throws Exception {
        remove(body().put("year",2024)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("year"));
        remove(body().put("replaceConfirmed",true)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("replaceConfirmed"));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"", "   ", "\u00a0"})
    void blankUserIsRejected(String value) throws Exception {save(body().put("userId",value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("userId"));noWrites();}
    @Test void longUserAndWrongTypesAreRejected() throws Exception {
        save(body().put("userId","a".repeat(31))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("userId"));
        save(body().put("userId",3)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("userId"));
        save(body().put("replaceConfirmed","true")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("replaceConfirmed"));
        save(body().put("expectedRevision",3)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("expectedRevision"));noWrites();
    }
    @Test void trimUserBeforeLockingAndStoring() throws Exception {save(body().put("userId"," chosen ")).andExpect(status().isOk());verify(locks).lock("USER:chosen");assertThat(scopes.get(0).userId()).isEqualTo("chosen");}
    @Test void malformedJsonIsInvalidInputWithSafeRequestId() throws Exception {
        mvc.perform(put("/api/projects/P-PM/pm").header("X-Request-Id","pm-invalid").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("body")).andExpect(jsonPath("$.requestId").value("pm-invalid"));noWrites();
    }
    @Test void stalePutRevisionIncludesOtherChildren() throws Exception {var b=body();children+="concurrent-plan";save(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();verify(manager).rollback(any());}
    @Test void staleDeleteLeavesAllConnections() throws Exception {legacy();var b=body();scopes.add(new Scope("chosen",7,"P-PM","선택 사용자"));remove(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));assertThat(scopes).hasSize(3);noWrites();}
    @Test void locksAreGloballySortedAndQualificationRunsAfterAllAffectedUsers() throws Exception {
        legacy();save(body().put("replaceConfirmed",true)).andExpect(status().isOk());
        var order=inOrder(locks,aggregate,mapper);order.verify(locks).lock("PROJECT:P-PM");order.verify(locks).lock("USER:chosen");order.verify(locks).lock("USER:old-a");order.verify(locks).lock("USER:old-b");order.verify(aggregate).snapshot("P-PM");order.verify(mapper).scopes("P-PM");order.verify(mapper).find("P-PM");order.verify(mapper).eligible("chosen");
    }
    @Test void changedMembershipAfterDiscoveryIsStaleRatherThanTakingOutOfOrderLocks() throws Exception {
        when(mapper.scopes(anyString())).thenReturn(List.of(),List.of(new Scope("new-member",1,"P-PM","새 담당")));
        save(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();verify(locks,never()).lock("USER:new-member");
    }
    @Test void scopeSerialLimitIs409BeforeAnyDelete() throws Exception {legacy();when(mapper.greatestScopeSn("chosen")).thenReturn(99999);save(body().put("replaceConfirmed",true)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SERIAL_LIMIT"));noWrites();}
    @Test void historySerialLimitIs409BeforeMutation() throws Exception {when(aggregate.greatestHistorySn(anyString())).thenReturn(99999);save(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SERIAL_LIMIT"));noWrites();}
    @Test void auditFailureRollsBackAndDoesNotInvalidateOrLeakSql() throws Exception {
        doThrow(new IllegalStateException("private SQL stack")).when(audit).insert(any());
        save(body()).andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
        verify(manager).rollback(any());verifyNoInteractions(invalidation);
    }
}
