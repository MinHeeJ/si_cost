package com.sicost.b9;

import com.sicost.b7.B7Dtos.Issue;
import com.sicost.common.projects.ProjectRegistrationService.Project;
import java.time.LocalDateTime;
import java.util.List;

public final class B9Dtos {
    private B9Dtos() {}
    public record Summary(int totalRows, int successRows, int warningRows, int errorRows,
                          int fileWarningCount, int tableWarningCount) {}
    public record Candidate(String id, String name, String businessNo) {}
    public record Problem(int sequence, String severity, int rowNumber, String columnName,
                          String inputValue, String reason, List<Candidate> candidates) {}
    public record IssuesPage(List<Problem> items, long total, int page, int size) {}
    public record Validation(long uploadId, Summary summary, IssuesPage issues, boolean canApply,
                             String revision, Object prospectiveComparison, String uploadedTableTotal) {}
    public record ResultRow(int rowNumber, String id, String name) {}
    public record Result(long uploadId, String formType, String status, String projectId, String step,
                         Summary summary, List<ResultRow> results, String revision, boolean changed) {}
    public record Checked(List<ProjectRow> projects, Summary summary, List<Problem> issues) {}
    public record ProjectRow(int rowNumber, String explicitId, Project project) {}
    public record Part(String id, String name, boolean headquarters) {}
    public record Defaults(String cashBaseMonth, String marginRate, String teamExpense, String projectExpense) {}
    public record Upload(long uploadId, String formType, String screenId, String filename, long size,
                         String actor, String status, int totalRows, int successRows, int warningRows,
                         int errorRows, String projectId, String step, LocalDateTime appliedAt) {}
    public record Context(int year, String contentSha256) {}
    public record Receipt(String value, String reason) {}
    public record UploadWrite(long uploadId, String filename, long size, Summary summary, String actor, LocalDateTime now) {}
    public record IssueWrite(long uploadId, Issue issue, String actor, LocalDateTime now) {}
    public static final class UploadDraft {
        private Long uploadId;
        private final UploadWrite write;
        public UploadDraft(UploadWrite write) { this.write = write; }
        public Long getUploadId() { return uploadId; }
        public void setUploadId(Long uploadId) { this.uploadId = uploadId; }
        public UploadWrite getWrite() { return write; }
    }
}
