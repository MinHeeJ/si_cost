package com.sicost.clientsops;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sicost.common.api.ApiError;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ClientActionsDtos {
    private ClientActionsDtos() {}
    public record Option(String id, String name, String type) {}
    public record Options(List<Option> items) {}
    public record SelectionItem(String id, String revision) {}
    public record Selection(List<SelectionItem> items, long total) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Account(String id, String bankCode, String bankName, String accountNumber, String depositor) {
        @Override public String toString() { return "Account[id=" + id + "]"; }
    }
    public record Assignment(String assignmentId, String projectId, String projectName, String managementNo,
            String developerName, String jobCode, String jobName, LocalDate inputBeginDate, LocalDate inputEndDate) {}
    public record Assignments(List<Assignment> items, long total, int page, int size, LocalDate basisDate) {}
    public record Target(String id, String expectedRevision) {}
    public record BulkRequest(String targetUseYn, List<Target> items) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BulkItem(String id, String name, String result, ApiError error) {}
    public record BulkResult(List<BulkItem> items, long applied, long noChange, long rejected) {}
    public record Reference(String type, long count) {}
    public record Filters(String type, String useYn, String q, String sort, String direction, LocalDate basisDate) {}
    public record AssignmentQuery(String id, String type, LocalDate basisDate, int page, int size, long offset) {}
    public record StatusChange(String id, String useYn, String actor, LocalDateTime now) {}
    public record OptionQuery(String purpose, String q) {}
}
