package com.sicost.apiops;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.sicost.apiops.WithdrawalDtos.*;

/** Pure NEXT eligibility and top-five ranking, shared by list counts and generation adapters. */
public final class WithdrawalPositionCalculator {
    private WithdrawalPositionCalculator() {}
    public record Ranked(Position position,YearMonth actualStart,int gap,int waitDays,int jobScore,int gradeScore,
        int gapScore,int customerScore,int probabilityScore) {
        public int total(){return jobScore+gradeScore+gapScore+customerScore+probabilityScore;}
    }
    public static boolean eligible(Source person,LocalDate today,List<Leave> leaves) {
        return "Y".equals(person.developerUse())&&"DRCT".equals(person.directClass())
            &&person.retireDate()==null&&!Set.of("RETIRE","LEAVE").contains(person.workStatus())
            &&leaves.stream().noneMatch(l->overlaps(l,today,today));
    }
    public static List<Ranked> calculate(Source source,LocalDate today,List<Position> positions,List<Leave> leaves) {
        if(!eligible(source,today,leaves))return Collections.emptyList();
        YearMonth from=YearMonth.from(source.withdrawalDate()).plusMonths(1);
        List<Ranked> result=new ArrayList<>();
        for(Position p:positions){
            YearMonth start=YearMonth.parse(p.startMonth()),actual=start.isAfter(from)?start:from;
            long distance=Math.max(0,ChronoUnit.MONTHS.between(from,start));
            if(p.months()<1||p.remainingCount()<=0||distance>3||start.plusMonths(p.months()-1).isBefore(from))continue;
            if(!source.developerJob().equals(p.jobCode())&&(p.relatedJobs()==null||Arrays.stream(p.relatedJobs().split(",")).map(String::strip).noneMatch(source.developerJob()::equals)))continue;
            if("DP".equals(p.contractType())&&Set.of("FL","OS").contains(source.employeeTypeCode()))continue;
            if(source.joinDate()!=null&&source.joinDate().isAfter(actual.atEndOfMonth()))continue;
            if(leaves.stream().anyMatch(l->overlaps(l,actual.atDay(1),actual.atEndOfMonth())))continue;
            int gap=(int)distance;
            List<String> grades=List.of("JR","MD","SR","EX");
            int developerGrade=source.gradeCode()==null?-1:grades.indexOf(source.gradeCode()),requestedGrade=grades.indexOf(p.gradeCode()==null||p.gradeCode().isBlank()?"MD":p.gradeCode());
            int grade=developerGrade<0||requestedGrade<0?0:Math.abs(developerGrade-requestedGrade)==0?20:Math.abs(developerGrade-requestedGrade)==1?10:0;
            result.add(new Ranked(p,actual,gap,(int)Math.max(0,ChronoUnit.DAYS.between(source.withdrawalDate().plusDays(1),actual.atDay(1))),
                source.developerJob().equals(p.jobCode())?40:20,grade,switch(gap){case 0->25;case 1->15;case 2->8;default->3;},
                source.customerId()!=null&&source.customerId().equals(p.customerId())?10:0,
                p.probability().compareTo(new BigDecimal("60"))>=0?5:0));
        }
        return result.stream().sorted(Comparator.comparingInt(Ranked::total).reversed()
            .thenComparing(r->r.position().startMonth()).thenComparing(r->r.position().opportunityId(),WithdrawalPositionCalculator::compareId).thenComparingInt(r->r.position().needSn())).limit(5).toList();
    }
    private static int compareId(String a,String b){return a.matches("[0-9]+")&&b.matches("[0-9]+")?new java.math.BigInteger(a).compareTo(new java.math.BigInteger(b)):a.compareTo(b);}
    private static boolean overlaps(Leave leave,LocalDate start,LocalDate end){return !leave.startDate().isAfter(end)&&(leave.endDate()==null||!leave.endDate().isBefore(start));}
}
