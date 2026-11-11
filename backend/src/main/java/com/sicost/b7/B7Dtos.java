package com.sicost.b7;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class B7Dtos {
    private B7Dtos() {}
    public record Summary(int totalRows, int successRows, int warningRows, int errorRows, int fileWarningCount) {}
    public record Issue(int sequence, String severity, int rowNumber, String columnName, String inputValue, String reason) {}
    public record IssuesPage(List<Issue> items, long total, int page, int size) {}
    public record Validation(long uploadId, String status, Summary summary, boolean canApply, IssuesPage issues) {}
    public record Applied(long uploadId, String status, Summary summary, int appliedCount, String appliedAt) {}
    public record Upload(long uploadId, String formType, String screenId, String filename, long size, String actor,
                         String status, int totalRows, int successRows, int warningRows, int errorRows, LocalDateTime appliedAt) {}
    public record Code(String groupId, String code, String name, boolean active, String source) {}
    public record Existing(String businessNo, String name) {}
    public record CellValue(String text, String error) {}
    public record SourceRow(int number, Map<String, CellValue> cells) {}
    public record Parsed(String filename, long size, Map<String,Integer> columns, List<SourceRow> rows, List<Issue> fileIssues) {}
    public record Client(String type, String name, String businessNo, String ceo, String industryCode, String contactName,
                         String phone, String taxEmail, String bankCode, String accountNumber, String depositor) {}
    public record Checked(List<Client> clients, Summary summary, List<Issue> issues) {}
    public record UploadWrite(long uploadId, String filename, long size, Summary summary, String actor, LocalDateTime now) {}
    public record IssueWrite(long uploadId, Issue issue, String actor, LocalDateTime now) {}
    public record ClientWrite(String id, Client client, String encryptedAccount, String actor, LocalDateTime now) {}
}
