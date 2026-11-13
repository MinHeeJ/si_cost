package com.sicost.corporatetaxesops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.sicost.corporatetaxesops.CorporateTaxDtos.*;

public final class CorporateTaxInput {
    private CorporateTaxInput() {}
    public static ApiException invalid(String field){return new ApiException(400,"VALIDATION_ERROR","입력 항목의 형식과 필수 값을 확인해 주세요.",field,null);}
    public static String year(String year){if(year==null||!year.matches("(?!0000)[0-9]{4}"))throw invalid("year");return year;}
    public static String payType(String type){if(!Set.of("MID","CNFM").contains(type))throw invalid("payType");return type;}
    public static void query(Map<String,String> query){query.keySet().forEach(k->{throw unknown(k);});}
    public static YearMonth month(String value){try{if(!value.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))throw invalid("month");return YearMonth.parse(value);}catch(DateTimeException e){throw invalid("month");}}
    public static YearMonth initialize(JsonNode body){allow(body,Set.of("month"));return month(text(body,"month"));}
    public static Confirmation confirmation(JsonNode body){
        allow(body,Set.of("month","expectedRevision","paidDate","payAmount"));
        YearMonth month=month(text(body,"month"));String revision=text(body,"expectedRevision");
        if(revision.isBlank()||revision.length()>200)throw invalid("expectedRevision");
        String date=text(body,"paidDate");LocalDate paid;
        try{if(!date.matches("(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw invalid("paidDate");paid=LocalDate.parse(date);}catch(DateTimeException e){throw invalid("paidDate");}
        String amount=text(body,"payAmount").replaceAll("[\\s\\p{Z},]","").replaceFirst("원$","");
        if(!amount.matches("[0-9]{1,15}"))throw invalid("payAmount");
        return new Confirmation(month,revision,paid,new BigDecimal(amount));
    }
    private static String text(JsonNode body,String field){JsonNode value=body.get(field);if(value==null||!value.isTextual())throw invalid(field);return value.textValue();}
    private static void allow(JsonNode body,Set<String> allowed){if(body==null||!body.isObject())throw invalid("body");body.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw unknown(k);});}
    private static ApiException unknown(String key){return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);}
}
