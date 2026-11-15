package com.sicost.apiops;

import com.sicost.common.api.ApiException;
import com.sicost.monthclosesops.MonthCloseInput;
import java.util.Set;
import org.springframework.util.MultiValueMap;

public final class MonthCloseReadInput {
    private MonthCloseReadInput() {}
    public static MonthCloseReadDtos.Query parse(MultiValueMap<String,String> values) {
        values.forEach((key,v)->{
            if(!Set.of("month","page","size").contains(key))throw new ApiException(400,"UNKNOWN_FIELD","등록되지 않은 입력 항목입니다.",key,null);
            if(v.size()!=1)throw invalid(key);
        });
        String month=values.getFirst("month");if(month!=null)MonthCloseInput.month(month);
        int page=integer(values.getFirst("page"),1,"page"),size=integer(values.getFirst("size"),50,"size");
        if(!Set.of(20,50,100).contains(size))throw invalid("size");
        return new MonthCloseReadDtos.Query(month,page,size);
    }
    private static int integer(String text,int fallback,String field) {
        if(text==null)return fallback;
        if(!text.matches("[1-9][0-9]{0,9}"))throw invalid(field);
        try{return Integer.parseInt(text);}catch(NumberFormatException e){throw invalid(field);}
    }
    private static ApiException invalid(String field){return new ApiException(400,"VALIDATION_ERROR","입력 형식과 허용값을 확인해 주세요.",field,null);}
}
