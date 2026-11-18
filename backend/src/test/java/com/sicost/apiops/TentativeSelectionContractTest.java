package com.sicost.apiops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.assignmentcandidatesops.*;
import com.sicost.assignments.AssignmentMapper;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.sicost.apiops.TentativeSelectionDtos.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest(TentativeSelectionController.class)
@org.springframework.transaction.annotation.EnableTransactionManagement
@ContextConfiguration(classes=TentativeSelectionController.class)
@Import({TentativeSelectionService.class,AssignmentCandidateRepository.class,AssignmentMonthlyService.class,RevisionService.class,ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class TentativeSelectionContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean TentativeSelectionMapper mapper;
    @MockBean AssignmentCandidateMapper candidates;
    @MockBean AssignmentMapper assignments;
    @MockBean AssignmentMonthlyMapper months;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper audit;
    @MockBean SettlementSourceLocks locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    @BeforeEach void setup(){
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));when(users.userId()).thenReturn("admin");
        when(candidates.snapshot(anyString())).thenAnswer(c->c.getArgument(0));
        when(mapper.need(any())).thenAnswer(c->{TentativeSelectionInput.Create i=c.getArgument(0);return new Need(i.opportunityId(),i.needSn(),"202607",3,2,"ING");});
        when(mapper.eligible("D-1")).thenReturn(true);when(mapper.nextId()).thenReturn("101");when(mapper.insert(any())).thenReturn(1);when(mapper.release(any())).thenReturn(1);when(mapper.touch(any())).thenReturn(1);when(audit.insert(any())).thenReturn(1);when(months.cashBase()).thenReturn("202501");
    }
    String revision(String id){return revisions.revision(id);}
    ObjectNode body(){return json.createObjectNode().put("opportunityId","O-1").put("needSn",1).put("developerId","D-1").put("startMonth","2026-07").put("sourceScreenId","UI-29").put("expectedRevision",revision("O-1"));}
    org.springframework.test.web.servlet.ResultActions post(ObjectNode b)throws Exception{return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/tentative-assignments").contentType("application/json").content(b.toString()));}
    void noWrites(){verify(mapper,never()).insert(any());verify(mapper,never()).release(any());verify(mapper,never()).touch(any());verifyNoInteractions(audit);}
    @Test void publicCreateUsesServerActorAndFieldAuditOnly()throws Exception{
        post(body()).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.tentativeId").value("101"));
        verify(mapper).insert(argThat(w->w.actor().equals("admin")&&w.startMonth().equals("202607")&&w.sourceScreenId().equals("UI-29")));
        verify(audit).insert(argThat(a->a.item().equals("DEV_ID")&&a.after().equals("D-1")&&a.type().equals("NEW")));verify(months,never()).saveMonth(any());
    }
    @ParameterizedTest @ValueSource(strings={"opportunityId","needSn","developerId","startMonth","sourceScreenId","expectedRevision"}) void requiredFields(String field)throws Exception{var b=body();b.remove(field);post(b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field)).andExpect(jsonPath("$.requestId").isNotEmpty());noWrites();}
    @ParameterizedTest @ValueSource(strings={"opportunityId","needSn","developerId","startMonth","sourceScreenId","expectedRevision"}) void wrongTypes(String field)throws Exception{post(body().put(field,true)).andExpect(status().isBadRequest());noWrites();}
    @Test void actorInjectionIsRejected()throws Exception{post(body().put("actor","other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();}
    @Test void externalCandidateOrInactiveDeveloperIsNotADeveloper()throws Exception{post(body().put("developerId","C-1")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("developerId"));noWrites();}
    @Test void missingNeedIs404()throws Exception{doReturn(null).when(mapper).need(any());post(body()).andExpect(status().isNotFound());noWrites();}
    @ParameterizedTest @ValueSource(strings={"WIN","LOSE","STOP"}) void endedOpportunityCannotCreate(String state)throws Exception{doReturn(new Need("O-1",1,"202607",3,2,state)).when(mapper).need(any());post(body()).andExpect(status().isConflict());noWrites();}
    @Test void fullNeedCannotStealAnotherSelection()throws Exception{when(mapper.count(any())).thenReturn(2);post(body()).andExpect(status().isConflict());noWrites();}
    @Test void identicalReselectionIsNoOp()throws Exception{when(mapper.active("D-1")).thenReturn(List.of(new Active("7","O-1",1,"D-1","202607","ACT")));post(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false)).andExpect(jsonPath("$.data.tentativeId").value("7"));noWrites();}
    @Test void moveAtomicallyReleasesPreviousAndTouchesBothOpportunities()throws Exception{
        when(mapper.active("D-1")).thenReturn(List.of(new Active("7","O-2",1,"D-1","202607","ACT")));
        post(body().put("previousOpportunityRevision",revision("O-2"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.warnings[0]").isNotEmpty());
        verify(mapper).release(argThat(w->w.id().equals("7")&&w.opportunityId().equals("O-2")));verify(mapper).touch(argThat(w->w.opportunityId().equals("O-2")));verify(mapper).touch(argThat(w->w.opportunityId().equals("O-1")));
    }
    @Test void previousRevisionRequiredBeforeMove()throws Exception{when(mapper.active("D-1")).thenReturn(List.of(new Active("7","O-2",1,"D-1","202607","ACT")));post(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();}
    @Test void historicalDuplicatesAreReportedNeverChosen()throws Exception{when(mapper.active("D-1")).thenReturn(List.of(new Active("7","O-1",1,"D-1","202607","ACT"),new Active("8","O-2",1,"D-1","202607","ACT")));post(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.details.tentativeIds.length()").value(2));noWrites();}
    @Test void staleRevisionDoesNotWrite()throws Exception{post(body().put("expectedRevision","old")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"2026-06","2026-10","0000-01","2026-13"}) void invalidStartDoesNotWrite(String value)throws Exception{post(body().put("startMonth",value)).andExpect(status().isBadRequest());noWrites();}
    @Test void followupCanUseComputedStartWithinNeed()throws Exception{post(body().put("sourceScreenId","UI-05").put("startMonth","2026-08")).andExpect(status().isOk());verify(mapper).insert(argThat(w->w.startMonth().equals("202608")));}
    @Test void auditFailureRollsBackTransaction()throws Exception{when(audit.insert(any())).thenReturn(0);post(body()).andExpect(status().isInternalServerError());verify(manager,atLeastOnce()).rollback(any());}
    @Test void exhaustedSequenceIs409BeforeMutation()throws Exception{when(mapper.nextId()).thenReturn("10000000000");post(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ID_EXHAUSTED"));noWrites();}
}
