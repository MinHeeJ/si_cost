package com.sicost.currentuserops;

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
import static com.sicost.currentuserops.PermissionSettingsDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PermissionSettingsController.class)
@ContextConfiguration(classes=PermissionSettingsController.class)
@Import({PermissionSettingsService.class,PermissionSettingsRepository.class,RevisionService.class,
         ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class PermissionSettingsContractTest {
    @Autowired MockMvc mvc;
    @Autowired PermissionSettingsService service;
    @MockBean PermissionSettingsMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AccessLogMapper access;
    @MockBean Clock clock;
    @BeforeEach void setup() {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","실제 관리자",List.of("ADMIN"),true));
        Clock fixed=Clock.fixed(Instant.parse("2026-06-30T15:00:00Z"),ZoneOffset.UTC);
        when(clock.withZone(any())).thenAnswer(call->fixed.withZone(call.getArgument(0)));
        when(mapper.roles("admin")).thenReturn(List.of(new Role("ADMIN","PART",false),new Role("PM","PROJ",true)));
        when(mapper.assignedParts("admin","202607")).thenReturn(List.of(new AssignedPart("part","영업파트",false)));
        when(mapper.assignedProjects("admin")).thenReturn(List.of(new AssignedProject("project",1)));
        when(mapper.menuPermissions("admin")).thenReturn(List.of(new MenuPermission("menu",false,false,false,false,false,false,false)));
        when(mapper.snapshot("admin")).thenReturn("stored revision source");
    }
    @AfterEach void noAuditOrAccessWrites() { verifyNoInteractions(history,access); }
    @Test void publicRequestReturnsExactSettingsAndBothScopesWithoutEnforcingDeniedMenu() throws Exception {
        mvc.perform(get("/api/current-user/permission-settings")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.userId").value("admin"))
            .andExpect(jsonPath("$.data.roleIds[0]").value("ADMIN"))
            .andExpect(jsonPath("$.data.roleIds[1]").value("PM"))
            .andExpect(jsonPath("$.data.sensitiveView").value(true))
            .andExpect(jsonPath("$.data.menuPermissions[0].inquire").value(false))
            .andExpect(jsonPath("$.data.rowScope.all").value(false))
            .andExpect(jsonPath("$.data.rowScope.partIds[0]").value("part"))
            .andExpect(jsonPath("$.data.rowScope.projectIds[0]").value("project"))
            .andExpect(jsonPath("$.data.developmentMode").value(true))
            .andExpect(jsonPath("$.data.accessEnforcement").value(false))
            .andExpect(jsonPath("$.data.settingsRevision").isNotEmpty())
            .andExpect(jsonPath("$.data.name").doesNotExist());
        verify(users,times(1)).currentUser();verify(mapper).assignedParts("admin","202607");
    }
    @Test void allDoesNotBecomeEmptyWhenThereAreNoAssignments() {
        when(mapper.roles("admin")).thenReturn(List.of(new Role("ADMIN","ALL",false)));
        when(mapper.assignedParts(anyString(),anyString())).thenReturn(List.of());
        when(mapper.assignedProjects(anyString())).thenReturn(List.of());
        var result=service.getCurrentUserPermissionSettings();
        assertThat(result.rowScope().all()).isTrue();assertThat(result.assignedParts()).isEmpty();
        assertThat(result.rowScope().partIds()).isEmpty();assertThat(result.rowScope().projectIds()).isEmpty();
    }
    @Test void missingTargetsYieldEmptyScopeWithoutLosingRoleSelection() {
        when(mapper.assignedParts(anyString(),anyString())).thenReturn(List.of());
        when(mapper.assignedProjects(anyString())).thenReturn(List.of());
        var result=service.getCurrentUserPermissionSettings();
        assertThat(result.rowScope().all()).isFalse();assertThat(result.rowScope().partIds()).isEmpty();
        assertThat(result.rowScope().projectIds()).isEmpty();assertThat(result.roleIds()).containsExactly("ADMIN","PM");
    }
    @Test void assignmentsRemainVisibleWhenRoleDoesNotSelectTheirScope() {
        when(mapper.roles("admin")).thenReturn(List.of(new Role("ADMIN","PROJ",false)));
        var result=service.getCurrentUserPermissionSettings();
        assertThat(result.assignedParts()).hasSize(1);assertThat(result.rowScope().partIds()).isEmpty();
        assertThat(result.sensitiveView()).isFalse();
    }
    @Test void legacyDuplicateProjectsDeduplicateOnlyUnionNotConnections() {
        when(mapper.assignedProjects("admin")).thenReturn(List.of(new AssignedProject("project",1),new AssignedProject("project",2)));
        var result=service.getCurrentUserPermissionSettings();
        assertThat(result.assignedProjects()).hasSize(2);assertThat(result.rowScope().projectIds()).containsExactly("project");
    }
    @Test void noPermissionSourceReturnsEmptyListAndNoDefaultRows() throws Exception {
        when(mapper.menuPermissions("admin")).thenReturn(List.of());
        mvc.perform(get("/api/current-user/permission-settings")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menuPermissions").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"actorId","userId","regUserId","modDttm","developmentMode","accessEnforcement","sort","q","roleId"})
    void everyQueryFieldIsRejectedBeforeReading(String field) throws Exception {
        mvc.perform(get("/api/current-user/permission-settings").param(field,"값".repeat(300)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value(field))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.matchesPattern(".*[가-힣].*")));
        verifyNoInteractions(mapper,users);
    }
    @Test void processorInjectionInJsonIsRejected() throws Exception {
        mvc.perform(get("/api/current-user/permission-settings").contentType("application/json").content("{\"actorId\":\"other\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value("actorId"));verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @ValueSource(strings={"[]","true","{}","\"Y\""})
    void bodyHasNoAcceptedType(String body) throws Exception {
        mvc.perform(get("/api/current-user/permission-settings").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("body"));verifyNoInteractions(mapper,users);
    }
    @Test void stableReadHasStableRevisionButStoredChangeInvalidatesIt() {
        String revision=service.getCurrentUserPermissionSettings().settingsRevision();
        assertThat(service.getCurrentUserPermissionSettings().settingsRevision()).isEqualTo(revision);
        when(mapper.snapshot("admin")).thenReturn("new stored revision source");
        assertThat(service.getCurrentUserPermissionSettings().settingsRevision()).isNotEqualTo(revision);
    }
    @Test void readFailureIsSafeAndDoesNotInitializeData() throws Exception {
        when(mapper.menuPermissions("admin")).thenThrow(new IllegalStateException("private SQL"));
        mvc.perform(get("/api/current-user/permission-settings")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
    }
}
