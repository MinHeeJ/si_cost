package com.sicost.salesparts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.persistence.MasterIdService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.salesparts.SalesPartDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SalesPartController.class)
@ContextConfiguration(classes=SalesPartController.class)
@Import({SalesPartService.class,SalesPartRepository.class,RevisionService.class,ChangeHistoryWriter.class,
        ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class SalesPartContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean SalesPartMapper mapper;
    @MockBean AggregateLockMapper locks;
    @MockBean MasterLockMapper references;
    @MockBean MasterIdService ids;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean CommitInvalidation invalidation;
    @MockBean Clock clock;
    AtomicReference<Stored> part;
    StoredStandard standard;
    @BeforeEach void setup() {
        when(users.userId()).thenReturn("admin");
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        part=new AtomicReference<>(new Stored("PART-101","영업파트","N","Y","admin",LocalDateTime.parse("2026-01-01T00:00:00.123456"),null,null));
        standard=new StoredStandard("PART-101","202601",null,"admin",new BigDecimal("40.00"),new BigDecimal("1500000"),"admin",LocalDateTime.parse("2026-01-01T00:00:00.123456"),null,null);
        when(mapper.part(eq("PART-101"),anyBoolean())).thenAnswer(i->part.get());
        when(mapper.standards(eq("PART-101"),anyBoolean())).thenReturn(List.of(standard));
        when(mapper.counts(anyString(),anyString(),anyBoolean())).thenReturn(new Counts(0,0));
        when(mapper.leaderName("admin")).thenReturn("관리자");when(mapper.cashMonth()).thenReturn("202601");
        when(mapper.eligible("admin")).thenReturn(true);when(ids.nextPartId()).thenReturn("PART-101");
        when(mapper.list(any())).thenAnswer(i->List.of(part.get()));
        when(mapper.insertPart(any())).thenReturn(1);when(mapper.insertStandard(any())).thenReturn(1);
        when(mapper.update(any())).thenAnswer(i->{Mutation m=i.getArgument(0);Stored p=part.get();
            part.set(new Stored(p.id(),m.name()==null?p.name():m.name(),p.hqYn(),m.useYn()==null?p.useYn():m.useYn(),p.regUserId(),p.regDttm(),m.actor(),m.now()));return 1;});
    }
    String revision() { return revisions.revision(new Snapshot(part.get(),List.of(standard))); }
    ObjectNode patchBody(String key,String value) { return json.createObjectNode().put("expectedRevision",revision()).put(key,value); }
    ObjectNode createBody() { return json.createObjectNode().put("name","신규파트").put("effectiveMonth","2026-06").put("leaderUserId","admin").put("profitRate","100").put("salesExpenseAmount","999999999999999"); }
    void rejectPost(ObjectNode body,String field) throws Exception {
        mvc.perform(post("/api/sales-parts").contentType("application/json").content(body.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field)).andExpect(jsonPath("$.message").isString());
        verify(mapper,never()).insertPart(any());verify(mapper,never()).insertStandard(any());verifyNoInteractions(history);
    }
    @Test void renameWithoutAuthenticationUsesServerActorAndPreservesIdentityAndTimeline() throws Exception {
        mvc.perform(patch("/api/sales-parts/PART-101").contentType("application/json").content(patchBody("name"," 새 이름 ").toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true))
                .andExpect(jsonPath("$.data.part.part.id").value("PART-101")).andExpect(jsonPath("$.data.part.part.name").value("새 이름"));
        verify(mapper).update(argThat(m->m.actor().equals("admin")&&m.useYn()==null));
        verify(history).insert(argThat(h->h.type().equals("UPD")&&h.before().equals("영업파트")&&h.after().equals("새 이름")&&h.userId().equals("admin")));
        verify(mapper,never()).insertStandard(any());
    }
    @ParameterizedTest @ValueSource(strings={"0","100"})
    void creationRateBoundariesAndMaximumExpenseHaveTwoNewRowsWithoutAuth(String rate) throws Exception {
        mvc.perform(post("/api/sales-parts").contentType("application/json").content(createBody().put("profitRate",rate).toString()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap());
        var ordered=inOrder(mapper,history);ordered.verify(mapper).insertPart(any());ordered.verify(mapper).insertStandard(any());
        verify(history,times(2)).insert(argThat(h->h.type().equals("NEW")&&h.before()==null&&h.userId().equals("admin")));
        verify(references).lockClose();verify(locks).lock("operating-standards:CASH_BASE_YM");
    }
    @Test void missingRate() throws Exception {ObjectNode b=createBody();b.remove("profitRate");rejectPost(b,"profitRate");}
    @ParameterizedTest @ValueSource(strings={"100.01","1.001","-1","1e2","NaN"})
    void invalidRate(String value) throws Exception {rejectPost(createBody().put("profitRate",value),"profitRate");}
    @ParameterizedTest @ValueSource(strings={"-1","1.1","1000000000000000"})
    void invalidExpense(String value) throws Exception {rejectPost(createBody().put("salesExpenseAmount",value),"salesExpenseAmount");}
    @Test void unknownProcessorOnCreate() throws Exception {rejectPost(createBody().put("regUserId","other"),"regUserId");}
    @Test void unknownProcessorOnPatch() throws Exception {
        mvc.perform(patch("/api/sales-parts/PART-101").contentType("application/json").content(patchBody("name","수정").put("regUserId","other").toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper,history,locks);
    }
    @Test void bothPatchFields() throws Exception {
        mvc.perform(patch("/api/sales-parts/PART-101").contentType("application/json").content(patchBody("name","수정").put("useYn","N").toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("name"));verifyNoInteractions(mapper,history);
    }
    @Test void neitherPatchField() throws Exception {
        mvc.perform(patch("/api/sales-parts/PART-101").contentType("application/json").content(json.createObjectNode().put("expectedRevision",revision()).toString()))
                .andExpect(status().isBadRequest());verifyNoInteractions(mapper,history);
    }
    @Test void staleBeforeNoOp() throws Exception {
        mvc.perform(patch("/api/sales-parts/PART-101").contentType("application/json").content(patchBody("useYn","Y").put("expectedRevision","old").toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));verify(mapper,never()).update(any());verifyNoInteractions(history);
    }
    @Test void sameTimestampChangedStoredValueRejectsOldRevision() throws Exception {
        var b=patchBody("name","다른 이름");Stored p=part.get();part.set(new Stored(p.id(),"외부 수정",p.hqYn(),p.useYn(),p.regUserId(),p.regDttm(),p.modUserId(),p.modDttm()));
        mvc.perform(patch("/api/sales-parts/PART-101").contentType("application/json").content(b.toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));verifyNoInteractions(history);
    }
    @Test void entireTimelineChangesRevision() throws Exception {
        var b=patchBody("name","다른 이름");when(mapper.standards("PART-101",true)).thenReturn(List.of(standard,
            new StoredStandard("PART-101","202701",null,"admin",BigDecimal.ONE,BigDecimal.ZERO,"admin",null,null,null)));
        mvc.perform(patch("/api/sales-parts/PART-101").contentType("application/json").content(b.toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));verifyNoInteractions(history);
    }
    @Test void stopReferencedProject() throws Exception {
        when(mapper.counts("PART-101","202606",true)).thenReturn(new Counts(0,2));
        mvc.perform(patch("/api/sales-parts/PART-101").contentType("application/json").content(patchBody("useYn","N").toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFERENCE_IN_USE"))
                .andExpect(jsonPath("$.details.references[1].count").value(2));verifyNoInteractions(history);verify(mapper,never()).update(any());
    }
    @Test void stopReferencedDeveloper() throws Exception {
        when(mapper.counts("PART-101","202606",true)).thenReturn(new Counts(1,0));
        mvc.perform(patch("/api/sales-parts/PART-101").contentType("application/json").content(patchBody("useYn","N").toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFERENCE_IN_USE"));verifyNoInteractions(history);
    }
    @Test void stopThenMatchingNoOpHasOneStopHistory() throws Exception {
        mvc.perform(patch("/api/sales-parts/PART-101").contentType("application/json").content(patchBody("useYn","N").toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.part.part.useYn").value("N"));
        mvc.perform(patch("/api/sales-parts/PART-101").contentType("application/json").content(patchBody("useYn","N").toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false)).andExpect(jsonPath("$.data.message").value("바뀐 내용이 없습니다."));
        verify(history).insert(argThat(h->h.type().equals("STOP")));verify(mapper,times(1)).update(any());
    }
    @Test void resumeHasResumeHistory() throws Exception {
        Stored p=part.get();part.set(new Stored(p.id(),p.name(),p.hqYn(),"N",p.regUserId(),p.regDttm(),null,null));
        mvc.perform(patch("/api/sales-parts/PART-101").contentType("application/json").content(patchBody("useYn","Y").toString()))
                .andExpect(status().isOk());verify(history).insert(argThat(h->h.type().equals("RESUME")));
    }
    @Test void duplicateStoppedNameOnCreate() throws Exception {
        when(mapper.nameExists("신규파트")).thenReturn(true);
        mvc.perform(post("/api/sales-parts").contentType("application/json").content(createBody().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_PART_NAME"));verifyNoInteractions(history,ids);
    }
    @Test void duplicateNameOnRename() throws Exception {
        when(mapper.nameExists("중복")).thenReturn(true);
        mvc.perform(patch("/api/sales-parts/PART-101").contentType("application/json").content(patchBody("name","중복").toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_PART_NAME"));verifyNoInteractions(history);
    }
    @Test void closedMinimum() throws Exception {
        when(mapper.lastClose()).thenReturn("202606");
        mvc.perform(post("/api/sales-parts").contentType("application/json").content(createBody().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));verifyNoInteractions(ids,history);
    }
    @Test void maximumCloseRejectsSave() throws Exception {
        when(mapper.lastClose()).thenReturn("999912");
        mvc.perform(post("/api/sales-parts").contentType("application/json").content(createBody().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));verifyNoInteractions(history);
    }
    @Test void ineligibleLeaderIsValidationNotAuthorization() throws Exception {
        when(mapper.eligible("admin")).thenReturn(false);rejectPost(createBody(),"leaderUserId");
    }
    @Test void exhaustedIdsAreConflict() throws Exception {
        when(ids.nextPartId()).thenThrow(new ApiException(409,"ID_EXHAUSTED","사용 가능한 식별자를 모두 사용했습니다."));
        mvc.perform(post("/api/sales-parts").contentType("application/json").content(createBody().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ID_EXHAUSTED"));verifyNoInteractions(history);
    }
    @Test void detailTimelineDescendingAndFutureDoesNotLeakCurrent() throws Exception {
        when(mapper.standards("PART-101",false)).thenReturn(List.of(new StoredStandard("PART-101","202701",null,"admin",BigDecimal.ONE,BigDecimal.ZERO,"admin",null,null,null)));
        mvc.perform(get("/api/sales-parts/PART-101")).andExpect(status().isOk()).andExpect(jsonPath("$.data.part.currentStandard").isEmpty())
                .andExpect(jsonPath("$.data.part.scheduledChange.status").value("FUTURE")).andExpect(jsonPath("$.data.standards[0].effectiveMonth").value("2027-01"));
        verifyNoInteractions(history);
    }
    @Test void missingOrHqPart() throws Exception {mvc.perform(get("/api/sales-parts/HQ")).andExpect(status().isNotFound());verifyNoInteractions(history);}
    @Test void listFiltersTotalAndEmptyPage() throws Exception {
        mvc.perform(get("/api/sales-parts").param("q"," 영업 ").param("useYn","N").param("page","2").param("size","20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items").isEmpty());
        verify(mapper).list(argThat(q->q.q().equals("영업")&&q.useYn().equals("N")));
    }
    @Test void omittedStatusIsAll() throws Exception {mvc.perform(get("/api/sales-parts")).andExpect(status().isOk());verify(mapper).list(argThat(q->q.useYn()==null));}
    @ParameterizedTest @ValueSource(strings={"page=0","size=10","sort=unknown","direction=down","useYn=ALL","processor=admin"})
    void invalidListQuery(String pair) throws Exception {
        String[] bits=pair.split("=");mvc.perform(get("/api/sales-parts").param(bits[0],bits[1])).andExpect(status().isBadRequest());verifyNoInteractions(mapper,history);
    }
}
