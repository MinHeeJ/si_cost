package com.sicost.monthclosesops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.time.YearMonth;
import java.util.*;

public final class MonthCloseInput {
    private MonthCloseInput() {}
    public record Command(String expectedRevision,String operationId,Boolean accepted,String reason) {}
    public static YearMonth month(String text) {
        if(text==null||!text.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))throw InputValidator.invalid("month");
        return YearMonth.parse(text);
    }
    public static Command parse(JsonNode body,String action) {
        if(body==null||!body.isObject())throw InputValidator.invalid("body");
        Set<String> allowed=new HashSet<>(Set.of("expectedRevision"));
        String acceptance=action.equals("OPEN")?"acceptAdjustments":"acceptWarnings";
        boolean execution=Set.of("OPEN","CLOSE").contains(action);
        if(execution){allowed.add("operationId");allowed.add(acceptance);if(action.equals("OPEN"))allowed.add("reason");}
        body.fieldNames().forEachRemaining(f->{if(!allowed.contains(f))throw new ApiException(400,"UNKNOWN_FIELD","등록되지 않은 입력 항목입니다.",f,null);});
        String revision=text(body,"expectedRevision");
        if(revision.isBlank())throw InputValidator.invalid("expectedRevision");
        String id=null,reason=null;Boolean accepted=null;
        if(execution) {
            id=text(body,"operationId");
            if(!id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw InputValidator.invalid("operationId");
            id=UUID.fromString(id).toString();
            if(!body.has(acceptance)||!body.get(acceptance).isBoolean())throw InputValidator.invalid(acceptance);
            accepted=body.get(acceptance).booleanValue();
            if(action.equals("OPEN"))reason=InputValidator.text(text(body,"reason"),500,"reason");
        }
        return new Command(revision,id,accepted,reason);
    }
    private static String text(JsonNode body,String field){if(!body.has(field)||!body.get(field).isTextual())throw InputValidator.invalid(field);return body.get(field).textValue();}
}
