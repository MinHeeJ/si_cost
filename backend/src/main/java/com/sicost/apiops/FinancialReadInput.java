package com.sicost.apiops;

import com.sicost.common.api.ApiException;
import java.time.YearMonth;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import static com.sicost.apiops.FinancialReadDtos.*;

public final class FinancialReadInput {
    private FinancialReadInput() {}
    public static void query(MultiValueMap<String,String> query,boolean history) {
        Set<String> allowed=history?Set.of("month","scope","page","size"):Set.of("month");
        query.forEach((key,values)->{
            if(!allowed.contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);
            if(values.size()!=1)throw invalid(key);
        });
    }
    public static Period period(String value) {
        if(value==null || !value.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))throw invalid("month");
        YearMonth month=YearMonth.parse(value);
        return new Period(value,value.replace("-",""),value.substring(0,4),String.format("%04d",month.getYear()-1),month.atDay(1),month.atEndOfMonth());
    }
    public static HistoryQuery history(MultiValueMap<String,String> query) {
        query(query,true);Period period=period(query.getFirst("month"));String scope=query.getFirst("scope");
        if(scope==null || !Set.of("MONTH","TAX","CARDS").contains(scope))throw invalid("scope");
        int page=integer(query.getFirst("page"),1,"page"),size=integer(query.getFirst("size"),50,"size");
        if(!Set.of(20,50,100).contains(size))throw invalid("size");
        return new HistoryQuery(period,scope,page,size,((long)page-1)*size);
    }
    private static int integer(String value,int fallback,String field) {
        if(value==null)return fallback;
        if(!value.matches("[1-9][0-9]{0,9}"))throw invalid(field);
        try{return Integer.parseInt(value);}catch(NumberFormatException e){throw invalid(field);}
    }
    public static ApiException invalid(String field) {
        return new ApiException(400,"VALIDATION_ERROR","입력 항목의 형식과 범위를 확인해 주세요.",field,null);
    }
}
