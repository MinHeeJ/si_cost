package com.sicost.apiops;

import com.sicost.common.api.ApiException;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Map;
import java.util.Set;
import static com.sicost.apiops.BillingReviewDtos.*;

public final class BillingReviewInput {
    private BillingReviewInput() {}
    public static PageQuery checks(Map<String,String> query) { return parse(query,Set.of("month","page","size")); }
    public static PageQuery receivables(Map<String,String> query) { return parse(query,Set.of("page","size")); }
    private static PageQuery parse(Map<String,String> query,Set<String> allowed) {
        query.keySet().forEach(key->{if(!allowed.contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 조회 항목입니다.",key,null);});
        String month=query.get("month");
        if(month!=null) {
            if(!month.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))throw invalid("month");
            month=ProjectInputPolicy.month(month,"month").toString();
        }
        int page=integer(query,"page",1),size=integer(query,"size",50);
        if(!Set.of(20,50,100).contains(size))throw invalid("size");
        return new PageQuery(month,page,size);
    }
    private static int integer(Map<String,String> query,String key,int fallback) {
        if(!query.containsKey(key))return fallback;
        try {
            String value=query.get(key);
            if(value==null||!value.matches("[0-9]{1,10}"))throw invalid(key);
            int number=Integer.parseInt(value);if(number<1)throw invalid(key);return number;
        } catch(NumberFormatException e) {throw invalid(key);}
    }
    public static ApiException invalid(String field) {return new ApiException(400,"VALIDATION_ERROR","조회 조건의 형식과 범위를 확인해 주세요.",field,null);}
}
