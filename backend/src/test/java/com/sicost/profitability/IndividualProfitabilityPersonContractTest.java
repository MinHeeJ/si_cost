package com.sicost.profitability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.shared.SharedQueriesService;
import com.sicost.shared.SharedQueryDtos.Items;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static com.sicost.profitability.MonthlyCostCalculationTest.*;
import static com.sicost.profitability.IndividualProfitabilityCalculationTest.*;
import static com.sicost.profitability.IndividualProfitabilityContractTest.code;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual controller -> service -> monthly calculation, replacing only persistent source adapters. */
class IndividualProfitabilityPersonContractTest {
    MonthlyCostRepository repository;
    MonthlyCostMapper mapper;
    SharedQueriesService shared;
    IndividualProfitabilityController controller;
    MockMvc mvc;
    Sources source;
    final com.fasterxml.jackson.databind.ObjectMapper json = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach void setup() {
        repository = mock(MonthlyCostRepository.class);
        mapper = mock(MonthlyCostMapper.class);
        shared = mock(SharedQueriesService.class);
        var payroll = new EstimatedPayrollAdapter();
        var turnkey = new TurnkeyRevenueCalculation();
        var monthly = new MonthlyCostService(repository, new MonthlyCostCalculation(payroll), turnkey, payroll);
        var service = new IndividualProfitabilityService(monthly, new IndividualProfitabilityCalculation(turnkey), shared, mapper);
        controller = new IndividualProfitabilityController(service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        source = sources(person("FT"), List.of(pay("FT", "4970000")),
            List.of(assignment("b", "0.40"), assignment("a", "0.60")), List.of(project("MO", "ACT")));
        when(repository.loadYear(any())).thenAnswer(call -> source);
        when(shared.getCodes(anyString(), eq(true))).thenAnswer(call -> new Items<>(switch ((String) call.getArgument(0)) {
            case "JOB" -> List.of(code("JOB", "직무"));
            case "EMP_TYPE" -> List.of(code("FT", "정규직"));
            default -> List.of();
        }));
    }
    private Sources protectedSource(List<StoredCost> stored) {
        return new Sources(source.period(), source.actor(), source.asOfDate(), source.people(), source.prices(), source.states(),
            source.payroll(), source.assignments(), source.projectMonths(), source.projects(), source.projectParts(), source.turnkey(),
            stored, source.standards(), source.parts(), List.of(new com.sicost.shared.SharedQueryDtos.CloseRow(MONTH, "CLOSE")), source.allowanceCodes());
    }
    private StoredCost stored(String assignmentId) {
        var audit = LocalDateTime.of(2026, 1, 31, 10, 0);
        return new StoredCost(MONTH, "actual", "project", assignmentId, n("0.50"), n("0.00"), n("1000"), n("500"),
            n("20"), n("10"), n("30"), "ALLOC", "MO", "historical-part", "Y", "actor", audit, null, null,
            null, null, null); // Deliberately absent display masters: never discard a protected physical row.
    }
    private JsonNode detail(String view) throws Exception {
        var bytes = mvc.perform(get("/api/individual-profitability/actual").param("view", view))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.person.personId").value("actual"))
            .andExpect(jsonPath("$.data.context.view").value(view)).andReturn().getResponse().getContentAsByteArray();
        return json.readTree(bytes).get("data");
    }
    @Test void closedSameProjectTwoAssignmentsRemainTwoOriginalRowsInBothViewsWithNoSourceChanges() throws Exception {
        source = protectedSource(List.of(stored("b"), stored("a")));
        var before = json.valueToTree(source);
        var cumulative = detail("CUMULATIVE");
        var average = detail("MONTHLY_AVERAGE");
        assertThat(average.get("months")).isEqualTo(cumulative.get("months"));
        var month = cumulative.get("months").get(0);
        assertThat(month.get("rows")).hasSize(2);
        assertThat(month.at("/rows/0/assignmentId").asText()).isEqualTo("a");
        assertThat(month.at("/rows/1/assignmentId").asText()).isEqualTo("b");
        for (var row : month.get("rows")) {
            assertThat(row.get("fixed").asText()).isEqualTo("Y");
            assertThat(row.get("projectName").isNull()).isTrue();
            assertThat(row.get("assignmentJobCode").isNull()).isTrue();
            assertThat(row.get("projectSalesPartId").asText()).isEqualTo("historical-part");
            assertThat(n(row.get("totalCost").asText())).isEqualByComparingTo(n(row.get("laborAllocated").asText())
                .add(n(row.get("teamExpense").asText())).add(n(row.get("projectExpense").asText())));
        }
        assertThat(month.at("/laborSource/source").asText()).isEqualTo("마감 사본");
        assertThat(month.at("/laborSource/laborCost").asText()).isEqualTo("1000");
        assertThat(cumulative.get("issues")).isNotEmpty();
        assertThat(json.<JsonNode>valueToTree(source)).isEqualTo(before);
        verify(repository, times(2)).loadYear(null); verifyNoMoreInteractions(repository); verifyNoInteractions(mapper);
    }
    @Test void closedMissingSnapshotIsNormalExplicitUnknownNotCurrentPayrollBackfillOrZero() throws Exception {
        source = protectedSource(List.of());
        mvc.perform(get("/api/individual-profitability/actual"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.person.completeness.costComplete").value(false))
            .andExpect(jsonPath("$.data.person.metrics.totalCost").value(nullValue()))
            .andExpect(jsonPath("$.data.months[0].rows").isEmpty())
            .andExpect(jsonPath("$.data.months[0].laborSource.laborCost").value(nullValue()))
            .andExpect(jsonPath("$.data.months[0].completeness.issues[0].source").value("TB_CO_MON_COST"))
            .andExpect(jsonPath("$.data.issues").isNotEmpty());
        verify(repository).loadYear(null); verifyNoMoreInteractions(repository);
    }
    @Test void validZeroSnapshotIsCompleteAndNotConfusedWithMissingSnapshot() throws Exception {
        var base = stored("zero");
        source = protectedSource(List.of(new StoredCost(base.month(), base.personId(), base.projectId(), base.assignmentId(),
            base.assignmentMm(), base.freeMm(), n("0"), n("0"), n("0"), n("0"), n("0"), base.costClass(), base.contractType(),
            base.projectSalesPartId(), base.fixed(), base.regUserId(), base.regDttm(), base.modUserId(), base.modDttm(),
            base.projectName(), base.assignmentJobCode(), base.inputType())));
        mvc.perform(get("/api/individual-profitability/actual"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.person.completeness.costComplete").value(true))
            .andExpect(jsonPath("$.data.months[0].rows.length()").value(1))
            .andExpect(jsonPath("$.data.months[0].rows[0].totalCost").value("0"))
            .andExpect(jsonPath("$.data.person.metrics.totalCost").value("0"));
    }
    @Test void nonexistentIdIs404AndSameContextFiltersAreNotSilentlyDropped() throws Exception {
        mvc.perform(get("/api/individual-profitability/nonexistent"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"))
            .andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/api/individual-profitability/actual").param("name", "not-the-name"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));
        mvc.perform(get("/api/individual-profitability/actual").param("page", "1"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("page"));
    }
    @Test void averageUsesOriginalMonthAmountsWhileParentUsesItsOwnWorkingMonthDivisor() throws Exception {
        source = annual(source, "202602", List.of(payAt(MONTH, "FT", "101"), payAt("202602", "FT", "102")),
            List.of(at("202602", "later", "0.50"), at(MONTH, "b", "0.40"), at(MONTH, "a", "0.40")));
        var cumulative = detail("CUMULATIVE"); var average = detail("MONTHLY_AVERAGE");
        assertThat(average.get("months")).isEqualTo(cumulative.get("months"));
        assertThat(average.at("/person/metrics/pay")).isNotEqualTo(cumulative.at("/person/metrics/pay"));
        assertThat(average.at("/months/0/month").asText()).isEqualTo(MONTH);
        assertThat(average.at("/months/1/month").asText()).isEqualTo("202602");
        var idle = java.util.stream.StreamSupport.stream(average.at("/months/0/rows").spliterator(), false)
            .filter(row -> "IDLE".equals(row.get("assignmentId").asText())).findFirst().orElseThrow();
        assertThat(n(idle.get("profit").asText())).isNegative();
    }
    @Test void controllerOffersNoMoneyWriteRouteAndRejectsMutationBeforeServiceInvocation() throws Exception {
        // Default MVC 405 handling isolates mapping ownership from the legacy global error translator.
        var mappings = MockMvcBuilders.standaloneSetup(controller).build();
        mappings.perform(put("/api/individual-profitability/actual").contentType("application/json").content("{\"pay\":\"1\"}"))
            .andExpect(status().isMethodNotAllowed());
        mappings.perform(delete("/api/individual-profitability/actual")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(repository, mapper, shared);
    }
    @Test void sourceFailureDoesNotLeakSqlSalaryOrStackTrace() throws Exception {
        when(repository.loadYear(any())).thenThrow(new IllegalStateException("SELECT secret payroll salary"));
        mvc.perform(get("/api/individual-profitability/actual"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SELECT"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("salary"))));
    }
}
