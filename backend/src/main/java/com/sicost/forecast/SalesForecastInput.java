package com.sicost.forecast;

import com.sicost.common.api.ApiException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.MultiValueMap;

public final class SalesForecastInput {
    private SalesForecastInput() {}
    public static String validate(MultiValueMap<String,String> query,boolean export,String body) {
        if (body!=null && !body.isBlank()) throw invalid("body","GET 요청에는 본문을 지정할 수 없습니다.","UNKNOWN_FIELD");
        Set<String> allowed=export?Set.of("amountUnit"):Set.of();
        for (var entry:query.entrySet()) {
            if(!allowed.contains(entry.getKey()))throw invalid(entry.getKey(),"허용되지 않은 조회 항목입니다.","UNKNOWN_FIELD");
            if(entry.getValue().size()!=1)throw invalid(entry.getKey(),"조회 항목은 한 번만 지정해 주세요.","VALIDATION_ERROR");
        }
        String unit=query.getFirst("amountUnit");
        if(unit==null)return "THOUSAND";
        if(!Set.of("WON","THOUSAND","MILLION").contains(unit))throw invalid("amountUnit","금액 단위를 확인해 주세요.","VALIDATION_ERROR");
        return unit;
    }
    private static ApiException invalid(String field,String reason,String code) {
        return new ApiException(400,code,reason,field,Map.of("violations",List.of(Map.of("field",field,"message",reason))));
    }
}
