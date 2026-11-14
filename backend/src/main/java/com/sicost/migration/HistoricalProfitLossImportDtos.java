package com.sicost.migration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.B7Dtos.IssuesPage;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class HistoricalProfitLossImportDtos {
    private HistoricalProfitLossImportDtos() {}
    public record Request(String uploadId, String expectedRevision) {}
    public record Summary(int totalRows, int successRows, int warningRows, int errorRows, int fileWarningCount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Change(String month, int rowNumber, boolean changed, Map<String,String> before, Map<String,String> after) {}
    public record Validation(String uploadId, String status, Summary summary, IssuesPage issues,
        String expectedRevision, boolean canApply, List<String> warnings, String allowedFromMonth,
        String allowedThroughMonth, List<Change> changes) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Applied(String uploadId, String status, Summary summary, boolean alreadyApplied, String message,
        String appliedAt, List<String> changedMonths, List<String> unchangedMonths, List<String> keptMonths, List<String> warnings) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Result(String uploadId, String formType, String screenId, Integer year, String targetMonth,
        String status, Summary summary, String appliedAt, List<String> changedMonths, IssuesPage issues) {}
    public record Row(int rowNumber, String month, Map<String,BigDecimal> amounts) {}
    public record Stored(String month, BigDecimal salesAmt, BigDecimal allocCostAmt, BigDecimal idleCostAmt,
        BigDecimal drctExpsAmt, BigDecimal salesCostAmt, BigDecimal grossPrftAmt, BigDecimal sgaInputAmt,
        BigDecimal indrctLaborAmt, BigDecimal rndAmt, BigDecimal sgaTotAmt, BigDecimal prevAdjAmt,
        BigDecimal oprtPrftAmt, BigDecimal noprIncmAmt, BigDecimal noprExpsAmt, BigDecimal corpTaxAmt,
        BigDecimal netPrftAmt, String migrated, String fixed, String regUserId, LocalDateTime regDttm,
        String modUserId, LocalDateTime modDttm) {
        public Map<String,BigDecimal> amounts() {
            var values = new java.util.LinkedHashMap<String,BigDecimal>();
            BigDecimal[] data = {salesAmt,salesCostAmt,sgaTotAmt,oprtPrftAmt,allocCostAmt,idleCostAmt,drctExpsAmt,
                grossPrftAmt,sgaInputAmt,indrctLaborAmt,rndAmt,prevAdjAmt,noprIncmAmt,noprExpsAmt,corpTaxAmt,netPrftAmt};
            for (int i=0;i<data.length;i++) values.put(HistoricalProfitLossImportDefinition.FIELDS.get(i),data[i]);
            return values;
        }
    }
    public record Checked(List<Row> rows, Summary summary, List<Issue> issues, List<String> warnings, List<Change> changes) {
        public boolean canApply() { return summary.errorRows()==0; }
    }
    public record Upload(long uploadId, String formType, String screenId, String filename, long size,
        String month, String projectId, String step, String actor, String status, int totalRows,
        int successRows, int warningRows, int errorRows, LocalDateTime appliedAt) {}
    public record UploadWrite(long uploadId, String filename, long size, Summary summary, String actor, LocalDateTime now) {}
    public static final class Draft {
        private Long uploadId;
        private final UploadWrite write;
        public Draft(UploadWrite write) { this.write=write; }
        public Long getUploadId() { return uploadId; }
        public void setUploadId(Long value) { uploadId=value; }
        public UploadWrite getWrite() { return write; }
    }
    public record IssueWrite(long uploadId, Issue issue, String actor, LocalDateTime now) {}
    public record ProfitLossWrite(String month, String base, Map<String,BigDecimal> amounts, String actor, LocalDateTime now) {}
}
