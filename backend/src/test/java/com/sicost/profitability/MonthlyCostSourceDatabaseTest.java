package com.sicost.profitability;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.developers.DeveloperRepository;
import com.sicost.shared.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses a runner-provided existing-data copy. Missing required cases fail with a fixture diagnosis. */
@EnabledIfEnvironmentVariable(named = "SICOST_COST_TEST_DB_URL", matches = ".+")
class MonthlyCostSourceDatabaseTest {
    CostDatabaseSupport db;
    MonthlyCostMapper mapper;
    MonthlyCostRepository repository;
    DeveloperRepository developers;
    Range range;
    Map<String, List<String>> before;

    @BeforeEach void connectWithoutInstallingOrSeeding() throws Exception {
        db = new CostDatabaseSupport();
        mapper = db.context.getBean(MonthlyCostMapper.class);
        repository = db.context.getBean(MonthlyCostRepository.class);
        developers = db.context.getBean(DeveloperRepository.class);
        range = db.existingRange();
        before = db.snapshot();
    }
    @AfterEach void preserveEverySourceAndAuditValue() {
        if (db != null) {
            try { if (before != null) assertTrue(before.equals(db.snapshot()), "원천/초기 기준값/감사시각이 변경되었습니다."); }
            finally { db.close(); }
        }
    }

    @Test void physicalKeysAndNumericTypesMatchTheExistingSchema() {
        List<String> keys = db.sql.queryForList("""
            SELECT a.attname FROM pg_index i
            JOIN pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=ANY(i.indkey)
            WHERE i.indrelid='tb_co_mon_cost'::regclass AND i.indisprimary
            ORDER BY array_position(i.indkey::smallint[],a.attnum)
            """, String.class);
        assertThat(keys).containsExactly("cost_ym", "dev_id", "proj_id", "asgn_id");
        assertThat(db.sql.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_co_mon_cost'", String.class))
            .doesNotContain("total_cost", "profit", "input_type", "input_type_cd", "job", "job_cd");
        assertThat(db.sql.queryForList("""
            SELECT column_name FROM information_schema.columns
            WHERE table_schema=current_schema() AND table_name='tb_hr_mon_pay'
            """, String.class)).doesNotContain("fix_yn");
        for (String column : List.of("asgn_mm", "free_mm")) {
            var value = db.sql.queryForMap("SELECT numeric_precision,numeric_scale FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_co_mon_cost' AND column_name=?", column);
            assertThat(((Number)value.get("numeric_precision")).intValue()).isEqualTo(6);
            assertThat(((Number)value.get("numeric_scale")).intValue()).isEqualTo(2);
        }
        var amount = db.sql.queryForMap("SELECT numeric_precision,numeric_scale FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_co_mon_cost' AND column_name='labor_alloc_amt'");
        assertThat(((Number)amount.get("numeric_precision")).intValue()).isEqualTo(15);
        assertThat(((Number)amount.get("numeric_scale")).intValue()).isZero();
    }

    @Test void snapshotProjectionKeepsAllFourKeysAllAmountsAndOriginalAuditValues() {
        var rows = mapper.storedCosts(range);
        assertThat(rows).as("기존 원가 사본 fixture 필요").isNotEmpty();
        long count = db.sql.queryForObject("SELECT count(*) FROM tb_co_mon_cost WHERE cost_ym>=? AND cost_ym<=?", Long.class, range.fromMonth(), range.toMonth());
        assertThat(rows).hasSize((int)count);
        assertThat(rows.stream().map(StoredCost::key).distinct().count()).isEqualTo(count);
        for (var row : rows) {
            db.sql.query("SELECT * FROM tb_co_mon_cost WHERE cost_ym=? AND dev_id=? AND proj_id=? AND asgn_id=?", r -> {
                assertThat(row.assignmentMm()).isEqualByComparingTo(r.getBigDecimal("asgn_mm"));
                assertThat(row.freeMm()).isEqualByComparingTo(r.getBigDecimal("free_mm"));
                assertThat(row.personalRevenue()).isEqualByComparingTo(r.getBigDecimal("sales_amt"));
                assertThat(row.laborAllocated()).isEqualByComparingTo(r.getBigDecimal("labor_alloc_amt"));
                assertThat(row.teamExpense()).isEqualByComparingTo(r.getBigDecimal("team_exps_amt"));
                assertThat(row.projectExpense()).isEqualByComparingTo(r.getBigDecimal("proj_exps_amt"));
                assertThat(row.directExpense()).isEqualByComparingTo(r.getBigDecimal("drct_exps_amt"));
                assertThat(row.costClass()).isEqualTo(r.getString("cost_cls_cd"));
                assertThat(row.contractType()).isEqualTo(r.getString("ctrt_type_cd"));
                assertThat(row.projectSalesPartId()).isEqualTo(r.getString("part_id"));
                assertThat(row.fixed()).isEqualTo(r.getString("fix_yn"));
                assertThat(row.regUserId()).isEqualTo(r.getString("reg_user_id"));
                assertThat(row.regDttm()).isEqualTo(r.getObject("reg_dttm", LocalDateTime.class));
                assertThat(row.modUserId()).isEqualTo(r.getString("mod_user_id"));
                assertThat(row.modDttm()).isEqualTo(r.getObject("mod_dttm", LocalDateTime.class));
            }, row.month(), row.personId(), row.projectId(), row.assignmentId());
            var projected = row.toRow();
            assertThat(projected.key()).isEqualTo(row.key());
            BigDecimal cost = row.laborAllocated().add(row.directExpense());
            assertThat(projected.totalCost()).isEqualTo(money(cost));
            assertThat(projected.profit()).isEqualTo(money(row.personalRevenue().subtract(cost)));
            assertThat(projected.rowGenerated()).isFalse();
            assertThat(projected.fixed()).isEqualTo(row.fixed());
        }
    }

    @Test void differentAssignmentsAndSyntheticRowsRemainSeparateEvenWithoutDisplayMasters() {
        var rows = mapper.storedCosts(range);
        var groups = new HashMap<List<String>, Set<String>>();
        rows.forEach(r -> groups.computeIfAbsent(List.of(r.month(), r.personId(), r.projectId()), k -> new HashSet<>()).add(r.assignmentId()));
        assertThat(groups.values()).as("같은 사람/월/프로젝트의 복수 ASGN_ID fixture 필요").anyMatch(s -> s.size() > 1);
        assertThat(rows).as("합성 IDLE/INDRCT 사본 fixture 필요").anyMatch(r ->
            Set.of("IDLE", "INDRCT").contains(r.projectId()) && r.projectId().equals(r.assignmentId()) && r.projectName() == null);
        assertThat(rows).as("실주 실제 ID의 IDLE 분류 fixture 필요").anyMatch(r ->
            r.costClass().equals("IDLE") && !Set.of("IDLE", "INDRCT").contains(r.projectId()));
        var orphanKeys = db.sql.queryForList("SELECT c.cost_ym||':'||c.dev_id||':'||c.proj_id||':'||c.asgn_id FROM tb_co_mon_cost c LEFT JOIN tb_hr_dev d ON d.dev_id=c.dev_id WHERE d.dev_id IS NULL", String.class);
        var actualKeys = rows.stream().map(r -> String.join(":", r.month(), r.personId(), r.projectId(), r.assignmentId())).toList();
        assertThat(actualKeys).containsAll(orphanKeys);
    }

    @Test void uploadedPayrollClassificationNullableReferencesAndAmountsAreNotMasterDefaults() {
        var rows = mapper.payroll(range);
        assertThat(rows).as("저장 월급여 fixture 필요").isNotEmpty();
        for (var row : rows) {
            var raw = db.sql.queryForMap("SELECT * FROM tb_hr_mon_pay WHERE pay_ym=? AND dev_id=?", row.month(), row.personId());
            assertThat(row.employeeType()).isEqualTo(raw.get("emp_type_cd"));
            assertThat(row.directClass()).isEqualTo(raw.get("drct_cls_cd"));
            assertThat(row.pay()).isEqualTo(raw.get("pay_amt"));
            assertThat(row.allowance()).isEqualTo(raw.get("alwnc_amt"));
            assertThat(row.companyInsurance()).isEqualTo(raw.get("insr_cmpny_amt"));
            assertThat(row.retirementReserve()).isEqualTo(raw.get("retire_rsv_amt"));
            assertThat(row.selfInsurance()).isEqualTo(raw.get("insr_self_amt"));
            assertThat(row.incomeTax()).isEqualTo(raw.get("incm_tax_amt"));
            assertThat(row.withholdingTax()).isEqualTo(raw.get("wthld_tax_amt"));
            assertThat(row.netPay()).isEqualTo(raw.get("real_pay_amt"));
            assertThat(row.vendorSupply()).isEqualTo(raw.get("vndr_sply_amt"));
            assertThat(row.uploadId()).isEqualTo(Objects.toString(raw.get("upld_id"), null));
        }
        assertThat(rows).as("현재 사람 분류와 다른 급여 snapshot fixture 필요").anyMatch(p -> developers.historicalPeople().stream().anyMatch(d ->
            d.personId().equals(p.personId()) && (!d.employeeType().equals(p.employeeType()) || !d.directClass().equals(p.directClass()))));
    }

    @Test void stoppedAndRetiredHistoricalPeopleAreNotFilteredAndRetirementMonthIsIncluded() {
        var people = developers.historicalPeople();
        assertThat(people).hasSize(db.sql.queryForObject("SELECT count(*) FROM tb_hr_dev", Integer.class));
        assertThat(people).as("중지된 과거 원가 인력 fixture 필요").anyMatch(p -> "N".equals(p.useYn())
            && mapper.storedCosts(range).stream().anyMatch(r -> r.personId().equals(p.personId()) && p.employed(r.month())));
        var retired = people.stream().filter(p -> p.retireDate() != null && mapper.payroll(range).stream()
            .anyMatch(r -> r.personId().equals(p.personId()) && parseMonth(r.month()).equals(YearMonth.from(p.retireDate())))).toList();
        assertThat(retired).as("퇴사월 업로드 급여 fixture 필요").isNotEmpty();
        for (var person : retired) {
            String month = YearMonth.from(person.retireDate()).toString().replace("-", "");
            assertThat(person.employed(month)).isTrue();
            assertThat(person.employed(YearMonth.from(person.retireDate()).plusMonths(1).toString().replace("-", ""))).isFalse();
        }
    }

    @Test void historicalPricesAndLeavePeriodsComeFromEffectiveIntervalsNotCurrentStatus() {
        var prices = developers.historicalPrices(range);
        assertThat(prices).hasSize(db.sql.queryForObject("SELECT count(*) FROM tb_hr_dev_prc WHERE aply_bgn_ym<=? AND (aply_end_ym IS NULL OR aply_end_ym>=?)", Integer.class, range.toMonth(), range.fromMonth()));
        for (var price : prices) {
            var raw = db.sql.queryForMap("SELECT * FROM tb_hr_dev_prc WHERE dev_id=? AND aply_bgn_ym=?", price.personId(), price.fromMonth());
            assertThat(price.monthlyPay()).isEqualTo(raw.get("mon_pay_amt"));
            assertThat(price.monthlyRate()).isEqualTo(raw.get("mon_uprc"));
            assertThat(price.toMonth()).isEqualTo(raw.get("aply_end_ym"));
            assertThat(price.effective(price.fromMonth())).isTrue();
        }
        var states = developers.historicalStates(range);
        assertThat(states).hasSize(db.sql.queryForObject("SELECT count(*) FROM tb_hr_dev_stat WHERE bgn_dt<=? AND (end_dt IS NULL OR end_dt>=?)", Integer.class, range.toDate(), range.fromDate()));
        for (var state : states) {
            assertThat(state.includes(state.fromDate())).isTrue();
            if (state.toDate() != null) assertThat(state.includes(state.toDate().plusDays(1))).isFalse();
        }
        assertThat(states).as("과거 휴직 이력 fixture 필요").anyMatch(s -> "LEAVE".equals(s.status()));
    }

    @Test void assignmentsUseActualPersonAndStoredMonthlyValuesWithoutReconstruction() {
        var rows = mapper.assignments(range);
        assertThat(rows).as("월투입 fixture 필요").isNotEmpty();
        assertThat(rows).hasSize(db.sql.queryForObject("SELECT count(*) FROM tb_pj_asgn_mon WHERE input_ym>=? AND input_ym<=?", Integer.class, range.fromMonth(), range.toMonth()));
        assertThat(rows).as("계약 인력과 실제 인력이 다른 대체투입 fixture 필요").anyMatch(r -> r.contractPersonId() != null && !r.personId().equals(r.contractPersonId()));
        for (var row : rows) {
            var raw = db.sql.queryForMap("SELECT * FROM tb_pj_asgn_mon WHERE asgn_id=? AND input_ym=?", row.assignmentId(), row.month());
            assertThat(row.personId()).isEqualTo(raw.get("dev_id"));
            assertThat(row.projectId()).isEqualTo(raw.get("proj_id"));
            assertThat(row.assignmentMm()).isEqualTo(raw.get("mon_mm"));
            assertThat(row.appliedUnitPrice()).isEqualTo(raw.get("aply_uprc"));
            assertThat(row.salesAmount()).isEqualTo(raw.get("sales_amt"));
        }
        assertThat(mapper.projectMonths(range)).containsAll(rows);
    }

    @Test void projectExpensesAndTurnkeyStagesReadStoredValuesIndependentOfCommonDefaults() {
        var projects = mapper.projects();
        assertThat(projects).as("저장 프로젝트 fixture 필요").isNotEmpty();
        for (var project : projects) {
            var raw = db.sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?", project.projectId());
            assertThat(project.teamExpense()).isEqualTo(raw.get("team_exps_amt"));
            assertThat(project.projectExpense()).isEqualTo(raw.get("proj_exps_amt"));
            assertThat(project.contractAmount()).isEqualTo(raw.get("ctrt_amt"));
            assertThat(project.budget()).isEqualTo(raw.get("bdgt_amt"));
        }
        assertThat(mapper.turnkey()).hasSize(db.sql.queryForObject("SELECT count(*) FROM tb_pj_tk_dstrb", Integer.class));
        assertThat(mapper.projectParts()).hasSize(db.sql.queryForObject("SELECT count(*) FROM tb_pj_proj_part", Integer.class));
    }

    @Test void decimalResponseProjectionPreservesNullsAndAllReferenceColumns() {
        var json = db.json.copy().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        var rows = mapper.storedCosts(range);
        assertThat(rows).isNotEmpty();
        for (var row : rows) {
            var tree = json.valueToTree(row.toRow());
            for (String field : List.of("assignmentMm", "freeMm", "personalRevenue", "laborAllocated", "teamExpense", "projectExpense", "directExpense", "totalCost", "profit"))
                assertThat(tree.path(field).isTextual()).as(field).isTrue();
            assertThat(tree.has("contractType")).isTrue();
            assertThat(tree.has("projectSalesPartId")).isTrue();
            assertThat(tree.has("modDttm")).isTrue();
        }
        for (var payroll : mapper.payroll(range)) {
            // The source retains nullable reference columns for every temporary-admin caller.
            assertThat(payroll.selfInsurance()).isEqualTo(db.sql.queryForObject("SELECT insr_self_amt FROM tb_hr_mon_pay WHERE pay_ym=? AND dev_id=?", BigDecimal.class, payroll.month(), payroll.personId()));
        }
    }

    @Test void sharedHttpContextIsPublicAndUsesSameBaseCodesAndCloseDecision() throws Exception {
        var shared = db.context.getBean(SharedQueriesService.class);
        var users = db.context.getBean(CurrentUserProvider.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SharedQueriesController(shared, db.context.getBean(MenuService.class), users))
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        mvc.perform(get("/api/current-user")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.developmentMode").value(true))
            .andExpect(jsonPath("$.data.roleIds").isArray());
        var context = shared.getPeriodContext();
        var sources = repository.loadYear(null);
        assertThat(sources.period().baseMonth()).isEqualTo(context.cashBaseMonth().replace("-", ""));
        assertThat(sources.actor()).isEqualTo(users.userId());
        assertThat(sources.asOfDate()).isEqualTo(context.currentDate());
        assertThat(sources.allowanceCodes()).isEqualTo(shared.getCodes("ALWNC", false).items());
        var closes = shared.getMonthClose(sources.period().year()+"-01", context.currentMonth()).items();
        closes.forEach(c -> assertThat(sources.protectedMonth(c.month().replace("-", ""))).isEqualTo(!"작업중".equals(c.status())));
        mvc.perform(get("/api/period-context")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mvc.perform(get("/api/codes/invalid-group")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
    }

    @Test void repeatedCompanySourceReadsStayUnfilteredUncachedAndReadOnlyInOneSnapshot() {
        db.read(() -> {
            assertThat(db.sql.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("repeatable read");
            assertThat(db.sql.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("on");
            var first = repository.loadYear(null);
            var second = repository.loadYear(null);
            assertTrue(first.people().equals(second.people()), "동일 snapshot의 사람 원천 불일치");
            assertTrue(first.payroll().equals(second.payroll()), "동일 snapshot의 월급여 불일치");
            assertTrue(first.assignments().equals(second.assignments()), "동일 snapshot의 투입 불일치");
            assertTrue(first.storedCosts().equals(second.storedCosts()), "동일 snapshot의 사본 불일치");
            // Sources are complete immutable lists; UI consumers cannot narrow the shared result.
            assertThatThrownBy(() -> first.people().clear()).isInstanceOf(UnsupportedOperationException.class);
            return true;
        });
    }
    @Test void invalidMonthAndFutureYearAreRejectedWithoutCreatingAnyBusinessRows() {
        int year = db.context.getBean(SharedQueriesService.class).getPeriodContext().currentDate().getYear();
        for (int month : new int[]{0, 13}) {
            assertThatThrownBy(() -> repository.loadMonth(year, month)).isInstanceOfSatisfying(ApiException.class,
                e -> { assertThat(e.status()).isEqualTo(400); assertThat(e.field()).isEqualTo("month"); });
        }
        assertThatThrownBy(() -> repository.loadYear(year + 1)).isInstanceOfSatisfying(ApiException.class,
            e -> { assertThat(e.status()).isEqualTo(400); assertThat(e.field()).isEqualTo("year"); });
    }
}
