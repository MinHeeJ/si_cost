package com.sicost.businessusersops;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class BusinessUserProjectsDtos {
    private BusinessUserProjectsDtos() {}
    public record Query(String userId, LocalDate today, String month) {}
    public record ProjectRow(String id, String name, String contractType, LocalDate startDate, LocalDate endDate,
                             BigDecimal contractAmount, String status, String customerName, long personCount,
                             String partName, String selectedStep, BigDecimal distributionTotal, long mismatchCount) {}
    public record DistributionBadge(String selectedStep, boolean hasTable, String totalAmount,
                                    String contractDifference, long mismatchCount) {}
    public record ProjectItem(String id, String name, String contractType, String startDate, String endDate,
                              String contractAmount, String status, String customerName, long personCount,
                              String partName, DistributionBadge distribution) {}
    public record BusinessUserProjectsData(String userId, List<ProjectItem> items, String revision) {}
}
