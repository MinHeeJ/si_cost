package com.sicost.operatingstandards;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.operatingstandards.OperatingStandardsDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OperatingStandardsController.class)
@ContextConfiguration(classes=OperatingStandardsController.class)
@Import({OperatingStandardsService.class, OperatingStandardsRepository.class, RevisionService.class,
        ChangeHistoryWriter.class, ApiExceptionHandler.class, RequestIdFilter.class, JsonConfiguration.class})
class OperatingStandardsWriteContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean OperatingStandardsMapper mapper;
    @MockBean AggregateLockMapper locks;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean CommitInvalidation invalidation;
    @MockBean Clock clock;
    Stored cashMonth;
    Stored cashAmount;
    Stored vat;

    Stored row(String key,String value) {
        return new Stored(key,"202601",key,new BigDecimal(value),"원","설명","admin",null,null,null);
    }
    @BeforeEach void setup() throws Exception {
        assertThat(new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8))
            .contains("operationId: getOperatingStandards", "operationId: saveOperatingStandards", "operationId: getOperatingStandardHistory");
        when(users.userId()).thenReturn("admin");
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        cashMonth=row("CASH_BASE_YM","202601"); cashAmount=row("CASH_BASE_AMT","1150000000"); vat=row("VAT_RT","10");
        when(mapper.rows("CASH_BASE_YM",true)).thenReturn(List.of(cashMonth));
        when(mapper.rows("CASH_BASE_AMT",true)).thenReturn(List.of(cashAmount));
        when(mapper.rows("VAT_RT",true)).thenReturn(List.of(vat));
        when(mapper.all()).thenReturn(List.of(cashAmount,cashMonth,vat));
        when(mapper.update(any())).thenReturn(1); when(mapper.insert(any())).thenReturn(1);
    }
    String body(Stored row,String value,String month) throws Exception {
        var body=json.createObjectNode();
        if(month!=null)body.put("effectiveMonth",month);
        body.putArray("changes").addObject().put("key",row.key()).put("value",value)
            .put("expectedRevision",revisions.revision(List.of(row)));
        return json.writeValueAsString(body);
    }
    @Test void authHappySideEffectCashPreservesAplyBgnYmAndInvalidatesGetOperatingStandardsGetParameterAndPeriod() throws Exception {
        mvc.perform(put("/api/operating-standards").contentType("application/json").content(body(cashMonth,"202602",null)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.items").isArray());
        var write=org.mockito.ArgumentCaptor.forClass(Mutation.class);
        verify(mapper).update(write.capture());
        assertThat(write.getValue().original()).isEqualTo(cashMonth);
        assertThat(write.getValue().month()).isEqualTo("202601");
        assertThat(write.getValue().actor()).isEqualTo("admin");
        assertThat(write.getValue().value()).isEqualByComparingTo("202602");
        verify(mapper,never()).insert(any());
        verify(locks).lock("operating-standards:CASH_BASE_AMT");
        verify(locks).lock("operating-standards:CASH_BASE_YM");
        verify(history).insert(argThat(e->e.tableName().equals("TB_SY_OPER_STD") && e.targetKey().equals("CASH_BASE_YM:202601")
            && e.type().equals("UPD") && e.before().equals("202601") && e.after().equals("202602") && e.userId().equals("admin")));
        verify(invalidation).afterCommit("operating-standards");
        verify(invalidation).afterCommit("parameters"); verify(invalidation).afterCommit("period-context");
    }
    @Test void businessCashCloseCannotBeBypassedWithNewStartingMonth() throws Exception {
        when(mapper.closedMonths()).thenReturn(List.of("202601"));
        mvc.perform(put("/api/operating-standards").contentType("application/json").content(body(cashMonth,"202607",null)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"))
            .andExpect(jsonPath("$.field").value("changes"));
        verify(mapper,never()).update(any()); verify(mapper,never()).insert(any());
        verifyNoInteractions(history,invalidation);
    }
    @Test void happyGeneralNewMonthInsertsAndDoesNotOverwriteExistingMonth() throws Exception {
        mvc.perform(put("/api/operating-standards").contentType("application/json").content(body(vat,"11","2026-06")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        verify(mapper).insert(argThat(w->w.month().equals("202606") && w.actor().equals("admin") && w.original().equals(vat)));
        verify(mapper,never()).update(any());
        verify(history).insert(argThat(e->e.type().equals("NEW") && e.targetKey().equals("VAT_RT:202606") && e.before()==null && e.after().equals("11")));
        verify(invalidation,never()).afterCommit("period-context");
    }
    @Test void businessNoChangeHasNoModDttmOrHistoryWrite() throws Exception {
        mvc.perform(put("/api/operating-standards").contentType("application/json").content(body(vat,"10.0000","2026-01")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false))
            .andExpect(jsonPath("$.data.message").value("바뀐 내용이 없습니다."));
        verify(mapper,never()).update(any()); verify(mapper,never()).insert(any()); verifyNoInteractions(history,invalidation);
    }
    @Test void businessStaleRevisionDoesNotWrite() throws Exception {
        var request=json.readTree(body(vat,"11","2026-06"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)request.get("changes").get(0)).put("expectedRevision","stale");
        mvc.perform(put("/api/operating-standards").contentType("application/json").content(request.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        verify(mapper,never()).update(any()); verify(mapper,never()).insert(any()); verifyNoInteractions(history,invalidation);
    }
    @Test void authForgedRegUserIdIsRejectedBeforeLocksOrWrite() throws Exception {
        var request=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(body(vat,"11","2026-06"));
        request.put("regUserId","other");
        mvc.perform(put("/api/operating-standards").contentType("application/json").content(request.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.field").value("regUserId"));
        verifyNoInteractions(mapper,locks,history,invalidation);
    }
    @Test void businessNoEffectiveValueIsNullNotInventedZero() throws Exception {
        mvc.perform(get("/api/operating-standards").param("month","2025-12"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].key").value("CASH_BASE_AMT"))
            .andExpect(jsonPath("$.data.items[0].value").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].effectiveMonth").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].keyRevision").isString());
        verify(mapper,never()).insert(any()); verify(mapper,never()).update(any()); verifyNoInteractions(history,invalidation);
    }
    @Test void happyHistoryAndValidationTooLongKeyAndNotFound() throws Exception {
        when(mapper.rows("VAT_RT",false)).thenReturn(List.of(vat));
        mvc.perform(get("/api/operating-standards/VAT_RT/history")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].value").value("10"))
            .andExpect(jsonPath("$.data.items[0].effectiveMonth").value("2026-01"))
            .andExpect(jsonPath("$.data.items[0].status").value("현재"))
            .andExpect(jsonPath("$.data.keyRevision").isString());
        mvc.perform(get("/api/operating-standards/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/history"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.field").value("key"));
        mvc.perform(get("/api/operating-standards/MISSING/history"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verify(mapper,never()).insert(any()); verify(mapper,never()).update(any()); verifyNoInteractions(history,invalidation);
    }
}
