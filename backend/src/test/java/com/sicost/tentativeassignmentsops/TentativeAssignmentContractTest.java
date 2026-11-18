package com.sicost.tentativeassignmentsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.assignmentcandidatesops.*;
import com.sicost.assignments.AssignmentMapper;
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
import static com.sicost.tentativeassignmentsops.TentativeAssignmentDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest(TentativeAssignmentController.class)
@org.springframework.transaction.annotation.EnableTransactionManagement
@ContextConfiguration(classes=TentativeAssignmentController.class)
@Import({TentativeAssignmentService.class,TentativeAssignmentRepository.class,AssignmentCandidateRepository.class,
    RevisionService.class,ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class TentativeAssignmentContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean TentativeAssignmentMapper mapper;
    @MockBean AssignmentCandidateMapper candidates;
    @MockBean AssignmentMapper assignments;
    @MockBean SettlementSourceLocks locks;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper audit;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    State row;
    final LocalDateTime now=LocalDateTime.of(2026,7,1,0,0);
    @BeforeEach void setup() {
        row=new State("101","O-0101","D-101","ACT","ING",null);
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        when(users.userId()).thenReturn("admin");
        when(candidates.origin("101")).thenAnswer(c->new AssignmentCandidateDtos.Origin("101",row.opportunityId(),null,row.developerId(),"BE",row.status(),"Y",null,null,row.processedAt()));
        when(candidates.snapshot(anyString())).thenAnswer(c->row.toString());
        when(mapper.state("101")).thenAnswer(c->row);
        when(mapper.activeIds("D-101")).thenReturn(List.of("101"));
        when(mapper.release(any())).thenAnswer(c->{Mutation m=c.getArgument(0);row=new State(row.id(),row.opportunityId(),row.developerId(),"REL",row.opportunityStatus(),m.now());return 1;});
        when(mapper.touchOpportunity(any())).thenReturn(1);
        when(audit.insert(any())).thenReturn(1);
    }
    String revision(){return revisions.revision(row.toString());}
    ObjectNode body(){return json.createObjectNode().put("expectedRevision",revision());}
    org.springframework.test.web.servlet.ResultActions release(ObjectNode body) throws Exception {
        return mvc.perform(post("/api/tentative-assignments/101/release").contentType("application/json").content(body.toString()));
    }
    void noWrites(){verify(mapper,never()).release(any());verify(mapper,never()).touchOpportunity(any());verifyNoInteractions(audit,invalidation);}
    void state(String status,String opportunityStatus){row=new State(row.id(),row.opportunityId(),row.developerId(),status,opportunityStatus,row.processedAt());}
    @Test void publicReleaseReturnsTypedEnvelopeAndUsesServerActorAndFieldAudit() throws Exception {
        release(body().put("reason","사용자 선택 해제")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.changed").value(true))
            .andExpect(jsonPath("$.data.opportunityId").value("O-0101")).andExpect(jsonPath("$.data.tentativeId").value("101"))
            .andExpect(jsonPath("$.data.revision").value(revision())).andExpect(jsonPath("$.data.warnings").isArray());
        assertThat(row.status()).isEqualTo("REL");assertThat(row.processedAt()).isEqualTo(now);
        verify(mapper).touchOpportunity(new Mutation("101","O-0101","admin",now));
        verify(audit).insert(argThat(e->e.tableName().equals("TB_SL_TENT_ASGN")&&e.targetKey().equals("101")&&e.item().equals("TENT_STAT_CD")&&e.before().equals("ACT")&&e.after().equals("REL")&&e.reason().contains("사용자 선택 해제")&&e.reason().contains("sourceTentId=101")));
        verify(audit).insert(argThat(e->e.item().equals("PROC_DTTM")&&e.before()==null&&e.after().equals(now.toString())));
        verify(locks).lock(argThat(keys->keys.containsAll(List.of("DEV:D-101","OPP:O-0101","TENT:101"))));
        verify(assignments).lockDevelopers(List.of("D-101"));verify(assignments,never()).lockProject(anyString());
        verify(users,atLeastOnce()).currentUser();verify(invalidation).afterCommit("sales-opportunities");
    }
    @Test void latestRelReplayKeepsTimeAndAuditUnchanged() throws Exception {
        state("REL","ING");State original=row;
        release(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));
        assertThat(row).isEqualTo(original);noWrites();
    }
    @Test void oldRevisionCannotReplayRelease() throws Exception {
        ObjectNode request=body();release(request).andExpect(status().isOk());clearInvocations(mapper,audit,invalidation);
        release(request).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"CNVT","DONE","DROP"}) void terminalStatesCannotBeReversed(String status) throws Exception {
        state(status,"ING");State original=row;release(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));assertThat(row).isEqualTo(original);noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"WIN","LOSE","STOP"}) void nonProgressOpportunityCannotReleaseAct(String status) throws Exception {
        state("ACT",status);release(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));noWrites();
    }
    @Test void historicalMultipleActIsReportedWithoutChoosingAWinner() throws Exception {
        when(mapper.activeIds("D-101")).thenReturn(List.of("101","102"));
        release(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.details.tentativeIds[1]").value("102"));noWrites();
    }
    @Test void staleRevisionIsCheckedAfterLockAndBeforeBusinessWrites() throws Exception {
        release(body().put("expectedRevision","old")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        var order=inOrder(locks,candidates);order.verify(candidates).origin("101");order.verify(locks).lock(any());order.verify(candidates).lockOpportunity("O-0101");order.verify(candidates).snapshot("O-0101");noWrites();
    }
    @Test void missingRevisionHasFullErrorEnvelope() throws Exception {
        var response=release(json.createObjectNode()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.field").value("expectedRevision")).andExpect(jsonPath("$.message").isNotEmpty()).andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn().getResponse();
        assertThat(json.readTree(response.getContentAsString()).path("requestId").asText()).isEqualTo(response.getHeader("X-Request-Id"));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"null","true","123","{}","[]","\"\"","\"  \""}) void invalidRevisionTypeOrBlankIsRejected(String value) throws Exception {
        mvc.perform(post("/api/tentative-assignments/101/release").contentType("application/json").content("{\"expectedRevision\":"+value+"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("expectedRevision"));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"actor","status","regUserId","processedAt","opportunityId"}) void serverOwnedFieldsAreRejected(String field) throws Exception {
        release(body().put(field,"injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();
    }
    @Test void unknownQueryIncludingActorIsUnknownField() throws Exception {
        mvc.perform(post("/api/tentative-assignments/101/release").param("actor","injected").contentType("application/json").content(body().toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value("actor"));noWrites();
    }
    @Test void optionalReasonAtLimitIsPreservedWithoutTruncation() throws Exception {
        String reason="가".repeat(300);release(body().put("reason",reason)).andExpect(status().isOk());
        verify(audit).insert(argThat(e->e.item().equals("TENT_STAT_CD")&&e.reason().endsWith(reason)));
    }
    @Test void overlongReasonIsRejected() throws Exception {release(body().put("reason","가".repeat(301))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("reason"));noWrites();}
    @Test void nonTextReasonIsRejected() throws Exception {release(body().put("reason",123)).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"0","01","-1","10000000000","not-an-id"}) void invalidPathIdIsRejected(String id) throws Exception {
        mvc.perform(post("/api/tentative-assignments/"+id+"/release").contentType("application/json").content(body().toString())).andExpect(status().isBadRequest());noWrites();
    }
    @Test void missingTentativeIs404() throws Exception {when(candidates.origin("101")).thenReturn(null);release(body()).andExpect(status().isNotFound());noWrites();}
    @Test void auditExceptionRollsBackAndHidesSql() throws Exception {
        when(audit.insert(any())).thenThrow(new IllegalStateException("private SQL credential"));
        release(body()).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
        verify(manager,atLeastOnce()).rollback(any());verifyNoInteractions(invalidation);
    }
    @Test void auditAffectedZeroRollsBack() throws Exception {when(audit.insert(any())).thenReturn(0);release(body()).andExpect(status().isInternalServerError());verify(manager,atLeastOnce()).rollback(any());verifyNoInteractions(invalidation);}
    @Test void updateAffectedZeroRollsBackBeforeAudit() throws Exception {doReturn(0).when(mapper).release(any());release(body()).andExpect(status().isInternalServerError());verify(manager,atLeastOnce()).rollback(any());verifyNoInteractions(audit,invalidation);}
    @Test void parentUpdateAffectedZeroRollsBack() throws Exception {when(mapper.touchOpportunity(any())).thenReturn(0);release(body()).andExpect(status().isInternalServerError());verify(manager,atLeastOnce()).rollback(any());verifyNoInteractions(audit,invalidation);}
}
