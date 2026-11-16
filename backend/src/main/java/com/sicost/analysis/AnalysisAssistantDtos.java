package com.sicost.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.*;
import java.util.*;

public final class AnalysisAssistantDtos {
    private AnalysisAssistantDtos() {}
    public record Context(String screenId,String baseMonth,String projectId,String developerId) {}
    public record Question(String question,Context context) {}
    public record Target(String targetType,String targetId,String displayName,List<String> metricKeys) {}
    public record Filter(String key,String operator,List<String> values) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Scope(List<Target> targets,List<Filter> filters,String fromMonth,String toMonth,LocalDate baseDate,String baseMonth) {}
    public record Evidence(String queryName,Scope scope,String sourceState,String calculationBasis,List<String> referenceIds) {}
    public record Column(String key,String label,String valueType,String unit) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Cell(String columnKey,String value,String reason) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Row(String rowKey,Target target,List<Cell> cells,String reason) {}
    public record Sort(String columnKey,String direction) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Table(String tableId,String title,String periodLabel,String metricName,String unit,String sourceState,String scenario,
        List<Column> columns,List<Row> rows,int totalRows,List<Cell> totals,List<Sort> sort,int displayPageSize,String emptyReason) {}
    public record ScreenLink(String screenId,Scope scope,String guidance) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Interpreter(String mode,String modelName,String notice) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Result(String schemaVersion,String conclusion,List<Table> tables,List<String> assumptions,List<String> unknownReasons,
        List<Evidence> usedData,List<String> nextActions,Context context,Scope appliedScope,String status,String reason,
        List<ScreenLink> screenLinks,Interpreter interpreter,OffsetDateTime dataAsOf) {}
    public record Summary(String qnaId,String question,OffsetDateTime questionAt,String status,boolean provisional) {}
    public record Detail(String qnaId,String question,OffsetDateTime questionAt,String status,boolean provisional,Result result,
        String useDataDescription,String baseMonth,String snapshotNotice) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Page(List<Summary> items,String nextCursor,boolean hasMore) {}
    public record Progress(String phase,String message,OffsetDateTime acceptedAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Final(boolean persisted,String qnaId,String status,Result result,String serverErrorId,String message) {}
    public record Stored(String qnaId,String userId,String question,String answer,String description,String baseMonth,String provisional,
        String status,LocalDateTime questionAt) {}
    public record Insert(String qnaId,String userId,String question,String answer,String description,String baseMonth,String provisional,
        String status,LocalDateTime questionAt,LocalDateTime registeredAt) {}
}
