package com.sicost.receivablesops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.util.*;
import static com.sicost.receivablesops.DunningDeliveryDtos.*;

public final class DunningDeliveryInput {
    private DunningDeliveryInput() {}
    public static String billId(String id) {
        if(id==null||!id.matches("[A-Za-z0-9_-]{1,20}"))throw InputValidator.invalid("billId");return id;
    }
    public static void allow(Map<String,String> query,Set<String> allowed) {
        query.keySet().forEach(key->{if(!allowed.contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 조회 항목입니다.",key,null);});
    }
    public static DeliveryQuery page(Map<String,String> query) {
        allow(query,Set.of("page","size","sort"));
        if(!query.getOrDefault("sort","reservedSn,asc").equals("reservedSn,asc"))throw InputValidator.invalid("sort");
        return new DeliveryQuery(InputValidator.page(integer(query,"page",1)),InputValidator.size(integer(query,"size",50)));
    }
    private static int integer(Map<String,String> query,String field,int fallback) {
        if(!query.containsKey(field))return fallback;
        try {if(!query.get(field).matches("[0-9]+"))throw InputValidator.invalid(field);return Integer.parseInt(query.get(field));}
        catch(NumberFormatException e){throw InputValidator.invalid(field);}
    }
    public static ReminderInput reminder(JsonNode body) {
        if(body==null||!body.isObject())throw InputValidator.invalid("body");
        body.fieldNames().forEachRemaining(key->{if(!Set.of("expectedRevision","operationId").contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 요청 항목입니다.",key,null);});
        if(!body.path("expectedRevision").isTextual()||body.path("expectedRevision").textValue().isBlank())throw InputValidator.invalid("expectedRevision");
        if(!body.path("operationId").isTextual()||!body.path("operationId").textValue().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw InputValidator.invalid("operationId");
        return new ReminderInput(body.get("expectedRevision").textValue(),UUID.fromString(body.get("operationId").textValue()));
    }
}
