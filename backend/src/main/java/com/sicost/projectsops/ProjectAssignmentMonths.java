package com.sicost.projectsops;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.sicost.projectsops.ProjectStatusDtos.*;

public final class ProjectAssignmentMonths {
    private ProjectAssignmentMonths() {}
    public static String effectiveStatus(Project project,LocalDate today) {
        return "LOSE".equals(project.status())?"LOSE":project.endDate().isBefore(today)?"END":project.status();
    }
    public static List<ValueHistory> effectiveHistory(List<ValueHistory> histories) {
        return histories.stream().filter(h->Set.of("INPUT","RATE","UPRC").contains(h.type()))
            .sorted(Comparator.comparing((ValueHistory h)->YearMonth.from(h.date())).thenComparingInt(ValueHistory::serial)).toList();
    }
    public static BigDecimal value(List<ValueHistory> histories,YearMonth month,boolean mm,BigDecimal fallback) {
        List<ValueHistory> effective=effectiveHistory(histories);
        var candidates=effective.stream().filter(h->("INPUT".equals(h.type())||(mm?"RATE":"UPRC").equals(h.type()))
            &&("INPUT".equals(h.type())||(mm?h.mm():h.price())!=null)&&!YearMonth.from(h.date()).isAfter(month)).toList();
        if(!candidates.isEmpty()) {var h=candidates.get(candidates.size()-1);return mm?h.mm():h.price();}
        for(ValueHistory h:effective)if("INPUT".equals(h.type()))return mm?h.mm():h.price();
        return fallback;
    }
    public static int days(YearMonth month,LocalDate start,LocalDate end) {
        LocalDate from=start.isAfter(month.atDay(1))?start:month.atDay(1);
        LocalDate to=end.isBefore(month.atEndOfMonth())?end:month.atEndOfMonth();
        return from.isAfter(to)?0:Math.toIntExact(ChronoUnit.DAYS.between(from,to)+1);
    }
    public static BigDecimal mm(BigDecimal contractMm,int days,YearMonth month) {
        return contractMm.multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(month.lengthOfMonth()),2,RoundingMode.HALF_UP);
    }
    public static String eventMemo(String reason) {
        String value="프로젝트 실주: "+reason;
        return value.codePointCount(0,value.length())<=200?value:value.substring(0,value.offsetByCodePoints(0,199))+"…";
    }
}
