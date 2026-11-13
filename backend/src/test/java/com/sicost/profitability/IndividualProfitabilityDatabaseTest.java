package com.sicost.profitability;

import com.sicost.common.api.ApiExceptionHandler;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static com.sicost.profitability.IndividualProfitabilityCalculationTest.query;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runner existing-data fixture only; NEVER installs migrations/seeds or modifies shared people. */
@EnabledIfEnvironmentVariable(named="SICOST_COST_TEST_DB_URL",matches=".+")
class IndividualProfitabilityDatabaseTest {
    CostDatabaseSupport db;
    IndividualProfitabilityService service;
    Map<String,List<String>> before;
    @BeforeEach void connect() throws Exception {
        db=new CostDatabaseSupport();before=db.snapshot();
        db.context.registerBean(EstimatedPayrollAdapter.class);
        db.context.registerBean(MonthlyCostCalculation.class);
        db.context.registerBean(TurnkeyRevenueCalculation.class);
        db.context.registerBean(MonthlyCostService.class);
        db.context.registerBean(IndividualProfitabilityCalculation.class);
        db.context.registerBean(IndividualProfitabilityService.class);
        service=db.context.getBean(IndividualProfitabilityService.class);
    }
    @AfterEach void verifyNoBusinessOrAuditWrites() {
        if(db!=null) try { if(before!=null) assertTrue(before.equals(db.snapshot()),"조회로 업무/설정/감사 원천이 변경되었습니다."); }
        finally { db.close(); }
    }
    @Test void realHttpListUsesPostgresqlAndEveryParentSatisfiesCostAndProfitEquations() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new IndividualProfitabilityController(service)).setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(db.json.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))).build();
        mvc.perform(get("/api/individual-profitability")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items").isArray()).andExpect(jsonPath("$.data.summary.companyHeadcount").isNumber())
            .andExpect(jsonPath("$.data.context.period.calculatedAt").isString());
        var data=service.getIndividualProfitability(query());
        assertThat(data.total()).as("runner FX-B137-PERIOD의 실제 조회 인력이 필요합니다.").isPositive();
        for(var p:data.items()) {
            var m=p.metrics();
            if(m.totalCost()!=null) assertThat(new BigDecimal(m.totalCost())).isEqualByComparingTo(new BigDecimal(m.personalCost())
                .add(new BigDecimal(m.teamExpense())).add(new BigDecimal(m.projectExpense())));
            if(m.revenue()!=null && m.profit()!=null) assertThat(new BigDecimal(m.profit())).isEqualByComparingTo(new BigDecimal(m.revenue()).subtract(new BigDecimal(m.totalCost())));
            var detail=service.getIndividualProfitabilityPerson(p.personId(),query());
            assertThat(detail.months()).extracting(MonthlyPersonCost::month).isSorted();
            assertThat(detail.person().personId()).isEqualTo(p.personId());
        }
    }
    @Test void realPersonDetailKeepsClosedCompositeRowsAndOriginalAmountsInBothViews() {
        var json = db.json.copy().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var mvc = MockMvcBuilders.standaloneSetup(new IndividualProfitabilityController(service)).setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        db.read(() -> {
            var snapshot = service.snapshot(query());
            var visible = service.project(snapshot, query(), true).items().stream().map(IndividualProfitabilityPerson::personId).toList();
            var grouped = new LinkedHashMap<String,List<StoredCost>>();
            for (var row : snapshot.sources().storedCosts()) {
                if (!visible.contains(row.personId()) || !snapshot.sources().protectedMonth(row.month())) continue;
                grouped.computeIfAbsent(row.personId() + ":" + row.month() + ":" + row.projectId(), ignored -> new ArrayList<>()).add(row);
            }
            var fixture = grouped.values().stream().filter(rows -> rows.size() >= 2).findFirst()
                .orElseThrow(() -> new AssertionError("runner FX-B137-CLOSE: 조회 대상의 같은 마감월·프로젝트 두 투입 사본이 필요합니다. 자동 삽입하지 않습니다."));
            var selected = fixture.get(0);
            try {
                var cumulative = mvc.perform(get("/api/individual-profitability/{personId}", selected.personId()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.person.personId").value(selected.personId()))
                    .andReturn().getResponse().getContentAsByteArray();
                var average = mvc.perform(get("/api/individual-profitability/{personId}", selected.personId()).param("view", "MONTHLY_AVERAGE"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
                var originalMonths = json.readTree(cumulative).at("/data/months");
                assertThat(json.readTree(average).at("/data/months")).isEqualTo(originalMonths);
                var month = java.util.stream.StreamSupport.stream(originalMonths.spliterator(), false)
                    .filter(item -> item.get("month").asText().equals(selected.month())).findFirst().orElseThrow();
                for (var stored : fixture) {
                    var actual = java.util.stream.StreamSupport.stream(month.get("rows").spliterator(), false)
                        .filter(row -> row.get("projectId").asText().equals(stored.projectId())
                            && row.get("assignmentId").asText().equals(stored.assignmentId())).findFirst().orElseThrow();
                    assertThat(actual).isEqualTo(json.valueToTree(stored.toRow()));
                }
            } catch (Exception error) { throw new AssertionError("실제 사람 상세 원장 검증 실패", error); }
            return true;
        });
    }
    @Test void sameSnapshotSupportsBothViewsAllPagesAndDetailWithoutWritingOrTakingWriteLocks() {
        db.read(()->{
            assertThat(db.sql.queryForObject("SHOW transaction_isolation",String.class)).isEqualTo("repeatable read");
            assertThat(db.sql.queryForObject("SHOW transaction_read_only",String.class)).isEqualTo("on");
            String snapshot=db.sql.queryForObject("SELECT pg_current_snapshot()::text",String.class);
            var materialized=service.snapshot(query());
            var cumulative=service.project(materialized,query(),true);
            var average=service.project(materialized,query("view","MONTHLY_AVERAGE"),true);
            var excluded=service.project(materialized,query("includeTurnkey","false"),true);
            var outside=service.project(materialized,query("page","2147483647"),false);
            assertThat(outside.items()).isEmpty();assertThat(outside.summary()).isEqualTo(cumulative.summary());
            assertThat(outside.totals()).isEqualTo(cumulative.totals());
            assertThat(average.summary().companyUtilization()).isEqualTo(cumulative.summary().companyUtilization());
            assertThat(excluded.summary().companyUtilization()).isEqualTo(cumulative.summary().companyUtilization());
            assertThat(db.sql.queryForObject("SELECT pg_current_snapshot()::text",String.class)).isEqualTo(snapshot);
            assertThat(db.sql.queryForObject("SELECT count(*) FROM pg_locks WHERE pid=pg_backend_pid() AND mode IN ('RowExclusiveLock','ShareRowExclusiveLock','ExclusiveLock','AccessExclusiveLock') AND relation IS NOT NULL",Integer.class)).isZero();
            return true;
        });
    }
    @Test void realPeriodAverageDenominatorsAndFilterWideTotalsMatchOriginalMonthlyEvidence() {
        db.read(() -> {
            var snapshot = service.snapshot(query());
            var cumulative = service.project(snapshot, query(), true);
            var averaged = service.project(snapshot, query("view", "MONTHLY_AVERAGE"), true);
            assertThat(cumulative.items()).as("runner FX-B137-PERIOD의 실제 인력이 필요합니다.").isNotEmpty();
            for (var person : cumulative.items()) {
                var average = averaged.items().stream().filter(p -> p.personId().equals(person.personId())).findFirst().orElseThrow();
                assertThat(person.workedMonths()).isEqualTo((int) person.monthBasis().stream().filter(ProfitabilityMonthBasis::averageMonthIncluded).count());
                assertThat(person.employmentMonths()).isEqualTo((int) person.monthBasis().stream().filter(ProfitabilityMonthBasis::employmentMonthIncluded).count());
                assertThat(person.utilizedMonths()).isEqualTo((int) person.monthBasis().stream().filter(ProfitabilityMonthBasis::utilizedMonthIncluded).count());
                assertThat(average.utilization()).isEqualTo(person.utilization());
                var original = db.json.valueToTree(person.metrics());
                var projected = db.json.valueToTree(average.metrics());
                for (String field : List.of("pay", "allowance", "companyInsurance", "retirementReserve", "personalCost", "teamExpense", "projectExpense", "revenue")) {
                    var value = original.get(field);
                    if (person.workedMonths() == 0 || value == null || value.isNull()) {
                        assertThat(projected.get(field).isNull()).as("불완전/N0 금액은 0이 아닙니다: " + field).isTrue();
                    } else assertThat(projected.get(field).asText()).isEqualTo(new BigDecimal(value.asText())
                        .divide(BigDecimal.valueOf(person.workedMonths()), 0, java.math.RoundingMode.DOWN).toPlainString());
                }
                var detail = service.detailMonths(snapshot, person.personId(), true);
                assertThat(detail).isNotEmpty();
                assertThat(detail).allSatisfy(month -> {
                    if (month.completeness().allocationComplete() && month.completeness().costComplete()
                        && month.laborSource().laborCost() != null && !month.rows().isEmpty()) {
                        assertThat(month.rows().stream().map(MonthlyCostRow::laborAllocated).filter(Objects::nonNull).map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add))
                            .isEqualByComparingTo(month.laborSource().laborCost());
                    }
                });
            }
            for (var data : List.of(cumulative, averaged)) {
                for (String field : List.of("revenue", "personalCost", "teamExpense", "projectExpense", "totalCost", "profit")) {
                    var values = data.items().stream().map(p -> db.json.valueToTree(p.metrics()).get(field)).toList();
                    var total = db.json.valueToTree(data.totals()).get(field);
                    // Revenue sums eligible nonblank parents only, but unknown revenue poisons the total.
                    boolean incomplete = "revenue".equals(field)
                        ? data.items().stream().anyMatch(p -> !p.completeness().revenueComplete())
                        : values.stream().anyMatch(com.fasterxml.jackson.databind.JsonNode::isNull);
                    if (incomplete) assertThat(total.isNull()).isTrue();
                    else assertThat(new BigDecimal(total.asText())).isEqualByComparingTo(values.stream()
                        .filter(v -> !v.isNull()).map(v -> new BigDecimal(v.asText())).reduce(BigDecimal.ZERO, BigDecimal::add));
                }
            }
            assertThat(averaged.calculationBasis()).isEqualTo(cumulative.calculationBasis());
            return true;
        });
    }
}
