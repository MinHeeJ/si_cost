package com.sicost.menumanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.util.*;
import static com.sicost.menumanagement.MenuManagementDtos.*;

public final class MenuManagementInput {
    private MenuManagementInput() {}
    public static void query(Map<String,String> query) {
        query.keySet().forEach(key -> { throw unknown(key); });
    }
    public static PutTree tree(JsonNode body) {
        allow(body, Set.of("items", "expectedRevision"), "body");
        String revision = text(body, "expectedRevision", Integer.MAX_VALUE, "expectedRevision");
        if (!body.has("items") || !body.get("items").isArray()) throw InputValidator.invalid("items");
        List<MenuInput> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode node : body.get("items")) {
            String prefix = "items." + items.size() + ".";
            int level = integer(node, "level", 1, 2, prefix);
            allow(node, level == 1 ? Set.of("menuId", "name", "level", "parentId", "screenId", "sortOrder")
                : Set.of("menuId", "name", "level", "parentId", "screenId", "sortOrder", "use", "pcOnly"), prefix);
            String id = text(node, "menuId", 20, prefix + "menuId");
            if (!ids.add(id)) throw InputValidator.invalid(prefix + "menuId");
            String parent = null, screen = null;
            if (level == 1) {
                if (!node.has("parentId") || !node.get("parentId").isNull()) throw InputValidator.invalid(prefix + "parentId");
                if (!node.has("screenId") || !node.get("screenId").isNull()) throw InputValidator.invalid(prefix + "screenId");
            } else {
                parent = text(node, "parentId", 20, prefix + "parentId");
                screen = text(node, "screenId", 10, prefix + "screenId");
            }
            items.add(new MenuInput(id, text(node, "name", 50, prefix + "name"), level, parent, screen,
                integer(node, "sortOrder", 1, 99999, prefix), level == 2 ? bool(node, "use", prefix) : null,
                level == 2 ? bool(node, "pcOnly", prefix) : null));
        }
        return new PutTree(List.copyOf(items), revision);
    }
    private static boolean bool(JsonNode node, String key, String prefix) {
        if (!node.has(key) || !node.get(key).isBoolean()) throw InputValidator.invalid(prefix + key);
        return node.get(key).booleanValue();
    }
    private static int integer(JsonNode node, String key, int min, int max, String prefix) {
        JsonNode value = node == null ? null : node.get(key);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < min || value.intValue() > max)
            throw InputValidator.invalid(prefix + key);
        return value.intValue();
    }
    private static String text(JsonNode node, String key, int max, String field) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual()) throw InputValidator.invalid(field);
        return InputValidator.text(value.textValue(), max, field);
    }
    private static void allow(JsonNode node, Set<String> allowed, String prefix) {
        if (node == null || !node.isObject()) throw InputValidator.invalid(prefix);
        node.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) throw unknown(prefix.equals("body") ? key : prefix + key); });
    }
    private static ApiException unknown(String field) {
        return new ApiException(400, "UNKNOWN_FIELD", "허용되지 않은 입력 항목입니다.", field, null);
    }
}
