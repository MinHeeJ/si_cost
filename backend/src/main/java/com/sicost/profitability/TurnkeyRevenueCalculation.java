package com.sicost.profitability;

import com.sicost.b6.B6Comparison;
import com.sicost.b6.B6Dtos;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.sicost.profitability.MonthlyCostDtos.*;

/** Pure revenue composition. Stored protected rows never pass through this calculator. */
@Component
public class TurnkeyRevenueCalculation {
    public MonthlyPersonCost calculate(Sources sources, MonthlyPersonCost original) {
        if (sources.protectedPersonMonth(original.month(), original.personId()))
            throw new IllegalArgumentException("보호월의 개인 단가는 저장 사본을 사용해야 합니다.");
        List<MonthlyCostIssue> issues = new ArrayList<>(original.completeness().issues());
        if (!original.completeness().allocationComplete() && original.rows().isEmpty()
            && original.laborSource().laborCost() != null && new BigDecimal(original.laborSource().laborCost()).signum() != 0) {
            issues.add(new MonthlyCostIssue(original.personId(), original.month(), "지급 인건비의 월 투입 원천이 없습니다.",
                "TB_PJ_ASGN_MON", "UI-12", original.laborSource().laborCost()));
        }
        Map<CostKey, String> revenues = new HashMap<>();
        boolean allocationComplete = original.completeness().allocationComplete();
        for (Project project : sources.projects()) {
            if (!"TK".equals(project.contractType())) continue;
            var selected = selected(sources, project.projectId());
            var personCells = selected.stream().filter(c -> "DEV".equals(c.rowType())
                && original.personId().equals(c.developerId())).toList();
            var paid = original.rows().stream().filter(r -> project.projectId().equals(r.projectId())
                && "ALLOC".equals(r.costClass()) && !"FREE".equals(r.inputType()))
                .sorted(Comparator.comparing(MonthlyCostRow::projectId).thenComparing(MonthlyCostRow::assignmentId)).toList();
            BigDecimal amount = personCells.stream().filter(c -> original.month().equals(c.month()))
                .map(B6Dtos.Cell::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!personCells.isEmpty()) {
                BigDecimal denominator = paid.stream().map(r -> new BigDecimal(r.assignmentMm())).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (denominator.signum() == 0) {
                    if (amount.signum() != 0) {
                        allocationComplete = false;
                        issues.add(issue(original, "유상 투입 없는 턴키 배분액: 월 투입과 배분표를 확인해 주세요.", "TB_PJ_TK_DSTRB", amount));
                    }
                } else {
                    BigDecimal allocated = BigDecimal.ZERO;
                    for (int i = 0; i < paid.size(); i++) {
                        var row = paid.get(i);
                        BigDecimal share = i == paid.size() - 1 ? amount.subtract(allocated)
                            : MonthlyCostCalculation.allocate(amount, new BigDecimal(row.assignmentMm()), denominator);
                        allocated = allocated.add(share);
                        revenues.put(row.key(), checked(share, original, issues));
                    }
                }
            } else {
                for (var row : paid) {
                    String revenue = null;
                    if (project.budget() == null || project.budget().signum() <= 0) {
                        issues.add(issue(original, "턴키 예산 미확인: 프로젝트의 저장 예산을 확인해 주세요.", "TB_PJ_PROJ", project.budget()));
                    } else if (row.laborAllocated() != null && project.contractAmount() != null) {
                        revenue = checked(MonthlyCostCalculation.allocate(new BigDecimal(row.laborAllocated()),
                            project.contractAmount(), project.budget()), original, issues);
                    } else {
                        issues.add(issue(original, "턴키 대체 단가의 노동비 또는 계약금액이 없습니다.", "TB_PJ_PROJ", null));
                    }
                    revenues.put(row.key(), revenue);
                }
            }
            // The project editor's existing comparison owns the mismatch definition. No upload logic here.
            var comparison = comparison(sources, project);
            for (var mismatch : comparison.items()) {
                if (original.personId().equals(mismatch.developerId())
                    && original.month().equals(mismatch.month().replace("-", ""))) {
                    var diagnostic = issue(original, "턴키 배정/배분 불일치: " + mismatch.type(),
                        "TB_PJ_TK_DSTRB", new BigDecimal(mismatch.amount()));
                    if (!issues.contains(diagnostic)) issues.add(diagnostic);
                }
            }
        }
        var rows = original.rows().stream().map(r -> revenues.containsKey(r.key())
            ? withRevenue(r, revenues.get(r.key()), original, issues) : r).toList();
        return composed(original, rows, allocationComplete, issues);
    }

    public List<B6Dtos.Cell> selected(Sources sources, String projectId) {
        return B6Comparison.selected(sources.turnkey().stream().filter(c -> projectId.equals(c.projectId()))
            .map(c -> new B6Dtos.Cell(c.step(), Integer.parseInt(c.sequence()), c.month(), c.rowType(),
                c.personId(), c.itemName(), c.amount())).toList());
    }

    public B6Dtos.Comparison comparison(Sources sources, Project project) {
        Map<String, B6Dtos.Developer> people = new LinkedHashMap<>();
        sources.people().forEach(p -> people.put(p.personId(), new B6Dtos.Developer(p.personId(), p.managementNo(), p.name(), p.useYn())));
        sources.turnkey().stream().filter(c -> project.projectId().equals(c.projectId()) && "DEV".equals(c.rowType()))
            .forEach(c -> people.putIfAbsent(c.personId(), new B6Dtos.Developer(c.personId(), c.personId(), c.personId(), null)));
        var actuals = sources.projectMonths().stream().filter(a -> project.projectId().equals(a.projectId())
            && "ACT".equals(a.status()) && a.assignmentMm() != null && a.assignmentMm().signum() > 0).map(a -> {
                var p = people.computeIfAbsent(a.personId(), id -> new B6Dtos.Developer(id, id, id, null));
                return new B6Dtos.Actual(a.personId(), p.managementNo(), p.name(), a.month(), a.assignmentMm());
            }).toList();
        return B6Comparison.compare(new B6Dtos.Project(project.projectId(), project.name(), project.contractType(), project.status(),
            project.fromDate(), project.toDate(), project.contractAmount()), selected(sources, project.projectId()), actuals, List.copyOf(people.values()));
    }

    /** View-only TK exclusion: amounts, MM, source rows and diagnostics in the original stay immutable. */
    public MonthlyPersonCost project(MonthlyPersonCost original, boolean includeTurnkey) {
        if (includeTurnkey) return original;
        var issues = new ArrayList<>(original.completeness().issues());
        var rows = original.rows().stream().map(r -> "TK".equals(r.contractType())
            ? withRevenue(r, "0", original, issues) : r).toList();
        return composed(original, rows, original.completeness().allocationComplete(), issues);
    }

    private static MonthlyPersonCost composed(MonthlyPersonCost original, List<MonthlyCostRow> rows,
        boolean allocationComplete, List<MonthlyCostIssue> issues) {
        boolean revenueComplete = rows.isEmpty() ? original.completeness().revenueComplete()
            : rows.stream().allMatch(MonthlyCostRow::revenueComplete)
                && (original.completeness().revenueComplete()
                    || original.rows().stream().anyMatch(r -> !r.revenueComplete()));
        return new MonthlyPersonCost(original.personId(), original.month(), rows, original.laborSource(),
            new MonthlyCostCompleteness(original.completeness().costComplete(), revenueComplete,
                original.completeness().estimated(), allocationComplete, List.copyOf(new LinkedHashSet<>(issues))),
            original.knownLaborAllocated(), rows.isEmpty() ? original.knownPersonalRevenue()
                : money(rows.stream().map(MonthlyCostRow::personalRevenue).filter(Objects::nonNull)
                .map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add)), original.assignmentMm(),
            original.idleRowGenerated(), original.indirectRowGenerated());
    }
    private static MonthlyCostRow withRevenue(MonthlyCostRow r, String revenue, MonthlyPersonCost owner, List<MonthlyCostIssue> issues) {
        String profit = revenue == null || r.totalCost() == null ? null
            : checked(new BigDecimal(revenue).subtract(new BigDecimal(r.totalCost())), owner, issues);
        String rate = revenue == null || new BigDecimal(revenue).signum() <= 0 || profit == null ? null
            : MonthlyCostCalculation.rate(new BigDecimal(profit), new BigDecimal(revenue)).toPlainString();
        return new MonthlyCostRow(r.personId(), r.projectId(), r.assignmentId(), r.month(), r.assignmentMm(), r.freeMm(),
            revenue, r.laborAllocated(), r.teamExpense(), r.projectExpense(), r.directExpense(), r.totalCost(), profit,
            r.costClass(), r.contractType(), r.projectSalesPartId(), r.fixed(), r.projectName(), r.assignmentJobCode(), r.inputType(),
            rate, r.estimated(), r.costComplete(), revenue != null && (r.totalCost() == null || profit != null), r.rowGenerated(),
            r.regUserId(), r.regDttm(), r.modUserId(), r.modDttm());
    }
    private static String checked(BigDecimal amount, MonthlyPersonCost owner, List<MonthlyCostIssue> issues) {
        try { return money(EstimatedPayrollAdapter.required(amount)); }
        catch (IllegalArgumentException | ArithmeticException e) {
            issues.add(issue(owner, "턴키 계산 금액이 NUMERIC(15) 범위를 초과합니다.", "TB_PJ_TK_DSTRB", amount));
            return null;
        }
    }
    private static MonthlyCostIssue issue(MonthlyPersonCost owner, String message, String source, BigDecimal amount) {
        return new MonthlyCostIssue(owner.personId(), owner.month(), message, source, "UI-01", amount == null ? null : amount.toPlainString());
    }
}
