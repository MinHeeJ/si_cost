package com.sicost.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import com.sicost.common.validation.MasterDataValidator;
import java.util.*;
import static com.sicost.clients.ClientDtos.*;

public final class ClientInput {
    private ClientInput() {}
    private static final Set<String> COMMON = Set.of("type","name","ceo","industryCode","contactName","phone","taxEmail","bankCode","accountNumber","depositor");
    public static final Set<String> SORTS = Set.of("type","name","businessNo","ceo","industry","contactName","phone","taxEmail","projectCount","personCount","useYn");
    public static Save parse(JsonNode body, boolean editing) {
        Set<String> allowed = new HashSet<>(COMMON);
        if (editing) allowed.addAll(Set.of("accountAction","expectedRevision")); else allowed.add("businessNo");
        if (body == null || !body.isObject()) throw InputValidator.invalid("body");
        body.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) throw unknown(key); });
        String type=MasterDataValidator.enumValue(text(body,"type",10,true),Set.of("CUST","VNDR","BOTH"),"type");
        String action=editing?MasterDataValidator.enumValue(text(body,"accountAction",10,true),Set.of("KEEP","REPLACE","CLEAR"),"accountAction"):null;
        String revision=editing?text(body,"expectedRevision",200,true):null;
        String businessNo=editing?null:businessNo(text(body,"businessNo",12,true));
        String name=text(body,"name",50,true), ceo=text(body,"ceo",20,false), industry=text(body,"industryCode",10,false),
            contact=text(body,"contactName",20,false), phone=text(body,"phone",20,false);
        List<Warning> warnings=new ArrayList<>();
        String email=null,bank=null,account=null,depositor=null;
        if (!type.equals("VNDR")) email=MasterDataValidator.email(text(body,"taxEmail",100,true),"taxEmail");
        else discard(body,"taxEmail",warnings);
        if (type.equals("CUST")) {
            if (editing && !"CLEAR".equals(action)) throw InputValidator.invalid("accountAction");
            for(String key:List.of("bankCode","accountNumber","depositor")) discard(body,key,warnings);
        } else {
            if ("CLEAR".equals(action)) throw InputValidator.invalid("accountAction");
            if ("KEEP".equals(action)) {
                for(String key:List.of("bankCode","accountNumber","depositor")) if(body.has(key)) throw InputValidator.invalid(key);
            } else {
                bank=text(body,"bankCode",10,true); account=text(body,"accountNumber",20,true); depositor=text(body,"depositor",30,true);
                if(!account.matches("(?=.*[0-9])[0-9-]{8,20}")) throw InputValidator.invalid("accountNumber");
            }
        }
        return new Save(new Fields(type,name,ceo,industry,contact,phone,email,bank,account,depositor),businessNo,action,revision,List.copyOf(warnings));
    }
    public static String businessNo(String value) {
        if(!value.matches("(?:[0-9]{10}|[0-9]{3}-[0-9]{2}-[0-9]{5})")) throw InputValidator.invalid("businessNo");
        String digits=value.replace("-","");
        return digits.substring(0,3)+"-"+digits.substring(3,5)+"-"+digits.substring(5);
    }
    private static void discard(JsonNode body,String field,List<Warning> warnings) {
        JsonNode value=body.get(field);
        if(value!=null && !value.isNull() && (!value.isTextual() || !value.textValue().isBlank()))
            warnings.add(new Warning(field,field+" 항목은 선택한 구분에 적용되지 않아 저장하지 않습니다."));
    }
    private static String text(JsonNode body,String key,int max,boolean required) {
        JsonNode value=body.get(key);
        if(value==null || value.isNull()) {if(required) throw InputValidator.invalid(key);return null;}
        if(!value.isTextual())throw InputValidator.invalid(key);
        return required?InputValidator.text(value.textValue(),max,key):InputValidator.optionalText(value.textValue(),max,key);
    }
    public static Query query(Map<String,String> query) {
        allowQuery(query,Set.of("type","useYn","q","sort","direction","page","size"));
        String type=query.containsKey("type")?MasterDataValidator.enumValue(query.get("type"),Set.of("CUST","VNDR","BOTH"),"type"):null;
        String use=query.containsKey("useYn")?InputValidator.yesNo(query.get("useYn"),"useYn"):null;
        String sort=query.getOrDefault("sort","name"), direction=query.getOrDefault("direction","ASC");
        if(!SORTS.contains(sort))throw InputValidator.invalid("sort");
        if(!Set.of("ASC","DESC").contains(direction))throw InputValidator.invalid("direction");
        int page=number(query,"page",1),size=number(query,"size",50);
        return new Query(type,use,MasterDataValidator.search(query.get("q")),sort,direction,InputValidator.page(page),InputValidator.size(size));
    }
    private static int number(Map<String,String> query,String key,int fallback) {
        if(!query.containsKey(key))return fallback;
        try {if(!query.get(key).matches("[0-9]+"))throw InputValidator.invalid(key);return Integer.parseInt(query.get(key));}
        catch(NumberFormatException e){throw InputValidator.invalid(key);}
    }
    public static void allowQuery(Map<String,String> query,Set<String> allowed) {
        query.keySet().forEach(key->{if(!allowed.contains(key))throw unknown(key);});
    }
    private static ApiException unknown(String field){return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",field,null);}
}
