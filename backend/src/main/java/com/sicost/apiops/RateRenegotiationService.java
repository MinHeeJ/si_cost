package com.sicost.apiops;

import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.profitability.MonthlyCostDtos.*;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.apiops.RateRenegotiationDtos.*;
import static com.sicost.apiops.RateRenegotiationCalculator.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RateRenegotiationService {
    private final RateRenegotiationRepository repository;
    private final RateRenegotiationCalculator calculator;
    public RateRenegotiationService(RateRenegotiationRepository repository, RateRenegotiationCalculator calculator) {
        this.repository = repository; this.calculator = calculator;
    }
    public Result getRateRenegotiations(RateRenegotiationInput.Query query) {
        return project(repository.load(), query, false);
    }
    public Result export(RateRenegotiationInput.Query query) { return project(repository.load(),query,true); }
    public Result project(RateRenegotiationRepository.Snapshot snapshot, RateRenegotiationInput.Query q, boolean allRows) {
        Sources s = snapshot.sources();
        String month = java.time.YearMonth.from(s.asOfDate()).toString(), dbMonth = DatabaseMonthCodec.toDatabase(month);
        Map<String, Person> people = new HashMap<>(); s.people().forEach(p -> people.put(p.personId(), p));
        Map<String, Project> projects = new HashMap<>(); s.projects().forEach(p -> projects.put(p.projectId(), p));
        if (q.projectId() != null && !projects.containsKey(q.projectId())) throw new com.sicost.common.api.ApiException(404, "NOT_FOUND", "프로젝트를 찾을 수 없습니다.", "projectId", null);
        Map<CostKey, MonthlyCostRow> costs = new HashMap<>();
        snapshot.costs().items().stream().filter(p -> dbMonth.equals(p.month())).flatMap(p -> p.rows().stream()).forEach(r -> costs.put(r.key(), r));
        BigDecimal target = s.standards().stream().filter(v -> "TGT_MRGN_RT".equals(v.code()) && v.fromMonth().compareTo(dbMonth) <= 0)
            .max(Comparator.comparing(Standard::fromMonth)).map(Standard::value).orElse(null);
        List<Row> rows = new ArrayList<>(); List<Issue> unevaluated = new ArrayList<>();
        for (var candidate : snapshot.candidates()) {
            var a = candidate.source(); var m = candidate.month(); var p = projects.get(a.projectId()); var person = people.get(a.developerId());
            if (person == null || p == null || !"ACT".equals(a.status()) || !"ING".equals(p.status())
                || (p.toDate() != null && p.toDate().isBefore(s.asOfDate())) || !Set.of("MO", "DP").contains(p.contractType())
                || "FREE".equals(a.inputType()) || a.endDate().isBefore(s.asOfDate())) continue;
            int days = Math.toIntExact(ChronoUnit.DAYS.between(s.asOfDate(), a.endDate()));
            String employee = s.payroll().stream().filter(pay -> pay.personId().equals(a.developerId()) && pay.month().equals(dbMonth))
                .map(Payroll::employeeType).findFirst().orElse(person.employeeType());
            if ((!"ALL".equals(q.renewalWindow()) && days > Integer.parseInt(q.renewalWindow()))
                || (q.employeeType() != null && !q.employeeType().equals(employee))
                || (q.projectId() != null && !q.projectId().equals(a.projectId()))) continue;
            BigDecimal mm = decimal(m.monthlyMm()), unit = decimal(m.appliedUnitPrice()), revenue = decimal(m.salesAmount());
            if ((mm != null && mm.signum() <= 0) || (revenue != null && revenue.signum() <= 0)) continue;
            MonthlyCostRow cost = costs.get(new CostKey(dbMonth, a.developerId(), a.projectId(), a.id()));
            BigDecimal labor = cost == null ? null : decimal(cost.laborAllocated());
            if (!positive(mm) || !positive(unit) || !positive(revenue) || labor == null) {
                String field = !positive(mm) ? "monthlyMm" : !positive(unit) ? "unitPrice" : !positive(revenue) ? "monthlyRevenue" : "monthlyLaborCost";
                unevaluated.add(new Issue("SOURCE_UNKNOWN", person.name() + " / " + p.name() + ": 배정 계산 원천을 확인할 수 없습니다.", field,
                    a.developerId(), a.id(), a.projectId(), month, field.equals("monthlyLaborCost") ? "TB_CO_MON_COST" : "TB_PJ_ASGN_MON", true));
                continue;
            }
            if (!calculator.marginBelow(revenue, labor, q.marginBelow())) continue;
            var metrics = calculator.calculate(new Amounts(revenue, labor, unit, mm, decimal(m.contractMm()), target, q.increaseRate()));
            List<Issue> issues = validTarget(target) ? List.of() : List.of(new Issue("TARGET_UNAVAILABLE", "목표 이익률로 단가를 계산할 수 없습니다", "targetMarginRate",
                a.developerId(), a.id(), a.projectId(), month, "TB_SY_OPER_STD", false));
            rows.add(new Row(a.id(), a.developerId(), person.name(), person.managementNo(), employee, p.projectId(), p.name(),
                a.endDate().toString(), days, a.startDate().isAfter(s.asOfDate()), m.contractMm(), m.monthlyMm(), whole(unit), whole(revenue), whole(labor),
                metrics.marginRate(), metrics.targetUnitPrice(), metrics.requiredIncreaseRate(), metrics.annualGain(), metrics.targetAnnualGain(), issues));
        }
        rows.sort(comparator(q));
        Summary summary = calculator.summarize(rows);
        int total = rows.size(); long offset = (long) (q.page() - 1) * q.size();
        List<Row> page = allRows ? List.copyOf(rows) : offset >= total ? Collections.emptyList()
            : List.copyOf(rows.subList((int) offset, (int) Math.min(offset + q.size(), total)));
        return new Result(month, s.period().calculatedAt(), q.increaseRate().toPlainString(), target == null ? null : target.toPlainString(),
            summary, page, new Page(q.page(), q.size(), total), unevaluated.size(), List.copyOf(unevaluated),
            validTarget(target) ? List.of() : List.of(new Issue("TARGET_UNAVAILABLE", "목표 이익률로 단가를 계산할 수 없습니다", "targetMarginRate", null, null, null, month, "TB_SY_OPER_STD", false)));
    }
    private static Comparator<Row> comparator(RateRenegotiationInput.Query q) {
        Comparator<Row> primary = (a, b) -> {
            String left = value(a, q.sort()), right = value(b, q.sort());
            if (left == null || right == null) return left == null ? right == null ? 0 : 1 : -1;
            int result;
            if (Set.of("name", "projectName", "renewalDate").contains(q.sort())) result = left.compareTo(right);
            else if (q.sort().equals("marginRate")) result = marginNumerator(a).multiply(decimal(b.monthlyRevenue())).compareTo(marginNumerator(b).multiply(decimal(a.monthlyRevenue())));
            else if (q.sort().equals("requiredIncreaseRate")) result = delta(a).multiply(decimal(b.unitPrice())).compareTo(delta(b).multiply(decimal(a.unitPrice())));
            else result = decimal(left).compareTo(decimal(right));
            return "DESC".equals(q.direction()) ? -result : result;
        };
        return primary.thenComparing(q.sort().equals("marginRate") ? Comparator.comparing(Row::renewalDate).thenComparing(Row::assignmentId) : Comparator.comparing(Row::assignmentId));
    }
    private static BigDecimal marginNumerator(Row r) { return decimal(r.monthlyRevenue()).subtract(decimal(r.monthlyLaborCost())); }
    private static BigDecimal delta(Row r) { return decimal(r.targetUnitPrice()).subtract(decimal(r.unitPrice())).max(BigDecimal.ZERO); }
    private static String value(Row r, String sort) {
        return switch (sort) {
            case "name" -> r.name(); case "projectName" -> r.projectName(); case "renewalDate" -> r.renewalDate();
            case "contractMm" -> r.contractMm(); case "unitPrice" -> r.unitPrice(); case "monthlyLaborCost" -> r.monthlyLaborCost();
            case "marginRate" -> r.marginRate(); case "targetUnitPrice" -> r.targetUnitPrice();
            case "requiredIncreaseRate" -> r.requiredIncreaseRate(); default -> r.annualGain();
        };
    }
    private static BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
}
