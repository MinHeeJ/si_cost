package com.sicost.codegroups;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.util.*;
import static com.sicost.codegroups.CodeGroupDtos.*;

public final class CodeGroupInput {
    private CodeGroupInput() {}
    public static void fields(JsonNode node, Set<String> allowed, String prefix) {
        if (node == null || !node.isObject()) throw InputValidator.invalid(prefix);
        node.fieldNames().forEachRemaining(key -> {
            if (!allowed.contains(key)) throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",prefix + key,null);
        });
    }
    public static String text(JsonNode node, String field) {
        if (node == null || !node.isTextual()) throw InputValidator.invalid(field);
        return node.textValue();
    }
    public static Create create(JsonNode node) {
        fields(node,Set.of("groupId","name"),"");
        return new Create(InputValidator.groupId(text(node.get("groupId"),"groupId")),
                InputValidator.text(text(node.get("name"),"name"),50,"name"));
    }
    public static Save save(JsonNode node) {
        fields(node,Set.of("expectedRevision","changes"),"");
        String revision = InputValidator.text(text(node.get("expectedRevision"),"expectedRevision"),200,"expectedRevision");
        JsonNode changes = node.get("changes");
        if (changes == null || !changes.isArray() || changes.isEmpty()) throw InputValidator.invalid("changes");
        List<Change> result = new ArrayList<>(); Set<String> codes = new HashSet<>();
        for (int i=0;i<changes.size();i++) {
            String field = "changes["+i+"]."; JsonNode item = changes.get(i);
            fields(item,Set.of("code","isNew","name","useYn","addVal1","addVal2","codeVal","unitNm","sortOrder"),field);
            String code;
            try { code = InputValidator.code(text(item.get("code"),field+"code")); }
            catch (ApiException e) { throw InputValidator.invalid(field+"code"); }
            if (!codes.add(code)) throw InputValidator.invalid(field+"code");
            if (!item.has("isNew") || !item.get("isNew").isBoolean()) throw InputValidator.invalid(field+"isNew");
            Map<String,JsonNode> values = new LinkedHashMap<>();
            item.fields().forEachRemaining(e -> { if (!Set.of("code","isNew").contains(e.getKey())) values.put(e.getKey(),e.getValue()); });
            result.add(new Change(code,item.get("isNew").booleanValue(),values,field));
        }
        return new Save(revision,List.copyOf(result));
    }
}
