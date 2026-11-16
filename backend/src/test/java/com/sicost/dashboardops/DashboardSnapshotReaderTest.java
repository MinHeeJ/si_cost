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
