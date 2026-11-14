package com.sicost.apiops;

import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.assignments.AssignmentMonthlyService.*;
import com.sicost.profitability.MonthlyCostDtos.*;
import java.math.BigDecimal;
import java.time.*;
import com.sicost.common.assignments.AssignmentMonthlyService.Month;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RateRenegotiationQueryTest {
    RateRenegotiationRepository repository;
    RateRenegotiationService service;
    MockMvc mvc;
    final List<RateRenegotiationRepository.Candidate> candidates = new ArrayList<>();
    final List<MonthlyCostRow> costs = new ArrayList<>();
    BigDecimal target;
    static BigDecimal d(String value) { return value == null ? null : new BigDecimal(value); }
    @BeforeEach void setup() {
        repository = mock(RateRenegotiationRepository.class);
        service = new RateRenegotiationService(repository, new RateRenegotiationCalculator());
        mvc = MockMvcBuilders.standaloneSetup(new RateRenegotiationController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
        candidates.clear(); costs.clear(); target = d("20");
        add("a", 19, "1.00", "5400000", "ACT", "ADD");
        when(repository.load()).thenAnswer(call -> snapshot());
    }
    void add(String id, int days, String mm, String labor, String status, String kind) {
        var start = LocalDate.of(2080, 7, 1); var end = LocalDate.of(2080, 7, 15).plusDays(days);
        var source = new Source(id, "p", "dev", "DEV", start, end, d("1"), d("6000000"), kind, null, null, 0, "N", status);
        candidates.add(new RateRenegotiationRepository.Candidate(source,
            new Month("2080-07", "p", "dev", 31, "1.00", mm, "6000000", "6000000", "N")));
        costs.add(new MonthlyCostRow("dev", "p", id, "208007", mm, "0.00", "6000000", labor,
            null, null, null, null, null, "DRCT", "MO", null, "N", "프로젝트", "DEV", kind, null, false, false, true, false, null, null, null, null));
    }
    RateRenegotiationRepository.Snapshot snapshot() {
        var period = new ProfitabilityPeriod(2080, "208001", "208007", "208007", OffsetDateTime.parse("2080-07-15T09:00:00+09:00"), false);
        var person = new Person("dev", "관리", "인력", "FL", null, null, null, null, null, null, "DRCT", null, null, null, null, "WORK", "Y");
        var pay = new Payroll("208007", "dev", null, null, null, null, null, null, null, null, null, "FT", "DRCT", null, null, null, null, null);
        var project = new Project("p", "프로젝트", "MO", "ING", null, LocalDate.of(2080,1,1), LocalDate.of(2082,1,1), null, null, null, null);
        var s = new Sources(period, "admin", LocalDate.of(2080,7,15), List.of(person), List.of(), List.of(), List.of(pay), List.of(), List.of(), List.of(project),
            List.of(), List.of(), List.of(), target == null ? List.of() : List.of(new Standard("TGT_MRGN_RT", "208001", target)), List.of(), List.of(), List.of());
        return new RateRenegotiationRepository.Snapshot(s, List.copyOf(candidates), new MonthlyCostsData(period,
            List.of(new MonthlyPersonCost("dev", "208007", List.copyOf(costs), null, null, null, null, "1.00", false, false))));
    }
    @Test void publicHttpReturnsApprovedExampleWithExactEnvelopeAndSnapshotEmployeeType() throws Exception {
        mvc.perform(get("/api/rate-renegotiations")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.baseMonth").value("2080-07"))
            .andExpect(jsonPath("$.data.appliedIncreaseRate").value("5"))
            .andExpect(jsonPath("$.data.items[0].marginRate").value("10.0"))
            .andExpect(jsonPath("$.data.items[0].targetUnitPrice").value("6750000"))
            .andExpect(jsonPath("$.data.items[0].requiredIncreaseRate").value("12.5"))
            .andExpect(jsonPath("$.data.items[0].targetAnnualGain").value("9000000"))
            .andExpect(jsonPath("$.data.items[0].annualGain").value("3600000"))
            .andExpect(jsonPath("$.data.items[0].employeeType").value("FT"))
            .andExpect(jsonPath("$.data.pagination.size").value(50));
        verify(repository).load(); verifyNoMoreInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings={"month=2080-07", "year=2080", "actor=admin", "userId=admin", "size=30", "page=0", "page=2147483648", "sort=employeeType", "direction=desc", "employeeType=BAD", "increaseRate=NaN", "increaseRate=word", "increaseRate=1.234", "page=1&page=1", "renewalWindow=30", "marginBelow=16"})
    void rejectsEveryUnknownDuplicateOrMalformedQueryBeforeReading(String query) throws Exception {
        mvc.perform(get("/api/rate-renegotiations?" + query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").exists());
        verifyNoInteractions(repository);
    }
    @Test void getBodyIsRejectedBeforeReading() throws Exception {
        mvc.perform(get("/api/rate-renegotiations").content("{}" )).andExpect(status().isBadRequest()); verifyNoInteractions(repository);
    }
    @ParameterizedTest @ValueSource(strings={"-1", "51", ""})
    void clampsOutOfRangeAndEmptyIncrease(String input) throws Exception {
        mvc.perform(get("/api/rate-renegotiations").param("increaseRate", input)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.appliedIncreaseRate").value(input.equals("51") ? "50" : "0"));
    }
    @Test void twoAssignmentsOfOneDeveloperRemainSeparateAndWholeSummarySurvivesPageChange() throws Exception {
        add("b", 30, "0.52", "5400000", "ACT", "ADD");
        mvc.perform(get("/api/rate-renegotiations")).andExpect(jsonPath("$.data.summary.personCount").value(1))
            .andExpect(jsonPath("$.data.summary.assignmentCount").value(2)).andExpect(jsonPath("$.data.summary.annualGain").value("7200000"))
            .andExpect(jsonPath("$.data.items[1].monthlyMm").value("0.52")).andExpect(jsonPath("$.data.items[1].contractMm").value("1.00"));
        mvc.perform(get("/api/rate-renegotiations?page=2147483647")).andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.pagination.total").value(2)).andExpect(jsonPath("$.data.summary.annualGain").value("7200000"));
    }
    @Test void missingCostIsNotZeroAndUnrelatedExpenseMissingDoesNotBlockGoodLabor() throws Exception {
        add("b", 20, "1.00", null, "ACT", "ADD");
        mvc.perform(get("/api/rate-renegotiations")).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.unevaluatedCount").value(1)).andExpect(jsonPath("$.data.unevaluatedAssignments[0].assignmentId").value("b"))
            .andExpect(jsonPath("$.data.unevaluatedAssignments[0].field").value("monthlyLaborCost"));
    }
    @ParameterizedTest @ValueSource(strings={"100", "101", "-1"})
    void unavailableTargetPreservesCurrentAndSimulation(String value) throws Exception {
        target = d(value);
        mvc.perform(get("/api/rate-renegotiations")).andExpect(jsonPath("$.data.items[0].targetUnitPrice").isEmpty())
            .andExpect(jsonPath("$.data.items[0].marginRate").value("10.0")).andExpect(jsonPath("$.data.items[0].annualGain").value("3600000"))
            .andExpect(jsonPath("$.data.summary.targetAnnualGain").isEmpty()).andExpect(jsonPath("$.data.issues[0].code").value("TARGET_UNAVAILABLE"));
    }
    @Test void canceledFreeZeroMmAndExpiredAssignmentsAreExcluded() throws Exception {
        add("cancel", 1, "1.00", "1", "CNCL", "ADD"); add("free", 1, "1.00", "1", "ACT", "FREE");
        add("zero", 1, "0.00", "1", "ACT", "ADD"); add("expired", -1, "1.00", "1", "ACT", "ADD");
        mvc.perform(get("/api/rate-renegotiations?marginBelow=ALL")).andExpect(jsonPath("$.data.pagination.total").value(1));
    }
    @ParameterizedTest @ValueSource(ints={0,30,31,92,183,366})
    void renewalBoundaryIsInclusive(int day) throws Exception {
        candidates.clear(); costs.clear(); add("edge", day, "1.00", "5400000", "ACT", "ADD");
        mvc.perform(get("/api/rate-renegotiations?renewalWindow=" + (day > 183 ? "366" : "183")))
            .andExpect(jsonPath("$.data.pagination.total").value(1)).andExpect(jsonPath("$.data.items[0].daysUntilRenewal").value(day));
    }
    @Test void strictThresholdAndWindowExcludeValuesOutsideBoundary() throws Exception {
        add("later",184,"1.00","5400000","ACT","ADD"); add("threshold",19,"1.00","5100000","ACT","ADD");
        mvc.perform(get("/api/rate-renegotiations")).andExpect(jsonPath("$.data.pagination.total").value(1));
    }
    @Test void numericSortStableByAssignmentAndNullLastInBothDirections() throws Exception {
        add("b",19,"1.00","5400000","ACT","ADD");
        mvc.perform(get("/api/rate-renegotiations?direction=DESC")).andExpect(jsonPath("$.data.items[0].assignmentId").value("a"));
    }
    @Test void sourceFailureIsGenericAndNotSqlOrPayrollLeak() throws Exception {
        when(repository.load()).thenThrow(new IllegalStateException("SELECT salary secret"));
        mvc.perform(get("/api/rate-renegotiations")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("salary"))));
    }
}
