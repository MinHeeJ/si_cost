package com.sicost.areasops;

import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.util.Set;
import org.springframework.util.MultiValueMap;

public final class AddressInput {
    private AddressInput() {}
    public static void allowQuery(MultiValueMap<String,String> query, Set<String> allowed) {
        query.forEach((key, values) -> {
            if (!allowed.contains(key)) throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);
            if (values.size() != 1) throw InputValidator.invalid(key);
        });
    }
    public static String code(String value, String field) {
        String code = InputValidator.text(value,10,field);
        if (!code.matches("[0-9]{10}")) throw InputValidator.invalid(field);
        return code;
    }
    public static String level(String value) {
        String level = InputValidator.text(value,3,"level");
        if (!Set.of("SGG","EMD").contains(level)) throw InputValidator.invalid("level");
        return level;
    }
}
