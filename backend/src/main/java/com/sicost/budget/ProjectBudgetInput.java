package com.sicost.budget;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Map;
import java.util.Set;
import static com.sicost.budget.ProjectBudgetDtos.*;

public final class ProjectBudgetInput {
    private ProjectBudgetInput() {}
    public static String id(String id) { return ProjectInputPolicy.text(id,20,"id"); }
    public static void query(Map<String,String> query) {
        query.keySet().stream().findFirst().ifPresent(key -> { throw ProjectInputPolicy.invalid(key); });
    }
    public static SaveProjectBudgetRequest save(JsonNode body) {
        if (body == null || !body.isObject()) throw ProjectInputPolicy.invalid("body");
        body.fieldNames().forEachRemaining(key -> {
            if (!Set.of("year","expectedRevision","budgetAmount").contains(key)) throw ProjectInputPolicy.invalid(key);
        });
        JsonNode year=body.get("year");
        if (year==null || !year.isIntegralNumber() || !year.canConvertToInt()) throw ProjectInputPolicy.invalid("year");
        return new SaveProjectBudgetRequest(year.intValue(),ProjectInputPolicy.expectedRevision(text(body,"expectedRevision")),
            ProjectInputPolicy.amount(text(body,"budgetAmount"),"budgetAmount"));
    }
    private static String text(JsonNode body,String key) {
        JsonNode value=body.get(key);
        if (value==null || !value.isTextual()) throw ProjectInputPolicy.invalid(key);
        return value.textValue();
    }
}
