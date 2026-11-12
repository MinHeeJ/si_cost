package com.sicost.receiptsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.sicost.receiptsops.ReceiptBatchDtos.*;

public final class ReceiptBatchInput {
    private ReceiptBatchInput() {}
    public static Request parse(JsonNode body) {
        allow(body,Set.of("year","date","items"),"");
        JsonNode year=body.get("year");
        if(year==null||!year.isIntegralNumber()||!year.canConvertToInt())throw invalid("year");
        LocalDate date;
        try { date=ProjectInputPolicy.date(text(body,"date"),"date"); }
        catch(ApiException e) {throw invalid("date");}
        JsonNode rows=body.get("items");
        if(rows==null||!rows.isArray()||rows.isEmpty())throw invalid("items");
        List<Item> items=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(int i=0;i<rows.size();i++) {
            String prefix="items["+i+"].";JsonNode row=rows.get(i);
            allow(row,Set.of("billId","amount","expectedRevision"),prefix);
            String id;
            try {id=ProjectInputPolicy.text(text(row,"billId"),20,prefix+"billId");}
            catch(ApiException e) {throw invalid(prefix+"billId");}
            if(!ids.add(id))throw invalid(prefix+"billId");
            String revision=text(row,"expectedRevision");
            if(revision.isBlank())throw invalid(prefix+"expectedRevision");
            items.add(new Item(id,amount(text(row,"amount"),prefix+"amount").toPlainString(),revision));
        }
        return new Request(year.intValue(),date,List.copyOf(items));
    }
    public static BigDecimal amount(String input,String field) {
        if(input==null)throw invalid(field);
        String value=input.replaceAll("[\\s\\p{Z}]","").replace(",","").replaceFirst("원$","");
        if(!value.matches("[0-9]+"))throw invalid(field);
        BigDecimal amount=new BigDecimal(value);
        if(amount.signum()<=0||amount.compareTo(new BigDecimal("999999999999999"))>0)throw invalid(field);
        return amount;
    }
    public static ApiException invalid(String field) {
        String message="입력 항목의 형식과 범위를 확인해 주세요.";
        return new ApiException(400,"VALIDATION_ERROR",message,field,
            Map.of("violations",List.of(Map.of("field",field,"message",message))));
    }
    private static String text(JsonNode body,String field) {
        JsonNode value=body.get(field);
        if(value==null||!value.isTextual())throw invalid(field);
        return value.textValue();
    }
    private static void allow(JsonNode body,Set<String> fields,String prefix) {
        if(body==null||!body.isObject())throw invalid(prefix.isEmpty()?"body":prefix.substring(0,prefix.length()-1));
        body.fieldNames().forEachRemaining(key->{if(!fields.contains(key))throw new ApiException(400,"UNKNOWN_FIELD",
            "허용되지 않은 입력 항목입니다.",prefix+key,Map.of("violations",List.of(Map.of("field",prefix+key,"message","허용되지 않은 입력 항목입니다."))));});
    }
}
