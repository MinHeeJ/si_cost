package com.sicost.codemappings;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CodeMappingRequests {
    private CodeMappingRequests() {}
    public record Create(String sourceText, String groupId, String code) {}
    public record DeleteItem(long mapId, String expectedRevision) {}
    public record Delete(List<DeleteItem> items) {}
    public static Create create(JsonNode body) {
        fields(body,Set.of("sourceText","groupId","code"),"body");
        return new Create(text(body,"sourceText",100),text(body,"groupId",30),text(body,"code",20));
    }
    public static Delete delete(JsonNode body) {
        fields(body,Set.of("items"),"body");
        JsonNode items=body.get("items");
        if(items==null || !items.isArray() || items.isEmpty()) throw InputValidator.invalid("items");
        List<DeleteItem> result=new ArrayList<>();
        Set<Long> ids=new HashSet<>();
        for(int i=0;i<items.size();i++) {
            JsonNode item=items.get(i); String prefix="items."+i;
            fields(item,Set.of("mapId","expectedRevision"),prefix);
            JsonNode id=item.get("mapId");
            if(id==null || !id.isIntegralNumber() || !id.canConvertToLong()) throw InputValidator.invalid(prefix+".mapId");
            JsonNode revision=item.get("expectedRevision");
            if(revision==null || !revision.isTextual() || revision.textValue().isEmpty())
                throw InputValidator.invalid(prefix+".expectedRevision");
            if(!ids.add(id.longValue())) throw InputValidator.invalid("items");
            result.add(new DeleteItem(id.longValue(),revision.textValue()));
        }
        return new Delete(List.copyOf(result));
    }
    private static String text(JsonNode body,String field,int max) {
        JsonNode value=body.get(field);
        if(value==null || !value.isTextual()) throw InputValidator.invalid(field);
        return InputValidator.text(value.textValue(),max,field);
    }
    private static void fields(JsonNode node,Set<String> allowed,String prefix) {
        if(node==null || !node.isObject()) throw InputValidator.invalid(prefix);
        node.fieldNames().forEachRemaining(field -> {
            if(!allowed.contains(field)) throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",
                    prefix.equals("body") ? field : prefix+"."+field,null);
        });
    }
}
