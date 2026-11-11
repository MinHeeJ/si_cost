package com.sicost.businessusers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.RevisionService;
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
import static com.sicost.businessusers.BusinessUserDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BusinessUserController.class)
@ContextConfiguration(classes=BusinessUserController.class)
@Import({BusinessUserService.class,BusinessUserRepository.class,RevisionService.class,ChangeHistoryWriter.class,
         AccessLogWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class BusinessUserContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean BusinessUserMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AccessLogMapper access;
    @MockBean Clock clock;
    Stored original;
    List<Role> roles;
    @BeforeEach void setup() {
        when(users.userId()).thenReturn("admin");
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        original=new Stored("ab12","사용자","user@example.org","USE",null,"admin",LocalDateTime.of(2026,1,1,0,0,0,123456000),null,null);
        roles=List.of(new Role("ADMIN","관리자","Y"),new Role("PM","프로젝트 담당","Y"));
        when(mapper.find(anyString(),anyBoolean())).thenReturn(original);
        when(mapper.roles(anyString())).thenAnswer(call->roles);
        when(mapper.availableRoles()).thenReturn(List.of(new Role("PM","프로젝트 담당","Y"),new Role("SALES_LDR","영업 파트장","Y")));
        when(mapper.insert(any())).thenReturn(1);when(mapper.update(any())).thenReturn(1);
        when(mapper.link(any())).thenReturn(1);when(mapper.unlink(any())).thenReturn(1);
        when(access.insert(any())).thenReturn(1);
    }
    ObjectNode create() {return json.createObjectNode().put("userId","ab12").put("name","사용자").put("email","user@example.org").set("businessRoleIds",json.valueToTree(List.of("PM")));}
    ObjectNode edit() {ObjectNode body=create();body.remove("userId");body.put("expectedRevision",revision());return body;}
    String revision() {return revisions.revision(new Snapshot(original.userId(),original.name(),original.email(),original.regDttm(),original.modDttm(),roles.stream().map(Role::roleId).sorted().toList()));}
    void noWrites() {verify(mapper,never()).insert(any());verify(mapper,never()).update(any());verify(mapper,never()).link(any());verify(mapper,never()).unlink(any());verifyNoInteractions(history,access);}
    @Test void createWithoutAuthenticationUsesServerActorAndSecretFreeEnvelope() throws Exception {
        mvc.perform(post("/api/business-users").contentType("application/json").content(create().toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.user.status").value("USE")).andExpect(jsonPath("$.data.user.businessRoleIds[0]").value("PM"))
            .andExpect(jsonPath("$.data.user.pwdHash").doesNotExist()).andExpect(jsonPath("$.data.user.mfaCodeHash").doesNotExist());
        verify(mapper).insert(argThat(row->row.actor().equals("admin") && row.passwordHash().startsWith("$2a$10$")));
        verify(history).insert(argThat(row->row.type().equals("NEW") && row.targetKey().equals("ab12") && row.userId().equals("admin")));
        verify(access).insert(argThat(row->row.action().equals("USER_REG") && row.userId().equals("admin")));
    }
    @ParameterizedTest @ValueSource(strings={"userId","name","email","businessRoleIds","regUserId","status","emptyRoles"})
    void createValidationAndUnknownFieldsNeverWrite(String field) throws Exception {
        ObjectNode body=create();String expected=field;
        switch(field) {
            case "userId" -> body.put(field,"AB12");
            case "name" -> body.put(field,"가".repeat(51));
            case "email" -> body.put(field,"invalid");
            case "businessRoleIds" -> body.set(field,json.valueToTree(List.of("PM","PM")));
            case "emptyRoles" -> {body.putArray("businessRoleIds");expected="businessRoleIds";}
            default -> body.put(field,"injected");
        }
        mvc.perform(post("/api/business-users").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(expected))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.matchesPattern(".*[가-힣].*")));
        noWrites();
    }
    @Test void duplicateUserIsConflictWithoutRolesOrAudit() throws Exception {
        when(mapper.insert(any())).thenReturn(0);
        mvc.perform(post("/api/business-users").contentType("application/json").content(create().toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_USER"));
        verify(mapper,never()).link(any());verifyNoInteractions(history,access);
    }
    @Test void inactiveRoleRejected() throws Exception {
        when(mapper.availableRoles()).thenReturn(List.of());
        mvc.perform(post("/api/business-users").contentType("application/json").content(create().toString())).andExpect(status().isBadRequest());noWrites();
    }
    @Test void requiredAccessFailureReturnsSafeKorean500() throws Exception {
        when(access.insert(any())).thenThrow(new IllegalStateException("private detail"));
        mvc.perform(post("/api/business-users").contentType("application/json").content(create().toString()))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private detail"))));
    }
    @Test void detailReturnsAllDisplayRolesEditableSubsetAndRevisionWithoutSecrets() throws Exception {
        mvc.perform(get("/api/business-users/ab12")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles[0]").value("관리자")).andExpect(jsonPath("$.data.roles[1]").value("프로젝트 담당"))
            .andExpect(jsonPath("$.data.businessRoleIds[0]").value("PM")).andExpect(jsonPath("$.data.revision").value(revision()));noWrites();
    }
    @Test void unknownUserIs404() throws Exception {
        when(mapper.find(anyString(),anyBoolean())).thenReturn(null);
        mvc.perform(get("/api/business-users/missing")).andExpect(status().isNotFound());noWrites();
    }
    @Test void listCombinesFiltersAndReturnsMatchingTotalAndAllDefaultStates() throws Exception {
        when(mapper.list(any())).thenReturn(List.of(original));when(mapper.count(any())).thenReturn(1L);
        mvc.perform(get("/api/business-users").param("q"," 사용 ").param("businessRole","PM").param("status","LOCK"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        verify(mapper).list(argThat(f->f.q().equals("사용") && f.businessRole().equals("PM") && f.status().equals("LOCK") && f.sort().equals("name")));
        mvc.perform(get("/api/business-users")).andExpect(status().isOk());
        verify(mapper).count(argThat(f->f.q()==null && f.businessRole()==null && f.status()==null));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"page","size","sort","direction","businessRole","status","q","regUserId"})
    void invalidListParametersAre400(String key) throws Exception {
        String value=key.equals("q")?"가".repeat(101):"invalid";
        mvc.perform(get("/api/business-users").param(key,value)).andExpect(status().isBadRequest());noWrites();
    }
    @Test void updateWithoutAuthenticationPreservesAdminAndWritesOnlyChangedNameHistory() throws Exception {
        ObjectNode body=edit().put("name","새 이름");
        mvc.perform(put("/api/business-users/ab12").contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        verify(mapper).find("ab12",true);
        verify(mapper).update(argThat(m->m.actor().equals("admin") && m.input().name().equals("새 이름")));
        verify(history).insert(argThat(h->h.item().equals("이름") && h.before().equals("사용자") && h.after().equals("새 이름")));
        verify(mapper,never()).unlink(any());verifyNoInteractions(access);
    }
    @ParameterizedTest @ValueSource(strings={"businessRoleIds","email","status","regUserId","userId"})
    void invalidUpdatesNeverWrite(String key) throws Exception {
        ObjectNode body=edit();
        if(key.equals("businessRoleIds"))body.set(key,json.valueToTree(List.of("PM","PM")));else body.put(key," ");
        mvc.perform(put("/api/business-users/ab12").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());noWrites();
    }
    @Test void staleRevisionPrecedesNoChange() throws Exception {
        mvc.perform(put("/api/business-users/ab12").contentType("application/json").content(edit().put("expectedRevision","old").toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();
    }
    @Test void sameTimestampButDifferentRoleSetIsStale() throws Exception {
        ObjectNode body=edit();roles=List.of(new Role("PM","프로젝트 담당","Y"));
        mvc.perform(put("/api/business-users/ab12").contentType("application/json").content(body.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();
    }
    @Test void unchangedUpdateHasNoTimestampOrHistoryWrite() throws Exception {
        mvc.perform(put("/api/business-users/ab12").contentType("application/json").content(edit().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false)).andExpect(jsonPath("$.data.message").value("바뀐 내용이 없습니다."));noWrites();
    }
    @Test void lastRoleCannotBeRemoved() throws Exception {
        roles=List.of(new Role("PM","프로젝트 담당","Y"));ObjectNode body=edit();body.putArray("businessRoleIds");
        mvc.perform(put("/api/business-users/ab12").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("businessRoleIds"));noWrites();
    }
    @Test void businessRoleRemovalPreservesNonBusinessRole() throws Exception {
        ObjectNode body=edit();body.putArray("businessRoleIds");
        mvc.perform(put("/api/business-users/ab12").contentType("application/json").content(body.toString())).andExpect(status().isOk());
        verify(mapper).unlink(argThat(link->link.roleId().equals("PM")));
        verify(mapper,never()).unlink(argThat(link->link.roleId().equals("ADMIN")));
        verify(history).insert(argThat(h->h.item().equals("업무 역할") && h.before().equals("PM") && h.after().equals("")));
    }
    @Test void currentOrFutureStoppedPartLeaderCannotLoseQualification() throws Exception {
        roles=List.of(new Role("PM","프로젝트 담당","Y"),new Role("SALES_LDR","파트장","Y"));
        when(mapper.leaderParts("ab12","202606")).thenReturn(List.of("중지 파트"));
        mvc.perform(put("/api/business-users/ab12").contentType("application/json").content(edit().toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_IS_PART_LEADER"))
            .andExpect(jsonPath("$.details.partName").value("중지 파트"));noWrites();
    }
}
