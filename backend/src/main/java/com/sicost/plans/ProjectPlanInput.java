package com.sicost.plans;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static com.sicost.plans.ProjectPlanDtos.*;

public final class ProjectPlanInput {
    private ProjectPlanInput() {}
    public static String id(String id) { return ProjectInputPolicy.text(id, 20, "id"); }
    public static void query(Map<String, String> query) {
        query.keySet().stream().findFirst().ifPresent(key -> { throw ProjectInputPolicy.invalid(key); });
    }
    public static SaveProjectPlansRequest save(JsonNode body) {
        object(body, Set.of("year", "expectedRevision", "items"), "body");
        JsonNode year = body.get("year");
        if (year == null || !year.isIntegralNumber() || !year.canConvertToInt()) throw ProjectInputPolicy.invalid("year");
        String revision = ProjectInputPolicy.expectedRevision(text(body, "expectedRevision", "expectedRevision"));
        JsonNode items = body.get("items");
        if (items == null || !items.isArray()) throw ProjectInputPolicy.invalid("items");
        var inputs = new ArrayList<PlanInput>();
        for (int index = 0; index < items.size(); index++) {
            String prefix = "items[" + index + "]";
            JsonNode item = items.get(index);
            object(item, Set.of("jobCode", "planMm"), prefix);
            inputs.add(new PlanInput(text(item, "jobCode", prefix + ".jobCode"), text(item, "planMm", prefix + ".planMm")));
        }
        return validated(new SaveProjectPlansRequest(year.intValue(), revision, inputs));
    }
    public static SaveProjectPlansRequest validated(SaveProjectPlansRequest input) {
        if (input == null) throw ProjectInputPolicy.invalid("body");
        if (input.year() == null) throw ProjectInputPolicy.invalid("year");
        String revision = ProjectInputPolicy.expectedRevision(input.expectedRevision());
        if (input.items() == null) throw ProjectInputPolicy.invalid("items");
        var result = new ArrayList<PlanInput>();
        var jobs = new HashSet<String>();
        for (int index = 0; index < input.items().size(); index++) {
            String prefix = "items[" + index + "]";
            PlanInput item = input.items().get(index);
            if (item == null) throw ProjectInputPolicy.invalid(prefix);
            String job = ProjectInputPolicy.text(item.jobCode(), 10, prefix + ".jobCode");
            if (!jobs.add(job)) throw ProjectInputPolicy.invalid("items");
            if (item.planMm() == null) throw ProjectInputPolicy.invalid(prefix + ".planMm");
            String mm = ProjectInputPolicy.mmString(ProjectInputPolicy.planMm(item.planMm(), prefix + ".planMm"));
            result.add(new PlanInput(job, mm));
        }
        return new SaveProjectPlansRequest(input.year(), revision, java.util.List.copyOf(result));
    }
    private static void object(JsonNode node, Set<String> allowed, String prefix) {
        if (node == null || !node.isObject()) throw ProjectInputPolicy.invalid(prefix);
        node.fieldNames().forEachRemaining(key -> {
            if (!allowed.contains(key)) throw ProjectInputPolicy.invalid(prefix.equals("body") ? key : prefix + "." + key);
        });
    }
    private static String text(JsonNode node, String key, String field) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual()) throw ProjectInputPolicy.invalid(field);
        return value.textValue();
    }
}
