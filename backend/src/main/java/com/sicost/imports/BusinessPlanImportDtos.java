package com.sicost.imports;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.B7Dtos.IssuesPage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class BusinessPlanImportDtos {
    private BusinessPlanImportDtos() {}
    public record Request(int year, String uploadId, String expectedRevision, String mode) {}
    public record Summary(int totalRows, int successRows, int warningRows, int errorRows, int fileWarningCount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Target(String salesTargetAmt, String operatingProfitTargetAmt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Change(String month, int rowNumber, boolean closed, boolean changed, Target before, Target after) {}
    public record Validation(String uploadId, String status, Summary summary, IssuesPage issues,
        String expectedRevision, boolean canApply, List<String> warnings, int year, List<Change> changes,
        boolean requiresClosedMonthConfirmation, List<String> blankTargetMonths) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Applied(String uploadId, String status, Summary summary, boolean alreadyApplied, String message,
        String appliedAt, List<String> changedMonths, List<String> unchangedMonths, List<String> keptMonths,
        List<String> warnings, int year, String mode) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Result(String uploadId, String formType, String screenId, Integer year, String targetMonth,
        String status, Summary summary, String appliedAt, List<String> changedMonths, IssuesPage issues) {}
    public record Stored(String month, BigDecimal sales, BigDecimal profit, Long uploadId,
        String regUserId, LocalDateTime regDttm, String modUserId, LocalDateTime modDttm) {}
    public record Close(String month, String status) {}
    public record Row(int rowNumber, String month, BigDecimal sales, BigDecimal profit) {}
    public record Checked(List<Row> rows, Summary summary, List<Issue> issues, List<String> warnings,
        List<String> blankTargetMonths, List<Change> changes) {
        public boolean canApply() { return summary.errorRows() == 0; }
    }
    public record Upload(long uploadId, String formType, String screenId, String filename, long size,
        String month, String projectId, String step, String actor, String status, int totalRows,
        int successRows, int warningRows, int errorRows, LocalDateTime appliedAt) {}
    public record UploadWrite(long uploadId, String filename, long size, String month, Summary summary,
        String actor, LocalDateTime now) {}
    public static final class Draft {
        private Long uploadId;
        private final UploadWrite write;
        public Draft(UploadWrite write) { this.write = write; }
        public Long getUploadId() { return uploadId; }
        public void setUploadId(Long value) { uploadId = value; }
        public UploadWrite getWrite() { return write; }
    }
    public record IssueWrite(long uploadId, Issue issue, String actor, LocalDateTime now) {}
    public record TargetWrite(String month, BigDecimal sales, BigDecimal profit, long uploadId,
        String actor, LocalDateTime now) {}
}
