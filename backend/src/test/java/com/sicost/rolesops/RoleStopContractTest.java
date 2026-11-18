package com.sicost.rolesops;

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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest(RoleStopController.class)
@ContextConfiguration(classes=RoleStopController.class)
@Import({RoleStopService.class,RoleStopRepository.class,RoleConfigurationRevision.class,WriteCoordinator.class,
    RevisionService.class,ChangeHistoryWriter.class,AccessLogWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class RoleStopContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RoleConfigurationRevision revisions;
    @MockBean RoleStopMapper mapper;
    @MockBean RoleConfigurationSnapshotMapper snapshots;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AccessLogMapper access;
    @MockBean Clock clock;
    @MockBean PlatformTransactionManager transactions;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    Map<String,Object> role;
    List<Map<String,Object>> permissions;
    @BeforeEach void setup() {
        when(users.userId()).thenReturn("admin");
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(transactions.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        role=new LinkedHashMap<>();role.put("roleId","R-801");role.put("name","추가 역할");role.put("dataScope","PROJ");
        role.put("systemRole","N");role.put("sensitiveView","N");role.put("use","Y");role.put("sortOrder",801);
        role.put("regUserId","admin");role.put("regDttm","2026-01-01 00:00:00.123456");role.put("modUserId",null);role.put("modDttm",null);
        permissions=List.of(Map.of("roleId","R-801","menuId","M-test","inquire","N","update","N","regDttm","2026-01-01 00:00:00.123456"));
        when(snapshots.role(anyString())).thenAnswer(call->role==null?null:new LinkedHashMap<>(role));
        when(snapshots.permissions(anyString())).thenAnswer(call->permissions);
        when(snapshots.storedSnapshot(anyString())).thenAnswer(call->role==null?null:
            json.writeValueAsString(Map.of("role",role,"permissions",permissions)));
        when(snapshots.settings()).thenAnswer(call->json.writeValueAsString(Map.of("role",role,"permissions",permissions)));
        when(mapper.menuPermissions(anyString())).thenReturn(List.of(new RoleStopDtos.Permission("M-test",false,false,false,false,false,false,false,"G-test","권한","UI-24",true)));
        when(mapper.stop(any())).thenAnswer(call->{var mutation=call.getArgument(0,RoleStopDtos.Mutation.class);
            role.put("use","N");role.put("modUserId",mutation.actor());role.put("modDttm",mutation.now().toString());return 1;});
        when(history.insert(any())).thenReturn(1);when(access.insert(any())).thenReturn(1);
    }
    ObjectNode body() {return json.createObjectNode().put("expectedRevision",revisions.revision("R-801"));}
    org.springframework.test.web.servlet.ResultActions stop(String id,ObjectNode body) throws Exception {
        return mvc.perform(post("/api/roles/{id}/stop",id).contentType("application/json").content(body.toString()));
    }
    void noWrites() {verify(mapper,never()).stop(any());verifyNoInteractions(history,access,invalidation);}
    @Test void publicStopWithStoredPermissionsFalsePreservesIdentityAndPermissionsAndUsesServerActor() throws Exception {
        var before=new LinkedHashMap<>(role);var authBefore=permissions;
        stop("R-801",body()).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.changed").value(true))
            .andExpect(jsonPath("$.data.value.use").value(false)).andExpect(jsonPath("$.data.value.roleId").value("R-801"))
            .andExpect(jsonPath("$.data.value.sortOrder").value(801)).andExpect(jsonPath("$.data.value.accessEnforcement").value(true))
            .andExpect(jsonPath("$.data.value.developmentMode").value(false)).andExpect(jsonPath("$.data.value.menuPermissions[0].inquire").value(false))
            .andExpect(jsonPath("$.data.revision").value(revisions.revision("R-801")));
        for(String key:before.keySet())if(!Set.of("use","modUserId","modDttm").contains(key))assertThat(role.get(key)).isEqualTo(before.get(key));
        assertThat(permissions).isEqualTo(authBefore);
        verify(history).insert(argThat(row->row.tableName().equals("TB_SY_ROLE") && row.targetKey().equals("R-801") && row.type().equals("STOP") && row.item().equals("USE_YN") && row.before().equals("Y") && row.after().equals("N") && row.userId().equals("admin")));
        verify(access,times(1)).insert(argThat(row->row.action().equals("AUTH_CHG") && row.screenId().equals("UI-24") && row.userId().equals("admin") && row.description().equals("R-801")));
        var order=inOrder(users,locks,mapper);order.verify(users).userId();order.verify(locks).lock("TB_SY_MENU");order.verify(locks).lock("TB_SY_ROLE");order.verify(mapper).connectedUsers("R-801");
        verify(invalidation).afterCommit("TB_SY_ROLE");verify(transactions).commit(any());
    }
    @ParameterizedTest @ValueSource(strings={"use","systemRole","actorId","regUserId","modDttm","roleId","sortOrder"})
    void serverFieldsAreUnknownAndNeverWrite(String field) throws Exception {
        stop("R-801",body().put(field,"injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value(field));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"missing","blank","number","null"})
    void invalidRevisionNeverWrites(String kind) throws Exception {
        ObjectNode input=body();switch(kind) {case "missing"->input.remove("expectedRevision");case "blank"->input.put("expectedRevision","  ");case "number"->input.put("expectedRevision",1);default->input.putNull("expectedRevision");}
        stop("R-801",input).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));noWrites();
    }
    @Test void unknownQueryRejectedBeforeMutation() throws Exception {
        mvc.perform(post("/api/roles/R-801/stop").param("sort","roleId").contentType("application/json").content(body().toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();
    }
    @Test void unknownTargetIs404() throws Exception {
        ObjectNode input=body();role=null;stop("R-801",input).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));noWrites();
    }
    @Test void overlongPathIdIs400() throws Exception {stop("x".repeat(21),body()).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"ADMIN","EXEC","FIN","SALES_LDR","PM","R-801"})
    void defaultsAndSystemFlagIndependentlyProtected(String id) throws Exception {
        if(id.equals("R-801"))role.put("systemRole","Y");
        stop(id,body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SYSTEM_ROLE_PROTECTED"));noWrites();
    }
    @Test void anyLinkedUserIncludingStoppedIsCounted() throws Exception {
        when(mapper.connectedUsers("R-801")).thenReturn(1L);
        stop("R-801",body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROLE_IN_USE"))
            .andExpect(jsonPath("$.message").value("1명이 이 역할을 쓰고 있습니다. 시스템 › 사용자에서 먼저 빼 주세요."));noWrites();
    }
    @Test void staleBeforeNoOp() throws Exception {
        ObjectNode input=body();role.put("use","N");stop("R-801",input).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();
    }
    @Test void latestStoppedRoleIsNoOpAndDoesNotTouchAudit() throws Exception {
        role.put("use","N");stop("R-801",body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));noWrites();
    }
    @Test void permissionAuditPrecisionChangeInvalidatesRevision() throws Exception {
        ObjectNode input=body();permissions=List.of(Map.of("roleId","R-801","menuId","M-test","inquire","N","update","N","regDttm","2026-01-01 00:00:00.123457"));
        stop("R-801",input).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"historyZero","historyException","accessZero","accessException","businessZero"})
    void requiredWriteFailuresRollbackCoordinatorAndReturnSafe500(String kind) throws Exception {
        switch(kind) {case "historyZero"->when(history.insert(any())).thenReturn(0);case "historyException"->when(history.insert(any())).thenThrow(new IllegalStateException("private SQL"));
            case "accessZero"->when(access.insert(any())).thenReturn(0);case "accessException"->when(access.insert(any())).thenThrow(new IllegalStateException("private SQL"));default->doReturn(0).when(mapper).stop(any());}
        stop("R-801",body()).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
        verify(transactions).rollback(any());verifyNoInteractions(invalidation);
    }
}
