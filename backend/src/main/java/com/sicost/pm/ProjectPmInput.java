package com.sicost.pm;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Map;
import java.util.Set;
import static com.sicost.pm.ProjectPmDtos.*;

public final class ProjectPmInput {
    private ProjectPmInput() {}
    public static String id(String value) { return ProjectInputPolicy.text(value,20,"id"); }
    public static void query(Map<String,String> query) {
        query.keySet().stream().findFirst().ifPresent(key -> { throw ProjectInputPolicy.invalid(key); });
    }
    public static SaveProjectPmRequest save(JsonNode body) {
        fields(body,Set.of("userId","replaceConfirmed","expectedRevision"));
        boolean confirmed=false;
        if (body.has("replaceConfirmed")) {
            if (!body.get("replaceConfirmed").isBoolean()) throw ProjectInputPolicy.invalid("replaceConfirmed");
            confirmed=body.get("replaceConfirmed").booleanValue();
        }
        return new SaveProjectPmRequest(user(body),confirmed,revision(body));
    }
    public static DeleteProjectPmRequest delete(JsonNode body) {
        fields(body,Set.of("userId","expectedRevision"));
        return new DeleteProjectPmRequest(user(body),revision(body));
    }
    private static void fields(JsonNode body,Set<String> allowed) {
        if (body==null || !body.isObject()) throw ProjectInputPolicy.invalid("body");
        body.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) throw ProjectInputPolicy.invalid(key); });
    }
    private static String text(JsonNode body,String field) {
        JsonNode value=body.get(field);
        if (value==null || !value.isTextual()) throw ProjectInputPolicy.invalid(field);
        return value.textValue();
    }
    private static String user(JsonNode body) { return ProjectInputPolicy.text(text(body,"userId"),30,"userId"); }
    private static String revision(JsonNode body) { return ProjectInputPolicy.expectedRevision(text(body,"expectedRevision")); }
}
