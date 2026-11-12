package com.sicost.b6;

import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.B7Dtos.IssuesPage;
import com.sicost.b9.B9Dtos.Summary;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class B6Dtos {
    private B6Dtos() {}
    public record Project(String id, String name, String contractType, String status, LocalDate startDate,
                          LocalDate endDate, BigDecimal contractAmount) {}
    public record Developer(String id, String managementNo, String name, String useYn) {}
    public record Cell(String step, int rowSn, String month, String rowType, String developerId, String itemName, BigDecimal amount) {}
    public record Actual(String developerId, String managementNo, String name, String month, BigDecimal mm) {}
    public record Mismatch(String developerId, String managementNo, String name, String month, String type, String amount, String actualMm) {}
    public record Comparison(String selectedStep, String contractAmount, String totalAmount, String differenceAmount,
                             int mismatchCount, List<Mismatch> items, List<String> warnings) {}
    public record Checked(List<Cell> cells, Summary summary, List<Issue> issues, Comparison comparison, String uploadedTableTotal) {}
    public record Validation(long uploadId, Summary summary, IssuesPage issues, boolean canApply, String revision,
                             Comparison prospectiveComparison, String uploadedTableTotal) {}
    public record Request(int year, String projectId, String step, Long uploadId, String expectedRevision) {}
    public record UploadWrite(long uploadId, String filename, long size, String projectId, String step, Summary summary, String actor, LocalDateTime now) {}
    public record CellWrite(String projectId, long uploadId, Cell cell, String actor, LocalDateTime now) {}
    public static final class B6UploadDraft {
        private Long uploadId;
        private final UploadWrite write;
        public B6UploadDraft(UploadWrite write) {this.write=write;}
        public Long getUploadId() {return uploadId;}
        public void setUploadId(Long uploadId) {this.uploadId=uploadId;}
        public UploadWrite getWrite() {return write;}
    }
}
