package com.sicost.apiops;

import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.AssignmentInputPolicy;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import static com.sicost.apiops.PayrollDtos.*;

public final class PayrollInput {
    private PayrollInput() {}
    public static Query query(MultiValueMap<String, String> values) {
        values.forEach((key, entries) -> {
            if (!Set.of("month", "q", "page", "size").contains(key) || entries.size() != 1)
                throw ProjectInputPolicy.invalid(key);
        });
        String month = ProjectInputPolicy.month(values.getFirst("month"), "month").toString();
        String q = ProjectInputPolicy.optionalText(values.getFirst("q"), 100, "q");
        int page = AssignmentInputPolicy.page(integer(values.getFirst("page"), 1, "page"));
        int size = AssignmentInputPolicy.size(integer(values.getFirst("size"), 50, "size"));
        return new Query(DatabaseMonthCodec.toDatabase(month), q, page, size, ((long) page - 1) * size);
    }
    private static int integer(String value, int fallback, String field) {
        if (value == null) return fallback;
        String text = ProjectInputPolicy.text(value, 10, field);
        if (!text.matches("[0-9]+")) throw ProjectInputPolicy.invalid(field);
        try { return Integer.parseInt(text); }
        catch (NumberFormatException e) { throw ProjectInputPolicy.invalid(field); }
    }
}
