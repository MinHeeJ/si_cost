package com.sicost.clientsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.security.AccountCipher;
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
import static com.sicost.clientsops.ClientActionsDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientActionsController.class)
@ContextConfiguration(classes=ClientActionsController.class)
@Import({ClientActionsService.class,ClientActionsRepository.class,ClientAccountService.class,ClientStatusService.class,
    ClientBulkStatusService.class,RevisionService.class,ChangeHistoryWriter.class,AccessLogWriter.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ClientActionsContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean ClientActionsMapper mapper;
    @MockBean AccountCipher cipher;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AccessLogMapper access;
    @MockBean CommitInvalidation invalidation;
    @MockBean Clock clock;
    ClientSnapshot client;
    ClientSnapshot row(String id,String type,String use) {
        return new ClientSnapshot(id,type,"검증 거래처","123-45-67890",null,null,null,null,null,"BANK",null,"예금주",use,
            "admin",LocalDateTime.of(2026,6,1,10,0,0,123456000),null,null);
    }
    @BeforeEach void setup() {
        when(users.userId()).thenReturn("admin");
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T16:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.withZone(any())).thenAnswer(call->Clock.fixed(Instant.parse("2026-06-15T16:00:00Z"),call.getArgument(0)));
        client=row("C-901","VNDR","Y");
        when(mapper.client("C-901",false)).thenReturn(client);
        when(mapper.client("C-901",true)).thenReturn(client);
        when(mapper.bankName("BANK")).thenReturn("은행");
        when(mapper.status(any())).thenReturn(1);
        when(access.insert(any())).thenReturn(1);
        when(mapper.references(anyString(),anyString())).thenReturn(List.of(new Reference("PROJECT",0),new Reference("DEVELOPER",0)));
        when(mapper.selection(any())).thenReturn(List.of(client));
    }
    String bulk(String target,List<Target> items) throws Exception { return json.writeValueAsString(new BulkRequest(target,items)); }
    Target target(ClientSnapshot row) { return new Target(row.id(),revisions.revision(row)); }
    @Test void accountWithoutAuthenticationReturnsOriginalAndNoStoreAfterAdminAccessRecord() throws Exception {
        when(cipher.decrypt(null)).thenReturn("001-23-45678");
        mvc.perform(get("/api/clients/C-901/account")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.accountNumber").value("001-23-45678"));
        var order=inOrder(cipher,access); order.verify(cipher).decrypt(null);
        order.verify(access).insert(argThat(entry->entry.action().equals("ACCT_VIEW") && entry.userId().equals("admin")
            && entry.screenId().equals("UI-15") && entry.description().equals("C-901")));
        verifyNoInteractions(history);
    }
    @Test void accountLogFailureNeverReturnsNumberOrSuccess() throws Exception {
        when(cipher.decrypt(null)).thenReturn("001-23-45678");
        when(access.insert(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("private"));
        mvc.perform(get("/api/clients/C-901/account")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("001-23-45678"))));
        verify(access,times(1)).insert(any());
    }
    @Test void accountConfigurationAndTagFailureReturnSanitized503() throws Exception {
        when(cipher.decrypt(null)).thenThrow(new ApiException(503,"ENCRYPTION_CONFIG_ERROR","계좌 암호화 설정을 확인해 주세요."));
        mvc.perform(get("/api/clients/C-901/account")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("ENCRYPTION_CONFIG_ERROR")).andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(access);
    }
    @Test void missingAccountReturns404WithoutDecryptOrLog() throws Exception {
        mvc.perform(get("/api/clients/C-902/account")).andExpect(status().isNotFound()); verifyNoInteractions(cipher,access);
    }
    @ParameterizedTest @ValueSource(strings={"account","assignments"})
    void unknownQueryOnDetailsIsRejectedBeforeAnyRead(String suffix) throws Exception {
        var response=mvc.perform(get("/api/clients/C-901/"+suffix).param("regUserId","other"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value("regUserId")).andReturn();
        assertThat(response.getResponse().getHeader("X-Request-Id")).isEqualTo(json.readTree(response.getResponse().getContentAsString()).get("requestId").asText());
        verifyNoInteractions(mapper,cipher,access,history);
    }
    @Test void optionsUsesValidatedPurposeAndSearchWithoutPagingOrAuthentication() throws Exception {
        when(mapper.options(any())).thenReturn(List.of(new Option("C-901","검증 거래처","BOTH")));
        mvc.perform(get("/api/clients/options").param("purpose","CUSTOMER").param("q"," 검증 "))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].type").value("BOTH"));
        verify(mapper).options(new OptionQuery("CUSTOMER","검증"));
    }
    @Test void optionsEmptyIsRealEmptyList() throws Exception {
        mvc.perform(get("/api/clients/options").param("purpose","VENDOR"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }
    @Test void optionsRequiresKnownPurpose() throws Exception {
        mvc.perform(get("/api/clients/options")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("purpose"));
        mvc.perform(get("/api/clients/options").param("purpose","ADMIN")).andExpect(status().isBadRequest());
        verifyNoInteractions(mapper);
    }
    @Test void optionsUnknownAndDuplicateQueryAreRejected() throws Exception {
        mvc.perform(get("/api/clients/options").param("purpose","VENDOR").param("size","20"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        mvc.perform(get("/api/clients/options").param("purpose","VENDOR","CUSTOMER")).andExpect(status().isBadRequest());
    }
    @Test void selectionContainsAll123CompleteRevisionsWithNoImplicitStatus() throws Exception {
        var rows=java.util.stream.IntStream.range(100,223).mapToObj(i->row("C-"+i,"CUST","N")).toList();
        when(mapper.selection(any())).thenReturn(rows);
        mvc.perform(get("/api/clients/selection")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(123))
            .andExpect(jsonPath("$.data.items.length()").value(123))
            .andExpect(jsonPath("$.data.items[122].revision").value(revisions.revision(rows.get(122))));
        verify(mapper).selection(argThat(f->f.useYn()==null && f.sort().equals("name") && f.basisDate().equals(LocalDate.of(2026,6,16))));
    }
    @Test void selectionRetainsRequestedFilterSortAndRejectsUnknownSort() throws Exception {
        mvc.perform(get("/api/clients/selection").param("type","CUST").param("useYn","Y").param("q","123-45").param("sort","projectCount").param("direction","DESC"))
            .andExpect(status().isOk());
        verify(mapper).selection(argThat(f->f.type().equals("CUST") && f.useYn().equals("Y") && f.q().equals("123-45") && f.direction().equals("DESC")));
        mvc.perform(get("/api/clients/selection").param("sort","account")).andExpect(status().isBadRequest());
    }
    @Test void selectionUnknownPageIsNotSilentlyIgnored() throws Exception {
        mvc.perform(get("/api/clients/selection").param("page","1")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(mapper);
    }
    @Test void assignmentsArePagedAtServerSeoulBasisDateWithCompleteFields() throws Exception {
        var assignment=new Assignment("A-901","P-901","프로젝트","M-901","개발자","DEV","개발",LocalDate.of(2026,6,1),LocalDate.of(2026,6,30));
        when(mapper.assignments(any())).thenReturn(List.of(assignment));when(mapper.assignmentCount(any())).thenReturn(21L);
        mvc.perform(get("/api/clients/C-901/assignments").param("page","2").param("size","20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(21)).andExpect(jsonPath("$.data.items[0].assignmentId").value("A-901"))
            .andExpect(jsonPath("$.data.basisDate").value("2026-06-16"));
        verify(mapper).assignments(argThat(q->q.offset()==20 && q.size()==20));
    }
    @Test void assignmentsEmptyAndBeyondPageHaveNoFabricatedRows() throws Exception {
        mvc.perform(get("/api/clients/C-901/assignments").param("page","100"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0)).andExpect(jsonPath("$.data.items").isEmpty());
    }
    @Test void missingAssignmentsReturns404() throws Exception { mvc.perform(get("/api/clients/C-902/assignments")).andExpect(status().isNotFound()); }
    @ParameterizedTest @ValueSource(strings={"0","-1","abc","2147483648"})
    void invalidAssignmentPageRejected(String value) throws Exception {
        mvc.perform(get("/api/clients/C-901/assignments").param("page",value)).andExpect(status().isBadRequest()); verifyNoInteractions(mapper);
    }
    @Test void invalidAssignmentSizeAndSortRejected() throws Exception {
        mvc.perform(get("/api/clients/C-901/assignments").param("size","30")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/clients/C-901/assignments").param("sort","name")).andExpect(status().isBadRequest());
    }
    @Test void statusWithoutAuthenticationAppliesStopWithServerActorAndHistory() throws Exception {
        mvc.perform(post("/api/clients/bulk-status").contentType("application/json").content(bulk("N",List.of(target(client)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(1));
        verify(mapper).status(argThat(w->w.useYn().equals("N") && w.actor().equals("admin")));
        verify(history).insert(argThat(h->h.type().equals("STOP") && h.before().equals("Y") && h.after().equals("N")));
    }
    @Test void resumeHasResumeHistoryAndDoesNotCheckStopReferences() throws Exception {
        client=row("C-901","VNDR","N");when(mapper.client("C-901",true)).thenReturn(client);
        mvc.perform(post("/api/clients/bulk-status").contentType("application/json").content(bulk("Y",List.of(target(client)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(1));
        verify(mapper,never()).references(anyString(),anyString());verify(history).insert(argThat(h->h.type().equals("RESUME")));
    }
    @Test void noChangeHasNoWritesOrReferenceChecks() throws Exception {
        mvc.perform(post("/api/clients/bulk-status").contentType("application/json").content(bulk("Y",List.of(target(client)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.noChange").value(1));
        verify(mapper,never()).status(any());verifyNoInteractions(history,invalidation);
    }
    @Test void staleEvenSameTargetIsRejectedBeforeStateOrReferences() throws Exception {
        mvc.perform(post("/api/clients/bulk-status").contentType("application/json").content(bulk("Y",List.of(new Target(client.id(),"stale")))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].error.code").value("STALE_WRITE"));
        verify(mapper,never()).references(anyString(),anyString());verify(mapper,never()).status(any());verifyNoInteractions(history);
    }
    @ParameterizedTest @ValueSource(strings={"PROJECT","DEVELOPER"})
    void stopBlockedByEveryReferenceKindWithCountAndNoHistory(String kind) throws Exception {
        when(mapper.references(anyString(),anyString())).thenReturn(List.of(new Reference(kind,1)));
        mvc.perform(post("/api/clients/bulk-status").contentType("application/json").content(bulk("N",List.of(target(client)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.rejected").value(1))
            .andExpect(jsonPath("$.data.items[0].error.code").value("REFERENCE_IN_USE"))
            .andExpect(jsonPath("$.data.items[0].error.details.references[0].count").value(1));
        verify(mapper,never()).status(any()); verifyNoInteractions(history);
    }
    @Test void mixedItemsAreConservedAndOnlySuccessfulItemWritesHistory() throws Exception {
        ClientSnapshot stopped=row("C-902","CUST","N"), blocked=row("C-903","BOTH","Y"), stale=row("C-904","VNDR","N");
        when(mapper.client(stopped.id(),true)).thenReturn(stopped);when(mapper.client(blocked.id(),true)).thenReturn(blocked);when(mapper.client(stale.id(),true)).thenReturn(stale);
        when(mapper.references(blocked.id(),blocked.type())).thenReturn(List.of(new Reference("DEVELOPER",1)));
        mvc.perform(post("/api/clients/bulk-status").contentType("application/json").content(bulk("N",List.of(target(client),target(stopped),target(blocked),new Target(stale.id(),"stale")))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(1)).andExpect(jsonPath("$.data.noChange").value(1))
            .andExpect(jsonPath("$.data.rejected").value(2)).andExpect(jsonPath("$.data.items.length()").value(4));
        verify(mapper,times(1)).status(any());verify(history,times(1)).insert(any());
    }
    @Test void historyFailureIsRejectedAndFollowingItemStillProcessed() throws Exception {
        when(history.insert(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("private"));
        mvc.perform(post("/api/clients/bulk-status").contentType("application/json").content(bulk("N",List.of(target(client),new Target("C-902","missing")))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.rejected").value(2)).andExpect(jsonPath("$.data.items[0].error.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.data.items[1].error.code").value("NOT_FOUND"));
    }
    @Test void duplicateIdsRejectEntireRequestBeforeAnyWrite() throws Exception {
        mvc.perform(post("/api/clients/bulk-status").contentType("application/json").content(bulk("N",List.of(target(client),target(client)))))
            .andExpect(status().isBadRequest());verifyNoInteractions(mapper,history);
    }
    @Test void invalidTargetRejectsEntireRequest() throws Exception {
        mvc.perform(post("/api/clients/bulk-status").contentType("application/json").content(bulk("STOP",List.of(target(client)))))
            .andExpect(status().isBadRequest());verifyNoInteractions(mapper,history);
    }
    @Test void injectedActorInBodyIsRejected() throws Exception {
        var body=json.readTree(bulk("N",List.of(target(client))));((com.fasterxml.jackson.databind.node.ObjectNode)body).put("regUserId","other");
        mvc.perform(post("/api/clients/bulk-status").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper,history);
    }
    @Test void injectedActorInItemIsRejected() throws Exception {
        var body=json.readTree(bulk("N",List.of(target(client))));((com.fasterxml.jackson.databind.node.ObjectNode)body.get("items").get(0)).put("modDttm","other");
        mvc.perform(post("/api/clients/bulk-status").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper,history);
    }
    @Test void revisionIncludesEditableValuesAndRawTimestampNotDateOrCounts() {
        var different=row("C-901","BOTH","Y");assertThat(revisions.revision(different)).isNotEqualTo(revisions.revision(client));
        assertThat(revisions.revision(client)).isEqualTo(revisions.revision(client));
    }
}
