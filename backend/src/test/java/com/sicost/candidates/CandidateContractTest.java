package com.sicost.candidates;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.areasops.*;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.persistence.MasterIdService;
import com.sicost.common.validation.PeopleInputPolicy.CodeSelection;
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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CandidateController.class)
@ContextConfiguration(classes=CandidateController.class)
@Import({CandidateService.class,CandidateQueryService.class,CandidateRepository.class,CandidateRevisionService.class,RevisionService.class,
    ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class CandidateContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean CandidateMapper mapper;
    @MockBean CandidateRevisionMapper snapshots;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean CommitInvalidation invalidation;
    @MockBean MasterIdService ids;
    @MockBean AddressService addresses;
    @MockBean Clock clock;
    @MockBean PlatformTransactionManager manager;
    final String id="CAND-900001",snapshot="candidate-snapshot";
    Map<String,Object> old;
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T16:00:00Z"));
        when(clock.withZone(any())).thenReturn(Clock.fixed(Instant.parse("2026-06-15T16:00:00Z"),ZoneId.of("Asia/Seoul")));
        when(users.userId()).thenReturn("admin");when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(ids.nextCandidateId()).thenReturn(id);when(snapshots.lockCandidate(id)).thenReturn(id);when(snapshots.snapshot(id)).thenReturn(snapshot);
        when(mapper.code(anyString(),anyString())).thenAnswer(a->new CodeSelection(a.getArgument(1),"Y","N"));
        when(addresses.resolve(any(),any(),eq("SGG"))).thenReturn(new AddressDtos.Selection(null,null));
        old=new LinkedHashMap<>();old.put("id",id);old.put("name","후보");old.put("employeeType","FL");old.put("jobCode","DEV");old.put("gradeCode","MD");old.put("sourceCode","SITE");old.put("desiredRate",new java.math.BigDecimal("1"));old.put("availableDate",LocalDate.parse("2026-07-16"));old.put("consent",false);old.put("contactStatus","BEFORE");old.put("lastContactDate",LocalDate.parse("2026-06-01"));
        when(mapper.find(anyString())).thenReturn(null);when(mapper.find(id)).thenReturn(old);when(mapper.labels(id)).thenReturn(Map.of("job","개발","grade","중급","source","구인 사이트"));
        when(mapper.skills(id)).thenReturn(List.of("Java","SQL"));when(mapper.list(any())).thenReturn(List.of(old));when(mapper.count(any())).thenReturn(1L);
        when(mapper.insert(any())).thenReturn(1);when(mapper.update(any())).thenReturn(1);when(mapper.insertSkill(any())).thenReturn(1);when(mapper.insertContact(any())).thenReturn(1);when(mapper.greatestSerial(id)).thenReturn(1);
    }
    ObjectNode body(){ObjectNode n=json.createObjectNode().put("name"," 후보 ").put("employeeType","FL").put("jobCode","DEV").put("gradeCode","MD").put("sourceCode","SITE").put("desiredRate","1 원").put("availableDate","2026-07-16").put("consent",false);n.putArray("skills").add(" Java, SQL ").add("Java");return n;}
    ObjectNode edit(){return body().put("expectedRevision",revisions.revision(snapshot));}
    void reject(ObjectNode body,String field) throws Exception {mvc.perform(post("/api/candidates").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));verify(mapper,never()).insert(any());verifyNoInteractions(history);}
    @Test void publicCreateCommitsNormalizedParentSkillsRegistrationAndNewAsAdmin() throws Exception {
        mvc.perform(post("/api/candidates").contentType("application/json").content(body().toString())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(id));
        verify(mapper).insert(argThat(r->r.get("name").equals("후보")&&r.get("consentDate")==null&&r.get("consentUserId")==null&&r.get("actor").equals("admin")&&r.get("today").equals(LocalDate.parse("2026-06-16"))));
        verify(mapper).insertSkill(argThat(r->r.get("name").equals("Java")&&r.get("order").equals(1)));verify(mapper).insertSkill(argThat(r->r.get("name").equals("SQL")&&r.get("order").equals(2)));
        verify(mapper).insertContact(argThat(r->r.get("sn").equals(1)&&r.get("status").equals("BEFORE")&&r.get("memo").equals("등록")));
        verify(history).insert(argThat(h->h.tableName().equals("TB_HR_CAND")&&h.type().equals("NEW")&&h.userId().equals("admin")));verify(manager).commit(any());
    }
    @Test void publicListHasExactDataAndNoBusinessWrites() throws Exception {
        mvc.perform(get("/api/candidates")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(id)).andExpect(jsonPath("$.data.items[0].desiredRate").value("1")).andExpect(jsonPath("$.data.items[0].availableDate").value("2026-07-16")).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.size").value(50));verify(mapper,never()).update(any());verifyNoInteractions(history);
    }
    @Test void publicDetailIncludesRequiredNullsAndSharedAggregateRevision() throws Exception {
        var response=mvc.perform(get("/api/candidates/{id}",id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.consent").value(false)).andExpect(jsonPath("$.data.revision").value(revisions.revision(snapshot))).andReturn().getResponse();
        var data=json.readTree(response.getContentAsString()).get("data");for(String key:List.of("phone","email","companyId","consentDate","consentUserId","consentUserName","homeAreaCode","subJobCode","memo"))assertThat(data.has(key)).isTrue();assertThat(data.get("consentDate").isNull()).isTrue();verifyNoInteractions(history);
    }
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","lastContactDate","contactStatus","consentUserId","modDttm","revision","id"})
    void rejectsSystemBodyFieldsForCreateAndUpdate(String field) throws Exception {
        for(boolean edit:List.of(false,true))mvc.perform((edit?put("/api/candidates/{id}",id):post("/api/candidates")).contentType("application/json").content((edit?edit():body()).put(field,"other").toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(history);verify(mapper,never()).update(any());
    }
    @ParameterizedTest @ValueSource(strings={"/api/candidates","/api/candidates/CAND-900001"})
    void queryActorIsRejectedOnEveryRead(String path) throws Exception {mvc.perform(get(path).param("actor","other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper,history);}
    @ParameterizedTest @ValueSource(strings={"page=0","page=-1","page=1.2","page=2147483648","size=21","employeeType=FT","availability=TODAY","contactStatus=BAD","sort=phone","direction=sideways"})
    void invalidListQueryCannotWrite(String value) throws Exception {var p=value.split("=",-1);mvc.perform(get("/api/candidates").param(p[0],p[1])).andExpect(status().isBadRequest());verifyNoInteractions(mapper,history);}
    @Test void detailRejectsPaginationAndMissingCandidateIs404() throws Exception {mvc.perform(get("/api/candidates/{id}",id).param("page","1")).andExpect(status().isBadRequest());mvc.perform(get("/api/candidates/CAND-999999")).andExpect(status().isNotFound());}
    @Test void nextMonthUsesMonthEndAndCountUsesIdenticalSelection() throws Exception {mvc.perform(get("/api/candidates").param("availability","NEXT_MONTH").param("gradeCode","MD").param("page","2").param("size","20")).andExpect(status().isOk());var selection=new CandidateInput.Selection(CandidateInput.query(Map.of("availability","NEXT_MONTH","gradeCode","MD","page","2","size","20")),LocalDate.parse("2026-07-31"),20);verify(mapper).list(selection);verify(mapper).count(selection);}
    @ParameterizedTest @ValueSource(strings={"0","-1","1.1","1e3","1000000000000000"}) void invalidRate(String rate) throws Exception {reject(body().put("desiredRate",rate),"desiredRate");}
    @Test void invalidPhone() throws Exception {reject(body().put("phone","1234"),"phone");}
    @Test void emailRejectsWhitespaceButAcceptsLetterS() throws Exception {reject(body().put("email","s @test.com").put("consent",true),"email");mvc.perform(post("/api/candidates").contentType("application/json").content(body().put("email","s@test.com").put("consent",true).toString())).andExpect(status().isCreated());}
    @Test void invalidDate() throws Exception {reject(body().put("availableDate","2026-02-30"),"availableDate");}
    @Test void emptySkills() throws Exception {var b=body();b.putArray("skills");reject(b,"skills");}
    @ParameterizedTest @ValueSource(strings={"phone","email","both"}) void contactsNeedExplicitConsent(String kind) throws Exception {var b=body();if(!kind.equals("email"))b.put("phone","010-1234-5678");if(!kind.equals("phone"))b.put("email","s@test.com");mvc.perform(post("/api/candidates").contentType("application/json").content(b.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));verifyNoInteractions(history);}
    @Test void outsourcingNeedsCompany() throws Exception {reject(body().put("employeeType","OS"),"companyId");}
    @Test void outsourcingRejectsStoppedOrCustomerCompany() throws Exception {for(var company:List.of(Map.<String,Object>of("useYn","N","type","VNDR"),Map.<String,Object>of("useYn","Y","type","CUST"))){when(mapper.company("C-901")).thenReturn(company);reject(body().put("employeeType","OS").put("companyId","C-901"),"companyId");}}
    @Test void freelancerDiscardsCompanyWithWarning() throws Exception {mvc.perform(post("/api/candidates").contentType("application/json").content(body().put("companyId","C-901").toString())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.warnings[0].field").value("companyId"));verify(mapper).insert(argThat(r->r.get("companyId")==null));}
    @Test void emdAddressIsRejectedWithNoWrites() throws Exception {when(addresses.resolve(eq("1111010100"),any(),eq("SGG"))).thenThrow(new ApiException(400,"INVALID_AREA","후보 주소는 시군구까지입니다.","homeAreaCode",null));reject(body().put("homeAreaCode","1111010100"),"homeAreaCode");}
    @Test void newConsentUsesSeoulTodayAdmin() throws Exception {mvc.perform(post("/api/candidates").contentType("application/json").content(body().put("consent",true).toString())).andExpect(status().isCreated());verify(mapper).insert(argThat(r->r.get("consentDate").equals(LocalDate.parse("2026-06-16"))&&r.get("consentUserId").equals("admin")));}
    @Test void updateAndNoOpAreDistinctAndLastContactIsNeverWritten() throws Exception {
        mvc.perform(put("/api/candidates/{id}",id).contentType("application/json").content(edit().put("memo","실제 수정").toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        verify(mapper).update(argThat(r->!r.containsKey("lastContactDate")&&!r.containsKey("contactStatus")));verify(mapper).insertContact(argThat(r->r.get("sn").equals(2)&&r.get("memo").equals("정보 수정")));
        verify(history).insert(argThat(h->h.item().equals("memo")&&h.after().equals("실제 수정")));
    }
    @Test void noOpDoesNotTouchTimestampSkillsContactsHistory() throws Exception {mvc.perform(put("/api/candidates/{id}",id).contentType("application/json").content(edit().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));verify(mapper,never()).update(any());verify(mapper,never()).deleteSkills(any());verify(mapper,never()).insertContact(any());verifyNoInteractions(history);}
    @Test void keepingTruePreservesConsentMetadata() throws Exception {old.put("consent",true);old.put("consentDate",LocalDate.parse("2026-01-01"));old.put("consentUserId","original");mvc.perform(put("/api/candidates/{id}",id).contentType("application/json").content(edit().put("consent",true).put("memo","수정").toString())).andExpect(status().isOk());verify(mapper).update(argThat(r->r.get("consentDate").equals(LocalDate.parse("2026-01-01"))&&r.get("consentUserId").equals("original")));}
    @Test void explicitFalseWithoutContactsClearsMetadata() throws Exception {old.put("consent",true);old.put("consentDate",LocalDate.parse("2026-01-01"));old.put("consentUserId","original");mvc.perform(put("/api/candidates/{id}",id).contentType("application/json").content(edit().toString())).andExpect(status().isOk());verify(mapper).update(argThat(r->r.get("consentDate")==null&&r.get("consentUserId")==null));}
    @Test void staleCannotWriteEvenNoOp() throws Exception {mvc.perform(put("/api/candidates/{id}",id).contentType("application/json").content(edit().put("expectedRevision","old").toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));verify(mapper,never()).update(any());verifyNoInteractions(history);verify(manager).rollback(any());}
    @Test void historyFailureRollsBackAndDoesNotLeakPersonalValues() throws Exception {doThrow(new org.springframework.dao.DataIntegrityViolationException("SQL private")).when(history).insert(any());var response=mvc.perform(post("/api/candidates").header("X-Request-Id","candidate-request").contentType("application/json").content(body().toString())).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.requestId").value("candidate-request")).andReturn().getResponse();assertThat(response.getContentAsString()).doesNotContain("SQL","private","후보");verify(manager).rollback(any());}
}
