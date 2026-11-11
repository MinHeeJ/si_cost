package com.sicost.apiops;

import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import static com.sicost.apiops.PeopleHistoryDtos.*;

public final class PeopleHistoryInput {
    private PeopleHistoryInput() {}
    public static Query parse(MultiValueMap<String,String> values) {
        values.forEach((key, entries) -> {
            if (!Set.of("targetType", "targetId", "page", "size").contains(key))
                throw new ApiException(400, "UNKNOWN_FIELD", "허용되지 않은 입력 항목입니다.", key, null);
            if (entries.size() != 1) throw InputValidator.invalid(key);
        });
        String type = InputValidator.text(values.getFirst("targetType"), 9, "targetType");
        if (!Set.of("DEVELOPER", "CANDIDATE").contains(type)) throw InputValidator.invalid("targetType");
        String id = InputValidator.text(values.getFirst("targetId"), 20, "targetId");
        int page = InputValidator.page(integer(values.getFirst("page"), 1, "page"));
        int size = InputValidator.size(integer(values.getFirst("size"), 50, "size"));
        return new Query(type, id, page, size, (long)(page - 1) * size);
    }
    private static int integer(String value, int fallback, String field) {
        if (value == null) return fallback;
        if (!value.matches("[0-9]+")) throw InputValidator.invalid(field);
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { throw InputValidator.invalid(field); }
    }
}
