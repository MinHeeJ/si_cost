package com.sicost.candidates;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.apiops.*;
import com.sicost.assignmentcandidatesops.AssignmentCandidateRepository;
import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
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
import static com.sicost.candidates.RecommendationActionDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest(RecommendationActionController.class)
@org.springframework.transaction.annotation.EnableTransactionManagement
@ContextConfiguration(classes=RecommendationActionController.class)
@Import({RecommendationActionService.class,RecommendationActionRepository.class,RevisionService.class,ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class RecommendationActionContractTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired RecommendationActionRepository repository;
    @MockBean RecommendationActionMapper mapper;@MockBean TentativeSelectionMapper tentatives;@MockBean TentativeSelectionService tentative;
    @MockBean AssignmentCandidateRepository opportunities;@MockBean AssignmentMonthlyService monthly;
    @MockBean CurrentUserProvider users;@MockBean Clock clock;@MockBean ChangeHistoryMapper audit;
    @MockBean SettlementSourceLocks locks;@MockBean CommitInvalidation invalidation;@MockBean PlatformTransactionManager manager;
    Row row;List<AuditValue> values;Need need;
    @BeforeEach void setup(){
        row=new Row("101",3,"AI","O-0101","1",null,"BE","202607","D-0101","{}",null,null,null,null,LocalDateTime.of(2026,7,1,9,0));values=new ArrayList<>();
        need=new Need("O-0101","2","FE","202607",3,1,"ING","MO","진행 기회");
        when(clock.instant()).thenReturn(Instant.parse("2026-07-01T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());when(users.userId()).thenReturn("admin");when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        when(mapper.row(any())).thenAnswer(c->row);when(mapper.lockRow(any())).thenAnswer(c->row);when(mapper.snapshot(any())).thenReturn("source");when(mapper.auditValues(any())).thenAnswer(c->List.copyOf(values));
        when(mapper.targetOpen(any())).thenReturn(true);when(mapper.activeReason("SCHD")).thenReturn(true);when(mapper.need(any())).thenAnswer(c->need);
        when(mapper.messageNames(any())).thenReturn(new MessageNames("추천 인력","프론트엔드 개발","이전 기회"));
        when(mapper.developer(any())).thenReturn(new Developer("D-0101","BE","FT","DRCT","Y",LocalDate.of(2020,1,1),null));when(monthly.cashBase()).thenReturn(YearMonth.of(2025,1));
        when(opportunities.revision(any())).thenReturn("opportunity-revision");when(tentative.create(any())).thenReturn(new TentativeSelectionDtos.Result(true,"new",List.of(),"O-0101","201"));
        when(mapper.choose(any())).thenAnswer(c->{Choice choice=c.getArgument(0);row=new Row(row.rcmdId(),row.rank(),row.type(),row.opportunityId(),row.needSn(),row.projectId(),row.jobCode(),row.startMonth(),row.developerId(),row.scoreDetail(),choice.action(),choice.reason(),choice.actor(),choice.now(),row.registeredAt());return 1;});
        when(audit.insert(any())).thenAnswer(c->{ChangeHistoryEntry entry=c.getArgument(0);values.add(new AuditValue(entry.item(),entry.after()));return 1;});
    }
    ObjectNode exclude(){return json.createObjectNode().put("reasonCode","SCHD").put("expectedRevision",repository.current(row).expectedRevision());}
    ObjectNode tent(){var r=repository.current(row);var b=json.createObjectNode().put("needSn","2").put("expectedRevision",r.expectedRevision()).put("targetRevision",r.targetRevision()).put("candidateRevision",r.candidateRevision());b.putArray("confirmedWarnings").add("JOB_MISMATCH");return b;}
    org.springframework.test.web.servlet.ResultActions call(String action,ObjectNode body)throws Exception{return mvc.perform(post("/api/workforce-recommendations/101/candidates/3/"+action).contentType("application/json").content(body.toString()));}
    void noWrites(){verify(mapper,never()).choose(any());verify(audit,never()).insert(any());verify(tentative,never()).create(any());}
    @Test void tentativeAuthRejectsForgedActorBeforeSeatSelection() throws Exception {
        mvc.perform(post("/api/workforce-recommendations/101/candidates/3/tentative-assignment").contentType("application/json").content(tent().put("actor","other").toString()))
            .andExpect(DurableOpenApi.response("POST","/api/workforce-recommendations/{rcmdId}/candidates/{rank}/tentative-assignment",400))
            .andExpect(jsonPath("$.field").value("actor"));noWrites();
    }
    @Test void publicExclusionUsesServerActorAndFourFieldAudit()throws Exception{Row original=row;call("exclude",exclude()).andExpect(status().isOk()).andExpect(jsonPath("$.data.selection.actionCode").value("EXCL")).andExpect(jsonPath("$.data.selection.actedBy").value("admin")).andExpect(jsonPath("$.data.selection.actedAt").value("2026-07-01 09:00:00"));assertThat(row.rank()).isEqualTo(3);assertThat(row.scoreDetail()).isEqualTo(original.scoreDetail());verify(audit).insert(argThat(e->e.item().equals("RCMD_EXCL_CD")&&e.before()==null&&e.after().equals("SCHD")));}
    @Test void exclusionReplayPrecedesLaterRevisionAndDoesNotAudit()throws Exception{var request=exclude();call("exclude",request).andExpect(status().isOk());int size=values.size();when(mapper.snapshot(any())).thenReturn("later payroll and off-page month");call("exclude",request).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));assertThat(values).hasSize(size);verify(mapper,times(1)).choose(any());}
    @Test void differentExclusionPayloadIsAlreadyTaken()throws Exception{var request=exclude();call("exclude",request).andExpect(status().isOk());call("exclude",request.put("reasonCode","HOPE")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACTION_ALREADY_TAKEN"));verify(mapper,times(1)).choose(any());}
    @ParameterizedTest @ValueSource(strings={"reasonCode","expectedRevision"}) void exclusionRequiresEachField(String field)throws Exception{var b=exclude();b.remove(field);call("exclude",b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrites();}
    @Test void inactiveReasonIsRejected()throws Exception{when(mapper.activeReason(any())).thenReturn(false);call("exclude",exclude()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("reasonCode"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"","TOOLONG","MISS"}) void exclusionRejectsInvalidReasons(String value)throws Exception{call("exclude",exclude().put("reasonCode",value)).andExpect(status().isBadRequest());noWrites();}
    @Test void nextCannotBeExcluded()throws Exception{type("NEXT","{\"e\":{\"aid\":\"A-01\"}}");call("exclude",exclude()).andExpect(status().isBadRequest());noWrites();}
    @Test void projectCannotBeTentativelyAssigned()throws Exception{row=new Row("101",3,"AI",null,null,"P-1","BE","202607","D-0101","{}",null,null,null,null,row.registeredAt());call("tentative-assignment",tent()).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"ACT_USER_ID","REG_USER_ID","actor","actedAt","sourceScreenId","totalScore","scoreDetail","developerId"}) void serverFieldsRejectedForBothActions(String field)throws Exception{call("exclude",exclude().put(field,"injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").isNotEmpty()).andExpect(jsonPath("$.requestId").isNotEmpty());call("tentative-assignment",tent().put(field,"injected")).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"needSn","expectedRevision","targetRevision","candidateRevision","confirmedWarnings"}) void tentativeRequiresEachField(String field)throws Exception{var b=tent();b.remove(field);call("tentative-assignment",b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrites();}
    @ParameterizedTest @ValueSource(strings={"0","6","9999999999"}) void rankBoundsAreValidated(String rank)throws Exception{mvc.perform(post("/api/workforce-recommendations/101/candidates/"+rank+"/exclude").contentType("application/json").content(exclude().toString())).andExpect(status().isBadRequest());noWrites();}
    @Test void wireNeedMustBeDecimalString()throws Exception{call("tentative-assignment",tent().put("needSn",2)).andExpect(status().isBadRequest());noWrites();}
    @Test void unknownQueryIsRejected()throws Exception{mvc.perform(post("/api/workforce-recommendations/101/candidates/3/exclude?actor=other").contentType("application/json").content(exclude().toString())).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"exclude","tentative-assignment"}) void missingRecommendationIs404(String action)throws Exception{var b=action.equals("exclude")?exclude():tent();when(mapper.row(any())).thenReturn(null);call(action,b).andExpect(status().isNotFound());noWrites();}
    @ParameterizedTest @ValueSource(strings={"exclude","tentative-assignment"}) void staleSourcesRejectBeforeWrites(String action)throws Exception{var b=action.equals("exclude")?exclude():tent();when(mapper.snapshot(any())).thenReturn("changed payroll, prices, off-page months");call(action,b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();}
    @Test void storedOriginalMetadataCannotBeRefreshedAway()throws Exception{values.add(new AuditValue("candidateRevision","old"));call("tentative-assignment",tent()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();}
    @Test void resumedTargetRejectsOldRound()throws Exception{when(mapper.resumed(any())).thenReturn(row.registeredAt().plusDays(1));call("exclude",exclude()).andExpect(status().isConflict());noWrites();}
    @Test void validExclusionOnSameTargetAndJobPreventsSave()throws Exception{when(mapper.excluded(any())).thenReturn(true);call("tentative-assignment",tent()).andExpect(status().isConflict());noWrites();}
    @Test void tentativeUsesCurrentSeatAndKeepsOriginalNeed()throws Exception{mvc.perform(post("/api/workforce-recommendations/101/candidates/3/tentative-assignment").contentType("application/json").content(tent().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.selection.finalNeedSn").value("2")).andExpect(jsonPath("$.data.message").value("추천 인력님을 「진행 기회」 2번 자리(프론트엔드 개발)에 가배정했습니다.")).andExpect(jsonPath("$.data.selection.tentativeAssignmentId").value("201"));assertThat(row.needSn()).isEqualTo("1");verify(tentative).create(argThat(i->i.needSn()==2&&i.sourceScreenId().equals("UI-30")&&i.startMonth().equals(YearMonth.of(2026,7))));}
    @ParameterizedTest @ValueSource(strings={"AI","NEXT"}) void otherOpportunityStaysAssignedForBothSources(String type)throws Exception{
        if(type.equals("NEXT"))next();
        when(tentatives.active(any())).thenReturn(List.of(new TentativeSelectionDtos.Active("old","O-OLD",1,"D-0101","202607","ACT")));
        var body=tent().put("needSn",need.needSn());
        call("tentative-assignment",body).andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value("추천 인력님을 「진행 기회」 "+need.needSn()+"번 자리(프론트엔드 개발)에 가배정했습니다."));
        verify(mapper).messageNames(new MessageContext("D-0101","FE",null));
    }
    @Test void tentativeRetryAfterChangedRevisionsUsesLinkedResult()throws Exception{var b=tent();call("tentative-assignment",b).andExpect(status().isOk());int size=values.size();when(mapper.snapshot(any())).thenReturn("later");call("tentative-assignment",b).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));assertThat(values).hasSize(size);verify(tentative,times(1)).create(any());}
    @Test void selectedTentativeCannotBeExcluded()throws Exception{call("tentative-assignment",tent()).andExpect(status().isOk());call("exclude",exclude()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACTION_ALREADY_TAKEN"));assertThat(row.actionCode()).isEqualTo("TENT");}
    @Test void unconfirmedWarningReturnsDetailsWithoutMutation()throws Exception{var b=tent();b.putArray("confirmedWarnings");call("tentative-assignment",b).andExpect(status().isConflict()).andExpect(jsonPath("$.details.workforceRecommendationWarnings[0].code").value("JOB_MISMATCH"));noWrites();}
    @Test void fabricatedWarningCannotAuthorizeMutation()throws Exception{var b=tent();b.putArray("confirmedWarnings").add("invented");call("tentative-assignment",b).andExpect(status().isConflict());noWrites();}
    @Test void removedSeatIs409AndPreservesLog()throws Exception{when(mapper.need(any())).thenReturn(null);call("tentative-assignment",tent()).andExpect(status().isConflict());noWrites();}
    @Test void fullSeatIsSlotFullBeforeCommonMutation()throws Exception{when(tentatives.count(any())).thenReturn(1);call("tentative-assignment",tent()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_FULL"));noWrites();}
    @Test void startAfterSeatEndIsConflict()throws Exception{when(mapper.progressingEnd(any(),any())).thenReturn(LocalDate.of(2026,9,1));call("tentative-assignment",tent()).andExpect(status().isConflict());noWrites();}
    @Test void midMonthWithdrawalUsesNextMonthNotSameMonth()throws Exception{when(mapper.progressingEnd(any(),any())).thenReturn(LocalDate.of(2026,7,15));call("tentative-assignment",tent()).andExpect(status().isOk());verify(tentative).create(argThat(i->i.startMonth().equals(YearMonth.of(2026,8))));}
    @Test void inactivePersonCannotBeSelected()throws Exception{when(mapper.developer(any())).thenReturn(new Developer("D-0101","BE","FT","DRCT","N",null,null));call("tentative-assignment",tent()).andExpect(status().isConflict());noWrites();}
    @Test void leaveDuringStartMonthRejects()throws Exception{when(mapper.leave(any())).thenReturn(true);call("tentative-assignment",tent()).andExpect(status().isConflict());noWrites();}
    @Test void dpExcludesFreelance()throws Exception{need=new Need(need.opportunityId(),need.needSn(),need.jobCode(),need.startMonth(),need.months(),need.count(),need.status(),"DP",need.name());when(mapper.developer(any())).thenReturn(new Developer("D-0101","BE","FL","DRCT","Y",null,null));call("tentative-assignment",tent()).andExpect(status().isConflict());noWrites();}
    @Test void nextSourceAndStartAreServerDetermined()throws Exception{next();var b=tent().put("needSn","1");call("tentative-assignment",b).andExpect(status().isOk());verify(tentative).create(argThat(i->i.sourceScreenId().equals("UI-05")&&i.startMonth().equals(YearMonth.of(2026,8))));}
    @Test void laterHighNewNextIsRejected()throws Exception{next();when(mapper.withdrawal(any())).thenReturn(new Withdrawal("A-01",LocalDate.of(2026,11,1),"H","ACT","ING"));call("tentative-assignment",tent().put("needSn","1")).andExpect(status().isConflict());noWrites();}
    @ParameterizedTest @ValueSource(strings={"CONFIRMED","CNVT","WITHDRAWN"}) void nextFinalizedSourcesCannotReplace(String state)throws Exception{next();if(state.equals("CONFIRMED"))when(mapper.hasConfirmedNext(any(),any())).thenReturn(true);if(state.equals("CNVT"))when(mapper.hasConvertedNext(any())).thenReturn(true);if(state.equals("WITHDRAWN"))when(mapper.withdrawal(any())).thenReturn(new Withdrawal("A-01",LocalDate.of(2026,6,30),null,"ACT","ING"));call("tentative-assignment",tent().put("needSn","1")).andExpect(status().isConflict());noWrites();}
    @ParameterizedTest @ValueSource(strings={"exclude","tentative-assignment"}) void auditZeroRollsBackTransaction(String action)throws Exception{doReturn(0).when(audit).insert(any());call(action,action.equals("exclude")?exclude():tent()).andExpect(status().isInternalServerError());verify(manager,atLeastOnce()).rollback(any());}
    @ParameterizedTest @ValueSource(strings={"exclude","tentative-assignment"}) void auditExceptionNeverLeaksSql(String action)throws Exception{doThrow(new IllegalStateException("SELECT private source")).when(audit).insert(any());call(action,action.equals("exclude")?exclude():tent()).andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SELECT private"))));verify(manager,atLeastOnce()).rollback(any());}
    void type(String type,String detail){row=new Row(row.rcmdId(),row.rank(),type,row.opportunityId(),row.needSn(),row.projectId(),row.jobCode(),row.startMonth(),row.developerId(),detail,null,null,null,null,row.registeredAt());}
    void next(){type("NEXT","{\"e\":{\"aid\":\"A-01\"}}");need=new Need(need.opportunityId(),"1",need.jobCode(),need.startMonth(),5,1,need.status(),need.contractType(),need.name());when(mapper.withdrawal(any())).thenReturn(new Withdrawal("A-01",LocalDate.of(2026,7,15),null,"ACT","ING"));}
}
