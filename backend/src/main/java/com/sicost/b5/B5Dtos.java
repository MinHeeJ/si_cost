package com.sicost.b5;

import com.sicost.assignmentsops.AssignmentActionsDtos.*;
import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b9.B9Dtos.Summary;
import com.sicost.common.assignments.AssignmentMonthlyService.Source;
import java.time.LocalDateTime;
import java.util.List;

public final class B5Dtos {
    private B5Dtos() {}
    public record Selection(int rowNumber,List<Withdrawal> withdrawals) {}
    public record Request(int year,Long uploadId,String previewToken,List<Selection> withdrawalSelections) {}
    public record ResultRow(int rowNumber,String assignmentId) {}
    public record Substitution(int rowNumber,SubPreview preview) {}
    public record Validation(long uploadId,String formType,String screenId,String status,Summary summary,List<ResultRow> results,
        boolean changed,List<String> withdrawnIds,int issueCount,String previewToken,List<Substitution> substitutionPreviews) {}
    public record Result(long uploadId,String formType,String screenId,String status,Summary summary,List<ResultRow> results,boolean changed,List<String> withdrawnIds) {}
    public record Row(int rowNumber,Create create,Source source,List<com.sicost.common.assignments.AssignmentMonthlyService.Month> months) {}
    public record Checked(List<Row> rows,Summary summary,List<Issue> issues,List<Substitution> substitutionPreviews,List<Withdrawal> withdrawals) {}
    public record Upload(long uploadId,String formType,String screenId,String filename,long size,String actor,String status,int totalRows,int successRows,int warningRows,int errorRows) {}
    public record UploadWrite(long uploadId,String filename,long size,Summary summary,String actor,LocalDateTime now) {}
    public record IssueWrite(long uploadId,Issue issue,String actor,LocalDateTime now) {}
    public record Receipt(String item,String value) {}
    public record DeveloperNumber(String id,String managementNo) {}
    public static final class Draft {
        private Long uploadId;
        private final UploadWrite write;
        public Draft(UploadWrite write) {this.write=write;}
        public Long getUploadId() {return uploadId;}
        public void setUploadId(Long value) {uploadId=value;}
        public UploadWrite getWrite() {return write;}
    }
}
