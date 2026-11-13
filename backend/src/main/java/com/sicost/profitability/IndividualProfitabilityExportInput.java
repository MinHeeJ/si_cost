package com.sicost.profitability;

import com.sicost.common.validation.ProjectInputPolicy;
import java.util.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public final class IndividualProfitabilityExportInput {
    private IndividualProfitabilityExportInput() {}
    public record Export(IndividualProfitabilityInput.Query query, List<String> order, List<String> hidden, String unit) {}
    public static Export parse(MultiValueMap<String, String> values) {
        var query = new LinkedMultiValueMap<String, String>(values);
        for (String key : List.of("order", "hidden", "unit")) {
            if (values.containsKey(key) && values.get(key).size() != 1) throw ProjectInputPolicy.invalid(key);
            query.remove(key);
        }
        // Export is unpaginated; detail parser rejects page/size rather than silently ignoring them.
        var filters = IndividualProfitabilityInput.query(query, true);
        String unit = values.getFirst("unit");
        if (values.containsKey("unit") && !Set.of("원", "천원", "백만원").contains(Objects.toString(unit, "")))
            throw ProjectInputPolicy.invalid("unit");
        return new Export(filters, csv(values, "order"), csv(values, "hidden"), unit);
    }
    private static List<String> csv(MultiValueMap<String, String> values, String key) {
        if (!values.containsKey(key)) return null;
        String text = values.getFirst(key);
        if (text == null) throw ProjectInputPolicy.invalid(key);
        var result = text.isEmpty() ? List.<String>of() : Arrays.asList(text.split(",", -1));
        if (new HashSet<>(result).size() != result.size() || result.stream().anyMatch(String::isBlank))
            throw ProjectInputPolicy.invalid(key);
        return List.copyOf(result);
    }
}
