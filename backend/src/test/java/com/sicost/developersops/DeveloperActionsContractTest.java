package com.sicost.developersops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.concurrent.DeveloperAggregateRevision;
import com.sicost.common.concurrent.DeveloperRevisionMapper;
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
import static com.sicost.developersops.DeveloperActionsDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeveloperActionsController.class)
@ContextConfiguration(classes=DeveloperActionsController.class)
@Import({DeveloperActionsQueryService.class,DeveloperActionsRepository.class,DeveloperBulkStatusService.class,
    DeveloperUseStatusService.class,DeveloperAggregateRevision.class,RevisionService.class,ChangeHistoryWriter.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class DeveloperActionsContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @Autowired DeveloperActionsRepository repository;
    @MockBean DeveloperActionsMapper mapper;
    @MockBean DeveloperRevisionMapper revisionMapper;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean DeveloperStatusRecalculationService states;
    @MockBean Clock clock;
    Base developer;
    Base row(String id,String use,LocalDate retire) {
        return new Base(id,"K-0901","시험개발자","FT",null,null,"DEV",null,null,"DRCT","HQ",null,retire,"IDLE",null,null,null,use,
            "admin",LocalDateTime.of(2026,6,1,10,0,0,123456000),null,null);
    }
    void stored(Base row) {
        when(mapper.developer(row.id(),false)).thenReturn(row); when(mapper.developer(row.id(),true)).thenReturn(row);
        when(revisionMapper.developer(eq(row.id()),anyBoolean())).thenReturn(row.toString());
    }
    @BeforeEach void setup() {
        when(users.userId()).thenReturn("admin");
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T16:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.withZone(any())).thenAnswer(call->Clock.fixed(Instant.parse("2026-06-15T16:00:00Z"),call.getArgument(0)));
        developer=row("DEV-000901","Y",null); stored(developer);
        when(mapper.selection(any())).thenReturn(List.of(developer)); when(mapper.status(any())).thenReturn(1);
    }
    SelectionItem target(Base row) { return new SelectionItem(row.id(),repository.revision(row,false)); }
    String bulk(String use,List<SelectionItem> targets) throws Exception { return json.writeValueAsString(new BulkRequest(use,targets)); }
    @Test void selectionHasOnlyContractItemsAndFullAggregateRevisionWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/developers/selection")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].id").value(developer.id()))
            .andExpect(jsonPath("$.data.items[0].revision").value(target(developer).revision()))
            .andExpect(jsonPath("$.data.total").doesNotExist());
        verify(states).refresh();verify(users).userId();
    }
    @Test void selectionPreservesTrimFiltersAndDefaultOrderAndSeoulDate() throws Exception {
        when(mapper.activeJob("DEV")).thenReturn(1);
        mvc.perform(get("/api/developers/selection").param("q"," 시험 ").param("employeeType","FT").param("jobCode","DEV")
            .param("directClass","DRCT").param("workStatus","RETIRE").param("sort","monthlyRate").param("direction","DESC"))
            .andExpect(status().isOk());
        verify(mapper).selection(argThat(f->f.q().equals("시험") && f.includeStopped() && f.direction().equals("DESC") && f.today().equals(LocalDate.of(2026,6,16))));
    }
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","page","size","unknown"})
    void selectionUnknownQueryIs400WithMatchingRequestIdAndNoWrites(String field) throws Exception {
        var response=mvc.perform(get("/api/developers/selection").param(field,"other"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andReturn();
        assertThat(response.getResponse().getHeader("X-Request-Id")).isEqualTo(json.readTree(response.getResponse().getContentAsString()).get("requestId").asText());
        verifyNoInteractions(states,mapper,history);
    }
    @ParameterizedTest @ValueSource(strings={"employeeType","workStatus","directClass","sort","direction","includeStopped"})
    void selectionInvalidEnumHasNoReadOrWrite(String field) throws Exception {
        mvc.perform(get("/api/developers/selection").param(field,"invalid")).andExpect(status().isBadRequest());verifyNoInteractions(mapper,states,history);
    }
    @Test void assignmentHistoryIncludesCancelledAndEndedWithStringMmAndNoWrites() throws Exception {
        when(mapper.assignments(any())).thenReturn(List.of(new Assignment("A-901","P-901","동명","DEV","개발",LocalDate.of(2025,1,1),LocalDate.of(2025,2,1),"2.50","CNCL")));
        when(mapper.assignmentCount(developer.id())).thenReturn(21L);
        mvc.perform(get("/api/developers/DEV-000901/assignments").param("page","2").param("size","20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].assignmentStatus").value("CNCL"))
            .andExpect(jsonPath("$.data.items[0].contractMm").value("2.50")).andExpect(jsonPath("$.data.total").value(21))
            .andExpect(jsonPath("$.data.items[0].startDate").value("2025-01-01"));
        verify(mapper).assignments(new AssignmentQuery(developer.id(),2,20,20));verifyNoInteractions(history,states);
    }
    @Test void assignmentsMissingIs404() throws Exception { mvc.perform(get("/api/developers/DEV-999999/assignments")).andExpect(status().isNotFound()); }
    @Test void assignmentsBeyondLastPageIsEmpty() throws Exception {
        mvc.perform(get("/api/developers/DEV-000901/assignments").param("page","999")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","abc","2147483648"})
    void assignmentsInvalidPageHasNoInteractions(String page) throws Exception {
        mvc.perform(get("/api/developers/DEV-000901/assignments").param("page",page)).andExpect(status().isBadRequest());verifyNoInteractions(mapper,history);
    }
    @Test void assignmentsInvalidSizeIs400() throws Exception { mvc.perform(get("/api/developers/DEV-000901/assignments").param("size","30")).andExpect(status().isBadRequest());verifyNoInteractions(mapper); }
    @Test void assignmentsInjectedActorIs400() throws Exception { mvc.perform(get("/api/developers/DEV-000901/assignments").param("actor","other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper); }
    @Test void stopUsesAdminAndAppendsStopOnly() throws Exception {
        mvc.perform(post("/api/developers/bulk-status").contentType("application/json").content(bulk("N",List.of(target(developer)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.appliedCount").value(1)).andExpect(jsonPath("$.data.applied[0].id").value(developer.id()));
        verify(mapper).status(argThat(m->m.useYn().equals("N") && m.actor().equals("admin")));
        verify(history).insert(argThat(h->h.type().equals("STOP") && h.before().equals("Y") && h.after().equals("N")));
    }
    @Test void resumeManualStopAppendsResume() throws Exception {
        developer=row(developer.id(),"N",null);stored(developer);
        mvc.perform(post("/api/developers/bulk-status").contentType("application/json").content(bulk("Y",List.of(target(developer)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.appliedCount").value(1));
        verify(history).insert(argThat(h->h.type().equals("RESUME")));
    }
    @Test void noChangeDoesNotChangeAuditOrHistory() throws Exception {
        mvc.perform(post("/api/developers/bulk-status").contentType("application/json").content(bulk("Y",List.of(target(developer)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.noChangeCount").value(1));
        verify(mapper,never()).status(any());verifyNoInteractions(history);
    }
    @Test void staleEvenSameUseIsRejectedBeforeBusinessChecks() throws Exception {
        mvc.perform(post("/api/developers/bulk-status").contentType("application/json").content(bulk("Y",List.of(new SelectionItem(developer.id(),"stale")))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.rejected[0].code").value("STALE_WRITE"));
        verify(mapper,never()).blockingProjects(any(),any());verifyNoInteractions(history);
    }
    @Test void futureOrTodayEndingActBlocksStopWithProjectPeriods() throws Exception {
        when(mapper.blockingProjects(any(),any())).thenReturn(List.of(new Project("P-1","예정",LocalDate.of(2026,7,1),LocalDate.of(2026,8,1))));
        mvc.perform(post("/api/developers/bulk-status").contentType("application/json").content(bulk("N",List.of(target(developer)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.rejected[0].code").value("DEVELOPER_IN_USE"))
            .andExpect(jsonPath("$.data.rejected[0].details.referenceCount").value(1)).andExpect(jsonPath("$.data.rejected[0].details.projects[0].id").value("P-1"));
        verify(mapper,never()).status(any());verifyNoInteractions(history);
    }
    @Test void actualRetireCannotResumeAndMessageIsExact() throws Exception {
        developer=row(developer.id(),"N",LocalDate.of(2026,6,15));stored(developer);
        mvc.perform(post("/api/developers/bulk-status").contentType("application/json").content(bulk("Y",List.of(target(developer)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.rejected[0].code").value("RETIRED_DEVELOPER"))
            .andExpect(jsonPath("$.data.rejected[0].message").value("퇴사한 개발자는 다시 사용할 수 없습니다. 새로 등록하세요."));
        verify(mapper,never()).status(any());verifyNoInteractions(history);
    }
    @Test void mixedResponseConservesEveryItem() throws Exception {
        Base stopped=row("DEV-000902","N",null);stored(stopped);
        mvc.perform(post("/api/developers/bulk-status").contentType("application/json").content(bulk("N",List.of(target(developer),target(stopped),new SelectionItem("DEV-000903","missing")))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.appliedCount").value(1)).andExpect(jsonPath("$.data.noChangeCount").value(1))
            .andExpect(jsonPath("$.data.rejectedCount").value(1)).andExpect(jsonPath("$.data.rejected[0].code").value("NOT_FOUND"));
        verify(mapper,times(1)).status(any());verify(history,times(1)).insert(any());
    }
    @Test void duplicateIdRejectsWholeRequest() throws Exception {
        mvc.perform(post("/api/developers/bulk-status").contentType("application/json").content(bulk("N",List.of(target(developer),target(developer)))))
            .andExpect(status().isBadRequest());verifyNoInteractions(history);verify(mapper,never()).status(any());
    }
    @Test void duplicateRevisionRejectsWholeRequest() throws Exception {
        mvc.perform(post("/api/developers/bulk-status").contentType("application/json").content(bulk("N",List.of(new SelectionItem(developer.id(),"same"),new SelectionItem("DEV-000902","same")))))
            .andExpect(status().isBadRequest());verifyNoInteractions(mapper,history);
    }
    @Test void invalidTargetRejectsWholeRequest() throws Exception {
        mvc.perform(post("/api/developers/bulk-status").contentType("application/json").content(bulk("STOP",List.of(target(developer)))))
            .andExpect(status().isBadRequest());verify(mapper,never()).status(any());verifyNoInteractions(history);
    }
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","modDttm"})
    void forgedFieldsNeverReachWrites(String field) throws Exception {
        var body=json.createObjectNode().put("targetUseYn","N").put(field,"other");body.putArray("items");
        mvc.perform(post("/api/developers/bulk-status").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper,history);
    }
    @Test void databaseFailureIsSanitizedAndFollowingItemContinues() throws Exception {
        when(history.insert(any())).thenThrow(new IllegalStateException("SQL private"));
        mvc.perform(post("/api/developers/bulk-status").contentType("application/json").content(bulk("N",List.of(target(developer),new SelectionItem("DEV-000902","missing")))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.rejectedCount").value(2))
            .andExpect(jsonPath("$.data.rejected[0].code").value("INTERNAL_ERROR")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL private"))));
    }
    @Test void childChangesAffectRevisionWithNullModificationTimes() {
        String first=target(developer).revision();
        when(revisionMapper.skills(developer.id(),false)).thenReturn(List.of("{\"skill_nm\":\"Java\",\"sort_ord\":1}"));
        assertThat(target(developer).revision()).isNotEqualTo(first);
    }
    @Test void childTimestampNanosecondsAreNotCollapsedToMilliseconds() {
        var first=java.sql.Timestamp.valueOf("2026-06-01 10:00:00.123456001");
        var second=java.sql.Timestamp.valueOf("2026-06-01 10:00:00.123456002");
        when(revisionMapper.prices(developer.id(),false)).thenReturn(List.of(first.toString()));
        String before=target(developer).revision();
        when(revisionMapper.prices(developer.id(),false)).thenReturn(List.of(second.toString()));
        assertThat(target(developer).revision()).isNotEqualTo(before);
    }
}
