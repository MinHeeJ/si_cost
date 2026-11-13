package com.sicost.profitability;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.sicost.profitability.MonthlyCostDtos.*;

/** Company-wide pure labor/expense engine. Presentation filters must run after this boundary.
 * The monthly application service composes this engine with turnkey revenue and protected snapshots.
 */
@Component
public class MonthlyCostCalculation {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = new BigDecimal("1.00");
    private final EstimatedPayrollAdapter payroll;
    public MonthlyCostCalculation(EstimatedPayrollAdapter payroll) { this.payroll = payroll; }

    /** Open-month allocation only: protected snapshots are deliberately not recalculated. */
    public MonthlyPersonCost calculate(Sources sources, Person person, String month) {
        parseMonth(month);
        if (sources.protectedPersonMonth(month, person.personId()))
            throw new IllegalArgumentException("보호월은 월별 원가 사본 조회를 사용해 주세요.");
        var resolved = payroll.resolve(sources, person, month);
        var laborSource = resolved.source();
        List<MonthlyCostIssue> issues = new ArrayList<>(resolved.issues());
        var context = new Context(sources, person, month, laborSource, issues);
        var assignments = validAssignments(sources.assignments(), person.personId(), month);
        BigDecimal totalMm = assignments.stream().map(Assignment::assignmentMm).reduce(ZERO, BigDecimal::add);
        if (!person.employed(month)) {
            if (sources.payroll().stream().anyMatch(p -> p.personId().equals(person.personId()) && p.month().equals(month)))
                issues.add(issue(person, month, "재직기간 밖 급여 자료 불일치: 원가에서 제외했습니다.", laborSource.laborCost()));
            return result(context, List.of(), true, ZERO, false, false);
        }
        BigDecimal labor = resolved.labor();
        String type = laborSource.employeeType();
        boolean salaried = "FT".equals(type) || "CT".equals(type);
        if ("INDRCT".equals(laborSource.directClass())) {
            var row = fixed(context, "INDRCT", ZERO, labor);
            return result(context, List.of(row), labor != null, ZERO, false, true);
        }
        boolean fullLeave = salaried && fullMonthLeave(sources.states(), person.personId(), month);
        if (fullLeave) {
            if (!assignments.isEmpty()) issues.add(issue(person, month, "월 전체 휴직·배정 겹침: IDLE에만 배분했습니다.", money(labor)));
            var row = fixed(context, "IDLE", ONE.subtract(totalMm).max(ZERO), labor);
            return result(context, List.of(row), labor != null, totalMm, true, false);
        }
        if (!salaried && totalMm.signum() == 0) {
            boolean complete = labor != null && labor.signum() == 0;
            if (labor != null && labor.signum() != 0)
                issues.add(issue(person, month, "투입 없는 지급액: 월 투입 자료를 확인해 주세요.", money(labor)));
            return result(context, List.of(), complete, totalMm, false, false);
        }
        BigDecimal denominator = salaried ? totalMm.max(ONE) : totalMm;
        boolean idle = salaried && totalMm.compareTo(ONE) < 0;
        BigDecimal allocated = ZERO;
        List<MonthlyCostRow> rows = new ArrayList<>();
        Map<String, Project> projects = new HashMap<>();
        sources.projects().forEach(p -> projects.put(p.projectId(), p));
        for (int i = 0; i < assignments.size(); i++) {
            Assignment assignment = assignments.get(i);
            BigDecimal amount = labor == null ? null : !idle && i == assignments.size() - 1
                ? labor.subtract(allocated) : allocate(labor, assignment.assignmentMm(), denominator);
            if (amount != null) allocated = allocated.add(amount);
            Project project = projects.get(assignment.projectId());
            rows.add(actual(context, assignment, project, amount));
        }
        if (idle) rows.add(fixed(context, "IDLE", ONE.subtract(totalMm).max(ZERO), labor == null ? null : labor.subtract(allocated)));
        return result(context, rows, labor != null, totalMm, idle, false);
    }

    public static List<Assignment> validAssignments(List<Assignment> rows, String personId, String month) {
        return rows.stream().filter(a -> a.personId().equals(personId) && a.month().equals(month)
            && "ACT".equals(a.status()) && a.assignmentMm() != null && a.assignmentMm().signum() > 0)
            .sorted(Comparator.comparing(Assignment::projectId).thenComparing(Assignment::assignmentId)).toList();
    }

    /** Union of adjacent/overlapping LEAVE intervals; partial leave never changes source MM. */
    public static boolean fullMonthLeave(List<WorkState> states, String personId, String month) {
        YearMonth ym = parseMonth(month);
        var end = ym.atEndOfMonth();
        var next = ym.atDay(1);
        for (var state : states.stream().filter(s -> s.personId().equals(personId) && "LEAVE".equals(s.status()))
            .sorted(Comparator.comparing(WorkState::fromDate)).toList()) {
            if (state.toDate() != null && state.toDate().isBefore(next)) continue;
            if (state.fromDate().isAfter(next)) return false;
            if (state.toDate() == null || !state.toDate().isBefore(end)) return true;
            next = state.toDate().plusDays(1);
        }
        return false;
    }

    /** Multiply first; there is no intermediate ratio rounding. Signed DOWN is toward zero. */
    public static BigDecimal allocate(BigDecimal principal, BigDecimal numerator, BigDecimal denominator) {
        return principal.multiply(numerator).divide(denominator, 0, RoundingMode.DOWN);
    }
    /** Rates and average headcounts are not row amounts and are never NUMERIC(15)-capped. */
    public static BigDecimal average(BigDecimal numerator, BigDecimal denominator) {
        return numerator == null || denominator == null || denominator.signum() == 0 ? null
            : numerator.divide(denominator, 1, RoundingMode.HALF_UP);
    }
    public static BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        return numerator == null ? null : average(numerator.multiply(new BigDecimal("100")), denominator);
    }
    public static String costClass(Project project) {
        if (project == null) return null;
        if ("LOSE".equals(project.status())) return "IDLE";
        if ("IN".equals(project.contractType())) return "RND";
        if ("PRE".equals(project.status())) return "PRE";
        return "ALLOC";
    }
    public static String projectPart(Sources sources, Project project, String month) {
        if (project == null || "IN".equals(project.contractType())) return null;
        return sources.projectParts().stream().filter(p -> p.projectId().equals(project.projectId()) && p.fromMonth().compareTo(month) <= 0)
            .max(Comparator.comparing(ProjectPart::fromMonth)).map(ProjectPart::partId).orElse(null);
    }
    private record Context(Sources sources, Person person, String month, MonthlyLaborSource laborSource, List<MonthlyCostIssue> issues) {}
    private MonthlyCostRow actual(Context context, Assignment a, Project p, BigDecimal labor) {
        Sources sources = context.sources();
        Person person = context.person();
        boolean estimated = context.laborSource().estimated();
        List<MonthlyCostIssue> issues = context.issues();
        String cls = costClass(p);
        if (p == null) issues.add(issue(person, a.month(), "프로젝트 자료가 없습니다: " + a.projectId(), null));
        if ("PRE".equals(cls)) issues.add(issue(person, a.month(), "선투입 원가: 프로젝트 진행 상태를 확인해 주세요.", null));
        String type = context.laborSource().employeeType();
        BigDecimal team = ZERO, expense = ZERO;
        if (!"OS".equals(type)) {
            BigDecimal storedTeam = checked(p == null ? null : p.teamExpense(), person, a.month(), issues);
            BigDecimal storedExpense = checked(p == null ? null : p.projectExpense(), person, a.month(), issues);
            team = storedTeam == null ? null : checked(storedTeam.multiply(a.assignmentMm()).setScale(0, RoundingMode.DOWN), person, a.month(), issues);
            expense = storedExpense == null ? null : checked(storedExpense.multiply(a.assignmentMm()).setScale(0, RoundingMode.DOWN), person, a.month(), issues);
        }
        BigDecimal revenue = null;
        if (p != null) {
            if (!"ALLOC".equals(cls) || "FREE".equals(a.inputType())) revenue = ZERO;
            else if (Set.of("MO", "DP").contains(p.contractType())) revenue = checked(a.salesAmount(), person, a.month(), issues);
            // TK revenue is resolved by the next composition step, never by an invented default.
        }
        return row(new RowIdentity(person, a.month(), a.projectId(), a.assignmentId(), a.assignmentMm(),
            "FREE".equals(a.inputType()) ? a.assignmentMm() : ZERO, cls, p == null ? null : p.contractType(),
            projectPart(sources, p, a.month()), p == null ? null : p.name(), a.jobCode(), a.inputType()),
            new RowAmounts(revenue, labor, team, expense), estimated, issues);
    }
    private MonthlyCostRow fixed(Context context, String cls, BigDecimal mm, BigDecimal labor) {
        return row(new RowIdentity(context.person(), context.month(), cls, cls, mm, ZERO, cls, null, null, null, null, null),
            new RowAmounts(ZERO, labor, ZERO, ZERO), context.laborSource().estimated(), context.issues());
    }
    private record RowIdentity(Person person, String month, String projectId, String assignmentId, BigDecimal mm,
        BigDecimal freeMm, String cls, String contractType, String partId, String name, String job, String inputType) {}
    private record RowAmounts(BigDecimal revenue, BigDecimal labor, BigDecimal team, BigDecimal expense) {}
    private MonthlyCostRow row(RowIdentity id, RowAmounts amounts, boolean estimated, List<MonthlyCostIssue> issues) {
        BigDecimal labor = checked(amounts.labor(), id.person(), id.month(), issues);
        BigDecimal direct = amounts.team() == null || amounts.expense() == null ? null
            : checked(amounts.team().add(amounts.expense()), id.person(), id.month(), issues);
        BigDecimal total = labor == null || direct == null ? null : checked(labor.add(direct), id.person(), id.month(), issues);
        BigDecimal profit = amounts.revenue() == null || total == null ? null
            : checked(amounts.revenue().subtract(total), id.person(), id.month(), issues);
        BigDecimal profitRate = amounts.revenue() == null || amounts.revenue().signum() <= 0 ? null : rate(profit, amounts.revenue());
        return new MonthlyCostRow(id.person().personId(), id.projectId(), id.assignmentId(), id.month(), mm(id.mm()), mm(id.freeMm()),
            money(amounts.revenue()), money(labor), money(amounts.team()), money(amounts.expense()), money(direct), money(total), money(profit),
            id.cls(), id.contractType(), id.partId(), "N", id.name(), id.job(), id.inputType(),
            profitRate == null ? null : profitRate.toPlainString(), estimated, total != null && (amounts.revenue() == null || profit != null),
            amounts.revenue() != null, true, null, null, null, null);
    }
    private static BigDecimal checked(BigDecimal amount, Person person, String month, List<MonthlyCostIssue> issues) {
        try { return EstimatedPayrollAdapter.required(amount); }
        catch (IllegalArgumentException | ArithmeticException e) {
            var issue = issue(person, month, "원가 계산 미확인: " + e.getMessage(), moneyOrNull(amount));
            if (!issues.contains(issue)) issues.add(issue);
            return null;
        }
    }
    private static String moneyOrNull(BigDecimal value) { return value == null ? null : value.toPlainString(); }
    private static MonthlyCostIssue issue(Person person, String month, String reason, String amount) {
        return new MonthlyCostIssue(person.personId(), month, reason, "월별 원가 계산", "UI-03", amount);
    }
    private static MonthlyPersonCost result(Context context, List<MonthlyCostRow> rows,
        boolean allocated, BigDecimal totalMm, boolean idle, boolean indirect) {
        Person person = context.person();
        String month = context.month();
        MonthlyLaborSource source = context.laborSource();
        List<MonthlyCostIssue> issues = context.issues();
        boolean costComplete = allocated && rows.stream().allMatch(MonthlyCostRow::costComplete);
        boolean revenueComplete = rows.stream().allMatch(MonthlyCostRow::revenueComplete);
        BigDecimal knownLabor = rows.stream().map(MonthlyCostRow::laborAllocated).filter(Objects::nonNull)
            .map(BigDecimal::new).reduce(ZERO, BigDecimal::add);
        BigDecimal knownRevenue = rows.stream().map(MonthlyCostRow::personalRevenue).filter(Objects::nonNull)
            .map(BigDecimal::new).reduce(ZERO, BigDecimal::add);
        return new MonthlyPersonCost(person.personId(), month, List.copyOf(rows), source,
            new MonthlyCostCompleteness(costComplete, revenueComplete, source.estimated(), allocated, List.copyOf(issues)),
            money(knownLabor), money(knownRevenue), mm(totalMm), idle, indirect);
    }
}
