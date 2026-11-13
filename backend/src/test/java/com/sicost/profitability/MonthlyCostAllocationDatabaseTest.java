package com.sicost.profitability;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runner supplies the independent existing-data baseline. Never installs migrations or inserts examples. */
@EnabledIfEnvironmentVariable(named="SICOST_COST_TEST_DB_URL", matches=".+")
class MonthlyCostAllocationDatabaseTest {
    CostDatabaseSupport db;
    MonthlyCostRepository repository;
    final EstimatedPayrollAdapter payroll = new EstimatedPayrollAdapter();
    final MonthlyCostCalculation calculation = new MonthlyCostCalculation(payroll);
    Map<String,List<String>> before;
    @BeforeEach void connect() throws Exception {
        db = new CostDatabaseSupport();
        repository = db.context.getBean(MonthlyCostRepository.class);
        before = db.snapshot();
    }
    @AfterEach void preserveAllSourcesIncludingExcludedFinanceAndAudit() {
        if(db != null) try { if(before != null) assertTrue(before.equals(db.snapshot()), "원천/기준/감사 값이 변경되었습니다."); }
        finally { db.close(); }
    }
    @Test void allAllocationSourcesUseTheDocumentedPhysicalKeysAndColumns() {
        keys("tb_pj_asgn", "asgn_id");
        keys("tb_pj_asgn_mon", "asgn_id", "input_ym");
        keys("tb_pj_proj", "proj_id"); keys("tb_bs_clnt", "clnt_id"); keys("tb_bs_part", "part_id");
        keys("tb_cl_close", "close_ym"); keys("tb_sy_oper_std", "std_cd", "aply_bgn_ym"); keys("tb_sy_code", "code_grp_id", "code");
        columns("tb_pj_asgn", "proj_id", "dev_id", "job_cd", "input_type_cd", "ctrt_dev_id", "asgn_stat_cd", "input_bgn_dt", "input_end_dt");
        columns("tb_pj_asgn_mon", "input_ym", "proj_id", "dev_id", "mon_mm", "aply_uprc", "sales_amt", "fix_yn");
        columns("tb_pj_proj", "proj_nm", "ctrt_type_cd", "proj_stat_cd", "lose_dt", "ctrt_amt", "bdgt_amt", "team_exps_amt", "proj_exps_amt");
        columns("tb_bs_clnt", "clnt_nm", "use_yn"); columns("tb_bs_part", "part_nm", "hq_yn", "use_yn");
        columns("tb_cl_close", "close_stat_cd"); columns("tb_sy_oper_std", "std_val");
        columns("tb_sy_code", "code_nm", "code_val", "sort_ord", "use_yn", "del_yn");
        for(String column:List.of("mon_mm","aply_uprc","sales_amt")) {
            var metadata=db.sql.queryForMap("SELECT numeric_precision,numeric_scale FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_pj_asgn_mon' AND column_name=?",column);
            assertThat(((Number)metadata.get("numeric_precision")).intValue()).isEqualTo(column.equals("mon_mm")?6:15);
            assertThat(((Number)metadata.get("numeric_scale")).intValue()).isEqualTo(column.equals("mon_mm")?2:0);
        }
    }
    @Test void existingAssignmentsDisplayMastersAndCloseCodesMaterializeExactlyWithoutNewGuessedFields() {
        var mapper=db.context.getBean(MonthlyCostMapper.class);
        var range=db.existingRange();
        for(var row:mapper.assignments(range)) {
            var raw=db.sql.queryForMap("SELECT m.*,a.job_cd,a.input_type_cd,a.ctrt_dev_id,a.asgn_stat_cd,a.input_bgn_dt,a.input_end_dt FROM tb_pj_asgn_mon m LEFT JOIN tb_pj_asgn a ON a.asgn_id=m.asgn_id WHERE m.asgn_id=? AND m.input_ym=?",row.assignmentId(),row.month());
            assertThat(row.personId()).isEqualTo(raw.get("dev_id")); assertThat(row.assignmentMm()).isEqualTo(raw.get("mon_mm"));
            assertThat(row.appliedUnitPrice()).isEqualTo(raw.get("aply_uprc")); assertThat(row.salesAmount()).isEqualTo(raw.get("sales_amt"));
            assertThat(row.jobCode()).isEqualTo(raw.get("job_cd")); assertThat(row.inputType()).isEqualTo(raw.get("input_type_cd"));
            assertThat(row.contractPersonId()).isEqualTo(raw.get("ctrt_dev_id")); assertThat(row.status()).isEqualTo(raw.get("asgn_stat_cd"));
            assertThat(row.fromDate()).isEqualTo(date(raw.get("input_bgn_dt"))); assertThat(row.toDate()).isEqualTo(date(raw.get("input_end_dt")));
        }
        var sources=repository.loadYear(null);
        for(var person:sources.people()) {
            var raw=db.sql.queryForMap("SELECT c.clnt_nm,p.part_nm FROM tb_hr_dev d LEFT JOIN tb_bs_clnt c ON c.clnt_id=d.blng_clnt_id LEFT JOIN tb_bs_part p ON p.part_id=d.part_id WHERE d.dev_id=?",person.personId());
            assertThat(person.companyName()).isEqualTo(raw.get("clnt_nm")); assertThat(person.personPartName()).isEqualTo(raw.get("part_nm"));
        }
        for(var row:sources.closes()) assertThat(sources.protectedMonth(row.month())).isEqualTo("CLOSE".equals(row.status()) || row.month().compareTo(sources.period().baseMonth())<0);
    }
    @Test void completeOpenCompanyAllocationsRepeatInOneReadSnapshotAndConserveStoredLabor() {
        db.read(() -> {
            var sources=repository.loadYear(null);
            assertThat(db.sql.queryForObject("SHOW transaction_read_only",String.class)).isEqualTo("on");
            assertThat(db.sql.queryForObject("SHOW transaction_isolation",String.class)).isEqualTo("repeatable read");
            int checked=0;
            for(String month:months(sources)) for(var person:sources.people()) {
                if(sources.protectedPersonMonth(month,person.personId()) || !person.employed(month)) continue;
                var first=calculation.calculate(sources,person,month);
                assertThat(calculation.calculate(sources,person,month)).isEqualTo(first);
                if(!first.completeness().allocationComplete()) continue;
                var labor=payroll.resolve(sources,person,month).labor();
                if(labor == null) continue;
                assertThat(first.rows().stream().map(MonthlyCostRow::laborAllocated).filter(Objects::nonNull).map(BigDecimal::new).reduce(BigDecimal.ZERO,BigDecimal::add))
                    .isEqualByComparingTo(labor);
                for(var row:first.rows()) {
                    if(row.projectId().equals("IDLE") || row.projectId().equals("INDRCT")) {
                        assertThat(row.teamExpense()).isEqualTo("0"); assertThat(row.projectExpense()).isEqualTo("0"); continue;
                    }
                    var original=sources.assignments().stream().filter(a->a.assignmentId().equals(row.assignmentId()) && a.month().equals(row.month())).findFirst().orElseThrow();
                    assertThat(row.assignmentMm()).isEqualTo(mm(original.assignmentMm()));
                    assertThat(row.personId()).isEqualTo(original.personId());
                    if(!"OS".equals(first.laborSource().employeeType())) {
                        var project=sources.projects().stream().filter(p->p.projectId().equals(row.projectId())).findFirst().orElseThrow();
                        assertThat(row.teamExpense()).isEqualTo(money(project.teamExpense().multiply(original.assignmentMm()).setScale(0,RoundingMode.DOWN)));
                        assertThat(row.projectExpense()).isEqualTo(money(project.projectExpense().multiply(original.assignmentMm()).setScale(0,RoundingMode.DOWN)));
                    } else assertThat(row.directExpense()).isEqualTo("0");
                }
                checked++;
            }
            assertThat(checked).as("기존 독립 DB에 완전한 미마감 급여/투입 원천 fixture가 필요합니다.").isPositive();
            return true;
        });
    }
    @Test void specifiedNumericExamplesAreReachedFromExistingDatabaseSourcesNotAutoInsertedRows() {
        var sources=repository.loadYear(null);
        Set<String> witnessed=new HashSet<>();
        for(String month:months(sources)) for(var person:sources.people()) {
            if(sources.protectedPersonMonth(month,person.personId()) || !person.employed(month)) continue;
            var value=calculation.calculate(sources,person,month);
            if(!value.completeness().allocationComplete()) continue;
            var amounts=value.rows().stream().map(MonthlyCostRow::laborAllocated).toList();
            var mm=value.rows().stream().filter(r->!r.projectId().equals("IDLE")).map(MonthlyCostRow::assignmentMm).toList();
            if("4970000".equals(value.laborSource().laborCost()) && mm.equals(List.of("1.00","0.50"))) {
                assertThat(amounts).containsExactly("3313333","1656667"); witnessed.add("over");
            }
            if("4970000".equals(value.laborSource().laborCost()) && mm.equals(List.of("0.60")) && value.idleRowGenerated()) {
                assertThat(amounts).containsExactly("2982000","1988000"); witnessed.add("partial");
            }
            if("FL".equals(value.laborSource().employeeType()) && "5000000".equals(value.laborSource().laborCost()) && mm.equals(List.of("0.50"))) {
                assertThat(amounts).containsExactly("5000000"); witnessed.add("freelance");
            }
        }
        assertThat(witnessed).as("runner 독립 DB의 원문 FX-B137-ALLOC 세 조건이 필요합니다. 자동 삽입/기준 재적재는 하지 않습니다.")
            .contains("over","partial","freelance");
    }
    @Test void closedPreBeforeLossKeepsOriginalSnapshotAcrossRepeatedReadsWithoutAssignmentChanges() {
        db.read(() -> {
            var mapper = db.context.getBean(MonthlyCostMapper.class);
            var projects = mapper.projects();
            var candidates = mapper.storedCosts(db.existingRange()).stream().filter(c -> "PRE".equals(c.costClass()))
                .filter(c -> projects.stream().anyMatch(p -> p.projectId().equals(c.projectId()) && "LOSE".equals(p.status())
                    && p.loseDate() != null && parseMonth(c.month()).isBefore(java.time.YearMonth.from(p.loseDate())))).toList();
            int witnessed = 0;
            for (var original : candidates) {
                var ym = parseMonth(original.month());
                var sources = repository.loadMonth(ym.getYear(), ym.getMonthValue());
                if (sources.closes().stream().noneMatch(c -> c.month().equals(original.month()) && "CLOSE".equals(c.status()))) continue;
                var first = sources.protectedRows(original.month(), original.personId());
                assertThat(first).contains(original.toRow());
                var reread = repository.loadMonth(ym.getYear(), ym.getMonthValue()).protectedRows(original.month(), original.personId());
                assertThat(reread).isEqualTo(first);
                assertThat(reread).filteredOn(r -> r.key().equals(original.key())).singleElement().satisfies(row -> {
                    assertThat(row.costClass()).isEqualTo("PRE");
                    assertThat(row.laborAllocated()).isEqualTo(money(original.laborAllocated()));
                    assertThat(row.fixed()).isEqualTo(original.fixed());
                    assertThat(row.rowGenerated()).isFalse();
                });
                witnessed++;
            }
            assertThat(witnessed).as("독립 DB에 실주 전월 CLOSE/PRE 사본과 이후 실주 프로젝트 조건이 필요합니다. 예제는 삽입하지 않습니다.").isPositive();
            return true;
        });
        // @AfterEach compares every assignment field/date and audit value, not just row counts.
    }

    @Test void projectMonthPartAndPersonFilterUseDifferentExistingPhysicalSources() {
        db.read(() -> {
            var sources = repository.loadYear(null);
            int differentParts = 0;
            for (String month : months(sources)) for (var person : sources.people()) {
                if (sources.protectedPersonMonth(month, person.personId()) || !person.employed(month)) continue;
                for (var row : calculation.calculate(sources, person, month).rows()) {
                    var actualPart = db.sql.queryForObject("SELECT part_id FROM tb_hr_dev WHERE dev_id=?", String.class, person.personId());
                    assertThat(person.personPartId()).isEqualTo(actualPart);
                    assertThat(person.matchesPart(actualPart)).isTrue();
                    if (row.projectId().equals("IDLE") || row.projectId().equals("INDRCT") || "IN".equals(row.contractType())) {
                        assertThat(row.projectSalesPartId()).isNull();
                        continue;
                    }
                    var effective = db.sql.queryForList("SELECT part_id FROM tb_pj_proj_part WHERE proj_id=? AND aply_bgn_ym<=? ORDER BY aply_bgn_ym DESC LIMIT 1",
                        String.class, row.projectId(), month);
                    String expected = effective.isEmpty() ? null : effective.get(0);
                    assertThat(row.projectSalesPartId()).isEqualTo(expected);
                    if (expected != null && !expected.equals(actualPart)) {
                        assertThat(person.matchesPart(expected)).isFalse();
                        differentParts++;
                    }
                }
            }
            assertThat(differentParts).as("독립 DB에 사람 소속과 월 프로젝트 영업 파트가 서로 다른 미마감 투입 조건이 필요합니다.").isPositive();
            return true;
        });
    }

    @Test void freeAndSyntheticMmAndGeneratedZeroRowsMatchExistingAllocationSources() {
        db.read(() -> {
            var sources = repository.loadYear(null);
            Set<String> witnessed = new HashSet<>();
            for (String month : months(sources)) for (var person : sources.people()) {
                if (sources.protectedPersonMonth(month, person.personId()) || !person.employed(month)) continue;
                var result = calculation.calculate(sources, person, month);
                for (var row : result.rows()) {
                    assertThat(row.rowGenerated()).isTrue();
                    if ("FREE".equals(row.inputType())) {
                        assertThat(row.freeMm()).isEqualTo(row.assignmentMm());
                        witnessed.add("FREE");
                    } else assertThat(row.freeMm()).isEqualTo("0.00");
                    if (row.projectId().equals("IDLE") && row.assignmentId().equals("IDLE")) {
                        assertThat(row.assignmentMm()).isEqualTo(mm(BigDecimal.ONE.subtract(new BigDecimal(result.assignmentMm())).max(BigDecimal.ZERO)));
                        assertThat(row.inputType()).isNull();
                        assertThat(row.contractType()).isNull();
                        assertThat(result.idleRowGenerated()).isTrue();
                    }
                    if (row.projectId().equals("INDRCT")) {
                        assertThat(row.assignmentMm()).isEqualTo("0.00");
                        assertThat(result.indirectRowGenerated()).isTrue();
                        witnessed.add("INDRCT");
                    }
                    if ((row.projectId().equals("IDLE") || row.projectId().equals("INDRCT")) && "0".equals(row.laborAllocated())) {
                        assertThat(row.costComplete()).isTrue();
                        witnessed.add("ZERO_FIXED");
                    }
                }
            }
            assertThat(witnessed).as("독립 DB의 FREE·간접·유효 0원 고정행 조건이 필요합니다. 사본이나 급여를 생성하지 않습니다.")
                .contains("FREE", "INDRCT", "ZERO_FIXED");
            return true;
        });
    }
    private static java.time.LocalDate date(Object value) { return value == null ? null : ((java.sql.Date)value).toLocalDate(); }
    private List<String> months(Sources sources) {
        var months=new TreeSet<String>();
        sources.payroll().forEach(p->months.add(p.month())); sources.assignments().forEach(a->months.add(a.month()));
        return List.copyOf(months);
    }
    private void keys(String table,String... expected) {
        var actual=db.sql.queryForList("SELECT a.attname FROM pg_index i JOIN pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=ANY(i.indkey) WHERE i.indrelid=CAST(? AS regclass) AND i.indisprimary ORDER BY array_position(i.indkey::smallint[],a.attnum)",String.class,table);
        assertThat(actual).containsExactly(expected);
    }
    private void columns(String table,String... expected) {
        assertThat(db.sql.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=current_schema() AND table_name=?",String.class,table)).contains(expected);
    }
}
