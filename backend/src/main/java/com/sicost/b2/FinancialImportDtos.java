package com.sicost.b2;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.B7Dtos.IssuesPage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class FinancialImportDtos {
    private FinancialImportDtos() {}
    public record Summary(int totalRows,int successRows,int warningRows,int errorRows,int replaceCount,int preserveCount) {}
    public record ResultItem(int rowNumber,String itemSn) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Result(String uploadId,String formType,String screenId,String month,String status,Summary summary,
        boolean canApply,String expectedRevision,boolean changed,String appliedAt,IssuesPage issues,List<ResultItem> items) {}
    public record Request(String month,String uploadId,String expectedRevision) {}
    public record Values(String finClass,String sgaItemCode,String itemName,BigDecimal amount,BigDecimal vatAmount,
        LocalDate ioDate,String repeat,String remark) {}
    public record CheckedRow(int rowNumber,Values value) {}
    public record Checked(List<CheckedRow> rows,Summary summary,List<Issue> issues) {}
    public record StoredItem(String month,int itemSn,String finClass,String sgaItemCode,String itemName,BigDecimal amount,
        BigDecimal vatAmount,LocalDate ioDate,String repeat,String remark,String autoFill,Long uploadId,
        String regUserId,LocalDateTime regDttm,String modUserId,LocalDateTime modDttm) {}
    public record Upload(long uploadId,String formType,String screenId,String filename,long size,String month,
        String projectId,String step,String actor,String status,int totalRows,int successRows,int warningRows,int errorRows,LocalDateTime appliedAt) {}
    public record Metadata(String item,String value) {}
    public record Code(String groupId,String code,String name,String source) {}
    public record UploadWrite(long uploadId,String filename,long size,String month,Summary summary,String actor,LocalDateTime now) {}
    public static final class Draft {
        private Long uploadId;
        private final UploadWrite write;
        public Draft(UploadWrite write) { this.write=write; }
        public Long getUploadId() { return uploadId; }
        public void setUploadId(Long value) { uploadId=value; }
        public UploadWrite getWrite() { return write; }
    }
    public record IssueWrite(long uploadId,Issue issue,String actor,LocalDateTime now) {}
    public record ItemWrite(String month,int itemSn,Values value,long uploadId,String actor,LocalDateTime now) {}
}
