package com.sicost.dashboardops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.me.PreferenceService;
import com.sicost.shared.SharedQueriesMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import static com.sicost.common.excel.ExcelExportService.*;

@Service
public class DashboardExportService {
    private final ExcelExportService excel;
    private final DashboardSnapshotReader dashboard;
    private final SharedQueriesMapper shared;
    private final PreferenceService preferences;
    private final Clock clock;
    public DashboardExportService(ExcelExportService excel, DashboardSnapshotReader dashboard, SharedQueriesMapper shared,
                                  PreferenceService preferences, Clock clock) {
        this.excel=excel; this.dashboard=dashboard; this.shared=shared; this.preferences=preferences; this.clock=clock;
    }
    public Download create(String requestedMonth, HttpServletRequest request, HttpServletResponse response) {
        var columns = List.of(new Column("field","항목",CellType.TEXT));
        return excel.generateWorkbook(new Specification("대시보드",null,columns,"선택월="+(requestedMonth==null?"서버 현재월":requestedMonth),"천원","UI-16"),() -> {
            YearMonth current=YearMonth.now(clock.withZone(TimeConfiguration.SEOUL));
            YearMonth selected=requestedMonth==null?current:YearMonth.parse(requestedMonth);
            String rawBase=shared.cashBaseMonth();
            if(rawBase==null||!rawBase.matches("(?!0000)[0-9]{4}(0[1-9]|1[0-2])"))
                throw new ApiException(500,"INTERNAL_ERROR","자금 시작월 기준을 확인해 주세요.");
            YearMonth base=YearMonth.parse(rawBase,DateTimeFormatter.ofPattern("yyyyMM"));
            if(selected.isBefore(base)||selected.isAfter(current))
                throw new ApiException(400,"INVALID_PERIOD","선택월은 자금 시작월부터 현재월까지 조회할 수 있습니다.","month",null);
            String unit=preferences.getPreferences().items().stream().filter(p -> p.type().equals("AMT_UNIT")&&p.key().equals("*"))
                .map(p -> p.value()==null?"천원":p.value().asText()).filter(v -> List.of("원","천원","백만원").contains(v)).findFirst().orElse("천원");
            return DashboardWorkbook.create(dashboard.read(selected.toString(),request,response),unit);
        },request);
    }
}
