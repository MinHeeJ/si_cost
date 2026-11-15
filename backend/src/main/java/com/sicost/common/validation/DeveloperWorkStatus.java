package com.sicost.common.validation;

import java.time.*;
import java.util.List;

public final class DeveloperWorkStatus {
    private DeveloperWorkStatus() {}
    public record Leave(LocalDate start,LocalDate end) {}
    public record Assignment(LocalDate start,LocalDate end) {}
    public static String calculate(LocalDate retire,List<Leave> leaves,List<Assignment> assignments,LocalDate today) {
        if(retire!=null&&today.isAfter(retire))return "RETIRE";
        if(leaves.stream().anyMatch(l->!today.isBefore(l.start())&&(l.end()==null||!today.isAfter(l.end()))))return "LEAVE";
        LocalDate end=assignments.stream().filter(a->!today.isBefore(a.start())&&!today.isAfter(a.end()))
            .map(Assignment::end).max(LocalDate::compareTo).orElse(null);
        return end==null?"IDLE":end.isAfter(YearMonth.from(today).plusMonths(2).atEndOfMonth())?"INPUT":"WTHDR";
    }
}
