package com.sicost.receipts;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import static com.sicost.receipts.ReceiptDtos.*;

public final class ReceiptInput {
    private ReceiptInput() {}
    public static ApiException invalid(String field) {
        String message="입력 항목의 형식과 필수 값을 확인해 주세요.";
        return new ApiException(400,"VALIDATION_ERROR",message,field,
            Map.of("violations",java.util.List.of(Map.of("field",field,"message",message))));
    }
    public static String id(String id) {
        if(id==null || !id.matches("B-[0-9]{6}-[0-9]{3}")) throw invalid("id");
        return id;
    }
    public static long receiptId(String value) {
        if(value==null || !value.matches("[0-9]{1,12}")) throw invalid("receiptId");
        long id=Long.parseLong(value);if(id<1)throw invalid("receiptId");return id;
    }
    public static void query(Map<String,String> query,boolean reading) {
        query.keySet().forEach(k->{if(!reading || !k.equals("year"))throw unknown(k);});
    }
    public static int queryYear(Map<String,String> query) {
        String value=query.get("year");if(value==null || !value.matches("[0-9]{4}"))throw invalid("year");
        return Integer.parseInt(value);
    }
    public static CreateReceipt create(JsonNode body) {
        allow(body,Set.of("year","expectedRevision","date","amount"));
        LocalDate date;
        try { date=ProjectInputPolicy.date(text(body,"date"),"date"); }
        catch(ApiException e){throw invalid("date");}
        String value=text(body,"amount").replaceAll("[\\s\\p{Z},]","").replaceFirst("원$","");
        if(!value.matches("[0-9]+"))throw invalid("amount");
        BigDecimal amount=new BigDecimal(value);
        if(amount.signum()<1 || amount.compareTo(new BigDecimal("999999999999999"))>0)throw invalid("amount");
        return new CreateReceipt(year(body),revision(body),date,amount);
    }
    public static CancelReceipt cancel(JsonNode body) {
        allow(body,Set.of("year","expectedRevision","reason"));
        return new CancelReceipt(year(body),revision(body),required(text(body,"reason"),500,"reason"));
    }
    private static String revision(JsonNode body){return required(text(body,"expectedRevision"),200,"expectedRevision");}
    private static String required(String value,int max,String field){
        try{return ProjectInputPolicy.text(value,max,field);}catch(ApiException e){throw invalid(field);}
    }
    private static int year(JsonNode body){
        JsonNode v=body.get("year");if(v==null || !v.isIntegralNumber() || !v.canConvertToInt())throw invalid("year");return v.intValue();
    }
    private static String text(JsonNode body,String field){
        JsonNode v=body.get(field);if(v==null || !v.isTextual())throw invalid(field);return v.textValue();
    }
    private static ApiException unknown(String field){return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",field,null);}
    private static void allow(JsonNode body,Set<String> fields){
        if(body==null || !body.isObject())throw invalid("body");
        body.fieldNames().forEachRemaining(k->{if(!fields.contains(k))throw unknown(k);});
    }
}
