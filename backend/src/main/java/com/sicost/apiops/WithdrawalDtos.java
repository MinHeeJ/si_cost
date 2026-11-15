package com.sicost.apiops;

import java.time.LocalDate;
import java.util.List;

public final class WithdrawalDtos {
    private WithdrawalDtos() {}
    public record Developer(String developerId,String name,String managementNumber,String jobCode,String subJobCode,
        String gradeCode,String employeeTypeCode,String detailId) {}
    public record NextAssignment(String kind,String assignmentId,String tentativeAssignmentId,String projectId,
        String opportunityId,String name,LocalDate startDate,String startMonth) {}
    public record Row(String assignmentId,Developer developer,String projectId,String projectName,String jobCode,
        LocalDate withdrawalDate,int remainingDays,int extensionCount,boolean earlyWithdrawal,String extensionPossibility,
        boolean extensionEditable,List<NextAssignment> nextAssignments,int recommendationCount,boolean canRecommend,
        boolean canChangeTentative,String revision) {}
    public record Guidance(boolean visible,int withinThreeMonthsPeople,int withoutNextAssignmentPeople,
        int withMatchingPositionsPeople,String message) {}
    public record Data(List<Row> items,int year,int page,int size,int totalCount,Guidance guidance,LocalDate serverDate,String baseMonth) {}
    public record Source(String assignmentId,String developerId,String name,String managementNumber,String developerJob,
        String subJobCode,String gradeCode,String employeeTypeCode,String projectId,String projectName,String jobCode,
        LocalDate withdrawalDate,int extensionCount,boolean earlyWithdrawal,String extensionPossibility,
        String projectStatus,String developerUse,String directClass,String workStatus,LocalDate joinDate,LocalDate retireDate,
        String customerId) {}
    public record Window(int year,LocalDate today,LocalDate soonEnd) {}
    public record NextQuery(String assignmentId,String developerId,LocalDate withdrawalDate) {}
    public record Leave(String developerId,LocalDate startDate,LocalDate endDate) {}
    public record Position(String opportunityId,int needSn,String jobCode,String gradeCode,String startMonth,int months,
        int remainingCount,String contractType,String relatedJobs,String customerId,java.math.BigDecimal probability) {}
}
