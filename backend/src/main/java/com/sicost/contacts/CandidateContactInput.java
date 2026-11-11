package com.sicost.contacts;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import static com.sicost.contacts.CandidateContactDtos.*;

public final class CandidateContactInput {
    private CandidateContactInput() {}
    public static String id(String id) {
        if(id==null || !id.matches("CAND-[0-9]{6,15}"))throw InputValidator.invalid("id");
        return id;
    }
    public static void allowQuery(MultiValueMap<String,String> query,Set<String> allowed) {
        query.forEach((key,values)->{
            if(!allowed.contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);
            if(values.size()!=1)throw InputValidator.invalid(key);
        });
    }
    public static Query query(MultiValueMap<String,String> query) {
        allowQuery(query,Set.of("page","size"));
        return new Query(InputValidator.page(number(query,"page",1)),InputValidator.size(number(query,"size",50)));
    }
    private static int number(MultiValueMap<String,String> query,String key,int fallback) {
        if(!query.containsKey(key))return fallback;
        String value=query.getFirst(key);
        if(value==null || !value.matches("[0-9]+"))throw InputValidator.invalid(key);
        try{return Integer.parseInt(value);}catch(NumberFormatException e){throw InputValidator.invalid(key);}
    }
    public static Create parse(JsonNode body) {
        if(body==null || !body.isObject())throw InputValidator.invalid("body");
        body.fieldNames().forEachRemaining(key->{
            if(!Set.of("contactStatus","memo","expectedRevision").contains(key))
                throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);
        });
        String status=text(body,"contactStatus",10);
        if(!Set.of("BEFORE","DONE","ITVW","HOLD","CTRT").contains(status))throw InputValidator.invalid("contactStatus");
        String memo=text(body,"memo",200);
        JsonNode revision=body.get("expectedRevision");
        if(revision==null || !revision.isTextual() || revision.textValue().isBlank())throw InputValidator.invalid("expectedRevision");
        return new Create(status,memo,revision.textValue());
    }
    private static String text(JsonNode body,String field,int max) {
        JsonNode node=body.get(field);
        if(node==null || !node.isTextual())throw InputValidator.invalid(field);
        return InputValidator.text(node.textValue(),max,field);
    }
}
