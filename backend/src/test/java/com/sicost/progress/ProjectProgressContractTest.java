package com.sicost.progress;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
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
import static com.sicost.progress.ProjectProgressDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectProgressController.class)
@ContextConfiguration(classes=ProjectProgressController.class)
@Import({ProjectProgressService.class,ProjectProgressQueryService.class,WriteCoordinator.class,RevisionService.class,
    ProjectProgressAggregateRevision.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProjectProgressContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @Autowired ProjectProgressView progressView;
    @MockBean ProjectProgressRepository repository;
    @MockBean ProjectProgressRevisionMapper snapshots;
    @MockBean ProjectProgressCostSource costs;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryWriter history;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    ProjectRow project;
    List<StepRow> steps;
    List<MemoRow> memos;
    List<IssueRow> issues;
    LocalDate today=LocalDate.of(2026,7,1);
    LocalDateTime created=LocalDateTime.of(2026,1,1,0,0);
    String root="/api/projects/P-0009/progress";
    @Test void issueWritesResolveTheProductionProgressView() {
        var beans=new org.springframework.beans.factory.support.StaticListableBeanFactory();
        beans.addBean("progress",progressView);
        var issuesRepository=new com.sicost.issues.ProjectIssueRepository(mock(com.sicost.issues.ProjectIssueMapper.class),beans.getBeanProvider(ProjectProgressView.class));
        assertThat(issuesRepository.progress("P-0009",2026)).isInstanceOfSatisfying(Data.class,data->{
            assertThat(data.projectId()).isEqualTo("P-0009");
            assertThat(data.progressRevision()).isNotBlank();
            assertThat(data.year()).isEqualTo(2026);
        });
        noWrites();
    }
    @Test void literalProgressReadAndValidationAreReadOnly() throws Exception {
        mvc.perform(get("/api/projects/P-0009/progress"))
            .andExpect(DurableOpenApi.response("GET","/api/projects/{id}/progress",200))
            .andExpect(jsonPath("$.data.projectId").value("P-0009"))
            .andExpect(jsonPath("$.data.progressRevision").isString());
        mvc.perform(get("/api/projects/P-0009/progress").param("actor","other"))
            .andExpect(DurableOpenApi.response("GET","/api/projects/{id}/progress",400))
            .andExpect(jsonPath("$.field").value("actor"));
        noWrites();
    }
    @Test void literalHistoryHappyAndForeignKeyValidationHaveNoSideEffects() throws Exception {
        mvc.perform(get("/api/projects/P-0009/progress/history"))
            .andExpect(DurableOpenApi.response("GET","/api/projects/{id}/progress/history",200))
            .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/projects/P-0009/progress/history").param("targetTable","TB_PJ_STEP").param("targetKey","P-0010:1"))
            .andExpect(DurableOpenApi.response("GET","/api/projects/{id}/progress/history",400))
            .andExpect(jsonPath("$.field").value("targetKey"));noWrites();
    }
    @Test void literalInitializationCommitsOnceAndRejectsStaleRevision() throws Exception {
        var body=write();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/projects/P-0009/progress/steps/initialize").contentType("application/json").content(body.toString()))
            .andExpect(DurableOpenApi.response("POST","/api/projects/{id}/progress/steps/initialize",200))
            .andExpect(jsonPath("$.data.changed").value(true));
        verify(repository,times(7)).insertStep(argThat(w->w.actor().equals("admin")));
        verify(manager).commit(any());clearInvocations(repository,history,invalidation);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/projects/P-0009/progress/steps/initialize").contentType("application/json").content(body.toString()))
            .andExpect(DurableOpenApi.response("POST","/api/projects/{id}/progress/steps/initialize",409))
            .andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();
    }
    @Test void literalPreviewHasNoSideEffectAndRejectsDuplicateSteps() throws Exception {
        initialized();var body=dates(true);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/projects/P-0009/progress/steps/preview").contentType("application/json").content(body.toString()))
            .andExpect(DurableOpenApi.response("POST","/api/projects/{id}/progress/steps/preview",200))
            .andExpect(jsonPath("$.data.steps.length()").value(7));
        ((ObjectNode)body.get("steps").get(1)).put("stepSn",1);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/projects/P-0009/progress/steps/preview").contentType("application/json").content(body.toString()))
            .andExpect(DurableOpenApi.response("POST","/api/projects/{id}/progress/steps/preview",400))
            .andExpect(jsonPath("$.field").value("steps"));noWrites();
    }
    @Test void literalStepSaveCommitsDateFieldsAndRejectsStaleRevision() throws Exception {
        initialized();var body=dates(false);((ObjectNode)body.get("steps").get(0)).put("endDate","2026-01-11");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/projects/P-0009/progress/steps").contentType("application/json").content(body.toString()))
            .andExpect(DurableOpenApi.response("PUT","/api/projects/{id}/progress/steps",200))
            .andExpect(jsonPath("$.data.progress.steps[1].startDate").value("2026-01-12"));
        verify(history).append(argThat(c->c.item().equals("PLAN_END_DT")&&c.after().equals("2026-01-11")));
        verify(manager).commit(any());clearInvocations(repository,history,invalidation);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/projects/P-0009/progress/steps").contentType("application/json").content(body.toString()))
            .andExpect(DurableOpenApi.response("PUT","/api/projects/{id}/progress/steps",409))
            .andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();
    }
    @Test void literalCompletionCommitsServerDateAndRejectsInjectedActor() throws Exception {
        initialized();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/projects/P-0009/progress/steps/1/complete").contentType("application/json").content(write().toString()))
            .andExpect(DurableOpenApi.response("POST","/api/projects/{id}/progress/steps/{stepSn}/complete",200))
            .andExpect(jsonPath("$.data.progress.steps[0].completedDate").value("2026-07-01"));
        verify(repository).complete(argThat(w->w.actor().equals("admin")));verify(manager).commit(any());
        clearInvocations(repository,history,invalidation);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/projects/P-0009/progress/steps/1/complete").contentType("application/json").content(write().put("actor","other").toString()))
            .andExpect(DurableOpenApi.response("POST","/api/projects/{id}/progress/steps/{stepSn}/complete",400))
            .andExpect(jsonPath("$.field").value("actor"));noWrites();
    }
    @Test void literalMemoCreateCommitsExplicitZeroAndRejectsForgedWriter() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/projects/P-0009/progress/week-memos").contentType("application/json").content(write().put("actualProgress",0).toString()))
            .andExpect(DurableOpenApi.response("POST","/api/projects/{id}/progress/week-memos",200))
            .andExpect(jsonPath("$.data.memo.actualProgress").value(0))
            .andExpect(jsonPath("$.data.memo.registeredBy.userId").value("admin"));
        verify(repository).insertMemo(any());verify(manager).commit(any());clearInvocations(repository,history,invalidation);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/projects/P-0009/progress/week-memos").contentType("application/json").content(write().put("registeredBy","other").toString()))
            .andExpect(DurableOpenApi.response("POST","/api/projects/{id}/progress/week-memos",400))
            .andExpect(jsonPath("$.field").value("registeredBy"));noWrites();
    }
    @Test void literalMemoUpdatePreservesWriterAndRejectsStaleRevision() throws Exception {
        memos.add(memo(1,today.minusDays(1),20));var body=write().put("thisWeek","수정").put("actualProgress",30);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/projects/P-0009/progress/week-memos/1").contentType("application/json").content(body.toString()))
            .andExpect(DurableOpenApi.response("PUT","/api/projects/{id}/progress/week-memos/{memoSn}",200))
            .andExpect(jsonPath("$.data.memo.registeredBy.userId").value("another"))
            .andExpect(jsonPath("$.data.memo.modifiedBy.userId").value("admin"));
        verify(repository).updateMemo(any());verify(manager).commit(any());clearInvocations(repository,history,invalidation);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/projects/P-0009/progress/week-memos/1").contentType("application/json").content(body.toString()))
            .andExpect(DurableOpenApi.response("PUT","/api/projects/{id}/progress/week-memos/{memoSn}",409))
            .andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();
    }
    @BeforeEach void setup() {
        project=project("TK","ING",LocalDate.of(2026,1,1),LocalDate.of(2026,7,19));steps=new ArrayList<>();memos=new ArrayList<>();issues=new ArrayList<>();
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(repository.required(anyString(),any())).thenAnswer(c->{if(project==null)throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");return project;});
        when(snapshots.snapshot(anyString())).thenAnswer(c->project==null?null:raw());
        when(repository.steps(anyString())).thenAnswer(c->List.copyOf(steps));when(repository.memos(anyString())).thenAnswer(c->List.copyOf(memos));when(repository.issues(anyString())).thenAnswer(c->List.copyOf(issues));
        when(repository.pm(anyString())).thenReturn(List.of(new Actor("admin","관리자")));
        when(repository.templates()).thenAnswer(c->templates());when(repository.history(anyString())).thenReturn(List.of());
        when(costs.load(any(),any())).thenAnswer(c->new Cost(new BigDecimal("10"),new Completeness(true,true,false,List.of(),0,"2026-01","2026-07",null)));
        doAnswer(c->{var w=(StepWrite)c.getArgument(0);steps.add(w.row());return null;}).when(repository).insertStep(any());
        doAnswer(c->{StepRow row=((StepWrite)c.getArgument(0)).row();steps.replaceAll(old->old.stepSn()==row.stepSn()?row:old);return null;}).when(repository).updateDates(any());
        doAnswer(c->{StepRow row=((StepWrite)c.getArgument(0)).row();steps.replaceAll(old->old.stepSn()==row.stepSn()?row:old);return null;}).when(repository).complete(any());
        doAnswer(c->{var w=(MemoWrite)c.getArgument(0);var r=w.row();memos.add(new MemoRow(r.projectId(),r.memoSn(),r.writtenDate(),r.thisWeek(),r.nextWeek(),r.request(),r.actualProgress(),w.actor(),"관리자",w.now(),null,null,null));return null;}).when(repository).insertMemo(any());
        doAnswer(c->{var w=(MemoWrite)c.getArgument(0);var r=w.row();memos.replaceAll(old->old.memoSn()==r.memoSn()?new MemoRow(r.projectId(),r.memoSn(),r.writtenDate(),r.thisWeek(),r.nextWeek(),r.request(),r.actualProgress(),r.registeredBy(),r.registeredName(),r.registeredAt(),w.actor(),"관리자",w.now()):old);return null;}).when(repository).updateMemo(any());
    }
    ProjectRow project(String type,String status,LocalDate start,LocalDate end){return new ProjectRow("P-0009","진행 프로젝트",type,status,start,end,new BigDecimal("1000"),created,"HQ","본사",true);}
    String raw(){return project.contractType()+project.sourceStatus()+project.startDate()+project.endDate()+steps+memos+issues;}
    ObjectNode write(){return json.createObjectNode().put("year",2026).put("expectedRevision",revisions.revision(raw()));}
    List<Template> templates(){String[] weights={"5","15","20","35","15","5","5"};var codes=ProjectProgressCalculation.codes("TK");var result=new ArrayList<Template>();for(int i=0;i<codes.size();i++)result.add(new Template(codes.get(i),codes.get(i),weights[i],"TK,IN","Y","N",i));return result;}
    void initialized(){steps.addAll(ProjectProgressCalculation.initialize(project,templates()));}
    MemoRow memo(int sn,LocalDate date,Integer actual){return new MemoRow(project.projectId(),sn,date,"기록",null,null,actual,"another","다른 작성자",created,null,null,null);}
    ObjectNode dates(boolean preview){var b=preview?json.createObjectNode().put("year",2026):write();var a=b.putArray("steps");steps.forEach(s->a.addObject().put("stepSn",s.stepSn()).put("endDate",s.endDate().toString()));return b;}
    org.springframework.test.web.servlet.ResultActions post(String suffix,ObjectNode b)throws Exception{return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(root+suffix).contentType("application/json").content(b.toString()));}
    org.springframework.test.web.servlet.ResultActions put(String suffix,ObjectNode b)throws Exception{return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(root+suffix).contentType("application/json").content(b.toString()));}
    void noWrites(){verify(repository,never()).insertStep(any());verify(repository,never()).updateDates(any());verify(repository,never()).complete(any());verify(repository,never()).insertMemo(any());verify(repository,never()).updateMemo(any());verifyNoInteractions(history,invalidation);}
    @Test void getUsesWrittenOrderAndNonNullPreviousZeroAndIncludesWholeChildren()throws Exception{
        memos.add(memo(1,today.minusDays(1),0));memos.add(memo(2,today,null));
        mvc.perform(get(root)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status.actualProgress").value("0.0"))
            .andExpect(jsonPath("$.data.status.lastUpdatedDate").value("2026-07-01")).andExpect(jsonPath("$.data.memos[0].memoSn").value(2))
            .andExpect(jsonPath("$.data.steps").isEmpty()).andExpect(jsonPath("$.data.issues").isArray()).andExpect(jsonPath("$.data.progressRevision").isString())
            .andExpect(jsonPath("$.data.status.planBasis").value("기간 기준·단계 미입력"));noWrites();
    }
    @Test void modifiedTimeDoesNotDetermineActualOrLastUpdate()throws Exception{
        memos.add(memo(2,today.minusDays(2),30));var old=memo(1,today.minusDays(5),90);memos.add(new MemoRow(old.projectId(),1,old.writtenDate(),old.thisWeek(),null,null,90,old.registeredBy(),old.registeredName(),created,"admin","관리자",LocalDateTime.of(2026,7,1,0,0)));
        mvc.perform(get(root)).andExpect(jsonPath("$.data.status.actualProgress").value("30.0")).andExpect(jsonPath("$.data.status.lastUpdatedDate").value("2026-06-29"));noWrites();
    }
    @Test void nextDayChangesSignalNotRevisionAndFourteenIsNotStale()throws Exception{
        project=project("MO","ING",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31));memos.add(memo(1,today.minusDays(14),null));String token=revisions.revision(raw());
        mvc.perform(get(root)).andExpect(jsonPath("$.data.status.updateWarning").value(false)).andExpect(jsonPath("$.data.status.light").value("정상"));
        when(clock.instant()).thenReturn(Instant.parse("2026-07-01T15:00:00Z"));
        mvc.perform(get(root)).andExpect(jsonPath("$.data.status.elapsedDays").value(15)).andExpect(jsonPath("$.data.status.light").value("주의")).andExpect(jsonPath("$.data.progressRevision").value(token));noWrites();
    }
    @Test void initializesTwoHundredDaysAndFieldAuditsWithoutMemoChange()throws Exception{
        post("/steps/initialize",write()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        assertThat(steps).hasSize(7);assertThat(steps.stream().map(r->ProjectProgressCalculation.days(r.startDate(),r.endDate()))).containsExactly(10L,30L,40L,70L,30L,10L,10L);
        assertThat(steps.get(0).startDate()).isEqualTo(project.startDate());assertThat(steps.get(6).endDate()).isEqualTo(project.endDate());
        verify(history,times(28)).append(argThat(c->c.tableName().equals("TB_PJ_STEP")&&c.type().equals("NEW")&&c.targetKey().startsWith("P-0009:")));assertThat(memos).isEmpty();
        verify(repository,times(7)).insertStep(argThat(w->w.actor().equals("admin")));verify(locks).lock("PROJECT:P-0009");
    }
    @Test void shortContractReturnsReasonAndZeroRows()throws Exception{project=project("TK","ING",today,today.plusDays(2));post("/steps/initialize",write()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false)).andExpect(jsonPath("$.data.message").value("계약기간이 짧아 기본 단계 계획을 만들 수 없습니다"));noWrites();}
    @Test void existingPartialSetNeverFilledAndFallsBack()throws Exception{initialized();steps=new ArrayList<>(steps.subList(0,1));post("/steps/initialize",write()).andExpect(jsonPath("$.data.changed").value(false)).andExpect(jsonPath("$.data.progress.status.planBasis").value("기간 기준·단계 집합 확인 필요"));assertThat(steps).hasSize(1);noWrites();}
    @Test void staleSecondInitializationCannotDuplicate()throws Exception{var body=write();post("/steps/initialize",body).andExpect(status().isOk());clearInvocations(repository,history,invalidation);post("/steps/initialize",body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));assertThat(steps).hasSize(7);noWrites();}
    @ParameterizedTest @ValueSource(strings={"sum","inactive","missing","types","decimal","negative"})
    void invalidTemplatesNeverGenerate(String kind)throws Exception{
        var list=new ArrayList<>(templates());var t=list.get(0);
        if(kind.equals("missing"))list.remove(0);else list.set(0,new Template(t.code(),t.name(),kind.equals("sum")?"4":kind.equals("decimal")?"5.001":kind.equals("negative")?"-5":t.weight(),kind.equals("types")?"MO":t.types(),kind.equals("inactive")?"N":"Y","N",0));
        when(repository.templates()).thenReturn(list);post("/steps/initialize",write()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("steps")).andExpect(jsonPath("$.details.violations").isArray());noWrites();
    }
    @Test void previewAndSaveUseSameContinuousBoundaries()throws Exception{
        initialized();var preview=dates(true);((ObjectNode)preview.get("steps").get(0)).put("endDate","2026-01-11");
        post("/steps/preview",preview).andExpect(status().isOk()).andExpect(jsonPath("$.data.steps[1].startDate").value("2026-01-12"));noWrites();
        var save=preview.deepCopy().put("expectedRevision",revisions.revision(raw()));put("/steps",save).andExpect(status().isOk()).andExpect(jsonPath("$.data.progress.steps[1].startDate").value("2026-01-12"));
        verify(history).append(argThat(c->c.item().equals("PLAN_END_DT")&&c.before().equals("2026-01-10")&&c.after().equals("2026-01-11")));
        verify(history).append(argThat(c->c.item().equals("PLAN_BGN_DT")&&c.after().equals("2026-01-12")));
    }
    @ParameterizedTest @ValueSource(strings={"missing","duplicate","order","reverse","outside","last","completed"})
    void malformedEntirePlanRejectedByPreviewAndSave(String kind)throws Exception{
        initialized();if(kind.equals("completed")){var s=steps.get(0);steps.set(0,ProjectProgressCalculation.withDates(s,s.startDate(),s.endDate(),today));}
        var b=dates(true);var a=(com.fasterxml.jackson.databind.node.ArrayNode)b.get("steps");
        switch(kind){case "missing"->a.remove(0);case "duplicate"->((ObjectNode)a.get(1)).put("stepSn",1);case "order"->((ObjectNode)a.get(0)).put("stepSn",2);case "reverse"->((ObjectNode)a.get(1)).put("endDate","2026-01-01");case "outside"->((ObjectNode)a.get(0)).put("endDate","2027-01-01");case "last"->((ObjectNode)a.get(6)).put("endDate","2026-07-18");default->((ObjectNode)a.get(0)).put("endDate","2026-01-11");}
        var before=raw();post("/steps/preview",b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("steps"));
        put("/steps",b.put("expectedRevision",revisions.revision(raw()))).andExpect(status().isBadRequest());assertThat(raw()).isEqualTo(before);noWrites();
    }
    @Test void identicalDatesNoAuditAndStaleWinsOverNoop()throws Exception{initialized();var b=dates(false);put("/steps",b).andExpect(jsonPath("$.data.changed").value(false));noWrites();put("/steps",b.put("expectedRevision","old")).andExpect(status().isConflict());noWrites();}
    @Test void completionUsesSeoulTodayWithoutPredecessorOrActualIncrease()throws Exception{initialized();memos.add(memo(1,today.minusDays(1),0));post("/steps/2/complete",write()).andExpect(status().isOk()).andExpect(jsonPath("$.data.progress.steps[1].completedDate").value("2026-07-01")).andExpect(jsonPath("$.data.progress.status.actualProgress").value("0.0")).andExpect(jsonPath("$.data.progress.status.lastUpdatedDate").value("2026-06-30"));assertThat(steps.get(0).completedDate()).isNull();verify(history).append(argThat(c->c.item().equals("CMPL_DT")&&c.before()==null&&c.after().equals("2026-07-01")));}
    @Test void completedIsNoopWithLatestButNotOldRevision()throws Exception{initialized();var old=write();post("/steps/1/complete",old).andExpect(status().isOk());clearInvocations(repository,history,invalidation);post("/steps/1/complete",write()).andExpect(jsonPath("$.data.changed").value(false));post("/steps/1/complete",old).andExpect(status().isConflict());noWrites();}
    @Test void futureStageRejected()throws Exception{project=project("TK","ING",today,today.plusDays(199));initialized();post("/steps/2/complete",write()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_STATUS"));noWrites();}
    @Test void missingStepIs404()throws Exception{post("/steps/2/complete",write()).andExpect(status().isNotFound());noWrites();}
    @Test void stepOutOfRangeIs400()throws Exception{post("/steps/100/complete",write()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("stepSn"));noWrites();}
    @Test void createMemoOnlyExplicitZeroAllocatesAndUpdatesToday()throws Exception{memos.add(memo(5,today.minusDays(10),50));post("/week-memos",write().put("actualProgress",0)).andExpect(status().isOk()).andExpect(jsonPath("$.data.memo.memoSn").value(6)).andExpect(jsonPath("$.data.memo.actualProgress").value(0)).andExpect(jsonPath("$.data.memo.registeredBy.userId").value("admin")).andExpect(jsonPath("$.data.progress.status.elapsedDays").value(0)).andExpect(jsonPath("$.data.progress.status.actualProgress").value("0.0"));verify(history).append(argThat(c->c.item().equals("ACTL_PRGRS_RT")&&c.after().equals("0")));verify(repository,never()).updateDates(any());}
    @Test void repeatedOldMemoCreateTokenDoesNotDuplicate()throws Exception{var b=write().put("thisWeek","내용");post("/week-memos",b).andExpect(status().isOk());post("/week-memos",b).andExpect(status().isConflict());assertThat(memos).hasSize(1);post("/week-memos",write().put("thisWeek","같은 날 추가")).andExpect(status().isOk());assertThat(memos).hasSize(2);}
    @ParameterizedTest @ValueSource(strings={"empty","fraction","negative","over","long"})
    void invalidMemoIsFieldErrorAndNoWrites(String kind)throws Exception{var b=write();switch(kind){case "empty"->b.put("thisWeek","  ").putNull("actualProgress");case "fraction"->b.put("actualProgress",1.5);case "negative"->b.put("actualProgress",-1);case "over"->b.put("actualProgress",101);default->b.put("thisWeek","가".repeat(201));}post("/week-memos",b).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"MO","DP"}) void operatingMemoRejectsActualEvenNull(String type)throws Exception{project=project(type,"ING",project.startDate(),project.endDate());post("/week-memos",write().put("thisWeek","텍스트").putNull("actualProgress")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("actualProgress"));noWrites();}
    @Test void oldOtherAuthorMemoUpdatePreservesOriginalAndLatestWrittenOrder()throws Exception{memos.add(memo(1,today.minusDays(10),20));memos.add(memo(2,today.minusDays(1),null));put("/week-memos/1",write().put("thisWeek"," 수정 ").put("actualProgress",30)).andExpect(status().isOk()).andExpect(jsonPath("$.data.memo.writtenDate").value("2026-06-21")).andExpect(jsonPath("$.data.memo.registeredBy.userId").value("another")).andExpect(jsonPath("$.data.memo.modifiedBy.userId").value("admin")).andExpect(jsonPath("$.data.progress.status.actualProgress").value("30.0")).andExpect(jsonPath("$.data.progress.status.lastUpdatedDate").value("2026-06-30")).andExpect(jsonPath("$.data.progress.memos[0].memoSn").value(2));assertThat(memos.get(0).registeredAt()).isEqualTo(created);verify(history).append(argThat(c->c.item().equals("THIS_WEEK_CN")&&c.after().equals("수정")));}
    @Test void identicalMemoPreservesModifiedTime()throws Exception{memos.add(memo(1,today.minusDays(1),20));put("/week-memos/1",write().put("thisWeek","기록").put("actualProgress",20)).andExpect(jsonPath("$.data.changed").value(false));noWrites();}
    @Test void staleMemoUpdateRejected()throws Exception{memos.add(memo(1,today.minusDays(1),20));put("/week-memos/1",write().put("thisWeek","변경").put("expectedRevision","old")).andExpect(status().isConflict());noWrites();}
    @Test void missingMemoIs404()throws Exception{put("/week-memos/1",write().put("thisWeek","내용")).andExpect(status().isNotFound());noWrites();}
    @ParameterizedTest @CsvSource({"END,2026","LOSE,2026","ING,2025"}) void readonlyMemoIs409(String state,int year)throws Exception{project=project("TK",state,project.startDate(),project.endDate());memos.add(memo(1,today,10));put("/week-memos/1",write().put("year",year).put("thisWeek","수정")).andExpect(status().isConflict());noWrites();}
    @Test void expiredIngIsReadonly()throws Exception{project=project("TK","ING",project.startDate(),today.minusDays(1));post("/week-memos",write().put("thisWeek","수정")).andExpect(status().isConflict());noWrites();}
    @ParameterizedTest @CsvSource({"/steps/initialize,regUserId","/steps/initialize,actor","/steps/preview,weight","/steps/1/complete,completedDate","/steps/1/complete,actor","/week-memos,writtenDate","/week-memos,registeredBy","/week-memos,userId"})
    void postRejectsInjectedServerFields(String suffix,String field)throws Exception{initialized();ObjectNode b=suffix.endsWith("preview")?dates(true):write();post(suffix,b.put(field,"other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrites();}
    @ParameterizedTest @CsvSource({"/steps,registeredBy","/steps,weight","/steps,startDate","/week-memos/1,regUserId","/week-memos/1,writtenDate","/week-memos/1,actor"})
    void putRejectsInjectedFields(String suffix,String field)throws Exception{initialized();ObjectNode b=suffix.equals("/steps")?dates(false):write();put(suffix,b.put(field,"other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrites();}
    @Test void previewRejectsNestedStartOrWeight()throws Exception{initialized();var b=dates(true);((ObjectNode)b.get("steps").get(0)).put("weight","5");post("/steps/preview",b).andExpect(status().isBadRequest());noWrites();}
    @Test void historyShowsActualColumnsFullKeysAndActor()throws Exception{when(repository.history(anyString())).thenReturn(List.of(new HistoryRow("1","TB_PJ_STEP","P-0009:2","UPD","CMPL_DT",null,"2026-07-01",null,"admin","관리자",created),new HistoryRow("2","TB_PJ_WEEK_MEMO","P-0009:1","UPD","THIS_WEEK_CN","전","후",null,"admin","관리자",created),new HistoryRow("3","TB_PJ_ISSUE","5","UPD","ISSUE_STAT_CD","OPEN","CLOSE",null,"admin","관리자",created)));mvc.perform(get(root+"/history")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.items[0].itemName").value("CMPL_DT")).andExpect(jsonPath("$.data.items[1].before").value("전")).andExpect(jsonPath("$.data.items[2].registeredBy.userId").value("admin"));noWrites();}
    @Test void historyOtherProjectKeyIs400()throws Exception{mvc.perform(get(root+"/history").param("targetTable","TB_PJ_STEP").param("targetKey","P-0010:1")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("targetKey"));noWrites();}
    @Test void historyFilterCountAndPageUseSameRows()throws Exception{initialized();var rows=new ArrayList<HistoryRow>();for(int i=0;i<21;i++)rows.add(new HistoryRow(""+i,"TB_PJ_STEP","P-0009:1","UPD","PLAN_END_DT","a","b",null,"admin","관리자",created));rows.add(new HistoryRow("90","TB_PJ_WEEK_MEMO","P-0009:1","UPD","THIS_WEEK_CN","a","b",null,"admin","관리자",created));when(repository.history(anyString())).thenReturn(rows);mvc.perform(get(root+"/history").param("targetTable","TB_PJ_STEP").param("targetKey","P-0009:1").param("page","2").param("size","20")).andExpect(jsonPath("$.data.total").value(21)).andExpect(jsonPath("$.data.items.length()").value(1));noWrites();}
    @Test void malformedQueryDuplicateYearAndBodyAre400()throws Exception{mvc.perform(get(root).param("year","2026","2026")).andExpect(status().isBadRequest());mvc.perform(get(root).param("baseDate","2026-01-01")).andExpect(status().isBadRequest());mvc.perform(get(root).content("{}")).andExpect(status().isBadRequest());noWrites();}
    @Test void missingProjectReturns404()throws Exception{project=null;mvc.perform(get(root)).andExpect(status().isNotFound());noWrites();}
    @Test void mandatoryAuditFailureRollsBackAndMasksDetails()throws Exception{initialized();doThrow(new IllegalStateException("private_sql")).when(history).append(any());post("/steps/1/complete",write()).andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private_sql"))));verify(manager).rollback(any());verifyNoInteractions(invalidation);}
    @Test void openAdditionalStatusAndStrictlyPastHighDeadlineProduceAllSignalReasons()throws Exception{
        memos.add(memo(1,today.minusDays(15),0));
        issues.add(new IssueRow(1,project.projectId(),project.projectName(),"지연 이슈","HIGH","높음",today.minusDays(1),"WAIT","대기",null,"admin","관리자",created,null,null,null));
        issues.add(new IssueRow(2,project.projectId(),project.projectName(),"오늘 기한","HIGH","높음",today,"OPEN","열림",null,"admin","관리자",created,null,null,null));
        issues.add(new IssueRow(3,project.projectId(),project.projectName(),"종료","HIGH","높음",today.minusDays(1),"CLOSE","종료",null,"admin","관리자",created,null,null,null));
        mvc.perform(get(root)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status.openIssueCount").value(2)).andExpect(jsonPath("$.data.status.overdueIssueCount").value(1)).andExpect(jsonPath("$.data.status.light").value("위험")).andExpect(jsonPath("$.data.issues[1].overdue").value(false));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"MO","DP","IN"})
    void onlyTurnkeyHasCostSignals(String type)throws Exception{
        project=project(type,"PRE",project.startDate(),project.endDate());
        mvc.perform(get(root)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status.costUsage").isEmpty()).andExpect(jsonPath("$.data.status.costProgressGap").isEmpty()).andExpect(jsonPath("$.data.status.costUnknown").value(false));noWrites();
    }
    @Test void zeroBudgetAndMissingCostsRemainUnknownNotZeroUsage()throws Exception{
        when(costs.load(any(),any())).thenReturn(new Cost(null,new Completeness(false,false,false,List.of(new MissingReason("MISSING","원천 미확인",null,null,null,"cumulativeCost")),1,"2026-01","2026-07",null)));
        mvc.perform(get(root)).andExpect(jsonPath("$.data.status.costUnknown").value(true)).andExpect(jsonPath("$.data.status.costUsage").isEmpty()).andExpect(jsonPath("$.data.status.completeness.issues[0].message").value("원천 미확인"));noWrites();
    }
    @Test void memoSerialExhaustionIsConflictWithoutWrites()throws Exception{memos.add(memo(99999,today,0));post("/week-memos",write().put("actualProgress",0)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ID_EXHAUSTED"));noWrites();}
    @Test void memoUpdateLengthIs400AndRetainsOriginalRow()throws Exception{memos.add(memo(1,today,10));String before=raw();put("/week-memos/1",write().put("thisWeek","가".repeat(201))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("thisWeek"));assertThat(raw()).isEqualTo(before);noWrites();}
    @ParameterizedTest @ValueSource(strings={"/steps/initialize","/steps/preview","/steps","/steps/1/complete","/week-memos","/week-memos/1"})
    void missingProjectAtEveryProgressWriteBoundaryIs404(String suffix)throws Exception {
        initialized();
        ObjectNode body=suffix.equals("/steps/preview")?dates(true):suffix.equals("/steps")?dates(false):write();
        if(suffix.contains("week-memos"))body.put("thisWeek","독립 입력");
        project=null;
        var response=suffix.equals("/steps")||suffix.equals("/week-memos/1")?put(suffix,body):post(suffix,body);
        response.andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("NOT_FOUND")).andExpect(jsonPath("$.requestId").isString())
            .andExpect(jsonPath("$.data").doesNotExist());noWrites();
    }
    @Test void historyFailureMasksPersistenceDetailsWithoutWriting()throws Exception {
        when(repository.history(anyString())).thenThrow(new IllegalStateException("private_history_sql"));
        mvc.perform(get(root+"/history")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.requestId").isString())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private_history_sql"))));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"MO","DP"})
    void memoUpdateRejectsActualFieldEvenNull(String type)throws Exception{project=project(type,"ING",project.startDate(),project.endDate());memos.add(memo(1,today,null));put("/week-memos/1",write().put("thisWeek","수정").putNull("actualProgress")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("actualProgress"));noWrites();}
}
