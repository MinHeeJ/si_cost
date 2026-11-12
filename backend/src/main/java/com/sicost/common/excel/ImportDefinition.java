package com.sicost.common.excel;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ImportDefinition(List<String> headers, Set<String> required, Set<String> identifiers,
                               Set<String> dates, Map<String,String> aliases, Set<String> exactNumbers) {
    public ImportDefinition(List<String> headers, Set<String> required, Set<String> identifiers,
                            Set<String> dates, Map<String,String> aliases) {
        this(headers,required,identifiers,dates,aliases,Set.of());
    }
    public String header(String value) {
        String normalized = value.replaceAll("[\\s\\p{Z}]", "").replaceFirst("\\*+$", "");
        String alias = aliases.get(normalized);
        if (alias != null) return alias;
        return headers.stream().filter(title -> title.replaceAll("[\\s\\p{Z}]", "").equals(normalized))
            .findFirst().orElse(normalized);
    }
}
