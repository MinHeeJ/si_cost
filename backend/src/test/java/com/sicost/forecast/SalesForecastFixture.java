package com.sicost.forecast;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.sicost.common.assignments.AssignmentMonthlyService.*;
import static com.sicost.forecast.SalesForecastSources.*;

final class SalesForecastFixture {
    static final LocalDate DAY=LocalDate.of(2026,7,15);
    static BigDecimal n(String v) {return new BigDecimal(v);}
    static Project project(String id,String type,String status,String end,String amount,String billed) {
        return new Project(id,id,type,status,LocalDate.of(2026,7,1),LocalDate.parse(end),n(amount),n(billed),null);
    }
    static Opportunity opportunity(String id,String status,String start,Integer months,String amount,String probability) {
        return new Opportunity(id,id,"TK",status,start,months,n(amount),n(probability),null);
    }
    static Map<String,BigDecimal> standards() {return Map.of("SHORT_PERSON",n("0.4"),"SURPLUS_PERSON",n("1.0"),"HIRE_LEAD_MON",n("2"),"EXT_PRBL_H",n("80"),"EXT_PRBL_M",n("50"),"EXT_PRBL_L",n("0"),"EXT_PRBL_NULL",n("50"));}
    static Snapshot snapshot(List<Project> projects,List<Opportunity> opportunities) {
        return new Snapshot(projects,opportunities,List.of(),List.of(),List.of(),List.of(),List.of(),standards(),List.of(new Job("BE",1)),List.of());
    }
    static Snapshot workforce(List<Need> needs,List<Plan> plans,List<Person> people,List<Leave> leaves,List<Assignment> assignments) {
        return new Snapshot(List.of(project("P","MO","ING","2027-03-31","0","0")),List.of(opportunity("O","ING","202608",6,"600","50")),needs,plans,people,leaves,List.of(new Candidate("BE",DAY)),standards(),List.of(new Job("BE",1)),assignments);
    }
    static Source source(String id,String type,String end, String extension) {
        return new Source(id,"P","D","BE",LocalDate.of(2026,8,1),LocalDate.parse(end),n("1.00"),n("100"),type,null,extension,0,"N","ACT");
    }
}
