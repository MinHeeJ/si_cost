package com.sicost.states;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import com.sicost.common.validation.PeopleInputPolicy;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import static com.sicost.states.DeveloperStateDtos.*;

public final class DeveloperStateInput {
    private DeveloperStateInput() {}
    public static void query(Map<String,String> query) {
        query.keySet().forEach(key->{throw unknown(key);});
    }
    public static void id(String id) {
        if(id==null || !id.matches("DEV-[0-9]{6,16}"))throw InputValidator.invalid("id");
    }
    public static void sn(int sn) { if(sn<1 || sn>99999)throw InputValidator.invalid("sn"); }
    private static void allow(JsonNode body, Set<String> fields) {
        if(body==null || !body.isObject())throw InputValidator.invalid("body");
        body.fieldNames().forEachRemaining(key->{if(!fields.contains(key))throw unknown(key);});
    }
    static String text(JsonNode body,String field,int max,boolean required) {
        JsonNode node=body.get(field);
        if(node==null || node.isNull()){if(required)throw InputValidator.invalid(field);return null;}
        if(!node.isTextual())throw InputValidator.invalid(field);
        return required?InputValidator.text(node.textValue(),max,field):InputValidator.optionalText(node.textValue(),max,field);
    }
    private static LocalDate date(JsonNode body,String field,boolean required) {
        String value=text(body,field,10,required);
        if(value==null) {
            if(body.has(field) && !body.get(field).isNull())throw InputValidator.invalid(field);
            return null;
        }
        return PeopleInputPolicy.date(value,field);
    }
    public static Create create(JsonNode body) {
        allow(body,Set.of("state","startDate","endDate","reason","expectedRevision"));
        String state=text(body,"state",10,true);
        if(!Set.of("LEAVE","WORK","RETIRE").contains(state))throw InputValidator.invalid("state");
        if(!state.equals("LEAVE") && body.has("endDate"))throw InputValidator.invalid("endDate");
        LocalDate start=date(body,"startDate",true),end=date(body,"endDate",false);
        if(end!=null && end.isBefore(start))throw InputValidator.invalid("endDate");
        String reason=text(body,"reason",300,false);
        if(state.equals("WORK") && reason==null)reason="휴직 복귀";
        return new Create(state,start,end,reason,text(body,"expectedRevision",200,true));
    }
    public static End end(JsonNode body) {
        allow(body,Set.of("endDate","expectedRevision"));
        if(!body.has("endDate"))throw InputValidator.invalid("endDate");
        return new End(date(body,"endDate",false),text(body,"expectedRevision",200,true));
    }
    public static Cancel cancel(JsonNode body) {
        allow(body,Set.of("reason","expectedRevision"));
        return new Cancel(text(body,"reason",300,false),text(body,"expectedRevision",200,true));
    }
    private static ApiException unknown(String field) {return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",field,null);}
}
