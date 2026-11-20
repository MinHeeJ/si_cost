package com.sicost.common.excel;

import com.sicost.shared.SharedQueriesMapper;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** AssignmentExportService.labels와 동일하게 현재·과거 공통코드의 이름을 조회한다. */
@Component
public class ExportLabels {
    private final SharedQueriesMapper codes;
    public ExportLabels(SharedQueriesMapper codes) { this.codes = codes; }
    public Map<String, String> labels(String group) {
        Map<String, String> result = new HashMap<>();
        codes.codes(group, true).forEach(code -> result.put(code.code(), code.name()));
        return result;
    }
    public static String name(Map<String, String> labels, String code) {
        return code == null ? "–" : labels.getOrDefault(code, "–");
    }
    public static String workforceStatus(String code) {
        return switch (code) {
            case "SHORT" -> "부족";
            case "SURPLUS" -> "여유";
            case "BALANCED" -> "보통";
            default -> "미확인";
        };
    }
}
