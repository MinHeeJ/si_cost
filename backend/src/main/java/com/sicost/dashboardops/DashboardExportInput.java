package com.sicost.dashboardops;

import com.sicost.common.api.ApiException;
import java.time.YearMonth;
import org.springframework.util.MultiValueMap;

final class DashboardExportInput {
    private DashboardExportInput() {}
    static String month(MultiValueMap<String,String> query, String body) {
        if (body != null && !body.isBlank()) throw invalid("body");
        for (String key : query.keySet()) {
            if (!key.equals("month")) throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 조회 항목입니다.",key,null);
            if (query.get(key).size() != 1) throw invalid(key);
        }
        String month = query.getFirst("month");
        if (month != null) {
            if (!month.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])")) throw invalid("month");
            YearMonth.parse(month);
        }
        return month;
    }
    private static ApiException invalid(String field) {
        return new ApiException(400,"VALIDATION_ERROR","조회 조건을 확인해 주세요.",field,null);
    }
}
