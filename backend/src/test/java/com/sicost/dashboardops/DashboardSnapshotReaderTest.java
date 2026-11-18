package com.sicost.dashboardops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.apiops.DashboardService;
import com.sicost.apiops.DashboardDtos;
import com.sicost.common.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardSnapshotReaderTest {
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final DashboardService dashboard = mock(DashboardService.class);
    final DashboardSnapshotReader reader = new DashboardSnapshotReader(json, dashboard);

    @org.junit.jupiter.api.BeforeEach void projection() {
        var policy=mock(com.sicost.permission.PermissionService.class);
        when(policy.capabilities()).thenReturn(new com.sicost.permission.PermissionService.Capabilities(
            java.util.Map.of("UI-16",java.util.Set.of("INQ","DNLD")),
            new com.sicost.permission.PermissionService.Scope(true,java.util.Set.of(),java.util.Set.of()),true,
            java.util.Map.of(),java.util.Map.of(),java.util.Set.of()));
        when(policy.allows(anyString(),anyString())).thenReturn(true);
        reader.setProjection(new com.sicost.permission.PermissionProjectionService(policy));
    }
    @Test void readsSelectedMonthOnceWithoutIncludingTheOriginalExportQuery() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/dashboard/export");
        request.setParameter("month", "2026-07");
        var response = new MockHttpServletResponse();
        var data = DashboardExportFixture.data(json);
        ((com.fasterxml.jackson.databind.node.ObjectNode)data.withArray("metrics").get(0)).putNull("value");
        when(dashboard.getDashboard("2026-07")).thenReturn(json.treeToValue(data, DashboardDtos.Data.class));
        assertThat(reader.read("2026-07", request, response).path("metrics").get(0).get("value").isNull()).isTrue();
        verify(dashboard).getDashboard("2026-07");
        verifyNoMoreInteractions(dashboard);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeaderNames()).isEmpty();
    }

    @Test void missingDataAndWrongSelectedMonthNeverBecomeWorkbookData() throws Exception {
        assertThatThrownBy(() -> reader.read("2026-07", new MockHttpServletRequest(), new MockHttpServletResponse()))
            .isInstanceOf(ApiException.class);
        var data = DashboardExportFixture.data(json).put("selectedMonth", "2026-06");
        when(dashboard.getDashboard("2026-07")).thenReturn(json.treeToValue(data, DashboardDtos.Data.class));
        assertThatThrownBy(() -> reader.read("2026-07", new MockHttpServletRequest(), new MockHttpServletResponse()))
            .isInstanceOf(ApiException.class);
    }

    @Test void sourceFailurePropagatesBeforeWorkbookOrAudit() {
        var failure = new ApiException(500, "INTERNAL_ERROR", "원천 조회 실패");
        when(dashboard.getDashboard("2026-07")).thenThrow(failure);
        assertThatThrownBy(() -> reader.read("2026-07", new MockHttpServletRequest(), new MockHttpServletResponse()))
            .isSameAs(failure);
    }
}
