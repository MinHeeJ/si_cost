package com.sicost.apiops;

import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.forecast.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import static com.sicost.apiops.StaffingReadDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkforceCalendarSortTest {
    @Test void currentAssignmentsSortBeforeAbsentPastAndFutureWhileSummaryDatesStayUnchanged() {
        var mapper=mock(StaffingReadMapper.class);
        var repository=mock(SalesForecastRepository.class);
        var monthly=mock(AssignmentMonthlyService.class);
        var users=mock(CurrentUserProvider.class);
        var clock=Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"),ZoneOffset.UTC);
        var service=new WorkforceCalendarService(mapper,repository,monthly,users,clock);
        when(monthly.cashBase()).thenReturn(YearMonth.of(2025,1));
        when(mapper.people()).thenReturn(List.of(person("none","가대기"),person("past","나종료"),
            person("future","다예정"),person("later","권다음자리"),person("soon","신철수")));
        var assignments=List.of(assignment("past","2026-01-01","2026-02-01"),
            assignment("future","2026-12-01","2026-12-31"),
            assignment("later","2026-10-01","2026-12-01"),
            assignment("later","2026-10-01","2026-11-20"),
            assignment("soon","2026-11-16","2026-11-16"),
            assignment("soon","2026-12-01","2027-03-01"));
        when(repository.load(YearMonth.of(2026,11))).thenReturn(new SalesForecastSources.Snapshot(
            List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),Map.of(),List.of(),assignments));
        var q=new LinkedMultiValueMap<String,String>();q.add("year","2026");
        var result=service.list(q);
        assertThat(result.items()).extracting(CalendarPerson::developerId)
            .containsExactly("soon","later","none","past","future");
        assertThat(result.items().get(0).lastWithdrawalDate()).isEqualTo(LocalDate.of(2027,3,1));
        assertThat(result.summary().leavingSoonPeople()).isEqualTo(2);
        q.set("sort","name");q.set("direction","ASC");
        assertThat(service.list(q).items()).extracting(CalendarPerson::developerId)
            .containsExactly("none","later","past","future","soon");
        q.set("sort","lastWithdrawalDate");q.set("direction","DESC");
        assertThat(service.list(q).items()).extracting(CalendarPerson::developerId)
            .containsExactly("later","soon","none","past","future");
    }
    private Person person(String id,String name) {
        return new Person(id,name,"DEV",null,"FT","Y","DRCT",LocalDate.of(2025,1,1),null);
    }
    private SalesForecastSources.Assignment assignment(String developer,String start,String end) {
        return new SalesForecastSources.Assignment(new AssignmentMonthlyService.Source(
            developer+start+end,"P-1",developer,"DEV",LocalDate.parse(start),LocalDate.parse(end),
            BigDecimal.ONE,null,"NORMAL",null,"H",0,"N","ING"),List.of());
    }
}
