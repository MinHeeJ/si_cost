package com.sicost.developersops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.util.MultiValueMap;
import static com.sicost.developersops.DeveloperActionsDtos.*;

public final class DeveloperActionsInput {
    private DeveloperActionsInput() {}
    public static void query(MultiValueMap<String,String> values, Set<String> allowed) {
        values.forEach((key,list)-> {
            if (!allowed.contains(key)) throw unknown(key);
            if (list.size()!=1) throw InputValidator.invalid(key);
        });
    }
    public static Filters filters(MultiValueMap<String,String> q, LocalDate today) {
        query(q,Set.of("employeeType","jobCode","directClass","workStatus","q","includeStopped","sort","direction"));
        String employee=option(q,"employeeType",Set.of("FT","CT","FL","OS"));
        String direct=option(q,"directClass",Set.of("DRCT","INDRCT"));
        String status=option(q,"workStatus",Set.of("INPUT","WTHDR","IDLE","LEAVE","RETIRE"));
        String sort=option(q,"sort",Set.of("name","companyName","employeeType","grade","job","status","withdrawalDate","monthlyRate"));
        String direction=option(q,"direction",Set.of("ASC","DESC"));
        String stopped=q.getFirst("includeStopped");
        if (stopped!=null && !Set.of("true","false").contains(stopped)) throw InputValidator.invalid("includeStopped");
        String job=InputValidator.optionalText(q.getFirst("jobCode"),10,"jobCode");
        return new Filters(employee,job,direct,status,MasterDataValidator.search(q.getFirst("q")),
            "true".equals(stopped)||"RETIRE".equals(status),sort,direction==null?"ASC":direction,today);
    }
    private static String option(MultiValueMap<String,String> q,String key,Set<String> allowed) {
        String value=InputValidator.optionalText(q.getFirst(key),100,key);
        return value==null?null:MasterDataValidator.enumValue(value,allowed,key);
    }
    public static String id(String value) {
        String id=InputValidator.text(value,20,"id");
        if (!id.matches("DEV-[0-9]{6,16}")) throw InputValidator.invalid("id");
        return id;
    }
    public static AssignmentQuery assignments(String id,MultiValueMap<String,String> q) {
        query(q,Set.of("page","size"));
        int page=number(q.getFirst("page"),1,"page"),size=InputValidator.size(number(q.getFirst("size"),50,"size"));
        return new AssignmentQuery(id(id),page,size,(long)(page-1)*size);
    }
    private static int number(String value,int fallback,String field) {
        if (value==null) return fallback;
        try { if (!value.matches("[0-9]+") || Integer.parseInt(value)<1) throw InputValidator.invalid(field); return Integer.parseInt(value); }
        catch (NumberFormatException e) { throw InputValidator.invalid(field); }
    }
    public static BulkRequest bulk(JsonNode body) {
        allow(body,Set.of("targetUseYn","items"));
        String use=InputValidator.yesNo(text(body,"targetUseYn"),"targetUseYn");
        JsonNode items=body.get("items");
        if(items==null || !items.isArray()) throw InputValidator.invalid("items");
        Set<String> ids=new HashSet<>(),revisions=new HashSet<>(); List<SelectionItem> result=new ArrayList<>();
        for(JsonNode item:items) {
            allow(item,Set.of("id","revision","expectedRevision"));
            if(item.has("revision")&&item.has("expectedRevision")) throw InputValidator.invalid("items");
            String id=id(text(item,"id")),revision=text(item,item.has("expectedRevision")?"expectedRevision":"revision");
            if(!ids.add(id)||!revisions.add(revision)) throw InputValidator.invalid("items");
            result.add(new SelectionItem(id,revision));
        }
        return new BulkRequest(use,List.copyOf(result));
    }
    private static String text(JsonNode body,String key) {
        if(!body.hasNonNull(key)||!body.get(key).isTextual()||body.get(key).textValue().isBlank()) throw InputValidator.invalid(key);
        return body.get(key).textValue().trim();
    }
    private static void allow(JsonNode body,Set<String> allowed) {
        if(body==null||!body.isObject()) throw InputValidator.invalid("body");
        body.fieldNames().forEachRemaining(key->{if(!allowed.contains(key)) throw unknown(key);});
    }
    private static ApiException unknown(String key) { return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null); }
}
