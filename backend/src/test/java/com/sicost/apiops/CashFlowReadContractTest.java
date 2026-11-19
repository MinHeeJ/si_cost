package com.sicost.apiops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.cashflowops.*;
import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.shared.SharedQueryDtos.CloseRow;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CashFlowReadContractTest {
    final JsonMapper json=JsonMapper.builder().findAndAddModules().build();
    CashFlowRepository repository;CurrentUserProvider users;MockMvc mvc;
    List<Person> people;List<Payroll> payroll;List<CloseRow> closes;List<Standard> standards;
    Map<String,JsonNode> financial,receipts;
    @BeforeEach void setup() throws Exception {
        repository=mock(CashFlowRepository.class);users=mock(CurrentUserProvider.class);
        people=new ArrayList<>();payroll=new ArrayList<>();closes=new ArrayList<>();
        standards=new ArrayList<>(List.of(new Standard("CASH_BASE_AMT","202601",new BigDecimal("1150000000")),new Standard("MIN_CASH","202601",new BigDecimal("1150000070")),new Standard("VAT_RT","202601",new BigDecimal("10"))));
        financial=new HashMap<>();receipts=new HashMap<>();
        financial.put("2026-01",json.readTree("""
            {"items":[{"fin_ym":"202601","item_sn":1,"fin_cls_cd":"NOPR_IN","amt":100,"vat_amt":0,"io_dt":"2026-01-05"},{"fin_ym":"202601","item_sn":2,"fin_cls_cd":"SGA","amt":30,"vat_amt":0}]}
            """));
        when(repository.base()).thenReturn(YearMonth.of(2026,1));
        when(repository.load(anyInt())).thenAnswer(c->new CashFlowRepository.Snapshot(YearMonth.of(2026,1),people,payroll,standards,closes,List.of()));
        when(repository.financial(anyString())).thenAnswer(c->financial.getOrDefault(c.getArgument(0),json.createObjectNode()));
        when(repository.receipts(anyString())).thenAnswer(c->receipts.getOrDefault(c.getArgument(0),json.createObjectNode()));
        controller(Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"),ZoneOffset.UTC));
    }
    void controller(Clock clock) {
        var service=new CashFlowService(repository,users,clock,mock(ExcelExportService.class));
        mvc=MockMvcBuilders.standaloneSetup(new CashFlowReadController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
    }
    ResultActions read() throws Exception{return mvc.perform(get("/api/cash-flow").param("year","2026").param("unit","THOUSAND_WON"));}
    @Test void annualContractUsesTwelveMonthsNineLeavesAndThreePeriodTotals() throws Exception {
        read().andExpect(status().isOk()).andExpect(jsonPath("$.data.months.length()").value(12)).andExpect(jsonPath("$.data.periodTotals.length()").value(3))
            .andExpect(jsonPath("$.data.months[0].items.length()").value(9)).andExpect(jsonPath("$.data.months[0].openingBalance").value("1150000000"))
            .andExpect(jsonPath("$.data.months[0].incomeTotal").value("100")).andExpect(jsonPath("$.data.months[0].expenseTotal").value("30"))
            .andExpect(jsonPath("$.data.months[0].closingBalance").value("1150000070")).andExpect(jsonPath("$.data.months[0].shortage").value(false))
            .andExpect(jsonPath("$.data.timezone").value("Asia/Seoul"));
        verify(users).currentUser();
    }
    @Test void undatedIncomeDoesNotEnterAnnualMiscIncomeOrCarriedCash() throws Exception {
        ((com.fasterxml.jackson.databind.node.ObjectNode)financial.get("2026-01").path("items").get(0)).remove("io_dt");
        read().andExpect(status().isOk()).andExpect(jsonPath("$.data.months[0].incomeTotal").value("0"))
            .andExpect(jsonPath("$.data.months[0].expenseTotal").value("30"))
            .andExpect(jsonPath("$.data.months[0].closingBalance").value("1149999970"));
    }
    @Test void publicDefaultsUseSeoulYearAndThousandWon() throws Exception {
        mvc.perform(get("/api/cash-flow")).andExpect(status().isOk()).andExpect(jsonPath("$.data.year").value(2026)).andExpect(jsonPath("$.data.unit").value("THOUSAND_WON"));
    }
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","REG_USER_ID","closedAt","unknown"})
    void unknownServerFieldsNeverReachSources(String field) throws Exception {
        mvc.perform(get("/api/cash-flow").param(field,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value(field));verifyNoInteractions(repository,users);
    }
    @ParameterizedTest @ValueSource(strings={"2026.5","0","10000","-1","","2147483648"})
    void malformedYearHasFieldError(String year) throws Exception {
        mvc.perform(get("/api/cash-flow").param("year",year)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("year"));verifyNoInteractions(repository);
    }
    @Test void invalidUnitIsRejectedBeforeAnyRead() throws Exception {
        mvc.perform(get("/api/cash-flow").param("unit","BAD")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("unit"));verifyNoInteractions(repository);
    }
    @Test void duplicateYearIsRejected() throws Exception {
        mvc.perform(get("/api/cash-flow").param("year","2026","2026")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("year"));verifyNoInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings={"2025","2027"})
    void validCalendarOutsideBusinessScopeIsConflictNotRoleDenial(String year) throws Exception {
        mvc.perform(get("/api/cash-flow").param("year",year)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERIOD_OUT_OF_SCOPE"));verify(repository,never()).load(anyInt());
    }
    @Test void closeExcludesOldSchedulesButFebruaryActualAndKnownTaxStayVerified() throws Exception {
        closes.add(new CloseRow("202601","CLOSE"));financial.clear();
        String tax="{\"taxPayments\":[{\"tax_yr\":\"2026\",\"pay_type_cd\":\"MID\",\"pay_yn\":\"N\",\"pay_due_dt\":\"2026-01-10\",\"pay_amt\":20},{\"tax_yr\":\"2025\",\"pay_type_cd\":\"CNFM\",\"pay_yn\":\"Y\",\"pay_dt\":\"2026-02-03\",\"pay_amt\":20}]}";
        financial.put("2026-01",json.readTree(tax));financial.put("2026-02",json.readTree(tax));
        receipts.put("2026-01",json.readTree("{\"bills\":[{\"bill_id\":\"bill\",\"bill_ym\":\"202601\",\"pay_due_dt\":\"2026-01-10\",\"unpaid\":50}]}"));
        receipts.put("2026-02",json.readTree("{\"receipts\":[{\"rcpt_id\":\"receipt\",\"rcpt_dt\":\"2026-02-03\",\"rcpt_amt\":50}]}"));
        read().andExpect(jsonPath("$.data.months[0].status").value("ACTUAL")).andExpect(jsonPath("$.data.months[0].incomeTotal").value("0"))
            .andExpect(jsonPath("$.data.months[0].expenseTotal").value("0")).andExpect(jsonPath("$.data.months[1].status").value("PLANNED"))
            .andExpect(jsonPath("$.data.months[1].incomeTotal").value("50")).andExpect(jsonPath("$.data.months[1].expenseTotal").value("20"))
            .andExpect(jsonPath("$.data.months[1].verified").value(true));
    }
    @Test void missingFutureReferenceNullifiesOnlyDependentTotalsAndAllFollowingBalances() throws Exception {
        people.add(new Person("person","person","person","FT",null,null,null,null,null,null,"DRCT",null,null,LocalDate.of(2026,1,1),null,"WORK","Y"));
        read().andExpect(jsonPath("$.data.months[10].expenseTotal").value("0")).andExpect(jsonPath("$.data.months[11].expenseTotal").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.months[11].closingBalance").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("$.data.months[11].incomeTotal").value("0"))
            .andExpect(jsonPath("$.data.months[11].issues[0].sourceId").exists());
        assertThat(payroll).isEmpty();assertThat(closes).isEmpty();
        verify(repository).base();verify(repository).load(2026);verify(repository,times(12)).financial(anyString());verify(repository,times(12)).receipts(anyString());verifyNoMoreInteractions(repository);
    }
    @Test void yearBoundaryNeverReinjectsBaseAndPeriodBalancesAreEndpointsNotSums() throws Exception {
        controller(Clock.fixed(Instant.parse("2027-02-01T00:00:00Z"),ZoneOffset.UTC));
        mvc.perform(get("/api/cash-flow").param("year","2027")).andExpect(status().isOk()).andExpect(jsonPath("$.data.months[0].openingBalance").value("1150000070"))
            .andExpect(jsonPath("$.data.periodTotals[2].closingBalance").value("1150000070"));
        read().andExpect(jsonPath("$.data.periodTotals[0].openingBalance").value("1150000000")).andExpect(jsonPath("$.data.periodTotals[0].closingBalance").value("1150000070"));
        mvc.perform(get("/api/cash-flow").param("year","2027")).andExpect(jsonPath("$.data.months[0].openingBalance").value("1150000070"));
    }
    @Test void unitsNeverChangeRawMoneyShortagesOrChart() throws Exception {
        JsonNode first=json.readTree(read().andReturn().getResponse().getContentAsString()).path("data");
        JsonNode second=json.readTree(mvc.perform(get("/api/cash-flow").param("unit","MILLION_WON")).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(second.path("months")).isEqualTo(first.path("months"));assertThat(second.path("chartPoints")).isEqualTo(first.path("chartPoints"));assertThat(second.path("shortages")).isEqualTo(first.path("shortages"));
    }
    @Test void beforeBaseMonthsAreNullAndExcludedFromHalfYearTotals() throws Exception {
        when(repository.base()).thenReturn(YearMonth.of(2026,8));
        read().andExpect(jsonPath("$.data.months[0].status").value("BEFORE_BASE")).andExpect(jsonPath("$.data.months[0].closingBalance").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.periodTotals[0].incomeTotal").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("$.data.periodTotals[1].openingBalance").value("1150000000"));
    }
    @Test void internalFailureNeverLeaksSql() throws Exception {
        when(repository.load(anyInt())).thenThrow(new IllegalStateException("private SQL"));
        read().andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
    }
}
