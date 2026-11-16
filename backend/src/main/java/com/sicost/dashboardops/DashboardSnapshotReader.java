package com.sicost.dashboardops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.apiops.DashboardService;
import com.sicost.common.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

/** Reuses the full dashboard read in the export transaction without servlet query merging. */
@Component
public class DashboardSnapshotReader {
    private final ObjectMapper json;
    private final DashboardService dashboard;
    public DashboardSnapshotReader(ObjectMapper json, DashboardService dashboard) {
        this.json = json;
        this.dashboard = dashboard;
    }

    public JsonNode read(String month, HttpServletRequest request, HttpServletResponse response) {
        var data = dashboard.getDashboard(month);
        if (data == null || !month.equals(data.selectedMonth()))
            throw new ApiException(500, "INTERNAL_ERROR", "대시보드 전체 조회를 불러오지 못했습니다. 다시 시도해 주세요.");
        return json.valueToTree(data);
    }
}
