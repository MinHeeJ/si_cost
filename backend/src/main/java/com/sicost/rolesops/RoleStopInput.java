package com.sicost.rolesops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.util.Map;

public final class RoleStopInput {
    private RoleStopInput() {}
    public static RoleStopDtos.Request parse(JsonNode body,Map<String,String> query) {
        query.keySet().forEach(key->{throw unknown(key);});
        if(body==null || !body.isObject())throw InputValidator.invalid("body");
        body.fieldNames().forEachRemaining(key->{if(!"expectedRevision".equals(key))throw unknown(key);});
        JsonNode revision=body.get("expectedRevision");
        if(revision==null || !revision.isTextual() || revision.textValue().strip().isEmpty())throw InputValidator.invalid("expectedRevision");
        return new RoleStopDtos.Request(revision.textValue().strip());
    }
    private static ApiException unknown(String field) {return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",field,null);}
}
