package com.sicost.cashflowops;

import com.sicost.common.api.ApiException;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import static com.sicost.cashflowops.CashFlowDtos.*;

public final class CashFlowInput {
    private CashFlowInput() {}
    public static Query export(MultiValueMap<String,String> values) {
        allowed(values,Set.of("year","unit"));
        String year=values.getFirst("year"),unit=values.getFirst("unit");
        Integer y=year==null?null:integer(year,0,"year");
        if(y!=null&&y>9999)throw invalid("year");
        try{return new Query(y,unit==null?Unit.THOUSAND_WON:Unit.valueOf(unit));}
        catch(IllegalArgumentException e){throw invalid("unit");}
    }
    public static SourceQuery sources(MultiValueMap<String,String> values) {
        allowed(values,Set.of("month","item","page","size"));
        String month=values.getFirst("month");
        if(month==null||!month.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))throw invalid("month");
        Item item;
        try{item=Item.valueOf(values.getFirst("item"));}catch(Exception e){throw invalid("item");}
        int page=integer(values.getFirst("page"),1,"page"),size=integer(values.getFirst("size"),50,"size");
        if(!Set.of(20,50,100).contains(size))throw invalid("size");
        return new SourceQuery(month,item,page,size);
    }
    private static void allowed(MultiValueMap<String,String> values,Set<String> allowed) {
        values.forEach((key,v)->{
            if(!allowed.contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);
            if(v.size()!=1)throw invalid(key);
        });
    }
    private static int integer(String value,int fallback,String field) {
        if(value==null)return fallback;
        if(!value.matches("[1-9][0-9]{0,9}"))throw invalid(field);
        try{return Integer.parseInt(value);}catch(NumberFormatException e){throw invalid(field);}
    }
    public static ApiException invalid(String field){return new ApiException(400,"VALIDATION_ERROR","입력 형식과 허용값을 확인해 주세요.",field,null);}
}
