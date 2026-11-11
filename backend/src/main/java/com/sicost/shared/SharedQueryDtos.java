package com.sicost.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class SharedQueryDtos {
    private SharedQueryDtos() {}
    public record Items<T>(List<T> items) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CodeLookup(String code, String name, String addVal1, String addVal2,
                             String addVal3, String codeVal, String unitNm) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Parameter(String key, String value, String unitNm, String effectiveMonth) {}
    public record CloseRow(String month, String status) {}
    public record MonthClose(String month, String status) {}
    public record PeriodContext(LocalDate currentDate, String currentMonth, String timezone,
                                String cashBaseMonth, MonthClose monthClose) {}
    public record MenuRow(String menuId, String parentId, String name, String screenId,
                          int level, int sortOrder, String useYn, String icon, boolean pcOnly) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MenuNode(String menuId, String parentId, String name, String screenId,
                           int sortOrder, String icon, boolean pcOnly, List<MenuNode> children) {}
    public record MenuDiagnostic(String menuId, String message) {}
    public record MenusData(List<MenuNode> items, List<MenuDiagnostic> diagnostics) {}
    public record HistoryQuery(String targetType, String groupId, int size, long offset) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record History(long histId, OffsetDateTime occurredAt, String target, String groupId,
                          String entityKey, String type, String item, String before, String after, String actor) {}
    public record HistoryPage(List<History> items, long total, int page, int size) {}
}
