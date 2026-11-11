package com.sicost.salespartsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import com.sicost.common.validation.MasterDataValidator;
import java.util.Map;
import java.util.Set;
import static com.sicost.salespartsops.PartStandardDtos.*;

public final class PartStandardInput {
    private PartStandardInput() {}
    public static void allow(JsonNode body) {
        if (body == null || !body.isObject()) throw InputValidator.invalid("body");
        Set<String> allowed=Set.of("effectiveMonth","leaderUserId","profitRate","salesExpenseAmount","expectedRevision");
        body.fieldNames().forEachRemaining(key->{if(!allowed.contains(key)) throw unknown(key);});
    }
    public static void query(Map<String,String> query, Set<String> allowed) {
        query.keySet().forEach(key->{if(!allowed.contains(key)) throw unknown(key);});
    }
    public static String text(JsonNode body,String field,int max) {
        JsonNode node=body.get(field);
        if(node==null || !node.isTextual()) throw InputValidator.invalid(field);
        return InputValidator.text(node.textValue(),max,field);
    }
    public static Save parse(JsonNode body) {
        String month=MasterDataValidator.month(text(body,"effectiveMonth",7),"effectiveMonth").toString();
        return new Save(month,text(body,"leaderUserId",30),
            MasterDataValidator.profitRate(text(body,"profitRate",100)),
            MasterDataValidator.salesExpenseAmount(text(body,"salesExpenseAmount",100)),text(body,"expectedRevision",200));
    }
    private static ApiException unknown(String field) {
        return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",field,null);
    }
}
