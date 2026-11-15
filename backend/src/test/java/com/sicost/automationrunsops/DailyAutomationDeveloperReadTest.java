package com.sicost.automationrunsops;

import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.developers.*;
import com.sicost.areasops.AddressService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DailyAutomationDeveloperReadTest {
    @Test void listAndDetailDeriveTodayStatusWithoutAnyDatabaseOrAuditMutation() {
        var mapper=mock(DeveloperMapper.class);var repository=mock(DeveloperRepository.class);var history=mock(ChangeHistoryWriter.class);
        var revisions=mock(RevisionService.class);var addresses=mock(AddressService.class);
        Clock clock=Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"),ZoneOffset.UTC);
        var json=JsonMapper.builder().findAndAddModules().build();
        var query=new DeveloperQueryService(repository,revisions,addresses,json,clock,history);
        Map<String,Object> row=new LinkedHashMap<>();row.put("id","DEV-000901");row.put("managementNo","K-0901");row.put("name","조회 검증");row.put("employeeType","FT");
        row.put("status","INPUT");row.put("useYn","N");row.put("retireDate",LocalDate.of(2026,7,14));
        var snapshot=new DeveloperRepository.Snapshot(row,List.of(),List.of(),List.of());
        when(repository.mapper()).thenReturn(mapper);
        when(repository.read(any())).thenAnswer(i->((Supplier<?>)i.getArgument(0)).get());
        when(repository.snapshot("DEV-000901",false)).thenReturn(snapshot);
        when(mapper.labels(any())).thenReturn(Map.of("job","개발"));when(mapper.mmOver(any())).thenReturn("1");when(mapper.assignments(any(),any(),any())).thenReturn(List.of());
        when(mapper.list(any())).thenReturn(List.of(row));when(mapper.count(any())).thenReturn(1L);
        var before=new LinkedHashMap<>(row);
        assertThat(query.get("DEV-000901").path("status").asText()).isEqualTo("RETIRE");
        var input=DeveloperInput.query(Map.of());
        assertThat(query.list(input).path("items").get(0).path("status").asText()).isEqualTo("RETIRE");
        assertThat(row).isEqualTo(before);verify(mapper,never()).updateStatus(any());verify(repository,never()).recalculate(any());verifyNoInteractions(history);
    }
}
