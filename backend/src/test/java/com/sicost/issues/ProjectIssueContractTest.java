package com.sicost.issues;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
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
import static com.sicost.issues.ProjectIssueDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectIssueController.class)
@ContextConfiguration(classes=ProjectIssueController.class)
@Import({ProjectIssueService.class,WriteCoordinator.class,RevisionService.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProjectIssueContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean ProjectIssueRepository repository;
    @MockBean ProjectProgressAggregateRevision progress;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryWriter history;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    Project project;
    List<Row> rows;
    int version;
    static final LocalDate TODAY=LocalDate.of(2026,7,1);
    static final LocalDateTime REGISTERED=LocalDateTime.of(2026,6,1,9,0);
    @BeforeEach void setup() {
        project=new Project("P-0009","검증 프로젝트","ING",LocalDate.of(2027,12,31));
        rows=new ArrayList<>();rows.add(row(17,"OPEN",TODAY,"MID"));version=0;
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(users.userId()).thenReturn("admin");
        when(repository.required(anyString())).thenAnswer(c->{if(project==null)throw new ApiException(404,"NOT_FOUND","프로젝트가 없습니다.");return project;});
        when(repository.issues(anyString())).thenAnswer(c->List.copyOf(rows));
        when(repository.activeCode(anyString(),anyString())).thenReturn(true);
        when(progress.snapshot(anyString())).thenAnswer(c->{if(project==null)throw new ApiException(404,"NOT_FOUND","프로젝트가 없습니다.");return "snapshot:"+version;});
        when(repository.progress(anyString(),anyInt())).thenReturn(Map.of("progressRevision","after"));
        when(repository.insert(any())).thenAnswer(c->{Mutation m=c.getArgument(0);rows.add(changed(m,18,null));version++;return 18L;});
        doAnswer(c->{Mutation m=c.getArgument(0);Row old=rows.stream().filter(r->r.issueId()==m.issueId()).findFirst().orElseThrow();rows.remove(old);rows.add(changed(m,m.issueId(),old));version++;return null;}).when(repository).update(any());
    }
    Row row(long id,String status,LocalDate due,String importance) {return new Row(id,"P-0009","검증 프로젝트","원 제목",importance,importance,due,status,status,"원 조치","creator","최초 담당",REGISTERED,"editor","수정자",REGISTERED.plusDays(1));}
    Row changed(Mutation m,long id,Row old) {var i=m.input();return new Row(id,m.projectId(),"검증 프로젝트",i.title(),i.importance(),i.importance(),i.dueDate(),i.status(),i.status(),i.action(),old==null?m.actor():old.regUserId(),old==null?"관리자":old.regUserName(),old==null?m.now():old.regDttm(),old==null?null:m.actor(),old==null?null:"관리자",old==null?null:m.now());}
    ObjectNode body() {return json.createObjectNode().put("year",2026).put("expectedRevision",revisions.revision("snapshot:"+version)).put("title","원 제목").put("dueDate",TODAY.toString()).put("importance","MID").put("status","OPEN").put("action","원 조치");}
    org.springframework.test.web.servlet.ResultActions save(ObjectNode b,boolean update) throws Exception {return mvc.perform((update?put("/api/projects/P-0009/issues/17"):post("/api/projects/P-0009/issues")).contentType("application/json").content(b.toString()));}
    void noWrites() {verify(repository,never()).insert(any());verify(repository,never()).update(any());verifyNoInteractions(history,invalidation);}
    @Test void historyOnEndedProjectPreservesAllActorsAndAuditDates() throws Exception {
        project=new Project(project.projectId(),project.projectName(),"END",TODAY.minusDays(1));
        mvc.perform(get("/api/projects/P-0009/issues").param("status","ALL")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].registeredBy.userId").value("creator"))
            .andExpect(jsonPath("$.data.items[0].modifiedBy.userId").value("editor"))
            .andExpect(jsonPath("$.data.items[0].registeredAt").value("2026-06-01T09:00:00+09:00"));noWrites();
    }
    @Test void dueTodayIsOpenButNotOverdueAndClosedPastIsExcluded() throws Exception {
        rows.add(row(19,"CLOSE",TODAY.minusDays(4),"HIGH"));
        mvc.perform(get("/api/projects/P-0009/issues").param("status","ALL")).andExpect(status().isOk()).andExpect(jsonPath("$.data.openCount").value(1)).andExpect(jsonPath("$.data.items[0].overdue").value(false));noWrites();
    }
    @Test void dynamicStatusOpenCountsButOpenActiveMeansExactlyOpenAndAct() throws Exception {
        rows.add(row(20,"REVIEW",TODAY.minusDays(1),"HIGH"));
        mvc.perform(get("/api/projects/P-0009/issues")).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.openCount").value(2));
        mvc.perform(get("/api/projects/P-0009/issues").param("status","OVERDUE")).andExpect(jsonPath("$.data.items[0].issueId").value(20));noWrites();
    }
    @Test void filteredListAndTotalUseSamePopulationAndHighFirstDateThenId() throws Exception {
        rows.add(row(21,"OPEN",TODAY.minusDays(2),"HIGH"));rows.add(row(20,"OPEN",TODAY.minusDays(2),"HIGH"));
        mvc.perform(get("/api/projects/P-0009/issues").param("importance","HIGH")).andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.items[0].issueId").value(20));noWrites();
    }
    @ParameterizedTest @CsvSource({"status,unknown","size,30","page,0","page,1.0","sort,title","year,2026","today,2026-07-01"})
    void queryRejectsUnsupportedFieldsAndTypes(String field,String value) throws Exception {mvc.perform(get("/api/projects/P-0009/issues").param(field,value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrites();}
    @Test void duplicateQueryRejected() throws Exception {mvc.perform(get("/api/projects/P-0009/issues").param("status","ALL","CLOSED")).andExpect(status().isBadRequest());noWrites();}
    @Test void inactiveImportanceFilterRejected() throws Exception {when(repository.activeCode("IMPR","OFF")).thenReturn(false);mvc.perform(get("/api/projects/P-0009/issues").param("importance","OFF")).andExpect(status().isBadRequest());noWrites();}
    @Test void missingProjectIs404() throws Exception {project=null;mvc.perform(get("/api/projects/missing/issues")).andExpect(status().isNotFound());save(body(),false).andExpect(status().isNotFound());noWrites();}
    @ParameterizedTest @ValueSource(strings={"ING","PRE"})
    void createUsesProviderGeneratedIdAndTrimmedRawValues(String state) throws Exception {
        project=new Project(project.projectId(),project.projectName(),state,project.endDate());
        save(body().put("title","  새 이슈  ").put("dueDate","2025-01-01").put("action","   "),false)
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.issue.issueId").value(18)).andExpect(jsonPath("$.data.issue.title").value("새 이슈"))
            .andExpect(jsonPath("$.data.issue.action").isEmpty()).andExpect(jsonPath("$.data.issue.registeredBy.userId").value("admin"));
        verify(repository).insert(argThat(m->m.actor().equals("admin")&&m.now().equals(TODAY.atStartOfDay())));
        verify(locks).lock("PROJECT:P-0009");verify(history,atLeastOnce()).append(argThat(c->c.type().equals("NEW")&&c.targetKey().equals("18")));
    }
    @ParameterizedTest @ValueSource(strings={"assigneeId","regUserId","actor","projectId","registeredBy","registeredAt","modifiedAt","completedDate","signal","issueId"})
    void serverOwnedFieldsRejectedForCreateAndUpdate(String field) throws Exception {save(body().put(field,"injected"),false).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));save(body().put(field,"injected"),true).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @CsvSource({"title,BLANK","title,LONG","action,LONG","dueDate,2026-02-30","dueDate,2026-7-1","importance,OFF","status,OFF"})
    void validationHasFieldAndNoTruncationForBothWrites(String field,String value) throws Exception {
        if (value.equals("BLANK")) value="   ";if(value.equals("LONG"))value="가".repeat(field.equals("title")?101:501);
        when(repository.activeCode(anyString(),eq("OFF"))).thenReturn(false);
        save(body().put(field,value),false).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));
        save(body().put(field,value),true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrites();assertThat(rows.get(0).title()).isEqualTo("원 제목");
    }
    @ParameterizedTest @ValueSource(strings={"title","dueDate","importance","status","year","expectedRevision"})
    void requiredFieldsAreNotDefaulted(String field) throws Exception {var b=body();b.remove(field);save(b,false).andExpect(status().isBadRequest());noWrites();}
    @Test void unknownAdditionalActiveCodesAllowed() throws Exception {save(body().put("importance","URGENT").put("status","REVIEW"),false).andExpect(status().isOk()).andExpect(jsonPath("$.data.issue.status").value("REVIEW"));}
    @Test void generatedIdentifierExhaustionRollsBackWithoutAudit() throws Exception {doThrow(new ApiException(409,"ID_EXHAUSTED","식별자 소진")).when(repository).insert(any());save(body(),false).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ID_EXHAUSTED"));verify(manager).rollback(any());verifyNoInteractions(history,invalidation);}
    @Test void staleCreateResubmissionDoesNotDuplicate() throws Exception {var original=body();save(original,false).andExpect(status().isOk());clearInvocations(repository,history,invalidation);save(original,false).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();assertThat(rows).hasSize(2);}
    @Test void highOverdueCommitRequeriesSameSourceAndAuditsEachField() throws Exception {save(body().put("importance","HIGH").put("dueDate",TODAY.minusDays(1).toString()),false).andExpect(status().isOk());mvc.perform(get("/api/projects/P-0009/issues").param("status","OVERDUE")).andExpect(jsonPath("$.data.items[0].importance").value("HIGH")).andExpect(jsonPath("$.data.openCount").value(2));verify(history).append(argThat(c->c.item().equals("IMPR_CD")&&c.after().equals("HIGH")));}
    @ParameterizedTest @ValueSource(strings={"OPEN","ACT"})
    void reopenPreservesProjectOriginalActorAndRegistration(String state) throws Exception {rows.set(0,row(17,"CLOSE",TODAY,"MID"));save(body().put("status",state),true).andExpect(status().isOk()).andExpect(jsonPath("$.data.issue.status").value(state)).andExpect(jsonPath("$.data.issue.registeredBy.userId").value("creator")).andExpect(jsonPath("$.data.issue.modifiedBy.userId").value("admin"));assertThat(rows.get(0).regDttm()).isEqualTo(REGISTERED);}
    @ParameterizedTest @ValueSource(strings={"END","LOSE"})
    void terminalProjectCannotReopenOrCreate(String state) throws Exception {rows.set(0,row(17,"CLOSE",TODAY,"MID"));project=new Project(project.projectId(),project.projectName(),state,project.endDate());save(body(),true).andExpect(status().isConflict());save(body(),false).andExpect(status().isConflict());noWrites();assertThat(rows.get(0).status()).isEqualTo("CLOSE");}
    @Test void expiredIngIsReadOnly() throws Exception {project=new Project(project.projectId(),project.projectName(),"ING",TODAY.minusDays(1));save(body(),true).andExpect(status().isConflict());noWrites();}
    @ParameterizedTest @ValueSource(ints={2025,2027})
    void onlyCurrentYearEdits(int year) throws Exception {save(body().put("year",year),true).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("READ_ONLY_YEAR"));save(body().put("year",year),false).andExpect(status().isConflict());noWrites();}
    @Test void wrongProjectChildIs404() throws Exception {rows.clear();save(body(),true).andExpect(status().isNotFound());noWrites();}
    @Test void noOpKeepsAuditAndModificationTimeAndStaleCheckedFirst() throws Exception {save(body(),true).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false)).andExpect(jsonPath("$.data.message").value("바뀐 내용이 없습니다."));noWrites();assertThat(rows.get(0).modDttm()).isEqualTo(REGISTERED.plusDays(1));save(body().put("expectedRevision","old"),true).andExpect(status().isConflict());noWrites();}
    @Test void closeDecreasesOpenCountAndAuditsOnlyActualField() throws Exception {save(body().put("status","CLOSE"),true).andExpect(status().isOk());verify(history).append(argThat(c->c.type().equals("UPD")&&c.item().equals("ISSUE_STAT_CD")&&c.before().equals("OPEN")&&c.after().equals("CLOSE")));verifyNoMoreInteractions(history);mvc.perform(get("/api/projects/P-0009/issues")).andExpect(jsonPath("$.data.openCount").value(0));}
    @Test void auditFailureRollsBackWithoutSqlLeakOrInvalidation() throws Exception {doThrow(new IllegalStateException("secret_sql")).when(history).append(any());save(body().put("title","변경"),true).andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret_sql"))));verify(manager).rollback(any());verifyNoInteractions(invalidation);}
    @Test void malformedJsonIsFieldError() throws Exception {mvc.perform(post("/api/projects/P-0009/issues").contentType("application/json").content("{")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("body"));noWrites();}
}
