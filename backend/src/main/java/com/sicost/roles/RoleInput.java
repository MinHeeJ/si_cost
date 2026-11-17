package com.sicost.roles;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.util.*;
import static com.sicost.roles.RoleDtos.*;

public final class RoleInput {
    private RoleInput() {}
    private static final Set<String> PERMISSION_FIELDS=Set.of("menuId","inquire","register","update","delete","upload","download","confirm");
    public static void query(Map<String,String> query) {query.keySet().forEach(key->{throw unknown(key);});}
    public static String id(String value) {return InputValidator.text(value,20,"id");}
    public static Create create(JsonNode body) {
        if(body==null || !body.isObject())throw InputValidator.invalid("body");
        String mode=text(body,"mode",4);
        if(!Set.of("NEW","COPY").contains(mode))throw InputValidator.invalid("mode");
        allow(body,mode.equals("NEW")?Set.of("mode","name","dataScope"):Set.of("mode","name","sourceRoleId","sourceRevision"));
        String name=text(body,"name",50);
        return mode.equals("NEW")?new Create(mode,name,scope(body),null,null)
            :new Create(mode,name,null,text(body,"sourceRoleId",20),text(body,"sourceRevision",200));
    }
    public static Edit edit(JsonNode body) {
        allow(body,Set.of("dataScope","sensitiveView","menuPermissions","expectedRevision"));
        String scope=scope(body),expected=text(body,"expectedRevision",200);
        boolean sensitive=bool(body,"sensitiveView");
        JsonNode rows=body.get("menuPermissions");
        if(rows==null || !rows.isArray())throw InputValidator.invalid("menuPermissions");
        List<PermissionInput> result=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(JsonNode row:rows) {
            allow(row,PERMISSION_FIELDS);String id=text(row,"menuId",20);
            if(!ids.add(id))throw InputValidator.invalid("menuPermissions");
            result.add(new PermissionInput(id,bool(row,"inquire"),bool(row,"register"),bool(row,"update"),bool(row,"delete"),
                bool(row,"upload"),bool(row,"download"),bool(row,"confirm")));
        }
        return new Edit(scope,sensitive,List.copyOf(result),expected);
    }
    private static String scope(JsonNode body) {
        String scope=text(body,"dataScope",10);
        if(!Set.of("ALL","PART","PROJ").contains(scope))throw InputValidator.invalid("dataScope");return scope;
    }
    private static boolean bool(JsonNode body,String key) {
        JsonNode value=body.get(key);if(value==null || !value.isBoolean())throw InputValidator.invalid(key);return value.booleanValue();
    }
    private static String text(JsonNode body,String key,int max) {
        JsonNode value=body.get(key);if(value==null || !value.isTextual())throw InputValidator.invalid(key);
        return InputValidator.text(value.textValue(),max,key);
    }
    private static void allow(JsonNode body,Set<String> allowed) {
        if(body==null || !body.isObject())throw InputValidator.invalid("body");
        body.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw unknown(key);});
    }
    private static ApiException unknown(String key) {return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);}
}
