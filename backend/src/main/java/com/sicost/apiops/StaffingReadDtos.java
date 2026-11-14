package com.sicost.apiops;

import java.time.*;
import java.math.BigDecimal;
import java.util.List;

public final class StaffingReadDtos {
    private StaffingReadDtos(){}
    public record CandidateSource(String id,String opportunityId,String projectId,String projectName,String developerId,String developerName,String jobCode,LocalDate startDate,LocalDate endDate,BigDecimal contractMm,BigDecimal billingUnitPrice){}
    public record Candidate(String id,String opportunityId,String projectId,String projectName,String developerId,String developerName,String jobCode,LocalDate startDate,LocalDate endDate,String contractMm,String billingUnitPrice,String revision){}
    public record CandidatePage(List<Candidate> items,int total,int page,int size){}
    public record Person(String id,String name,String jobCode,String gradeCode,String employeeType,String useYn,String directClass,LocalDate joinDate,LocalDate retireDate){}
    public record Tentative(String id,String opportunityId,int needSn,String developerId,String startMonth,String status,String sourceScreenId,LocalDateTime processedAt){}
    public record TentativeSource(String id,String opportunityId,int needSn,String developerId,String startMonth,String status,String sourceScreenId,LocalDateTime processedAt,String endMonth,BigDecimal probability){
        public Tentative view(){return new Tentative(id,opportunityId,needSn,developerId,com.sicost.common.persistence.DatabaseMonthCodec.toApi(startMonth),status,sourceScreenId,processedAt);}
    }
    public record ProjectMm(String projectId,String actualMm,LocalDate endDate,String extensionPossibility,boolean preContract){}
    public record Cell(String month,String status,String actualMm,int assignmentCount,boolean overAssigned,boolean leavingSoon,List<ProjectMm> projects,List<Tentative> tentativeAssignments,String tentativeProbability){}
    public record CalendarPerson(String developerId,String name,String jobCode,String gradeCode,String employeeType,LocalDate lastWithdrawalDate,List<Cell> cells){}
    public record MonthTotal(String month,Integer availablePeople,String remainingMm){}
    public record Summary(int availablePeople,String remainingMm,int leavingSoonPeople,int highExtensionPeople,int concurrentPeople,int overAssignedPeople,String thirdMonthRemainingMm){}
    public record CalendarPage(List<CalendarPerson> items,int total,int page,int size,int year,Summary summary,List<MonthTotal> monthTotals){}
}
