package com.sicost.workforceforecastsops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.MultiValueMap;

public final class WorkforceForecastInput {
    private WorkforceForecastInput() {}
    public record Query(YearMonth from, int months, boolean includeUnfilled, String amountUnit,
                        Boolean includeOpportunities, BigDecimal minimumWinProbability) {
        public Query(YearMonth from, int months, boolean includeUnfilled, String amountUnit) {
            this(from, months, includeUnfilled, amountUnit, null, null);
        }
    }
    public static Query parse(MultiValueMap<String,String> values, String body, Clock clock) {
        if (body != null && !body.isBlank()) throw invalid("body", "GET 요청에는 본문을 지정할 수 없습니다.", "UNKNOWN_FIELD");
        for (var entry : values.entrySet()) {
            if (!Set.of("from", "months", "includeUnfilled", "amountUnit", "includeOpportunities", "minimumWinProbability").contains(entry.getKey()))
                throw invalid(entry.getKey(), "허용되지 않은 조회 항목입니다.", "UNKNOWN_FIELD");
            if (entry.getValue().size() != 1) throw invalid(entry.getKey(), "조회 항목은 한 번만 지정해 주세요.", "VALIDATION_ERROR");
        }
        String from = values.getFirst("from"), months = values.getFirst("months");
        if (from == null || !from.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))
            throw invalid("from", "시작월을 실제 YYYY-MM 형식으로 지정해 주세요.", "VALIDATION_ERROR");
        if (months == null || !months.matches("[1-9]|1[0-2]"))
            throw invalid("months", "개월은 1~12 정수로 지정해 주세요.", "VALIDATION_ERROR");
        YearMonth start = YearMonth.parse(from);
        int count = Integer.parseInt(months);
        if (start.isBefore(YearMonth.now(clock.withZone(TimeConfiguration.SEOUL))))
            throw invalid("from", "현재월 이전은 예측할 수 없습니다.", "INVALID_PERIOD");
        if (start.plusMonths(count - 1).getYear() > 9999)
            throw invalid("months", "예측 종료월은 9999년 12월 이하여야 합니다.", "INVALID_PERIOD");
        String include = values.getFirst("includeUnfilled");
        if (include != null && !Set.of("true", "false").contains(include))
            throw invalid("includeUnfilled", "미충원 포함 여부는 true 또는 false입니다.", "VALIDATION_ERROR");
        String unit = values.getFirst("amountUnit");
        if (unit != null && !Set.of("WON", "THOUSAND", "MILLION").contains(unit))
            throw invalid("amountUnit", "금액 단위를 확인해 주세요.", "VALIDATION_ERROR");
        String opportunities = values.getFirst("includeOpportunities");
        if (opportunities != null && !Set.of("true", "false").contains(opportunities))
            throw invalid("includeOpportunities", "영업 기회 포함 여부는 true 또는 false입니다.", "VALIDATION_ERROR");
        String probability = values.getFirst("minimumWinProbability");
        if (probability != null && (!"true".equals(opportunities) ||
            !probability.matches("(?:100(?:\\.0{1,2})?|(?:[0-9]|[1-9][0-9])(?:\\.[0-9]{1,2})?)")))
            throw invalid("minimumWinProbability", "영업 기회를 포함할 때 수주 확률을 0~100%로 지정해 주세요.", "VALIDATION_ERROR");
        return new Query(start, count, include == null || Boolean.parseBoolean(include), unit == null ? "THOUSAND" : unit,
            opportunities == null ? null : Boolean.valueOf(opportunities), probability == null ? null : new BigDecimal(probability));
    }
    private static ApiException invalid(String field, String message, String code) {
        return new ApiException(400, code, message, field, Map.of("violations", List.of(Map.of("field", field, "message", message))));
    }
}
