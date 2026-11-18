package com.sicost.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PermissionProjectionService {
    private final PermissionService policies;
    public PermissionProjectionService(PermissionService policies) {this.policies=policies;}
    public boolean candidateRate() {return policies.candidateRateAllowed();}
    public boolean sensitive() {return policies.capabilities().sensitive();}
    public boolean billingRate() {return policies.candidateRateAllowed();}
    public boolean forbidden(String screen,String key) {
        return forbidden(screen,key,sensitive(),billingRate());
    }
    private boolean forbidden(String screen,String key,boolean sensitive,boolean billingRate) {
        String normalized=key.replace("_","").toLowerCase(Locale.ROOT);
        if(!billingRate && Set.of("monthlyrate","billingunitprice","appliedunitprice","defaultunitprice","newunitprice","unitprice","salesamount","newuprc").contains(normalized))return true;
        if(sensitive)return false;
        if(Set.of("UI-01","UI-31").contains(screen) && Set.of("costusage","costusagerate","costusageratio","costusagepercent","costprogressgap").contains(normalized))return false;
        if(screen.equals("UI-19") && normalized.equals("profitstatus"))return false;
        if(screen.equals("UI-32") && Set.of("marginrate","targetunitprice","requiredincreaserate","annualgain").contains(normalized))return false;
        if(screen.equals("UI-03") && Set.of("pay","netpay","teamexpense","projectexpense","labor","laborallocated","knownlaborallocated").contains(normalized))return true;
        if(normalized.equals("taxemail"))return false;
        if(normalized.equals("payamount") && !Set.of("UI-02","UI-03","UI-04").contains(screen))return false;
        return normalized.contains("salary") || normalized.contains("monthlypay") || normalized.contains("payamount") || normalized.contains("cost") || normalized.contains("profit") || normalized.contains("margin") || normalized.contains("insurance") || normalized.contains("retirement") || normalized.contains("allowance") || normalized.contains("incometax") || normalized.contains("withholdingtax") || normalized.contains("vendorsupply") || normalized.contains("overhead") || Set.of("bankcode","bankname","bankactive","accountnumber","depositor","account","cardamount").contains(normalized);
    }
    public JsonNode input(String screen,JsonNode input) {
        return input(screen,input,sensitive(),billingRate());
    }
    private JsonNode input(String screen,JsonNode input,boolean sensitive,boolean billingRate) {
        if(!(input instanceof ObjectNode original))return input;
        ObjectNode copy=original.deepCopy();
        copy.fieldNames().forEachRemaining(field->{
            boolean protectedField=screen.equals("UI-02") && Set.of("monthlyRate","monthlyPay").contains(field)
                || screen.equals("UI-12") && Set.of("billingUnitPrice","newUnitPrice").contains(field)
                || screen.equals("UI-15") && Set.of("bankCode","accountNumber","depositor").contains(field)
                || screen.equals("UI-19") && field.equals("cardAmount");
            if(protectedField)rejectForbidden(copy,field,!forbidden(screen,field,sensitive,billingRate));
        });
        List<String> remove=new ArrayList<>();copy.fields().forEachRemaining(entry->{
            if((screen.equals("UI-02") && Set.of("monthlyRate","monthlyPay").contains(entry.getKey()) || screen.equals("UI-12") && Set.of("billingUnitPrice","newUnitPrice").contains(entry.getKey()) || screen.equals("UI-15") && Set.of("bankCode","accountNumber","depositor").contains(entry.getKey())) && forbidden(screen,entry.getKey(),sensitive,billingRate))remove.add(entry.getKey());
            if(entry.getValue().isObject())copy.set(entry.getKey(),input(screen,entry.getValue(),sensitive,billingRate));
            else if(entry.getValue() instanceof com.fasterxml.jackson.databind.node.ArrayNode array)for(int i=0;i<array.size();i++)if(array.get(i).isObject())array.set(i,input(screen,array.get(i),sensitive,billingRate));
        });copy.remove(remove);return copy;
    }
    public JsonNode publicView(String screen,JsonNode value) {
        return publicView(screen,value,sensitive(),billingRate());
    }
    public JsonNode dashboardView(JsonNode value) {
        if(!(value instanceof ObjectNode object))return value;
        var allowed=policies.capabilities();
        if(!allowed.scope().all())object.remove(List.of("cashBasis","cashForecast","closeProgress","salesCostTrendBasis","salesCostTrend","salesPartsBasis","salesParts","salesPartExplanation"));
        if(!allowed.sensitive() || !allowed.screens().getOrDefault("UI-04",Set.of()).contains("INQ"))object.remove(List.of("projectAlerts","projectAlertsBasis"));
        return object;
    }
    private JsonNode publicView(String screen,JsonNode value,boolean sensitive,boolean billingRate) {
        if(value.isObject()) {
            ObjectNode object=(ObjectNode)value;List<String> remove=new ArrayList<>();
            object.fields().forEachRemaining(entry->{if(forbidden(screen,entry.getKey(),sensitive,billingRate))remove.add(entry.getKey());else publicView(screen,entry.getValue(),sensitive,billingRate);});object.remove(remove);
            if(object.get("availableFields") instanceof com.fasterxml.jackson.databind.node.ArrayNode available)
                for(int i=available.size()-1;i>=0;i--)if(available.get(i).isTextual() && forbidden(screen,available.get(i).asText(),sensitive,billingRate))available.remove(i);
        } else if(value instanceof com.fasterxml.jackson.databind.node.ArrayNode array) {
            for(int i=array.size()-1;i>=0;i--) {
                JsonNode item=array.get(i);String id=item.path("id").asText("");
                if(!id.isEmpty() && forbidden(screen,id,sensitive,billingRate))array.remove(i);else publicView(screen,item,sensitive,billingRate);
            }
        }
        return value;
    }
    public void rejectForbidden(JsonNode input,String field,boolean allowed) {
        if(allowed || input==null || !input.has(field) || input.get(field).isNull() || input.get(field).isTextual() && input.get(field).asText().isEmpty())return;
        throw new ApiException(400,"FIELD_NOT_EDITABLE","수정할 수 없는 항목입니다.",field,null);
    }
    public JsonNode candidateInput(JsonNode input,String preservedRate,boolean creating) {
        if(candidateRate())return input;
        rejectForbidden(input,"desiredRate",false);
        if(creating)throw new ApiException(409,"CANDIDATE_RATE_SOURCE_REQUIRED","단가 조회 권한 없는 신규 후보의 필수 단가 원천 정책이 확정되지 않았습니다.");
        if(!(input instanceof ObjectNode object))return input;
        ObjectNode copy=object.deepCopy();copy.put("desiredRate",preservedRate);return copy;
    }
    public List<String> candidateColumns(List<String> requested) {return candidateRate()?requested:requested.stream().filter(field->!field.equals("desiredRate")).toList();}
}
