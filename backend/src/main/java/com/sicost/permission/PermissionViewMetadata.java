package com.sicost.permission;

import com.sicost.salespartsettlements.SalesPartSettlementDtos.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.web.context.request.*;

/** Request-local non-financial facts from the exact calculation, never a stored/cross-user cache. */
public final class PermissionViewMetadata {
    private static final String KEY=PermissionViewMetadata.class.getName();
    private PermissionViewMetadata() {}
    @SuppressWarnings("unchecked") private static Map<Object,Map<String,Object>> values() {
        if(!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes))return null;
        Object old=attributes.getRequest().getAttribute(KEY);
        if(old instanceof Map<?,?>)return (Map<Object,Map<String,Object>>)old;
        var result=new IdentityHashMap<Object,Map<String,Object>>();attributes.getRequest().setAttribute(KEY,result);return result;
    }
    public static void totals(Totals total,List<Row> rows) {
        var cache=values();if(cache==null)return;
        Map<String,Object> facts=new LinkedHashMap<>();facts.put("personCount",total.personCount());facts.put("deficitPersonCount",total.deficitPersonCount());
        if(rows.stream().allMatch(r->r.profitAmount()!=null && new BigDecimal(r.profitAmount()).signum()!=0))
            facts.put("surplusPersonCount",rows.stream().filter(r->new BigDecimal(r.profitAmount()).signum()>0).count());
        cache.put(total,facts);
    }
    public static void month(Object target,Result result) {
        var cache=values();if(cache==null)return;
        var facts=new LinkedHashMap<>(get(result.totals()));facts.put("month",result.month());facts.put("status",result.status());facts.put("mm",result.totals().mm());cache.put(target,facts);
    }
    public static Map<String,Object> get(Object value) {var cache=values();return cache==null?Map.of():cache.getOrDefault(value,Map.of());}
}
