package com.sicost.apiops;

import com.sicost.common.api.ApiException;
import java.util.Set;
import org.springframework.util.MultiValueMap;

public final class ActivityQueryInput {
    private ActivityQueryInput() {}
    public record Query(int page,int size,String jobCode,long offset) {}
    public static Query parse(MultiValueMap<String,String> values,boolean automation) {
        allow(values,automation?Set.of("page","size","sort","jobCode"):Set.of("page","size","sort"));
        int page=integer(values.getFirst("page"),1,"page"),size=integer(values.getFirst("size"),automation?50:20,"size");
        if(page<1)throw invalid("page");
        if(!Set.of(20,50,100).contains(size))throw invalid("size");
        String sort=values.getFirst("sort"),expected=automation?"runDate,desc;jobCode,asc":"notifiedAt,desc;id,desc";
        if(sort!=null&&!expected.equals(sort))throw invalid("sort");
        String job=values.getFirst("jobCode");
        if(job!=null&&!Set.of("DAILY_OPERATIONS","RECEIVABLE_DUNNING").contains(job))throw invalid("jobCode");
        return new Query(page,size,job,((long)page-1)*size);
    }
    public static void allow(MultiValueMap<String,String> values,Set<String> allowed) {
        values.forEach((key,entries)->{
            if(!allowed.contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 조회 항목입니다.",key,null);
            if(entries.size()!=1)throw invalid(key);
        });
    }
    private static int integer(String value,int fallback,String field) {
        if(value==null)return fallback;
        try {if(!value.matches("[0-9]{1,10}"))throw invalid(field);return Integer.parseInt(value);}
        catch(NumberFormatException ex){throw invalid(field);}
    }
    public static ApiException invalid(String field) {return new ApiException(400,"VALIDATION_ERROR","조회 조건을 확인해 주세요.",field,null);}
}
