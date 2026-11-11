package com.sicost.salesparts;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import com.sicost.common.validation.MasterDataValidator;
import java.util.Map;
import java.util.Set;
import static com.sicost.salesparts.SalesPartDtos.*;

public final class SalesPartInput {
    private SalesPartInput() {}
    public static final Set<String> SORTS = Set.of("name", "leaderName", "profitRate", "salesExpenseAmount",
            "effectiveMonth", "personCount", "projectCount", "useYn");
    public static void query(Map<String,String> query, Set<String> allowed) {
        query.keySet().forEach(key -> { if (!allowed.contains(key)) throw unknown(key); });
    }
    private static ApiException unknown(String key) {
        return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);
    }
    private static void allow(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw InputValidator.invalid("body");
        node.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) throw unknown(key); });
    }
    private static String text(JsonNode node, String key, int max) {
        if (!node.hasNonNull(key) || !node.get(key).isTextual()) throw InputValidator.invalid(key);
        return InputValidator.text(node.get(key).textValue(),max,key);
    }
    public static Create create(JsonNode body) {
        allow(body,Set.of("name","effectiveMonth","leaderUserId","profitRate","salesExpenseAmount"));
        String month = MasterDataValidator.month(text(body,"effectiveMonth",7),"effectiveMonth").toString();
        return new Create(text(body,"name",30),month,text(body,"leaderUserId",30),
                MasterDataValidator.profitRate(text(body,"profitRate",100)),
                MasterDataValidator.salesExpenseAmount(text(body,"salesExpenseAmount",100)));
    }
    public static Patch patch(JsonNode body) {
        allow(body,Set.of("name","useYn","expectedRevision"));
        if (body.has("name") == body.has("useYn"))
            throw new ApiException(400,"VALIDATION_ERROR","파트명 또는 사용 여부 중 하나만 입력해 주세요.","name",null);
        return new Patch(body.has("name") ? text(body,"name",30) : null,
                body.has("useYn") ? InputValidator.yesNo(text(body,"useYn",1),"useYn") : null,
                text(body,"expectedRevision",200));
    }
    private static int integer(Map<String,String> query, String key, int fallback) {
        if (!query.containsKey(key)) return fallback;
        try { if (!query.get(key).matches("[0-9]+")) throw InputValidator.invalid(key); return Integer.parseInt(query.get(key)); }
        catch (NumberFormatException e) { throw InputValidator.invalid(key); }
    }
    public static Query list(Map<String,String> query) {
        query(query,Set.of("q","useYn","page","size","sort","direction"));
        return new Query(MasterDataValidator.search(query.get("q")),
                query.containsKey("useYn") ? InputValidator.yesNo(query.get("useYn"),"useYn") : null,
                InputValidator.page(integer(query,"page",1)),InputValidator.size(integer(query,"size",50)),
                MasterDataValidator.enumValue(query.getOrDefault("sort","name"),SORTS,"sort"),
                MasterDataValidator.enumValue(query.getOrDefault("direction","ASC"),Set.of("ASC","DESC"),"direction"));
    }
}
