package com.sicost.roles;

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
import static com.sicost.roles.RoleDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest(RoleController.class)
@ContextConfiguration(classes=RoleController.class)
@Import({RoleService.class,RoleRepository.class,RevisionService.class,WriteCoordinator.class,ChangeHistoryWriter.class,
    AccessLogWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class RoleContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean RoleMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AccessLogMapper access;
    @MockBean Clock clock;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    final Map<String,Stored> rows=new HashMap<>();
    final Map<String,List<Permission>> permissions=new HashMap<>();
    Permission permission(String id,String screen,boolean inquire,boolean update) {
        return new Permission(id,inquire,false,update,false,false,false,false,"ROOT","메뉴 "+id,screen,!id.equals("stopped"));
    }
    @BeforeEach void setup() throws Exception {
        rows.clear();permissions.clear();
        rows.put("PM",new Stored("PM","프로젝트 담당","ALL","N","Y","Y",5));
        rows.put("ADMIN",new Stored("ADMIN","관리자","ALL","N","Y","Y",1));
        permissions.put("PM",List.of(permission("permissions","UI-24",false,true),permission("stopped","UI-01",false,false)));
        permissions.put("ADMIN",List.of(permission("permissions","UI-24",true,true),permission("stopped","UI-01",false,false)));
        when(users.userId()).thenReturn("admin");when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(mapper.roles()).thenAnswer(c->rows.values().stream().sorted(Comparator.comparingInt(Stored::sortOrder).thenComparing(Stored::roleId)).toList());
        when(mapper.find(anyString())).thenAnswer(c->rows.get(c.getArgument(0)));
        when(mapper.permissions(anyString())).thenAnswer(c->permissions.getOrDefault(c.getArgument(0),List.of(permission("permissions","UI-24",false,false),permission("stopped","UI-01",false,false))));
        when(mapper.snapshot(anyString())).thenAnswer(c->json.writeValueAsString(Arrays.asList(rows.get(c.getArgument(0)),mapper.permissions(c.getArgument(0)))));
        when(mapper.settingsSnapshot()).thenAnswer(c->json.writeValueAsString(rows));
        when(mapper.insert(any())).thenAnswer(c->{RoleWrite w=c.getArgument(0);rows.put(w.roleId(),w.value());return 1;});
        when(mapper.update(any())).thenAnswer(c->{RoleWrite w=c.getArgument(0);rows.put(w.roleId(),w.value());return 1;});
        when(mapper.permissionExists(any())).thenAnswer(c->{PermissionWrite w=c.getArgument(0);return permissions.containsKey(w.roleId());});
        when(mapper.insertPermission(any())).thenAnswer(c->storePermission(c.getArgument(0)));
        when(mapper.updatePermission(any())).thenAnswer(c->storePermission(c.getArgument(0)));
        when(history.insert(any())).thenReturn(1);when(access.insert(any())).thenReturn(1);
    }
    int storePermission(PermissionWrite w) {
        List<Permission> current=mapper.permissions(w.roleId());PermissionInput p=w.value();
        permissions.put(w.roleId(),current.stream().map(v->v.menuId().equals(p.menuId())?new Permission(p.menuId(),p.inquire(),p.register(),p.update(),p.delete(),p.upload(),p.download(),p.confirm(),v.parentId(),v.name(),v.screenId(),v.use()):v).toList());return 1;
    }
    ObjectNode create() {return json.createObjectNode().put("mode","NEW").put("name","새 역할").put("dataScope","PART");}
    ObjectNode copy() throws Exception {return json.createObjectNode().put("mode","COPY").put("name","저장본 복사").put("sourceRoleId","PM").put("sourceRevision",revision("PM"));}
    String revision(String id) {return revisions.revision(mapper.snapshot(id));}
    ObjectNode edit(String id) {
        var row=rows.get(id);ObjectNode body=json.createObjectNode().put("dataScope",row.dataScope()).put("sensitiveView",row.sensitiveYn().equals("Y")).put("expectedRevision",revision(id));
        body.set("menuPermissions",json.valueToTree(mapper.permissions(id).stream().map(Permission::input).toList()));return body;
    }
    void noWrites() {verify(mapper,never()).insert(any());verify(mapper,never()).update(any());verify(mapper,never()).insertPermission(any());verify(mapper,never()).updatePermission(any());verifyNoInteractions(history,access);}
    @Test void activeListUsesStoredOrderAndValuesWithoutAuthenticationOrWrites() throws Exception {
        mvc.perform(get("/api/roles")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].roleId").value("ADMIN"))
            .andExpect(jsonPath("$.data.items[1].name").value("프로젝트 담당")).andExpect(jsonPath("$.data.developmentMode").value(false))
            .andExpect(jsonPath("$.data.accessEnforcement").value(true)).andExpect(jsonPath("$.data.settingsRevision").isString());noWrites();
    }
    @Test void emptySourceReturnsEmptyListWithoutCreatingDefaults() throws Exception {
        rows.clear();mvc.perform(get("/api/roles")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());noWrites();
    }
    @Test void stoppedRolesAreNotOptions() throws Exception {
        rows.put("STOP",new Stored("STOP","중지 역할","PART","Y","N","N",3));
        mvc.perform(get("/api/roles")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2));noWrites();
    }
    @Test void detailReadsStoppedMenusAndIndependentActionsAndNoSecrets() throws Exception {
        mvc.perform(get("/api/roles/PM")).andExpect(status().isOk()).andExpect(jsonPath("$.data.menuPermissions[0].inquire").value(false))
            .andExpect(jsonPath("$.data.menuPermissions[0].update").value(true)).andExpect(jsonPath("$.data.menuPermissions[1].use").value(false))
            .andExpect(jsonPath("$.data.revision").value(revision("PM"))).andExpect(jsonPath("$.data.regUserId").doesNotExist());noWrites();
    }
    @Test void missingDetailIs404() throws Exception {mvc.perform(get("/api/roles/missing")).andExpect(status().isNotFound());noWrites();}
    @Test void longIdIs400() throws Exception {mvc.perform(get("/api/roles/"+"x".repeat(21))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("id"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"actorId","regUserId","sort","q","expectedRevision"})
    void unknownReadQueryIsRejectedOnBothReads(String key) throws Exception {
        for(String path:List.of("/api/roles","/api/roles/PM"))mvc.perform(get(path).param(key,"injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();
    }
    @Test void newRoleUsesFalsePermissionsServerIdentityAndOneAccessEvent() throws Exception {
        mvc.perform(post("/api/roles").contentType("application/json").content(create().put("name"," 새 역할 ").toString())).andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.value.roleId").value("R-001")).andExpect(jsonPath("$.data.value.name").value("새 역할"))
            .andExpect(jsonPath("$.data.value.systemRole").value(false)).andExpect(jsonPath("$.data.value.use").value(true))
            .andExpect(jsonPath("$.data.value.dataScope").value("PART")).andExpect(jsonPath("$.data.value.sensitiveView").value(false))
            .andExpect(jsonPath("$.data.value.menuPermissions[0].update").value(false));
        verify(mapper).insert(argThat(w->w.actor().equals("admin")));verify(access).insert(argThat(e->e.action().equals("AUTH_CHG") && e.userId().equals("admin") && e.screenId().equals("UI-24")));
        verify(history,times(2)).insert(argThat(e->e.tableName().equals("TB_SY_ROLE_MENU_AUTH") && e.type().equals("NEW") && e.after().equals(RoleService.permissionJson(new PermissionInput("unused",false,false,false,false,false,false,false)))));
        var order=inOrder(locks,mapper);order.verify(locks).lock("TB_SY_MENU");order.verify(locks).lock("TB_SY_ROLE");order.verify(mapper).roles();
    }
    @Test void copyUsesPersistedSettingsWithoutSystemQualificationAndIsIndependent() throws Exception {
        mvc.perform(post("/api/roles").contentType("application/json").content(copy().toString())).andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.value.dataScope").value("ALL")).andExpect(jsonPath("$.data.value.systemRole").value(false))
            .andExpect(jsonPath("$.data.value.menuPermissions[0].update").value(true));
        permissions.put("PM",List.of(permission("permissions","UI-24",true,false)));
        assertThat(permissions.get("R-001").get(0).update()).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"blank","long","scopeMissing","badScope","wrongMode","actorId","regUserId","systemRole","roleId","sortOrder","sensitiveView","sourceRoleId"})
    void newValidationNeverWrites(String key) throws Exception {
        ObjectNode body=create();
        switch(key) {case "blank"->body.put("name"," ");case "long"->body.put("name","가".repeat(51));case "scopeMissing"->body.remove("dataScope");case "badScope"->body.put("dataScope","SELF");case "wrongMode"->body.put("mode",true);default->body.put(key,"injected");}
        mvc.perform(post("/api/roles").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").isString());noWrites();
    }
    @Test void copyRequiresSourceRevision() throws Exception {ObjectNode body=copy();body.remove("sourceRevision");mvc.perform(post("/api/roles").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());noWrites();}
    @Test void duplicateStoppedNameConflicts() throws Exception {
        rows.put("R-099",new Stored("R-099","새 역할","ALL","N","N","N",99));
        mvc.perform(post("/api/roles").contentType("application/json").content(create().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));noWrites();
    }
    @Test void stoppedCopySourceConflicts() throws Exception {
        rows.put("PM",new Stored("PM","프로젝트 담당","ALL","N","Y","N",5));
        mvc.perform(post("/api/roles").contentType("application/json").content(copy().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));noWrites();
    }
    @Test void staleCopySourceConflicts() throws Exception {
        mvc.perform(post("/api/roles").contentType("application/json").content(copy().put("sourceRevision","old").toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();
    }
    @Test void missingCopySourceIs404() throws Exception {
        mvc.perform(post("/api/roles").contentType("application/json").content(copy().put("sourceRoleId","missing").toString())).andExpect(status().isNotFound());noWrites();
    }
    @Test void numericIdContinuesPast999IncludingStoppedRows() throws Exception {
        rows.put("R-999",new Stored("R-999","중지","PROJ","N","N","N",20));
        mvc.perform(post("/api/roles").contentType("application/json").content(create().toString())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.value.roleId").value("R-1000")).andExpect(jsonPath("$.data.value.sortOrder").value(21));
    }
    @Test void sortCapacityRejectsBeforeWriting() throws Exception {
        rows.put("R-001",new Stored("R-001","끝","PROJ","N","N","N",99999));
        mvc.perform(post("/api/roles").contentType("application/json").content(create().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SORT_CAPACITY_EXCEEDED"));noWrites();
    }
    @Test void changedSettingsPersistIndependentlyAndAuditFixedSevenKeysOnce() throws Exception {
        ObjectNode body=edit("PM").put("dataScope","PART").put("sensitiveView",true);
        ((ObjectNode)body.path("menuPermissions").get(1)).put("download",true);
        mvc.perform(put("/api/roles/PM").contentType("application/json").content(body.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true))
            .andExpect(jsonPath("$.data.value.dataScope").value("PART")).andExpect(jsonPath("$.data.value.menuPermissions[0].inquire").value(false)).andExpect(jsonPath("$.data.value.menuPermissions[0].update").value(true));
        verify(access,times(1)).insert(any());verify(history).insert(argThat(e->e.targetKey().equals("PM:stopped") && e.before().contains("\"INQ_YN\":\"N\"") && e.after().equals(RoleService.permissionJson(permissions.get("PM").get(1).input()))));
        mvc.perform(get("/api/roles/PM")).andExpect(jsonPath("$.data.sensitiveView").value(true));
    }
    @Test void sameValuesHaveNoWritesOrInvalidation() throws Exception {
        mvc.perform(put("/api/roles/PM").contentType("application/json").content(edit("PM").toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));noWrites();verifyNoInteractions(invalidation);
    }
    @Test void staleUpdatePrecedesNoOp() throws Exception {
        mvc.perform(put("/api/roles/PM").contentType("application/json").content(edit("PM").put("expectedRevision","stale").toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"missingRow","duplicate","unknownMenu","missingBoolean","stringBoolean","name","systemRole","actorId","regUserId","roleId"})
    void updateWholeSetAndStrictFieldsAreValidated(String key) throws Exception {
        ObjectNode body=edit("PM");var array=(com.fasterxml.jackson.databind.node.ArrayNode)body.get("menuPermissions");
        switch(key) {case "missingRow"->array.remove(1);case "duplicate"->array.add(array.get(0).deepCopy());case "unknownMenu"->((ObjectNode)array.get(0)).put("menuId","unknown");case "missingBoolean"->((ObjectNode)array.get(0)).remove("download");case "stringBoolean"->body.put("sensitiveView","Y");default->body.put(key,"injected");}
        mvc.perform(put("/api/roles/PM").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"inquire","update"})
    void adminProtectionUsesScreenIdentityNotRenamedMenu(String action) throws Exception {
        permissions.put("ADMIN",List.of(new Permission("permissions",true,false,true,false,false,false,false,"ROOT","이름 변경","UI-24",true),permission("stopped","UI-01",false,false)));
        ObjectNode body=edit("ADMIN");((ObjectNode)body.path("menuPermissions").get(0)).put(action,false);
        mvc.perform(put("/api/roles/ADMIN").contentType("application/json").content(body.toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADMIN_PERMISSION_PROTECTED"));noWrites();
    }
    @Test void adminOtherActionsAndScopeRemainEditable() throws Exception {
        ObjectNode body=edit("ADMIN").put("dataScope","PART");((ObjectNode)body.path("menuPermissions").get(0)).put("download",true);
        mvc.perform(put("/api/roles/ADMIN").contentType("application/json").content(body.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
    }
    @ParameterizedTest @ValueSource(strings={"createHistory","createAccess","updateHistory","updateAccess"})
    void mandatoryAuditFailureRollsBackCoordinatorAndDoesNotInvalidate(String failure) throws Exception {
        if(failure.endsWith("History"))when(history.insert(any())).thenReturn(0);else when(access.insert(any())).thenReturn(0);
        var request=failure.startsWith("create")?post("/api/roles"):put("/api/roles/PM");
        var body=failure.startsWith("create")?create():edit("PM").put("dataScope","PART");
        mvc.perform(request.contentType("application/json").content(body.toString())).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        verify(manager).rollback(any());verifyNoInteractions(invalidation);
    }
}
