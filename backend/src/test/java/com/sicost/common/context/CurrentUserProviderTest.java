package com.sicost.common.context;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserProviderTest {
    @Test void databaseSuppliesOnlyTheTemporaryAdminContext() {
        var mapper = mock(CurrentUserMapper.class);
        when(mapper.findAdministrator()).thenReturn("admin"); when(mapper.findAdministratorRoles()).thenReturn(List.of("ADMIN"));
        var provider = new CurrentUserProvider(mapper); var user = provider.currentUser();
        assertThat(user.userId()).isEqualTo("admin"); assertThat(user.roleIds()).containsExactly("ADMIN");
        assertThat(user.name()).isEqualTo("임시 관리자"); assertThat(user.developmentMode()).isTrue();
        assertThat(user.dataScope()).isEqualTo("ALL"); assertThat(provider.allows("getMenus")).isTrue();
    }
    @Test void missingContextIsNotFabricatedAndDoesNotBecome401or403() {
        var mapper = mock(CurrentUserMapper.class); when(mapper.findAdministratorRoles()).thenReturn(List.of());
        assertThatThrownBy(() -> new CurrentUserProvider(mapper).currentUser()).isInstanceOf(IllegalStateException.class);
    }
}
