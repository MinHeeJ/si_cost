package com.sicost.businessusersops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.businessusers.BusinessUserInput;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.util.*;
import static com.sicost.businessusersops.UserAdministrationDtos.*;

public final class UserAdministrationInput {
    private UserAdministrationInput() {}
    public static void allow(JsonNode body, Set<String> fields) {
        if (body == null || !body.isObject()) throw InputValidator.invalid("body");
        body.fieldNames().forEachRemaining(key -> {
            if (!fields.contains(key)) throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);
        });
    }
    private static String text(JsonNode body,String field,int max) {
        JsonNode value=body.get(field);
        if (value==null || !value.isTextual()) throw InputValidator.invalid(field);
        return InputValidator.text(value.textValue(),max,field);
    }
    public static String revision(JsonNode body) {
        allow(body,Set.of("expectedRevision"));
        return text(body,"expectedRevision",200);
    }
    public static StatusRequest bulk(JsonNode body) {
        allow(body,Set.of("action","items"));
        String action=text(body,"action",10);
        if (!Set.of("STOP","RESUME").contains(action)) throw InputValidator.invalid("action");
        JsonNode items=body.get("items");
        if(items==null || !items.isArray()) throw InputValidator.invalid("items");
        var unique=new TreeMap<String,String>();
        for(JsonNode item:items) {
            allow(item,Set.of("userId","expectedRevision"));
            String id=text(item,"userId",30), revision=text(item,"expectedRevision",200);
            String previous=unique.putIfAbsent(id,revision);
            if(previous!=null && !previous.equals(revision)) throw InputValidator.invalid("items");
        }
        if(unique.isEmpty() || unique.size()>100) throw InputValidator.invalid("items");
        return new StatusRequest(action,unique.entrySet().stream().map(e->new StatusItem(e.getKey(),e.getValue())).toList());
    }
    public static ActivityQuery activities(String id,Map<String,String> query) {
        BusinessUserInput.query(query,Set.of("page","size"));
        int page=InputValidator.page(integer(query,"page",1)),size=InputValidator.size(integer(query,"size",50));
        return new ActivityQuery(InputValidator.text(id,30,"id"),page,size,((long)page-1)*size);
    }
    private static int integer(Map<String,String> query,String key,int fallback) {
        if(!query.containsKey(key))return fallback;
        try {
            if(!query.get(key).matches("[0-9]+"))throw InputValidator.invalid(key);
            return Integer.parseInt(query.get(key));
        } catch(NumberFormatException e) {throw InputValidator.invalid(key);}
    }
}
