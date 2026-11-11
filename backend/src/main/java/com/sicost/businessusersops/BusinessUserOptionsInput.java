package com.sicost.businessusersops;

import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import org.springframework.util.MultiValueMap;

public final class BusinessUserOptionsInput {
    private BusinessUserOptionsInput() {}
    public static String role(MultiValueMap<String, String> query) {
        query.keySet().forEach(key -> {
            if (!key.equals("purpose"))
                throw new ApiException(400, "UNKNOWN_FIELD", "허용되지 않은 입력 항목입니다.", key, null);
        });
        var values = query.get("purpose");
        if (values == null || values.size() != 1) throw InputValidator.invalid("purpose");
        return switch (values.get(0)) {
            case "PM" -> "PM";
            case "PART_LEADER" -> "SALES_LDR";
            default -> throw InputValidator.invalid("purpose");
        };
    }
}
