package com.sicost.b1;

import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b9.B9Dtos.Summary;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class B1Dtos {
    private B1Dtos() {}
    public record Request(String month, Long uploadId, String expectedRevision, boolean confirmFullReplacement) {}
    public record Developer(String id, String managementNo, String name, String employeeType, String directClass, String position) {}
    public record Rate(String code, BigDecimal value) {}
    public record ResultRow(int rowNumber, String developerId) {}
    public record Removed(String developerId, String managementNo, String developerName) {}
    public record Replacement(int newCount, int changedCount, int removedCount, List<Removed> removedDevelopers) {}
    public record Validation(long uploadId, String formType, String screenId, String status, Summary summary,
        List<ResultRow> results, boolean changed, String month, String revision, int issueCount, Replacement replacement) {}
    public record Result(long uploadId, String formType, String screenId, String status, Summary summary,
        List<ResultRow> results, boolean changed, String month, String revision) {}
    public record Values(BigDecimal pay, BigDecimal allowance, BigDecimal companyInsurance, BigDecimal selfInsurance,
        BigDecimal retirement, BigDecimal incomeTax, BigDecimal withholding, BigDecimal realPay, BigDecimal vendorSupply,
        String employeeType, String directClass) {}
    public record Payroll(String developerId, Values values) {}
    public record CheckedRow(int rowNumber, Developer developer, Values values) {}
    public record Checked(List<CheckedRow> rows, Summary summary, List<Issue> issues) {}
    public record Upload(long uploadId, String formType, String screenId, String filename, long size, String month,
        String actor, String status, int totalRows, int successRows, int warningRows, int errorRows) {}
    public record UploadWrite(long uploadId, String filename, long size, String month, Summary summary, String actor, LocalDateTime now) {}
    public record IssueWrite(long uploadId, Issue issue, String actor, LocalDateTime now) {}
    public record PayWrite(String month, String developerId, Values value, long uploadId, String actor, LocalDateTime now) {}
    public record Receipt(String item, String value) {}
    public static final class Draft {
        private Long uploadId;
        private final UploadWrite write;
        public Draft(UploadWrite write) { this.write=write; }
        public Long getUploadId() { return uploadId; }
        public void setUploadId(Long uploadId) { this.uploadId=uploadId; }
        public UploadWrite getWrite() { return write; }
    }
}
