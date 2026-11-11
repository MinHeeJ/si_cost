package com.sicost.developers;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DeveloperStatusTest {
    final LocalDate today=LocalDate.of(2026,6,15);
    Map<String,Object> leave(LocalDate start,LocalDate end){Map<String,Object> s=new HashMap<>();s.put("work_stat_cd","LEAVE");s.put("bgn_dt",start);s.put("end_dt",end);return s;}
    String status(Map<String,Object> r,List<Map<String,Object>> s,LocalDate end){return DeveloperQueryService.calculate(r,s,end==null?List.of():List.of(Map.of("endDate",end)),today);}
    @Test void retirementDayStillWorking(){assertThat(status(Map.of("retireDate",today),List.of(),null)).isEqualTo("IDLE");}
    @Test void dayAfterRetirementTakesPriority(){assertThat(status(Map.of("retireDate",today.minusDays(1)),List.of(leave(today,null)),today.plusYears(1))).isEqualTo("RETIRE");}
    @Test void leaveIncludesFirstDay(){assertThat(status(Map.of(),List.of(leave(today,today.plusDays(1))),today.plusYears(1))).isEqualTo("LEAVE");}
    @Test void leaveIncludesLastDay(){assertThat(status(Map.of(),List.of(leave(today.minusDays(1),today)),null)).isEqualTo("LEAVE");}
    @Test void dayAfterLeaveReturnsToAssignment(){assertThat(status(Map.of(),List.of(leave(today.minusDays(2),today.minusDays(1))),today.plusYears(1))).isEqualTo("INPUT");}
    @Test void twoMonthEndIsWithdrawal(){assertThat(status(Map.of(),List.of(),LocalDate.of(2026,8,31))).isEqualTo("WTHDR");}
    @Test void dayAfterTwoMonthEndIsInput(){assertThat(status(Map.of(),List.of(),LocalDate.of(2026,9,1))).isEqualTo("INPUT");}
    @Test void latestAssignmentControlsStatus(){assertThat(DeveloperQueryService.calculate(Map.of(),List.of(),List.of(Map.of("endDate",today),Map.of("endDate",LocalDate.of(2026,9,1))),today)).isEqualTo("INPUT");}
    @Test void stoppedIsNotInventedAsWorkStatus(){assertThat(status(Map.of("useYn","N"),List.of(),null)).isEqualTo("IDLE");}
}
