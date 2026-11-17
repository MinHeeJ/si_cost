package com.sicost.businessusers;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import com.sicost.common.validation.MasterDataValidator;
import java.util.*;
import static com.sicost.businessusers.BusinessUserDtos.*;

public final class BusinessUserInput {
    private BusinessUserInput() {}
    public static final Set<String> BUSINESS_ROLES = Set.of("PM", "SALES_LDR");
    public static void query(Map<String,String> values, Set<String> allowed) {
        values.keySet().forEach(key -> { if (!allowed.contains(key)) throw unknown(key); });
    }
    public static Filter filter(Map<String,String> query) {
        query(query, Set.of("q","businessRole","role","includeStopped","status","page","size","sort","direction"));
        if(query.containsKey("role") && query.containsKey("businessRole"))throw InputValidator.invalid("role");
        String role=query.containsKey("role")?InputValidator.text(query.get("role"),20,"role"):null;
        boolean includeStopped=false;
        if(query.containsKey("includeStopped")) {
            if(!Set.of("true","false").contains(query.get("includeStopped")))throw InputValidator.invalid("includeStopped");
            includeStopped=Boolean.parseBoolean(query.get("includeStopped"));
        }
        int page = InputValidator.page(integer(query, "page", 1));
        int size = InputValidator.size(integer(query, "size", 50));
        return new Filter(MasterDataValidator.search(query.get("q")), optionalEnum(query,"businessRole",BUSINESS_ROLES),
            optionalEnum(query,"status",Set.of("USE","LOCK","STOP")), page, size,
            enumValue(query.getOrDefault("sort","name"),Set.of("userId","name","email","status","lastLoginAt"),"sort"),
            enumValue(query.getOrDefault("direction","ASC"),Set.of("ASC","DESC"),"direction"), ((long)page-1)*size,role,includeStopped);
    }
    public static Create create(JsonNode body) {
        allow(body, Set.of("userId","name","email","businessRoleIds","roleIds","headOffice","projectAssignments"));
        String id=text(body,"userId",20);
        if (!id.matches("[a-z0-9]{4,20}")) throw InputValidator.invalid("userId");
        return new Create(id,text(body,"name",50),MasterDataValidator.email(text(body,"email",100),"email"),body.has("roleIds")?null:roles(body,true),allRoles(body),bool(body,"headOffice",false),assignments(body));
    }
    public static Edit edit(JsonNode body) {
        allow(body, Set.of("name","email","businessRoleIds","roleIds","headOffice","expectedRevision"));
        return new Edit(text(body,"name",50),MasterDataValidator.email(text(body,"email",100),"email"),
            body.has("roleIds")?null:roles(body,false),text(body,"expectedRevision",200),allRoles(body),body.has("headOffice")?bool(body,"headOffice",false):null);
    }
    private static List<String> allRoles(JsonNode body) {
        if(body.has("roleIds") && body.has("businessRoleIds"))throw InputValidator.invalid("roleIds");
        if(!body.has("roleIds"))return null;
        JsonNode value=body.get("roleIds");
        if(!value.isArray() || value.isEmpty())throw InputValidator.invalid("roleIds");
        Set<String> ids=new TreeSet<>();
        for(JsonNode item:value) {
            if(!item.isTextual() || !ids.add(InputValidator.text(item.textValue(),20,"roleIds")))throw InputValidator.invalid("roleIds");
        }
        return List.copyOf(ids);
    }
    private static boolean bool(JsonNode body,String key,boolean fallback) {
        if(!body.has(key))return fallback;
        if(!body.get(key).isBoolean())throw InputValidator.invalid(key);
        return body.get(key).booleanValue();
    }
    private static List<ProjectAssignment> assignments(JsonNode body) {
        if(!body.has("projectAssignments"))return List.of();
        JsonNode value=body.get("projectAssignments");
        if(!value.isArray())throw InputValidator.invalid("projectAssignments");
        Set<String> ids=new HashSet<>();List<ProjectAssignment> result=new ArrayList<>();
        for(JsonNode row:value) {
            allow(row,Set.of("projectId","expectedRevision","replaceConfirmed"));
            String id=text(row,"projectId",20);
            if(!ids.add(id))throw InputValidator.invalid("projectAssignments");
            result.add(new ProjectAssignment(id,text(row,"expectedRevision",200),bool(row,"replaceConfirmed",false)));
        }
        return List.copyOf(result);
    }
    private static List<String> roles(JsonNode body,boolean creating) {
        JsonNode value=body.get("businessRoleIds");
        if (value==null || !value.isArray() || value.size()>2 || (creating && value.isEmpty())) throw InputValidator.invalid("businessRoleIds");
        Set<String> ids=new TreeSet<>();
        for (JsonNode item:value) {
            if (!item.isTextual()) throw InputValidator.invalid("businessRoleIds");
            String id=enumValue(item.textValue(),BUSINESS_ROLES,"businessRoleIds");
            if (!ids.add(id)) throw InputValidator.invalid("businessRoleIds");
        }
        return List.copyOf(ids);
    }
    private static void allow(JsonNode body,Set<String> allowed) {
        if (body==null || !body.isObject()) throw InputValidator.invalid("body");
        body.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) throw unknown(key); });
    }
    private static ApiException unknown(String key) {return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);}
    private static String text(JsonNode body,String key,int max) {
        JsonNode value=body.get(key);
        if (value==null || !value.isTextual()) throw InputValidator.invalid(key);
        return InputValidator.text(value.textValue(),max,key);
    }
    private static int integer(Map<String,String> query,String key,int fallback) {
        if (!query.containsKey(key)) return fallback;
        try { if (!query.get(key).matches("[0-9]+")) throw InputValidator.invalid(key); return Integer.parseInt(query.get(key)); }
        catch (NumberFormatException e) {throw InputValidator.invalid(key);}
    }
    private static String optionalEnum(Map<String,String> query,String key,Set<String> allowed) {
        return query.containsKey(key)?enumValue(query.get(key),allowed,key):null;
    }
    private static String enumValue(String value,Set<String> allowed,String field) {return MasterDataValidator.enumValue(value,allowed,field);}
}
