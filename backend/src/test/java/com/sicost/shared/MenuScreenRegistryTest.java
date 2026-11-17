package com.sicost.shared;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuScreenRegistryTest {
    @Test void existingRouterWorkspacesAreImplementedEvenWhenTheirSharedHostIsOnlyAPlaceholder() {
        var mapper=mock(SharedQueriesMapper.class);when(mapper.menus()).thenReturn(List.of());
        var rows=new MenuService(mapper).screenRegistrations();
        assertThat(rows.stream().filter(MenuService.ScreenRegistration::implemented).map(MenuService.ScreenRegistration::screenId))
            .contains("UI-01","UI-15","UI-23").doesNotContain("UI-20","UI-22");
    }
}
