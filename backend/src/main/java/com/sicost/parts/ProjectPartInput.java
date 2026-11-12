package com.sicost.parts;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Map;
import java.util.Set;
import static com.sicost.parts.ProjectPartDtos.*;

public final class ProjectPartInput {
    private ProjectPartInput() {}
    public static String id(String id) { return ProjectInputPolicy.text(id,20,"id"); }
    public static void query(Map<String,String> query) {
        query.keySet().stream().findFirst().ifPresent(key -> { throw ProjectInputPolicy.invalid(key); });
    }
    public static SaveProjectPartRequest save(JsonNode body) {
        if (body==null || !body.isObject()) throw ProjectInputPolicy.invalid("body");
        body.fieldNames().forEachRemaining(key -> {
            if (!Set.of("year","expectedRevision","partId","effectiveMonth","reason").contains(key)) throw ProjectInputPolicy.invalid(key);
        });
        JsonNode year=body.get("year");
        if (year==null || !year.isIntegralNumber() || !year.canConvertToInt()) throw ProjectInputPolicy.invalid("year");
        String reason=null;
        if (body.hasNonNull("reason")) {
            reason=text(body,"reason");
            if (reason.codePointCount(0,reason.length())>500) throw ProjectInputPolicy.invalid("reason");
            if (reason.isBlank()) reason=null;
        }
        return new SaveProjectPartRequest(year.intValue(),ProjectInputPolicy.expectedRevision(text(body,"expectedRevision")),
            ProjectInputPolicy.text(text(body,"partId"),20,"partId"),ProjectInputPolicy.month(text(body,"effectiveMonth"),"effectiveMonth").toString(),reason);
    }
    private static String text(JsonNode body,String field) {
        JsonNode value=body.get(field);
        if (value==null || !value.isTextual()) throw ProjectInputPolicy.invalid(field);
        return value.textValue();
    }
}
