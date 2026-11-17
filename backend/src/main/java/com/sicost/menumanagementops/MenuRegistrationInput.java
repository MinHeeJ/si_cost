package com.sicost.menumanagementops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.util.Map;
import java.util.Set;
import static com.sicost.menumanagementops.MenuRegistrationDtos.*;

public final class MenuRegistrationInput {
    private MenuRegistrationInput() {}
    public static void query(Map<String,String> query) {
        query.keySet().forEach(key -> { throw unknown(key); });
    }
    public static Create create(JsonNode body) {
        if (body == null || !body.isObject()) throw InputValidator.invalid("body");
        JsonNode level = body.get("level");
        if (level == null || !level.isIntegralNumber() || !level.canConvertToInt()
                || level.intValue() < 1 || level.intValue() > 2) throw InputValidator.invalid("level");
        boolean root = level.intValue() == 1;
        Set<String> allowed = root ? Set.of("level","name","expectedRevision")
            : Set.of("level","name","parentId","screenId","pcOnly","expectedRevision");
        body.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) throw unknown(key); });
        boolean pc = false;
        if (body.has("pcOnly")) {
            if (!body.get("pcOnly").isBoolean()) throw InputValidator.invalid("pcOnly");
            pc = body.get("pcOnly").booleanValue();
        }
        return new Create(level.intValue(), text(body,"name",50), root ? null : text(body,"parentId",20),
            root ? null : text(body,"screenId",10), pc, text(body,"expectedRevision",200));
    }
    private static String text(JsonNode body, String key, int max) {
        JsonNode value = body.get(key);
        if (value == null || !value.isTextual()) throw InputValidator.invalid(key);
        return InputValidator.text(value.textValue(),max,key);
    }
    private static ApiException unknown(String key) {
        return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);
    }
}
