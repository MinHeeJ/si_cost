package com.sicost.apiops;

import com.sicost.common.api.ApiException;
import java.util.*;
import org.springframework.util.MultiValueMap;

public final class StaffingQueryInput {
    private StaffingQueryInput() {}
    public record Paging(int page, int size, String sort, String direction) {}
    public static void validate(MultiValueMap<String,String> query, Set<String> allowed, String body) {
        if(body!=null&&!body.isBlank())throw invalid("body","UNKNOWN_FIELD");
        query.forEach((k,v)->{if(!allowed.contains(k))throw invalid(k,"UNKNOWN_FIELD");if(v.size()!=1||v.get(0)==null||v.get(0).isBlank())throw invalid(k,"VALIDATION_ERROR");});
    }
    public static Paging paging(MultiValueMap<String,String> q, Set<String> sorts, String defaultSort) {
        int page=integer(q.getFirst("page"),1,"page"),size=integer(q.getFirst("size"),50,"size");
        if(!Set.of(20,50,100).contains(size))throw invalid("size","VALIDATION_ERROR");
        String sort=choice(q,"sort",sorts,defaultSort),direction=choice(q,"direction",Set.of("ASC","DESC"),"ASC");
        return new Paging(page,size,sort,direction);
    }
    public static int integer(String v,int fallback,String field) {
        if(v==null)return fallback;
        try{if(!v.matches("[1-9][0-9]{0,9}"))throw invalid(field,"VALIDATION_ERROR");return Integer.parseInt(v);}catch(NumberFormatException e){throw invalid(field,"VALIDATION_ERROR");}
    }
    public static String choice(MultiValueMap<String,String> q,String key,Set<String> allowed,String fallback) {
        String value=q.getFirst(key);if(value!=null&&!allowed.contains(value))throw invalid(key,"VALIDATION_ERROR");return value==null?fallback:value;
    }
    public static String text(MultiValueMap<String,String> q,String key,int max) {
        String v=q.getFirst(key);if(v!=null&&(v.length()>max||!v.equals(v.strip())||v.chars().anyMatch(Character::isISOControl)))throw invalid(key,"VALIDATION_ERROR");return v;
    }
    public static ApiException invalid(String field,String code) {return new ApiException(400,code,"입력 항목의 형식과 범위를 확인해 주세요.",field,Map.of("violations",List.of(Map.of("field",field,"message","입력 항목을 확인해 주세요."))));}
    public static <T> List<T> page(List<T> rows,Paging p){return rows.stream().skip((long)(p.page()-1)*p.size()).limit(p.size()).toList();}
}
