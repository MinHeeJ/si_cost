package com.sicost.operatingstandards;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.*;
import java.math.BigDecimal;
import java.util.*;
import static com.sicost.operatingstandards.OperatingStandardsDtos.*;

public final class OperatingStandardsInput {
    private OperatingStandardsInput() {}
    public static final Set<String> CASH = Set.of("CASH_BASE_YM", "CASH_BASE_AMT");
    public static final List<List<String>> GROUPS = List.of(
        List.of("DUN_D1","DUN_D2","DUN_D3"),
        List.of("EXT_PRBL_H","EXT_PRBL_M","EXT_PRBL_L","EXT_PRBL_NULL"),
        List.of("STEP_PRBL_1","STEP_PRBL_2","STEP_PRBL_3","STEP_PRBL_4"),
        List.of("BILL_MISS_RT","BILL_MISS_MIN"),
        List.of("AI_SCORE_JOB","AI_SCORE_SKILL","AI_SCORE_CUST","AI_SCORE_GRADE","AI_SCORE_PERF","AI_SCORE_COST","AI_SCORE_AVAIL","AI_SCORE_LOC"));
    public static Save parse(JsonNode body) {
        allow(body,Set.of("effectiveMonth","changes"),"");
        JsonNode changes=body.get("changes");
        if(changes==null || !changes.isArray() || changes.isEmpty()) throw InputValidator.invalid("changes");
        List<Change> result=new ArrayList<>(); Set<String> keys=new HashSet<>();
        for(int i=0;i<changes.size();i++) {
            JsonNode change=changes.get(i); String field="changes["+i+"].";
            allow(change,Set.of("key","value","expectedRevision"),field);
            String key=text(change,"key",30,field);
            if(!keys.add(key)) throw InputValidator.invalid(field+"key");
            String revision=text(change,"expectedRevision",200,field);
            JsonNode value=change.get("value");
            if(value==null || !value.isTextual()) throw InputValidator.invalid(field+"value");
            BigDecimal decimal;
            if(key.equals("CASH_BASE_YM")) {
                String month=value.textValue();
                if(!month.matches("(?!0000)[0-9]{4}(0[1-9]|1[0-2])")) throw InputValidator.invalid(field+"value");
                decimal=new BigDecimal(month);
            } else decimal=DecimalValidator.decimal(value.textValue(),false,field+"value");
            validateValue(key,decimal,field+"value");
            result.add(new Change(key,decimal,revision,field));
        }
        boolean cash=result.stream().anyMatch(c->CASH.contains(c.key()));
        if(cash && result.stream().anyMatch(c->!CASH.contains(c.key()))) throw InputValidator.invalid("changes");
        String month=null;
        if(cash) {if(body.has("effectiveMonth")) throw InputValidator.invalid("effectiveMonth");}
        else {
            month=text(body,"effectiveMonth",7,"");
            try {PeriodValidator.month(month);} catch(ApiException e) {throw new ApiException(400,"INVALID_PERIOD",e.getMessage(),"effectiveMonth",null);}
        }
        return new Save(month,List.copyOf(result));
    }
    public static void validateValue(String key,BigDecimal value,String field) {
        boolean probability=key.startsWith("AI_SCORE_") || key.startsWith("EXT_PRBL_") || key.startsWith("STEP_PRBL_");
        if(probability && (value.signum()<0 || value.compareTo(new BigDecimal("100"))>0)) throw InputValidator.invalid(field);
        if(key.equals("WTHDR_NOTI_D") && (value.compareTo(BigDecimal.ONE)<0 || value.scale()>0)) throw InputValidator.invalid(field);
    }
    public static void allow(JsonNode node,Set<String> allowed,String prefix) {
        if(node==null || !node.isObject()) throw InputValidator.invalid(prefix.isEmpty()?"changes":prefix);
        node.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",prefix+key,null);});
    }
    private static String text(JsonNode node,String key,int max,String prefix) {
        if(!node.hasNonNull(key) || !node.get(key).isTextual()) throw InputValidator.invalid(prefix+key);
        return InputValidator.text(node.get(key).textValue(),max,prefix+key);
    }
}
