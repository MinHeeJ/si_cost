package com.sicost.clientsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import com.sicost.common.validation.MasterDataValidator;
import java.time.LocalDate;
import java.util.*;
import org.springframework.util.MultiValueMap;
import static com.sicost.clientsops.ClientActionsDtos.*;

public final class ClientActionsInput {
    private ClientActionsInput() {}
    public static final Set<String> SORTS = Set.of("type", "name", "businessNo", "ceo", "industry", "contactName",
            "phone", "taxEmail", "projectCount", "personCount", "useYn");
    public static void query(MultiValueMap<String,String> query, Set<String> allowed) {
        query.forEach((key, values) -> {
            if (!allowed.contains(key)) throw unknown(key);
            if (values.size() != 1) throw InputValidator.invalid(key);
        });
    }
    public static Filters filters(MultiValueMap<String,String> query, LocalDate date) {
        query(query, Set.of("type", "useYn", "q", "sort", "direction"));
        String type=query.getFirst("type"), use=query.getFirst("useYn");
        if (type!=null) type=MasterDataValidator.enumValue(type,Set.of("CUST","VNDR","BOTH"),"type");
        if (use!=null) use=InputValidator.yesNo(use,"useYn");
        String sort=query.getFirst("sort"), direction=query.getFirst("direction");
        if (sort==null) sort="name";
        else if (!SORTS.contains(sort)) throw InputValidator.invalid("sort");
        if (direction==null) direction="ASC";
        else direction=MasterDataValidator.enumValue(direction,Set.of("ASC","DESC"),"direction");
        return new Filters(type,use,MasterDataValidator.search(query.getFirst("q")),sort,direction,date);
    }
    public static String id(String value) { return InputValidator.text(value,30,"id"); }
    public static int page(String value) { return number(value,1,"page"); }
    public static int size(String value) { return InputValidator.size(number(value,50,"size")); }
    private static int number(String value,int fallback,String field) {
        if (value==null) return fallback;
        try {
            if (!value.matches("[0-9]+")) throw InputValidator.invalid(field);
            int number=Integer.parseInt(value);
            if (number<1) throw InputValidator.invalid(field);
            return number;
        } catch (NumberFormatException e) { throw InputValidator.invalid(field); }
    }
    public static BulkRequest bulk(JsonNode body) {
        allow(body,Set.of("targetUseYn","items"),"");
        String target=InputValidator.yesNo(text(body,"targetUseYn",1,""),"targetUseYn");
        JsonNode items=body.get("items");
        if (items==null || !items.isArray() || items.isEmpty()) throw InputValidator.invalid("items");
        Set<String> ids=new HashSet<>(); List<Target> targets=new ArrayList<>();
        for (int i=0;i<items.size();i++) {
            String prefix="items["+i+"]."; JsonNode item=items.get(i);
            allow(item,Set.of("id","expectedRevision"),prefix);
            String id=text(item,"id",20,prefix);
            if (!id.matches("C-[0-9]{3,18}") || !ids.add(id)) throw InputValidator.invalid(prefix+"id");
            targets.add(new Target(id,text(item,"expectedRevision",200,prefix)));
        }
        return new BulkRequest(target,List.copyOf(targets));
    }
    private static void allow(JsonNode node,Set<String> allowed,String prefix) {
        if (node==null || !node.isObject()) throw InputValidator.invalid(prefix.isEmpty()?"items":prefix);
        node.fieldNames().forEachRemaining(key -> {if (!allowed.contains(key)) throw unknown(prefix+key);});
    }
    private static String text(JsonNode node,String key,int max,String prefix) {
        if (!node.hasNonNull(key) || !node.get(key).isTextual()) throw InputValidator.invalid(prefix+key);
        return InputValidator.text(node.get(key).textValue(),max,prefix+key);
    }
    private static ApiException unknown(String field) {
        return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",field,null);
    }
}
