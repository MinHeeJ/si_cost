package com.sicost.workforcerecommendationsops;

import java.time.LocalDate;
import java.util.List;

public final class RecommendationBrowseDtos {
    private RecommendationBrowseDtos() {}
    public record HistoryQuery(String targetType,String targetId,String job,String recommendationType,int page,int size) {
        public long offset(){return (long)(page-1)*size;}
    }
    public record HistoryItem(String rcmdId,String recommendationType,String targetId,String jobCode,String startMonth,
                              String requestedGradeCode,String executedAt,String executedBy,int candidateCount) {}
    public record HistoryData(List<HistoryItem> items,int page,int size,long totalCount) {}
    public record TargetSource(String targetType,String targetId,String name,String contractTypeCode,String projectIdAfterWin,
                               LocalDate contractStartDate,LocalDate contractEndDate,String startMonth) {}
    public record NeedSource(String needSn,String jobCode,String gradeCode,int needCount,String startMonth,int periodMonths,
                             String monthlyMm,int activeCount,String snapshot) {}
    public record Need(String needSn,String jobCode,String gradeCode,int needCount,String startMonth,int periodMonths,
                       String monthlyMm,int activeCount,int remainingCount,String revision) {}
    public record Defaults(String jobCode,String startMonth,String requestedGradeCode,String needSn) {}
    public record Target(String targetType,String targetId,String name,String contractTypeCode,String projectIdAfterWin,
                         LocalDate contractStartDate,LocalDate contractEndDate,List<Need> needs,Defaults defaults,String revision) {}
    public record TargetsData(List<Target> items,String firstTargetId,LocalDate serverDate,String currentMonth,String baseMonth,String revision) {}
}
