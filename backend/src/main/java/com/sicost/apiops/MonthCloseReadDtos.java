package com.sicost.apiops;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sicost.monthclosesops.MonthCloseDtos.Check;
import com.sicost.monthclosesops.MonthCloseDtos.Issue;
import java.util.List;

public final class MonthCloseReadDtos {
    private MonthCloseReadDtos() {}
    public record Query(String month,int page,int size) {}
    public record HistoryEntry(String month,int historySequence,String action,String actorId,
        String occurredAt,String reason,String remainingChecks,String actorName) {}
    public record HistoryPage(List<HistoryEntry> items,long total,int page,int size,boolean hasMore) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Result(String month,String status,String closedBy,String closedAt,String nextCloseMonth,
        String latestCloseMonth,boolean managementAllowed,boolean canCheck,boolean canClose,boolean canReopen,
        String revision,List<Check> checks,String checkMode,int warningCount,HistoryPage history,List<Issue> issues,
        String closedByName) {
        public Result(String month,String status,String closedBy,String closedAt,String nextCloseMonth,
            String latestCloseMonth,boolean managementAllowed,boolean canCheck,boolean canClose,boolean canReopen,
            String revision,List<Check> checks,String checkMode,int warningCount,HistoryPage history,List<Issue> issues) {
            this(month,status,closedBy,closedAt,nextCloseMonth,latestCloseMonth,managementAllowed,canCheck,
                canClose,canReopen,revision,checks,checkMode,warningCount,history,issues,null);
        }
    }
}
