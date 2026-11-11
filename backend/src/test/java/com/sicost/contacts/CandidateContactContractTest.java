package com.sicost.contacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import static com.sicost.contacts.CandidateContactDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CandidateContactController.class)
@ContextConfiguration(classes=CandidateContactController.class)
@Import({CandidateContactService.class,CandidateContactRepository.class,CandidateRevisionService.class,RevisionService.class,
    ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class CandidateContactContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean CandidateContactMapper mapper;
    @MockBean CandidateRevisionMapper snapshots;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean CommitInvalidation invalidation;
    @MockBean Clock clock;
    @MockBean PlatformTransactionManager manager;
    final String id="CAND-900001";
    final String snapshot="{\"candidate\":{\"cand_id\":\"CAND-900001\",\"mod_dttm\":null},\"skills\":[],\"contacts\":[]}";
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T16:00:00Z"));
        when(users.userId()).thenReturn("admin");
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(snapshots.lockCandidate(id)).thenReturn(id);when(snapshots.snapshot(id)).thenReturn(snapshot);
        when(mapper.candidate(id)).thenReturn(new Candidate(id,"BEFORE",LocalDate.parse("2026-05-01")));
        when(mapper.list(any())).thenReturn(List.of(new Contact(2,"BEFORE","정보 수정",LocalDate.parse("2026-06-15"),"admin","관리자")));
        when(mapper.count(id)).thenReturn(1L);when(mapper.greatestSerial(id)).thenReturn(2);
        when(mapper.insert(any())).thenReturn(1);when(mapper.updateCandidate(any())).thenReturn(1);
        when(mapper.contact(id,3)).thenReturn(new Contact(3,"BEFORE","연락 메모",LocalDate.parse("2026-06-16"),"admin","관리자"));
    }
    ObjectNode body(){return json.createObjectNode().put("contactStatus","BEFORE").put("memo"," 연락 메모 ").put("expectedRevision",revisions.revision(snapshot));}
    void rejected(ObjectNode body,String field) throws Exception {
        mvc.perform(post("/api/candidates/{id}/contacts",id).contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));
        verify(mapper,never()).insert(any());verify(mapper,never()).updateCandidate(any());verifyNoInteractions(history);
    }
    @Test void publicListHasExactEnvelopeAndPagingWithoutWrites() throws Exception {
        mvc.perform(get("/api/candidates/{id}/contacts",id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.items[0].sn").value(2)).andExpect(jsonPath("$.data.items[0].status").value("BEFORE"))
            .andExpect(jsonPath("$.data.items[0].memo").value("정보 수정")).andExpect(jsonPath("$.data.items[0].contactDate").value("2026-06-15"))
            .andExpect(jsonPath("$.data.items[0].actorId").value("admin")).andExpect(jsonPath("$.data.items[0].actorName").value("관리자"))
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.size").value(50));
        verify(users).userId();verify(mapper,never()).insert(any());verify(mapper,never()).updateCandidate(any());verifyNoInteractions(history,snapshots);
    }
    @Test void beyondLastPageIsEmptyAndTotalIsUnchanged() throws Exception {
        when(mapper.list(any())).thenReturn(List.of());when(mapper.count(id)).thenReturn(25L);
        mvc.perform(get("/api/candidates/{id}/contacts",id).param("page","999").param("size","20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.total").value(25));
        verify(mapper).list(new Selection(id,20,19960));
    }
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","sort","direction","contactStatus","q"})
    void listRejectsUnknownFields(String field) throws Exception {
        mvc.perform(get("/api/candidates/{id}/contacts",id).param(field,"other")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper,history);
    }
    @ParameterizedTest @ValueSource(strings={"page=0","page=-1","page=1.2","page=2147483648","size=21","size=","size=all"})
    void rejectsInvalidPagination(String query) throws Exception {
        String[] part=query.split("=",-1);
        mvc.perform(get("/api/candidates/{id}/contacts",id).param(part[0],part[1])).andExpect(status().isBadRequest());verifyNoInteractions(mapper,history);
    }
    @Test void rejectsRepeatedPagination() throws Exception {
        mvc.perform(get("/api/candidates/{id}/contacts",id).param("page","1","2")).andExpect(status().isBadRequest());verifyNoInteractions(mapper,history);
    }
    @Test void sameStatusIsANewEventAndUpdatesSeoulTodayAsAdminWithoutAuthentication() throws Exception {
        mvc.perform(post("/api/candidates/{id}/contacts",id).contentType("application/json").content(body().toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.contact.sn").value(3))
            .andExpect(jsonPath("$.data.contact.memo").value("연락 메모")).andExpect(jsonPath("$.data.contact.actorId").value("admin"))
            .andExpect(jsonPath("$.data.contactStatus").value("BEFORE")).andExpect(jsonPath("$.data.lastContactDate").value("2026-06-16"));
        verify(mapper).insert(argThat(m->m.id().equals(id)&&m.sn()==3&&m.memo().equals("연락 메모")&&m.actor().equals("admin")&&m.contactDate().equals(LocalDate.parse("2026-06-16"))));
        verify(mapper).updateCandidate(any());verify(history).insert(argThat(e->e.tableName().equals("TB_HR_CAND_CNTC")&&e.targetKey().equals(id+":3")&&e.type().equals("NEW")&&e.userId().equals("admin")));
        var order=inOrder(snapshots,mapper);order.verify(snapshots).lockCandidate(id);order.verify(snapshots).lockSkills(id);order.verify(snapshots).lockContacts(id);order.verify(snapshots).snapshot(id);order.verify(mapper).candidate(id);
        verify(manager).commit(any());verify(invalidation).afterCommit("candidates");
    }
    @ParameterizedTest @ValueSource(strings={"BEFORE","DONE","ITVW","HOLD","CTRT"})
    void everyContactStatusCanBeRecorded(String target) throws Exception {
        when(mapper.contact(id,3)).thenReturn(new Contact(3,target,"연락 메모",LocalDate.parse("2026-06-16"),"admin","관리자"));
        mvc.perform(post("/api/candidates/{id}/contacts",id).contentType("application/json").content(body().put("contactStatus",target).toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.contact.status").value(target)).andExpect(jsonPath("$.data.contactStatus").value(target));
        verify(mapper).updateCandidate(argThat(m->m.status().equals(target)));
    }
    @Test void acceptsExactlyTwoHundredCharactersWithoutTruncation() throws Exception {
        mvc.perform(post("/api/candidates/{id}/contacts",id).contentType("application/json").content(body().put("memo","가".repeat(200)).toString())).andExpect(status().isCreated());
        verify(mapper).insert(argThat(m->m.memo().equals("가".repeat(200))));
    }
    @Test void emptyMemoIsRejected() throws Exception {rejected(body().put("memo"," \n "),"memo");}
    @Test void longMemoIsRejected() throws Exception {rejected(body().put("memo","가".repeat(201)),"memo");}
    @Test void numericMemoIsRejected() throws Exception {rejected(body().put("memo",42),"memo");}
    @Test void invalidStatusIsRejected() throws Exception {rejected(body().put("contactStatus","DELETE"),"contactStatus");}
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","modUserId","regDttm","contactDate","lastContactDate","status","sn","id"})
    void rejectsSystemAndDateInjection(String field) throws Exception {
        mvc.perform(post("/api/candidates/{id}/contacts",id).contentType("application/json").content(body().put(field,"other").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper,history);
    }
    @Test void postRejectsQueryActor() throws Exception {
        mvc.perform(post("/api/candidates/{id}/contacts",id).param("actor","other").contentType("application/json").content(body().toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper,history);
    }
    @Test void missingRevisionIsValidationError() throws Exception {var input=body();input.remove("expectedRevision");rejected(input,"expectedRevision");}
    @Test void staleRevisionCannotWriteEvenSameStatus() throws Exception {
        mvc.perform(post("/api/candidates/{id}/contacts",id).contentType("application/json").content(body().put("expectedRevision","old").toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        verify(mapper,never()).insert(any());verifyNoInteractions(history);verify(manager).rollback(any());
    }
    @Test void serialExhaustionIsConflictWithNoEventOrParentWrite() throws Exception {
        when(mapper.greatestSerial(id)).thenReturn(99999);
        mvc.perform(post("/api/candidates/{id}/contacts",id).contentType("application/json").content(body().toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SERIAL_LIMIT"));
        verify(mapper,never()).insert(any());verify(mapper,never()).updateCandidate(any());verifyNoInteractions(history);
    }
    @Test void absentCandidateIs404OnList() throws Exception {
        mvc.perform(get("/api/candidates/CAND-999999/contacts")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
    @Test void absentCandidateIs404OnCreate() throws Exception {
        mvc.perform(post("/api/candidates/CAND-999999/contacts").contentType("application/json").content(body().toString()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));verifyNoInteractions(history);
    }
    @Test void malformedCandidateIdIs400() throws Exception {mvc.perform(get("/api/candidates/not-an-id/contacts")).andExpect(status().isBadRequest());}
    @Test void historyFailureUsesSafeErrorAndRollsBack() throws Exception {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("SQL private contact")).when(history).insert(any());
        var response=mvc.perform(post("/api/candidates/{id}/contacts",id).header("X-Request-Id","contacts-request")
            .contentType("application/json").content(body().toString())).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(jsonPath("$.requestId").value("contacts-request"))
            .andExpect(header().string("X-Request-Id","contacts-request")).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("SQL","private contact","연락 메모");verify(manager).rollback(any());verify(manager,never()).commit(any());
    }
}
