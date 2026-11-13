package com.sicost.profitability;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.profitability.MonthlyCostDtos.*;

/** Shared, uncached, company-wide result. Every public read joins one consistent source snapshot. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class MonthlyCostService {
    private final MonthlyCostRepository repository;
    private final MonthlyCostCalculation calculation;
    private final TurnkeyRevenueCalculation turnkey;
    private final EstimatedPayrollAdapter payroll;

    public MonthlyCostService(MonthlyCostRepository repository, MonthlyCostCalculation calculation,
        TurnkeyRevenueCalculation turnkey, EstimatedPayrollAdapter payroll) {
        this.repository = repository; this.calculation = calculation; this.turnkey = turnkey; this.payroll = payroll;
    }

    public MonthlyCostsData getMonthlyCosts(MonthlyCostInput.Query query) {
        Sources sources = repository.loadMonth(query.year(), query.month());
        MonthlyCostsData all = calculate(sources);
        return query.personId() == null ? all : new MonthlyCostsData(all.period(),
            all.items().stream().filter(p -> query.personId().equals(p.personId())).toList());
    }

    /** Annual consumers/export must load once and derive all views from this same source set. */
    public Sources loadYear(Integer year) { return repository.loadYear(year); }

    public MonthlyCostsData calculate(Sources sources) {
        var period = sources.period();
        if (period.noData()) return new MonthlyCostsData(period, List.copyOf(Collections.emptyList()));
        List<MonthlyPersonCost> result = new ArrayList<>();
        Map<String, Person> people = new HashMap<>();
        sources.people().forEach(p -> people.put(p.personId(), p));
        for (YearMonth ym = parseMonth(period.fromMonth()); !ym.isAfter(parseMonth(period.toMonth())); ym = ym.plusMonths(1)) {
            String month = ym.toString().replace("-", "");
            SortedSet<String> ids = new TreeSet<>();
            // Pre-operating data is evidence only: never generate a missing personal row there.
            if (month.compareTo(period.baseMonth()) >= 0) {
                sources.people().stream().filter(p -> p.employed(month)).forEach(p -> ids.add(p.personId()));
                sources.payroll().stream().filter(p -> month.equals(p.month())).forEach(p -> ids.add(p.personId()));
                sources.assignments().stream().filter(a -> month.equals(a.month())).forEach(a -> ids.add(a.personId()));
                sources.projects().stream().filter(p -> "TK".equals(p.contractType())).forEach(p ->
                    turnkey.selected(sources, p.projectId()).stream().filter(c -> "DEV".equals(c.rowType()) && month.equals(c.month()))
                        .forEach(c -> ids.add(c.developerId())));
            }
            sources.storedCosts().stream().filter(c -> month.equals(c.month())).forEach(c -> ids.add(c.personId()));
            for (String id : ids) {
                Person person = people.get(id);
                if (sources.protectedPersonMonth(month, id)) result.add(protectedCost(sources, new PersonMonth(month, id), person));
                else if (person != null) result.add(turnkey.calculate(sources, calculation.calculate(sources, person, month)));
                else result.add(missing(new PersonMonth(month, id), "인력 원천이 없습니다. 실제 DEV_ID를 확인해 주세요.", "TB_HR_DEV", "UI-02"));
            }
        }
        return new MonthlyCostsData(period, List.copyOf(result));
    }

    private MonthlyPersonCost protectedCost(Sources sources, PersonMonth key, Person person) {
        var rows = sources.protectedRows(key.month(), key.personId());
        if (rows.isEmpty()) return missing(key, "마감 원가 사본이 없습니다. 자료 없음/확인 필요", "TB_CO_MON_COST", "UI-03");
        List<MonthlyCostIssue> issues = new ArrayList<>();
        if (person == null) issues.add(issue(key, "인력 표시 자료 없음: " + key.personId(), "TB_HR_DEV", "UI-02"));
        for (var row : rows) {
            if (row.projectName() == null && !synthetic(row))
                issues.add(issue(key, "프로젝트 표시 자료 없음: " + row.projectId(), "TB_PJ_PROJ", "UI-01"));
            if (row.assignmentJobCode() == null && !synthetic(row))
                issues.add(issue(key, "투입 표시 자료 없음: " + row.assignmentId(), "TB_PJ_ASGN", "UI-12"));
        }
        boolean coverage = true;
        // Conservative mixed-fixed authority: retain ALL stored rows, but never invent missing allocations.
        if (!sources.protectedMonth(key.month())) {
            Set<CostKey> storedKeys = new HashSet<>(); rows.forEach(r -> storedKeys.add(r.key()));
            coverage = MonthlyCostCalculation.validAssignments(sources.assignments(), key.personId(), key.month()).stream()
                .allMatch(a -> storedKeys.contains(new CostKey(a.month(), a.personId(), a.projectId(), a.assignmentId())));
            if (!coverage) issues.add(issue(key, "고정 사람월의 투입 사본 범위 확인 필요: 누락행은 재계산하지 않습니다.", "TB_CO_MON_COST", "UI-03"));
        }
        String labor = sum(rows, MonthlyCostRow::laborAllocated);
        var storedPayroll = sources.payroll().stream().filter(p -> key.month().equals(p.month()) && key.personId().equals(p.personId())).findFirst();
        MonthlyLaborSource source;
        if (storedPayroll.isPresent()) {
            var resolved = payroll.uploaded(storedPayroll.get());
            var p = resolved.source();
            issues.addAll(resolved.issues());
            source = new MonthlyLaborSource(key.personId(), key.month(), p.employeeType(), p.directClass(), "마감 사본",
                labor, p.pay(), p.allowance(), p.companyInsurance(), p.retirementReserve(), p.selfInsurance(),
                p.withholdingTax(), p.incomeTax(), p.netPay(), false);
        } else {
            issues.add(issue(key, "보호된 월급여 자료 없음: 현재 급여로 복원하지 않습니다.", "TB_HR_MON_PAY", "UI-02"));
            source = new MonthlyLaborSource(key.personId(), key.month(), "", rows.stream().allMatch(r -> "INDRCT".equals(r.costClass()))
                ? "INDRCT" : "DRCT", "마감 사본", labor, null, null, null, null, null, null, null, null, false);
        }
        var completeness = new MonthlyCostCompleteness(coverage && rows.stream().allMatch(MonthlyCostRow::costComplete),
            coverage && rows.stream().allMatch(MonthlyCostRow::revenueComplete), false, coverage, List.copyOf(issues));
        return new MonthlyPersonCost(key.personId(), key.month(), rows, source, completeness,
            knownSum(rows, MonthlyCostRow::laborAllocated), knownSum(rows, MonthlyCostRow::personalRevenue),
            mm(rows.stream().filter(r -> !synthetic(r)).map(r -> new BigDecimal(r.assignmentMm())).reduce(BigDecimal.ZERO, BigDecimal::add)), false, false);
    }

    private static MonthlyPersonCost missing(PersonMonth key, String reason, String source, String screen) {
        return new MonthlyPersonCost(key.personId(), key.month(), List.copyOf(Collections.emptyList()),
            new MonthlyLaborSource(key.personId(), key.month(), "", "", "자료 없음", null, null, null, null, null, null, null, null, null, false),
            new MonthlyCostCompleteness(false, false, false, false, List.of(issue(key, reason, source, screen))),
            null, null, "0.00", false, false);
    }
    private static MonthlyCostIssue issue(PersonMonth key, String reason, String source, String screen) {
        return new MonthlyCostIssue(key.personId(), key.month(), reason, source, screen, null);
    }
    public static boolean synthetic(MonthlyCostRow row) {
        return ("IDLE".equals(row.projectId()) || "INDRCT".equals(row.projectId())) && row.projectId().equals(row.assignmentId());
    }

    /** Completeness-aware primitive for later period consumers; no UI population/filter policy here. */
    public PeriodAmounts summarize(List<MonthlyPersonCost> months, boolean includeTurnkey) {
        var view = months.stream().map(m -> turnkey.project(m, includeTurnkey)).toList();
        boolean costComplete = view.stream().allMatch(m -> m.completeness().costComplete());
        boolean revenueComplete = view.stream().allMatch(m -> m.completeness().revenueComplete());
        var rows = view.stream().flatMap(m -> m.rows().stream()).toList();
        String cost = costComplete ? sum(rows, MonthlyCostRow::totalCost) : null;
        String revenue = revenueComplete ? sum(rows, MonthlyCostRow::personalRevenue) : null;
        return new PeriodAmounts(cost, revenue, cost == null || revenue == null ? null : money(new BigDecimal(revenue).subtract(new BigDecimal(cost))),
            knownSum(rows, MonthlyCostRow::totalCost), knownSum(rows, MonthlyCostRow::personalRevenue),
            mm(view.stream().map(m -> new BigDecimal(m.assignmentMm())).reduce(BigDecimal.ZERO, BigDecimal::add)),
            view.stream().filter(m -> !m.completeness().costComplete() || !m.completeness().revenueComplete()).map(MonthlyPersonCost::month).distinct().toList(),
            view.stream().flatMap(m -> m.completeness().issues().stream()).distinct().toList());
    }
    public record PeriodAmounts(String totalCost, String revenue, String profit, String knownCost, String knownRevenue,
        String assignmentMm, List<String> problemMonths, List<MonthlyCostIssue> issues) {
        public boolean confirmedLoss() { return profit != null && new BigDecimal(profit).signum() < 0; }
        /** Caller supplies the independently established employment/working-month denominator. */
        public String averageCost(int months) { return totalCost == null || months <= 0 ? null
            : new BigDecimal(totalCost).divide(BigDecimal.valueOf(months), 0, RoundingMode.DOWN).toPlainString(); }
    }
    private static String sum(List<MonthlyCostRow> rows, Function<MonthlyCostRow, String> field) {
        return rows.stream().anyMatch(r -> field.apply(r) == null) ? null : knownSum(rows, field);
    }
    private static String knownSum(List<MonthlyCostRow> rows, Function<MonthlyCostRow, String> field) {
        return money(rows.stream().map(field).filter(Objects::nonNull).map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
