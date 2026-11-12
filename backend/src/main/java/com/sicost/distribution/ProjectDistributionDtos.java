package com.sicost.distribution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ProjectDistributionDtos {
    private ProjectDistributionDtos() {}
    public record Project(String id, String name, String customerId, String contractType, String status,
                          String startDate, String endDate, String workplace, String feature,
                          String estimateAmount, String originalContractAmount, String contractAmount,
                          String budgetAmount, String teamExpenseAmount, String projectExpenseAmount,
                          String budgetManual, String lossDate, String lossReason, String lossMemo, String opportunityId) {}
    public record Cell(String month, String amount) {}
    public record Row(int rowSn, String rowType, String developerId, String managementNo, String name,
                      String itemName, List<String> jobNames, String useYn, List<Cell> cells, String totalAmount) {}
    public record Data(Project project, String viewedStep, String selectedStep, List<String> months,
                       List<Row> rows, List<Cell> monthlyTotals, String totalAmount, String revision) {}
    public record Mismatch(String developerId, String managementNo, String name, String month,
                           String type, String amount, String actualMm) {}
    public record Comparison(String selectedStep, String contractAmount, String totalAmount, String differenceAmount,
                             int mismatchCount, List<Mismatch> items, List<String> warnings) {}
    public record Badge(String selectedStep, boolean hasTable, String totalAmount, String contractDifference, int mismatchCount) {}
    public record UploadRecord(long uploadId, String step, String fileName, String actor,
                               String registeredAt, String status, String appliedAt) {}
    public record Uploads(List<UploadRecord> items) {}
    public record StoredCell(String step, int rowSn, String month, String rowType, String developerId,
                             String itemName, BigDecimal amount, String managementNo, String name, String useYn) {}
    public record Job(String developerId, String name) {}
    public record Actual(String developerId, String managementNo, String name, String month, BigDecimal mm) {}
    public record Aggregate(String project, String parts, String plans, String expenses, String stages, String pm) {}
    public record ProjectLookup(String id, LocalDate today) {}
}
