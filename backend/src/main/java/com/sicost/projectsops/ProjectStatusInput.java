package com.sicost.projectsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Map;
import java.util.Set;
import static com.sicost.projectsops.ProjectStatusDtos.*;

public final class ProjectStatusInput {
    private ProjectStatusInput() {}
    public static String id(String value) { return ProjectInputPolicy.text(value,20,"id"); }
    public static void query(Map<String,String> query,Set<String> allowed) {
        query.keySet().forEach(key->{if(!allowed.contains(key))throw ProjectInputPolicy.invalid(key);});
    }
    public static String choice(String value,Set<String> allowed,String field) {
        String text=ProjectInputPolicy.text(value,100,field);
        if(!allowed.contains(text))throw ProjectInputPolicy.invalid(field);return text;
    }
    public static Progress progress(JsonNode body) {
        body(body,Set.of("year","expectedRevision"));return new Progress(year(body),revision(body));
    }
    public static Loss loss(JsonNode body) {
        body(body,Set.of("year","expectedRevision","lossReason","lossMemo","previewToken"));
        return new Loss(year(body),revision(body),ProjectInputPolicy.text(text(body,"lossReason"),10,"lossReason"),
            body.hasNonNull("lossMemo")?ProjectInputPolicy.optionalText(text(body,"lossMemo"),500,"lossMemo"):null,
            ProjectInputPolicy.text(text(body,"previewToken"),200,"previewToken"));
    }
    private static void body(JsonNode body,Set<String> allowed) {
        if(body==null||!body.isObject())throw ProjectInputPolicy.invalid("body");
        body.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw ProjectInputPolicy.invalid(key);});
    }
    private static int year(JsonNode body) {
        JsonNode year=body.get("year");
        if(year==null||!year.isIntegralNumber()||!year.canConvertToInt())throw ProjectInputPolicy.invalid("year");return year.intValue();
    }
    private static String revision(JsonNode body) {return ProjectInputPolicy.expectedRevision(text(body,"expectedRevision"));}
    private static String text(JsonNode body,String field) {
        JsonNode value=body.get(field);if(value==null||!value.isTextual())throw ProjectInputPolicy.invalid(field);return value.textValue();
    }
}
