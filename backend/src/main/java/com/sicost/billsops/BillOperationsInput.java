package com.sicost.billsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.*;
import org.springframework.util.MultiValueMap;
import static com.sicost.billsops.BillOperationsDtos.*;

public final class BillOperationsInput {
    private BillOperationsInput() {}
    public static final Set<String> SORTS=Set.of("id","projectName","order","billingDate","supplyAmount","vatAmount","totalAmount","receiptAmount","unpaidAmount","dueDate","status","dunning");
    public static String id(String value) {
        if(value==null||!value.matches("B-[0-9]{6}-[0-9]{3}"))throw invalid("id");return value;
    }
    public static String project(String value) {return ProjectInputPolicy.text(value,20,"projectId");}
    public static String month(String value) {
        try{return ProjectInputPolicy.month(value,"month").toString();}catch(ApiException e){throw invalid("month");}
    }
    public static void query(MultiValueMap<String,String> query,Set<String> allowed) {
        query.forEach((key,values)->{
            if(!allowed.contains(key))throw unknown(key);
            if(!Set.of("ids","excludeIds").contains(key)&&values.size()!=1)throw invalid(key);
        });
    }
    public static int year(String value) {
        try{if(value==null||!value.matches("[0-9]{4}"))throw invalid("year");return Integer.parseInt(value);}catch(NumberFormatException e){throw invalid("year");}
    }
    public static boolean carryover(String value) {
        if(value==null)return true;if(!Set.of("true","false").contains(value))throw invalid("includeCarryover");return Boolean.parseBoolean(value);
    }
    public static SummaryQuery summary(MultiValueMap<String,String> q) {
        query(q,Set.of("year","includeCarryover"));return new SummaryQuery(year(q.getFirst("year")),carryover(q.getFirst("includeCarryover")));
    }
    public static SelectionQuery selection(MultiValueMap<String,String> q) {
        query(q,Set.of("year","status","month","projectId","includeCarryover","sort","direction","ids","excludeIds"));
        String status=q.getFirst("status"),sort=q.getFirst("sort"),direction=q.getFirst("direction");
        if(status!=null&&!Set.of("PAID","UNPAID","LATE","CNCL").contains(status))throw invalid("status");
        if(sort!=null&&!SORTS.contains(sort))throw invalid("sort");
        if(direction!=null&&!Set.of("ASC","DESC").contains(direction))throw invalid("direction");
        return new SelectionQuery(year(q.getFirst("year")),status,q.containsKey("month")?month(q.getFirst("month")):null,
            q.containsKey("projectId")?project(q.getFirst("projectId")):null,carryover(q.getFirst("includeCarryover")),sort,
            direction==null?"DESC":direction,ids(q,"ids"),ids(q,"excludeIds"));
    }
    private static List<String> ids(MultiValueMap<String,String> q,String key) {
        if(!q.containsKey(key))return Collections.emptyList();
        List<String> values=new ArrayList<>();
        for(String item:q.get(key))for(String value:item.split(",",-1)) {
            try{values.add(id(value));}catch(ApiException e){throw invalid(key);}
        }
        if(values.isEmpty()||new HashSet<>(values).size()!=values.size())throw invalid(key);
        return List.copyOf(values);
    }
    public static MutationRequest mutation(JsonNode body,boolean cancel) {
        Set<String> allowed=cancel?Set.of("year","expectedRevision","reason"):Set.of("year","expectedRevision");
        if(body==null||!body.isObject())throw invalid("body");
        body.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw unknown(key);});
        JsonNode year=body.get("year");
        if(year==null||!year.isIntegralNumber()||!year.canConvertToInt())throw invalid("year");
        String revision=ProjectInputPolicy.expectedRevision(text(body,"expectedRevision"));
        String reason=cancel?ProjectInputPolicy.text(text(body,"reason"),500,"reason"):null;
        return new MutationRequest(year.intValue(),revision,reason);
    }
    private static String text(JsonNode body,String field) {
        if(!body.hasNonNull(field)||!body.get(field).isTextual())throw invalid(field);return body.get(field).textValue();
    }
    public static ApiException invalid(String field) {
        return new ApiException(400,"VALIDATION_ERROR","입력 항목의 형식과 길이를 확인해 주세요.",field,
            Map.of("violations",List.of(Map.of("field",field,"message","입력값을 확인해 주세요."))));
    }
    public static ApiException unknown(String field) {return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",field,null);}
}
