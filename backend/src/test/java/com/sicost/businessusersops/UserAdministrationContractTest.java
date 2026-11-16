package com.sicost.businessusersops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.businessusers.*;
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
import static com.sicost.businessusers.BusinessUserDtos.*;
import static com.sicost.businessusersops.UserAdministrationDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserAdministrationController.class)
@ContextConfiguration(classes=UserAdministrationController.class)
@Import({UserAdministrationService.class,UserAdministrationRepository.class,BusinessUserService.class,BusinessUserRepository.class,
    WriteCoordinator.class,RevisionService.class,ChangeHistoryWriter.class,AccessLogWriter.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class UserAdministrationContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean BusinessUserMapper usersMapper;
    @MockBean UserAdministrationMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AccessLogMapper access;
    @MockBean Clock clock;
    @MockBean PlatformTransactionManager transactions;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    final Map<String,String> states=new HashMap<>();
    @BeforeEach void setup() {
        states.clear();states.put("a123","USE");states.put("b123","STOP");states.put("c123","LOCK");
        when(users.userId()).thenReturn("admin");
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.withZone(any())).thenReturn(Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        when(transactions.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(usersMapper.find(anyString(),anyBoolean())).thenAnswer(c->{
            String id=c.getArgument(0);return states.containsKey(id)?new Stored(id,"사용자",id+"@example.org",states.get(id),null,"admin",LocalDateTime.of(2026,1,1,0,0),null,null):null;
        });
        when(usersMapper.roles(anyString())).thenReturn(List.of(new Role("PM","PM","Y")));
        when(usersMapper.snapshot(anyString())).thenAnswer(c->c.getArgument(0)+":"+states.get(c.getArgument(0)));
        when(mapper.changeState(any())).thenAnswer(c->{StateWrite w=c.getArgument(0);states.put(w.userId(),w.status());return 1;});
        when(history.insert(any())).thenReturn(1);
    }
    String revision(String id) {return revisions.revision(id+":"+states.get(id));}
    String body(String id) {return json.createObjectNode().put("expectedRevision",revision(id)).toString();}
    ObjectNode bulk(String action,String... ids) {
        ObjectNode body=json.createObjectNode().put("action",action);var items=body.putArray("items");
        for(String id:ids)items.addObject().put("userId",id).put("expectedRevision",revision(id));return body;
    }
    void noWrites() {verify(mapper,never()).changeState(any());verifyNoInteractions(history,access);}
    @Test void unlockLiteralRouteUsesServerActorAndPreservesRolesWithoutAccessLog() throws Exception {
        mvc.perform(post("/api/business-users/c123/unlock").contentType("application/json").content(body("c123")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.user.status").value("USE"))
            .andExpect(jsonPath("$.data.user.roleIds[0]").value("PM")).andExpect(jsonPath("$.data.user.pwdHash").doesNotExist());
        verify(mapper).changeState(argThat(w->w.userId().equals("c123")&&w.unlock()&&w.actor().equals("admin")));
        verify(history).insert(argThat(h->h.targetKey().equals("c123")&&h.item().equals("USER_STAT_CD")&&h.userId().equals("admin")));
        verifyNoInteractions(access);verify(usersMapper,never()).update(any());
    }
    @Test void unlockLiteralRouteRejectsMissingRevisionBeforeWriting() throws Exception {
        mvc.perform(post("/api/business-users/c123/unlock").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("expectedRevision"));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"stop","resume","unlock"})
    void publicStateCommandsWriteOnlyStatusAndServerAudit(String action) throws Exception {
        String id=action.equals("stop")?"a123":action.equals("resume")?"b123":"c123";
        mvc.perform(post("/api/business-users/"+id+"/"+action).contentType("application/json").content(body(id)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.user.status").value(action.equals("stop")?"STOP":"USE"))
            .andExpect(jsonPath("$.data.user.roleIds[0]").value("PM")).andExpect(jsonPath("$.data.user.pwdHash").doesNotExist());
        verify(mapper).changeState(argThat(w->w.actor().equals("admin") && w.unlock()==action.equals("unlock")));
        verify(history).insert(argThat(h->h.item().equals("USER_STAT_CD") && h.userId().equals("admin") && h.targetKey().equals(id)));
        verify(locks).lock("TB_SY_ROLE");verify(locks).lock("USER:"+id);verifyNoInteractions(access);
        verify(usersMapper,never()).update(any());verify(usersMapper,never()).link(any());verify(usersMapper,never()).unlink(any());
    }
    @ParameterizedTest @ValueSource(strings={"stop","resume","unlock"})
    void missingRevisionAndInjectedFieldsRejectedBeforeWriting(String action) throws Exception {
        for(String body:List.of("{}","{\"expectedRevision\":\" \"}","{\"expectedRevision\":3}","{\"expectedRevision\":\"x\",\"actorId\":\"x\"}","{\"expectedRevision\":\"x\",\"status\":\"USE\"}","{\"expectedRevision\":\"x\",\"password\":\"x\"}"))
            mvc.perform(post("/api/business-users/a123/"+action).contentType("application/json").content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").isString());
        noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"stop","resume","unlock"})
    void staleIsCheckedBeforeStateAndNoOp(String action) throws Exception {
        mvc.perform(post("/api/business-users/b123/"+action).contentType("application/json").content("{\"expectedRevision\":\"old\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"stop","resume","unlock"})
    void missingUserIs404(String action) throws Exception {
        mvc.perform(post("/api/business-users/missing/"+action).contentType("application/json").content(body("missing")))
            .andExpect(status().isNotFound());noWrites();
    }
    @Test void stopAndResumeLatestSameStateAreNoOps() throws Exception {
        mvc.perform(post("/api/business-users/b123/stop").contentType("application/json").content(body("b123"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));
        mvc.perform(post("/api/business-users/a123/resume").contentType("application/json").content(body("a123"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));noWrites();
    }
    @Test void lockedResumeExplainsUnlock() throws Exception {
        mvc.perform(post("/api/business-users/c123/resume").contentType("application/json").content(body("c123")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"))
            .andExpect(jsonPath("$.message").value("잠금 해제를 사용하세요."));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"a123","b123"})
    void unlockRefusesUseAndStopped(String id) throws Exception {
        mvc.perform(post("/api/business-users/"+id+"/unlock").contentType("application/json").content(body(id)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));noWrites();
    }
    @Test void allCurrentFutureLeadersAreReportedIncludingStoppedParts() throws Exception {
        when(usersMapper.leaderParts("a123","202606")).thenReturn(List.of("미래 파트","중지 파트"));
        mvc.perform(post("/api/business-users/a123/stop").contentType("application/json").content(body("a123")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_IS_PART_LEADER"))
            .andExpect(jsonPath("$.details.partNames.length()").value(2));noWrites();
    }
    @Test void lastUseActiveAdministratorCannotStop() throws Exception {
        when(mapper.activeAdministrator("a123")).thenReturn(true);when(mapper.activeAdministratorCount()).thenReturn(1L);
        mvc.perform(post("/api/business-users/a123/stop").contentType("application/json").content(body("a123")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ACTIVE_ADMIN"));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"stop","resume","unlock"})
    void mandatoryHistoryZeroRowsTriggersRollbackAndSafe500(String action) throws Exception {
        when(history.insert(any())).thenReturn(0);String id=action.equals("stop")?"a123":action.equals("resume")?"b123":"c123";
        mvc.perform(post("/api/business-users/"+id+"/"+action).contentType("application/json").content(body(id)))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        verify(transactions,atLeastOnce()).rollback(any());verifyNoInteractions(access,invalidation);
    }
    @Test void bulkDeduplicatesSortsAndAccountsForMixedResults() throws Exception {
        when(usersMapper.leaderParts("c123","202606")).thenReturn(List.of("미래 파트"));
        mvc.perform(post("/api/business-users/status-changes").contentType("application/json").content(bulk("STOP","c123","b123","a123","a123").toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(1)).andExpect(jsonPath("$.data.unchanged").value(1))
            .andExpect(jsonPath("$.data.rejected").value(1)).andExpect(jsonPath("$.data.items.length()").value(3))
            .andExpect(jsonPath("$.data.items[0].userId").value("a123")).andExpect(jsonPath("$.data.items[2].code").value("USER_IS_PART_LEADER"));
        verify(mapper,times(1)).changeState(any());verifyNoInteractions(access);
    }
    @Test void bulkResumeIncludesLockedAndStaleRejections() throws Exception {
        var body=bulk("RESUME","a123","b123","c123","missing");((ObjectNode)body.withArray("items").get(0)).put("expectedRevision","stale");
        mvc.perform(post("/api/business-users/status-changes").contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(1)).andExpect(jsonPath("$.data.rejected").value(3))
            .andExpect(jsonPath("$.data.items[0].code").value("STALE_WRITE")).andExpect(jsonPath("$.data.items[2].code").value("INVALID_STATE"));
    }
    @Test void bulkAllRejectedStillReturnsResultTable() throws Exception {
        mvc.perform(post("/api/business-users/status-changes").contentType("application/json").content(bulk("RESUME","c123","missing").toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.rejected").value(2)).andExpect(jsonPath("$.data.items.length()").value(2));noWrites();
    }
    @Test void bulkRequiredHistoryFailureDoesNotLoseEarlierSuccess() throws Exception {
        states.put("b123","USE");when(history.insert(any())).thenAnswer(c->{ChangeHistoryEntry h=c.getArgument(0);if(h.targetKey().equals("b123"))throw new IllegalStateException("secret SQL");return 1;});
        mvc.perform(post("/api/business-users/status-changes").contentType("application/json").content(bulk("STOP","b123","a123").toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.applied").value(1)).andExpect(jsonPath("$.data.rejected").value(1))
            .andExpect(jsonPath("$.data.items[1].code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret SQL"))));
        assertThat(states.get("a123")).isEqualTo("STOP");verify(transactions,atLeastOnce()).rollback(any());verifyNoInteractions(access);
    }
    @ParameterizedTest @ValueSource(strings={"action","empty","conflict","tooMany","actorId"})
    void malformedBulkNeverStartsProcessing(String kind) throws Exception {
        var body=bulk("STOP","a123");
        switch(kind) {
            case "action" -> body.put("action","UNLOCK");
            case "empty" -> body.putArray("items");
            case "conflict" -> body.withArray("items").addObject().put("userId","a123").put("expectedRevision","other");
            case "tooMany" -> {for(int i=0;i<101;i++)body.withArray("items").addObject().put("userId","u"+i).put("expectedRevision","r");}
            default -> body.put("actorId","injected");
        }
        mvc.perform(post("/api/business-users/status-changes").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        noWrites();verifyNoInteractions(transactions);
    }
    @Test void duplicateRawRowsBeyond100AreAcceptedAsOneUniqueItem() throws Exception {
        var body=bulk("STOP",Collections.nCopies(101,"b123").toArray(String[]::new));
        mvc.perform(post("/api/business-users/status-changes").contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.unchanged").value(1));noWrites();
    }
    @Test void scopesReturnRealSourceAndSharedRevisionWithoutWrites() throws Exception {
        when(usersMapper.assignedParts("a123","202606")).thenReturn(List.of(new AssignedPart("HQ","본사",true)));
        when(mapper.projects("a123")).thenReturn(List.of(new AssignedProject("P1",2)));
        mvc.perform(get("/api/business-users/a123/scopes")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignedParts[0].partId").value("HQ")).andExpect(jsonPath("$.data.assignedProjects[0].scopeSn").value(2))
            .andExpect(jsonPath("$.data.revision").value(revision("a123")));noWrites();
    }
    @Test void activitiesExposeOnlyStoredActorRowsAndDefaultPage() throws Exception {
        when(mapper.activities(any())).thenReturn(List.of(new Activity(20,"a123","2026-06-15 09:00","USER_REG","UI-23","b123")));
        when(mapper.activityCount("a123")).thenReturn(1L);
        mvc.perform(get("/api/business-users/a123/access-logs")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].targetDescription").value("b123"))
            .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.size").value(50));
        verify(mapper).activities(new ActivityQuery("a123",1,50,0));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"scopes","access-logs"})
    void emptyRealSourcesAreEmptyWithoutInventedEvents(String path) throws Exception {
        mvc.perform(get("/api/business-users/b123/"+path)).andExpect(status().isOk())
            .andExpect(jsonPath(path.equals("scopes")?"$.data.assignedParts":"$.data.items").isEmpty());noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"scopes","access-logs"})
    void readMissingTargetsReturn404(String path) throws Exception {
        mvc.perform(get("/api/business-users/missing/"+path)).andExpect(status().isNotFound());noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"scopes","access-logs"})
    void readQueryInjectionRejected(String path) throws Exception {
        mvc.perform(get("/api/business-users/a123/"+path).param("actorId","injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"page","size"})
    void badPaginationIs400(String key) throws Exception {
        mvc.perform(get("/api/business-users/a123/access-logs").param(key,"0")).andExpect(status().isBadRequest());noWrites();
    }
}
