package com.sicost.common.context;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserProviderTest {
    @Test void databaseSuppliesOnlyTheTemporaryAdminContext() {
        var mapper = mock(CurrentUserMapper.class);
        when(mapper.findAdministrator()).thenReturn("admin");
        when(mapper.findName("admin")).thenReturn("DB 관리자");
        when(mapper.findActiveRoles("admin")).thenReturn(List.of("ADMIN"));
        var provider = new CurrentUserProvider(mapper); var user = provider.currentUser();
        assertThat(user.userId()).isEqualTo("admin"); assertThat(user.roleIds()).containsExactly("ADMIN");
        assertThat(user.name()).isEqualTo("DB 관리자"); assertThat(user.developmentMode()).isTrue();
        assertThat(user.dataScope()).isEqualTo("ALL"); assertThat(provider.allows("getMenus")).isTrue();
    }
    @Test void missingContextIsNotFabricatedAndDoesNotBecome401or403() {
        var mapper = mock(CurrentUserMapper.class); when(mapper.findAdministratorRoles()).thenReturn(List.of());
        assertThatThrownBy(() -> new CurrentUserProvider(mapper).currentUser()).isInstanceOf(IllegalStateException.class);
    }
    @Test void requestPinsIdentityNameAndRolesEvenAfterSelfRemovalAndNextRequestResolvesAgain() {
        var mapper = mock(CurrentUserMapper.class);
        when(mapper.findAdministrator()).thenReturn("admin", "backup");
        when(mapper.findName("admin")).thenReturn("기존 관리자");
        when(mapper.findName("backup")).thenReturn("다음 관리자");
        when(mapper.findActiveRoles("admin")).thenReturn(List.of("ADMIN", "PM"), List.of("PM"));
        when(mapper.findActiveRoles("backup")).thenReturn(List.of("ADMIN"));
        var provider = new CurrentUserProvider(mapper);
        var first = new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest());
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(first);
        try {
            var pinned = provider.currentUser();
            assertThat(provider.userId()).isEqualTo("admin");
            assertThat(provider.currentUser()).isSameAs(pinned);
            assertThat(pinned.name()).isEqualTo("기존 관리자");
            assertThat(pinned.roleIds()).containsExactly("ADMIN", "PM");
            verify(mapper, times(1)).findAdministrator();
            first.requestCompleted();
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(new org.springframework.mock.web.MockHttpServletRequest()));
            assertThat(provider.currentUser().userId()).isEqualTo("backup");
            assertThat(provider.currentUser().name()).isEqualTo("다음 관리자");
        } finally { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }
    }
    @Test void nonHttpCallsDoNotShareAGlobalCachedActor() {
        var mapper = mock(CurrentUserMapper.class);
        when(mapper.findAdministrator()).thenReturn("admin", "backup");
        when(mapper.findName(anyString())).thenAnswer(call -> call.getArgument(0));
        when(mapper.findActiveRoles(anyString())).thenReturn(List.of("ADMIN"));
        var provider = new CurrentUserProvider(mapper);
        assertThat(provider.userId()).isEqualTo("admin");
        assertThat(provider.userId()).isEqualTo("backup");
    }
}
