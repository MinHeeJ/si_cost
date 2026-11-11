package com.sicost.salespartsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.salespartsops.PartStandardDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PartStandardController.class)
@ContextConfiguration(classes=PartStandardController.class)
@Import({PartStandardService.class,PartStandardRepository.class,RevisionService.class,ChangeHistoryWriter.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class PartStandardContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PartStandardService service;
    @MockBean PartStandardMapper mapper;
    @MockBean AggregateLockMapper locks;
    @MockBean MasterLockMapper references;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean CommitInvalidation invalidation;
    @MockBean Clock clock;
    Header part;
    List<Stored> timeline;
    Stored row(String month,String end,String leader,String rate) {
        return new Stored("PART-test",month,end,leader,new BigDecimal(rate),new BigDecimal("1500000"),"admin",LocalDateTime.parse("2026-01-01T00:00:00.123456"),null,null);
    }
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(users.userId()).thenReturn("admin");
        part=new Header("PART-test","영업 시험파트","N","N","admin",LocalDateTime.parse("2026-01-01T00:00:00.123456"),null,null);
        timeline=List.of(row("202601","202611","leader","40.00"),row("202612",null,"leader","50.00"));
        when(mapper.header("PART-test",true)).thenReturn(part);
        when(mapper.timeline(eq("PART-test"),anyBoolean())).thenReturn(timeline);
        when(mapper.eligible(anyString())).thenReturn(true);
        when(mapper.operatingValue("CASH_BASE_YM","999912")).thenReturn(new BigDecimal("202601"));
        when(mapper.operatingValue(eq("PART_DSTRB_RT"),anyString())).thenReturn(new BigDecimal("40.0000"));
        when(mapper.operatingValue(eq("PART_SALES_EXPS"),anyString())).thenReturn(new BigDecimal("1500000.0000"));
        when(mapper.leaderName(anyString())).thenReturn("파트장");
        when(mapper.endPrevious(any())).thenReturn(1);when(mapper.insert(any())).thenReturn(1);
    }
    ObjectNode body() {
        return json.createObjectNode().put("effectiveMonth","2026-08").put("leaderUserId","leader")
            .put("profitRate","45").put("salesExpenseAmount","1,500,000원").put("expectedRevision",service.revision(part,timeline));
    }
    org.springframework.test.web.servlet.ResultActions save(ObjectNode body) throws Exception {
        return mvc.perform(post("/api/sales-parts/PART-test/standards").contentType("application/json").content(body.toString()));
    }
    void noWrite() {verify(mapper,never()).insert(any());verify(mapper,never()).endPrevious(any());verifyNoInteractions(history,invalidation);}
    @Test void insertsBetweenRowsWithoutAuthenticationAndUsesServerActor() throws Exception {
        save(body()).andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.changed").value(true));
        verify(mapper).endPrevious(argThat(m->m.month().equals("202601") && m.endMonth().equals("202607")));
        verify(mapper).insert(argThat(m->m.month().equals("202608") && m.endMonth().equals("202611") && m.actor().equals("admin")));
        var order=inOrder(mapper,locks,references);
        order.verify(mapper).lockUser("leader");order.verify(mapper).header("PART-test",true);order.verify(mapper).timeline("PART-test",true);
        order.verify(locks).lock("operating-standards:CASH_BASE_YM");order.verify(references).lockClose();order.verify(references).lockSettlement();
        verify(history).insert(argThat(e->e.type().equals("NEW") && e.userId().equals("admin") && e.targetKey().equals("PART-test:202608")));
        verify(history).insert(argThat(e->e.type().equals("UPD") && e.before().equals("202611") && e.after().equals("202607")));
    }
    @Test void defaultsUseLastCloseNotWorkAndChooseFutureMinimum() throws Exception {
        when(mapper.lastClose()).thenReturn("202608");
        mvc.perform(get("/api/sales-parts/defaults")).andExpect(status().isOk()).andExpect(jsonPath("$.data.minMonth").value("2026-09"))
            .andExpect(jsonPath("$.data.effectiveMonth").value("2026-09")).andExpect(jsonPath("$.data.profitRate").value("40"));
        verify(mapper).operatingValue("PART_DSTRB_RT","202609");noWrite();
    }
    @Test void defaultsRequestedSingleMonthAndMissingOrInvalidSourcesAreNotRounded() throws Exception {
        when(mapper.operatingValue("PART_DSTRB_RT","202602")).thenReturn(new BigDecimal("40.001"));
        when(mapper.operatingValue("PART_SALES_EXPS","202602")).thenReturn(null);
        mvc.perform(get("/api/sales-parts/defaults").param("effectiveMonth","2026-02")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.effectiveMonth").value("2026-02")).andExpect(jsonPath("$.data.defaultErrors.length()").value(2))
            .andExpect(jsonPath("$.data.defaultErrors[0].source").value("PART_DSTRB_RT"));noWrite();
    }
    @ParameterizedTest @ValueSource(strings={"-1","101","1.001"})
    void invalidSourceRateReturnsNullWithError(String rate) throws Exception {
        when(mapper.operatingValue(eq("PART_DSTRB_RT"),anyString())).thenReturn(new BigDecimal(rate));
        mvc.perform(get("/api/sales-parts/defaults")).andExpect(status().isOk()).andExpect(jsonPath("$.data.defaultErrors[0].field").value("profitRate"));noWrite();
    }
    @Test void maximumCloseHasSupportedErrorsForReadAndWrite() throws Exception {
        when(mapper.lastClose()).thenReturn("999912");
        mvc.perform(get("/api/sales-parts/defaults")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.field").value("effectiveMonth"));
        save(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));noWrite();
    }
    @Test void optionsPreserveHqAndFutureFirstMonth() throws Exception {
        when(mapper.options()).thenReturn(List.of(new Option("HQ","본사","Y",null),new Option("PART-test","영업 시험파트","N","2027-01")));
        mvc.perform(get("/api/sales-parts/options")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].hqYn").value("Y"))
            .andExpect(jsonPath("$.data.items[1].firstEffectiveMonth").value("2027-01"));noWrite();
    }
    @Test void unknownQueryRejected() throws Exception {
        mvc.perform(get("/api/sales-parts/options").param("useYn","Y")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        mvc.perform(get("/api/sales-parts/defaults").param("actor","admin")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);
    }
    @Test void invalidMonthRejected() throws Exception {
        mvc.perform(get("/api/sales-parts/defaults").param("effectiveMonth","0000-01")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("effectiveMonth"));noWrite();
    }
    @Test void staleWinsOverMissingRequiredAndDuplicate() throws Exception {
        ObjectNode b=body().put("expectedRevision","stale").put("effectiveMonth","2026-01");b.remove("profitRate");
        save(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrite();
    }
    @Test void storedValueChangesEvenAtSameTimestampChangeRevision() {
        assertThat(service.revision(part,timeline)).isNotEqualTo(service.revision(part,List.of(row("202601","202611","leader","41.00"),timeline.get(1))));
    }
    @ParameterizedTest @ValueSource(strings={"effectiveMonth","leaderUserId","profitRate","salesExpenseAmount"})
    void missingRequiredFieldDoesNotWrite(String field) throws Exception {
        ObjectNode b=body();b.remove(field);save(b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));noWrite();
    }
    @ParameterizedTest @ValueSource(strings={"100.01","40.001","-1","1e2","NaN"})
    void invalidRateDoesNotWrite(String value) throws Exception {
        save(body().put("profitRate",value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("profitRate"));noWrite();
    }
    @ParameterizedTest @ValueSource(strings={"1.1","-1","1000000000000000"})
    void invalidExpenseDoesNotWrite(String value) throws Exception {
        save(body().put("salesExpenseAmount",value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("salesExpenseAmount"));noWrite();
    }
    @Test void beforeFirstStandardRejected() throws Exception {
        save(body().put("effectiveMonth","2025-12")).andExpect(status().isConflict());
        when(mapper.operatingValue("CASH_BASE_YM","999912")).thenReturn(new BigDecimal("202501"));
        save(body().put("effectiveMonth","2025-12")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("effectiveMonth"));noWrite();
    }
    @Test void processorInjectionIsUnknownFieldAndRequestIdMatches() throws Exception {
        var response=save(body().put("regUserId","forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andReturn().getResponse();
        assertThat(json.readTree(response.getContentAsString()).get("requestId").asText()).isEqualTo(response.getHeader("X-Request-Id"));verifyNoInteractions(mapper,history);
    }
    @Test void ineligibleLeaderRejectedWithoutChangingBusinessAuthorization() throws Exception {
        when(mapper.eligible("leader")).thenReturn(false);save(body()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("leaderUserId"));noWrite();
    }
    @Test void duplicateStartRejectedEvenWithSameValues() throws Exception {
        save(body().put("effectiveMonth","2026-01").put("profitRate","40")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STANDARD_MONTH_EXISTS"));noWrite();
    }
    @Test void belowLatestMinimumRejected() throws Exception {
        when(mapper.lastClose()).thenReturn("202608");save(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));noWrite();
    }
    @Test void confirmedSettlementBeyondFutureBoundaryBlocksFinancialChange() throws Exception {
        when(mapper.blockedMonths("PART-test","202608")).thenReturn(List.of("202701","202702"));
        save(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PART_SETTLEMENT_CONFIRMED"))
            .andExpect(jsonPath("$.details.blockedMonths[1]").value("2027-02")).andExpect(jsonPath("$.details.minMonth").value("2027-03"));noWrite();
    }
    @Test void leaderOnlyChangeAllowedAndStoppedPartAllowed() throws Exception {
        save(body().put("leaderUserId","other-leader").put("profitRate","40")).andExpect(status().isCreated());
        verify(references,never()).lockSettlement();verify(mapper,never()).blockedMonths(anyString(),anyString());
    }
    @Test void newMonthSameTripleIsNoOpAndNoAuditTimestampOrHistoryWrite() throws Exception {
        save(body().put("profitRate","40.00")).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false))
            .andExpect(jsonPath("$.data.message").value("지금 기준과 같습니다."));noWrite();
    }
    @Test void missingPartRejected() throws Exception {
        when(mapper.header("PART-test",true)).thenReturn(null);save(body()).andExpect(status().isNotFound());noWrite();
    }
    @Test void hqExcludedFromGeneralManagement() throws Exception {
        when(mapper.header("PART-test",true)).thenReturn(new Header("HQ","본사","Y","Y","admin",null,null,null));
        save(body()).andExpect(status().isNotFound());noWrite();
    }
}
