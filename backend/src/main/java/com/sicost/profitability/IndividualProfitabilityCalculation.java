package com.sicost.profitability;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static com.sicost.profitability.IndividualProfitabilityInput.Query;

/** Pure annual projection. All population policies are downstream of the common company result. */
@Component
public class IndividualProfitabilityCalculation {
    private final TurnkeyRevenueCalculation turnkey;
    public IndividualProfitabilityCalculation(TurnkeyRevenueCalculation turnkey) { this.turnkey = turnkey; }
    public static final List<String> BASIS = List.of(
        "개인 단가는 청구·입금액이 아닌 개인 배분 참고액입니다.",
        "월평균은 사람별 근무월 N으로 기초금액을 원 단위 0쪽 절사합니다. 급여구성 합과 개인 원가는 절사차이가 날 수 있습니다.",
        "가동률은 운영 이후 직접 재직달 중 실제 투입달 ÷ 직접 재직달입니다. 0.40MM도 투입 1달이며 전체휴직은 투입달에서 제외합니다.",
        "원가=개인인건비+팀비+프로젝트경비, 이익=개인단가−원가. 본인공제·재무비용·추가 직접경비를 가산하지 않습니다.",
        "월인건비 P: FT=급여+수당+회사보험+퇴직충당금, CT=급여+수당+회사보험, FL=공제전 지급액, OS=부가세전 공급가입니다. 본인공제와 VAT는 원가에 가감하지 않습니다.",
        "업로드 월급여와 당시 직원구분·직접구분이 우선입니다. 미마감 급여 미업로드만 유효 기본급여와 DB 수당 기준으로 예정 계산하며 급여를 저장하지 않습니다.",
        "배분은 원금×월 MM÷분모를 최종 원 단위 0쪽 절사합니다. FT/CT 분모=max(유효 월 MM합,1.00), FL/OS 분모=유효 월 MM합입니다. 프로젝트ID·투입ID순 마지막 행에 잔액을 보정하며 FT/CT의 1.00 미만 잔액은 대기에 배분합니다.",
        "간접은 INDRCT, 전체휴직 FT/CT는 IDLE에 노동비를 배분합니다. 프로젝트는 실주 IDLE→내부 RND→선투입 PRE→일반 ALLOC 순으로 분류하며 무상지원도 노동비를 배분합니다.",
        "팀비·프로젝트경비는 프로젝트에 저장된 각 금액×원본 월 MM을 원 단위 절사합니다. OS와 대기·간접 고정행의 두 경비는 0이며 공통 기본값으로 저장 프로젝트 경비를 재생성하지 않습니다.",
        "턴키 개인 단가는 선택 단계 배분표의 실제 DEV_ID별 금액을 해당 월 유상 MM으로 배분하고 마지막 행을 보정합니다. 해당 사람의 표가 없을 때만 배분 노동비×저장 계약금액÷저장 예산을 사용하며 예산 미확인은 0으로 대체하지 않습니다.",
        "월평균 N은 FT/CT 직접 재직 운영 근무월(대기 포함), FL/OS 실제 MM이 있는 달입니다. 전체 무급휴직은 제외하고 유급휴직은 포함하며 N=0의 금액은 미확인입니다. 사람별 monthBasis에 포함 여부와 분모 근거를 제공합니다.",
        "CLOSE·FIX_YN=Y·운영 시작월 이전은 저장 원가 사본을 유지합니다. 사본이 없으면 현재 급여로 복원하지 않으며 유효한 금액 0과 자료 없음을 구별합니다.",
        "P=월인건비, C=P+팀비+프로젝트경비, G=개인단가−C, 이익률은 개인단가>0일 때 G÷개인단가×100입니다.");

    public List<IndividualProfitabilityPerson> people(Sources sources, MonthlyCostsData common, Query query) {
        Map<String, List<MonthlyPersonCost>> grouped = new HashMap<>();
        common.items().forEach(m -> grouped.computeIfAbsent(m.personId(), ignored -> new ArrayList<>()).add(m));
        List<IndividualProfitabilityPerson> result = new ArrayList<>();
        if (sources.period().noData()) return List.copyOf(result);
        String last = sources.period().toMonth();
        LocalDate end = last.equals(month(YearMonth.from(sources.asOfDate()))) ? sources.asOfDate() : parseMonth(last).atEndOfMonth();
        for (Person person : sources.people()) {
            var months = grouped.getOrDefault(person.personId(), Collections.emptyList());
            String effective = person.retireDate() != null && YearMonth.from(person.retireDate()).isBefore(parseMonth(last))
                ? month(YearMonth.from(person.retireDate())) : last;
            if (effective.compareTo(sources.period().fromMonth()) < 0 || !person.employed(effective)) continue;
            var at = months.stream().filter(m -> m.month().equals(effective)).findFirst().orElse(null);
            if (!direct(person, at) || leaveAt(sources, person, end)) continue;
            var direct = months.stream().filter(m -> person.employed(m.month()) && direct(person, m)).toList();
            var projected = direct.stream().map(m -> turnkey.project(m, query.includeTurnkey())).toList();
            var bases = direct.stream().map(m -> basis(sources, person, m)).toList();
            int n = (int) bases.stream().filter(ProfitabilityMonthBasis::averageMonthIncluded).count();
            int employment = (int) bases.stream().filter(ProfitabilityMonthBasis::employmentMonthIncluded).count();
            int utilized = (int) bases.stream().filter(ProfitabilityMonthBasis::utilizedMonthIncluded).count();
            boolean excluded = !query.includeTurnkey() && tkOnly(direct);
            boolean costs = !direct.isEmpty() && projected.stream().allMatch(m -> m.completeness().costComplete());
            boolean revenues = !direct.isEmpty() && projected.stream().allMatch(m -> m.completeness().revenueComplete());
            var complete = new MonthlyCostCompleteness(costs, revenues,
                projected.stream().anyMatch(m -> m.completeness().estimated()),
                projected.stream().allMatch(m -> m.completeness().allocationComplete()),
                projected.stream().flatMap(m -> m.completeness().issues().stream()).distinct().toList());
            var metrics = metrics(projected, n, query.view(), excluded, complete);
            var row = new IndividualProfitabilityPerson(person.personId(), person.managementNo(), person.name(), person.companyName(),
                person.companyId(), person.personPartId(), person.personPartName(), person.position(),
                at == null || at.laborSource().employeeType().isBlank() ? person.employeeType() : at.laborSource().employeeType(),
                person.hireDate(), person.jobCode(), person.gradeCode(), metrics, rate(BigDecimal.valueOf(utilized), BigDecimal.valueOf(employment)),
                n, employment, utilized, excluded, bases, complete);
            if (matches(person, row, query)) result.add(row);
        }
        result.sort(order(query));
        return List.copyOf(result);
    }
    private static boolean direct(Person person, MonthlyPersonCost m) {
        String classification = m == null ? "" : m.laborSource().directClass();
        return "DRCT".equals(classification.isBlank() ? person.directClass() : classification);
    }
    private static boolean leaveAt(Sources s, Person p, LocalDate date) {
        return (p.retireDate() == null || !p.retireDate().isBefore(date)) && s.states().stream()
            .anyMatch(w -> w.personId().equals(p.personId()) && "LEAVE".equals(w.status()) && w.includes(date));
    }
    private static ProfitabilityMonthBasis basis(Sources s, Person person, MonthlyPersonCost m) {
        var rows = actual(m);
        BigDecimal mm = rows.stream().map(r -> number(r.assignmentMm())).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean leave = MonthlyCostCalculation.fullMonthLeave(s.states(), person.personId(), m.month());
        boolean employed = person.employed(m.month()) && direct(person, m) && m.month().compareTo(s.period().baseMonth()) >= 0;
        boolean utilized = employed && !leave && mm.signum() > 0;
        String type = m.laborSource().employeeType().isBlank() ? person.employeeType() : m.laborSource().employeeType();
        boolean salaried = Set.of("FT", "CT").contains(type);
        boolean paid = m.laborSource().laborCost() != null && number(m.laborSource().laborCost()).signum() != 0;
        boolean n = employed && (salaried ? !leave || paid : utilized);
        return new ProfitabilityMonthBasis(m.month(), mm(mm), rows.size(), n, employed, utilized,
            "직접 재직 운영월=" + employed + ", 전체휴직=" + leave + ", 평균 근무월=" + n + ", 실제 투입달=" + utilized);
    }
    private static List<MonthlyCostRow> actual(MonthlyPersonCost m) {
        return m.rows().stream().filter(r -> !MonthlyCostService.synthetic(r)).toList();
    }
    public MonthlyPersonCost detailMonth(MonthlyPersonCost original, boolean includeTurnkey) {
        return turnkey.project(original, includeTurnkey);
    }
    private static boolean tkOnly(List<MonthlyPersonCost> months) {
        var actual = months.stream().flatMap(m -> actual(m).stream()).filter(r -> number(r.assignmentMm()).signum() > 0).toList();
        return !actual.isEmpty() && actual.stream().allMatch(r -> "TK".equals(r.contractType()))
            && actual.stream().anyMatch(r -> !"FREE".equals(r.inputType()) && !Set.of("RND", "IDLE", "INDRCT").contains(r.costClass()));
    }
    private static IndividualProfitabilityMetrics metrics(List<MonthlyPersonCost> months, int n, String view,
        boolean excluded, MonthlyCostCompleteness complete) {
        boolean average = "MONTHLY_AVERAGE".equals(view);
        var rows = months.stream().flatMap(m -> m.rows().stream()).toList();
        var actual = rows.stream().filter(r -> !MonthlyCostService.synthetic(r)).toList();
        String labor = complete.costComplete() ? sum(months, m -> m.laborSource().laborCost()) : null;
        String team = complete.costComplete() ? sum(actual, MonthlyCostRow::teamExpense) : null;
        String expense = complete.costComplete() ? sum(actual, MonthlyCostRow::projectExpense) : null;
        String revenue = complete.revenueComplete() ? sum(rows, MonthlyCostRow::personalRevenue) : null;
        labor = average(labor, n, average); team = average(team, n, average); expense = average(expense, n, average);
        revenue = average(revenue, n, average);
        String cost = add(labor, team, expense);
        String profit = cost == null || revenue == null ? null : money(number(revenue).subtract(number(cost)));
        String mm = mm(actual.stream().map(r -> number(r.assignmentMm())).reduce(BigDecimal.ZERO, BigDecimal::add));
        if (average) mm = n == 0 ? null : number(mm).divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP).toPlainString();
        return new IndividualProfitabilityMetrics(excluded ? null : revenue,
            salary(months, MonthlyLaborSource::pay, n, average), salary(months, MonthlyLaborSource::allowance, n, average),
            salary(months, MonthlyLaborSource::companyInsurance, n, average), salary(months, MonthlyLaborSource::retirementReserve, n, average),
            labor, team, expense, cost, profit, salary(months, MonthlyLaborSource::selfInsurance, n, average),
            salary(months, MonthlyLaborSource::withholdingTax, n, average), salary(months, MonthlyLaborSource::incomeTax, n, average),
            salary(months, MonthlyLaborSource::netPay, n, average), mm, rate(profit, excluded ? null : revenue));
    }
    private static String salary(List<MonthlyPersonCost> months, Function<MonthlyLaborSource, String> field, int n, boolean average) {
        return average(sum(months, m -> field.apply(m.laborSource())), n, average);
    }
    private static String average(String amount, int n, boolean average) {
        return !average ? amount : amount == null || n == 0 ? null : number(amount).divide(BigDecimal.valueOf(n), 0, RoundingMode.DOWN).toPlainString();
    }
    public static String rate(String profit, String revenue) {
        return profit == null || revenue == null ? null : rate(number(profit), number(revenue));
    }
    private static String rate(BigDecimal numerator, BigDecimal denominator) {
        return denominator.signum() <= 0 ? null : numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 1, RoundingMode.HALF_UP).toPlainString();
    }
    private static String add(String... amounts) {
        return Arrays.stream(amounts).anyMatch(Objects::isNull) ? null
            : money(Arrays.stream(amounts).map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    private static BigDecimal number(String amount) { return new BigDecimal(amount); }
    private static <T> String sum(List<T> rows, Function<T, String> field) {
        return rows.stream().anyMatch(r -> field.apply(r) == null) ? null
            : money(rows.stream().map(field).map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    private static String month(YearMonth month) { return month.toString().replace("-", ""); }
    private static boolean matches(Person p, IndividualProfitabilityPerson row, Query q) {
        return (q.name() == null || p.name().contains(q.name()))
            && (q.employeeType() == null || q.employeeType().equals(row.employeeType()))
            && (q.companyId() == null || ("SELF".equals(q.companyId()) ? p.companyId() == null : q.companyId().equals(p.companyId())))
            && p.matchesPart(q.partId()) && (q.jobCode() == null || q.jobCode().equals(p.jobCode()))
            && (!"LOSS".equals(q.show()) || row.metrics().profit() != null && number(row.metrics().profit()).signum() < 0)
            && (!"CONCURRENT".equals(q.show()) || row.monthBasis().stream().anyMatch(m -> m.assignmentCount() >= 2 || number(m.assignmentMm()).compareTo(BigDecimal.ONE) > 0));
    }
    public static Comparator<IndividualProfitabilityPerson> order(Query q) {
        Comparator<IndividualProfitabilityPerson> fallback = Comparator.comparing(IndividualProfitabilityPerson::personPartName,
            Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(IndividualProfitabilityPerson::name)
            .thenComparing(IndividualProfitabilityPerson::personId);
        if (q.sort() == null) return fallback;
        boolean numeric = Set.of("utilization", "mm", "revenue", "totalCost", "profit", "profitRate").contains(q.sort());
        Comparator<String> values = numeric ? Comparator.comparing(BigDecimal::new) : Comparator.naturalOrder();
        if ("DESC".equals(q.direction())) values = values.reversed();
        return Comparator.comparing((IndividualProfitabilityPerson p) -> sortValue(p, q.sort()), Comparator.nullsLast(values)).thenComparing(fallback);
    }
    private static String sortValue(IndividualProfitabilityPerson p, String key) {
        return switch (key) {
            case "name" -> p.name(); case "companyName" -> p.companyName(); case "employeeType" -> p.employeeType();
            case "hireDate" -> p.hireDate() == null ? null : p.hireDate().toString(); case "utilization" -> p.utilization();
            case "mm" -> p.metrics().mm(); case "revenue" -> p.metrics().revenue(); case "totalCost" -> p.metrics().totalCost();
            case "profit" -> p.metrics().profit(); case "profitRate" -> p.metrics().profitRate(); default -> throw new IllegalArgumentException("정렬 열");
        };
    }
    public IndividualProfitabilityMetrics totals(List<IndividualProfitabilityPerson> people) {
        var m = people.stream().map(IndividualProfitabilityPerson::metrics).toList();
        String revenue = people.stream().anyMatch(p -> !p.completeness().revenueComplete()) ? null
            : money(m.stream().map(IndividualProfitabilityMetrics::revenue).filter(Objects::nonNull).map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add));
        String profit = sum(m, IndividualProfitabilityMetrics::profit);
        // MM has a fractional scale; all money fields are exact integer-won strings.
        String mm = m.stream().anyMatch(x -> x.mm() == null) ? null : mm(m.stream().map(x -> number(x.mm())).reduce(BigDecimal.ZERO, BigDecimal::add));
        return new IndividualProfitabilityMetrics(revenue, sum(m, IndividualProfitabilityMetrics::pay), sum(m, IndividualProfitabilityMetrics::allowance),
            sum(m, IndividualProfitabilityMetrics::companyInsurance), sum(m, IndividualProfitabilityMetrics::retirementReserve),
            sum(m, IndividualProfitabilityMetrics::personalCost), sum(m, IndividualProfitabilityMetrics::teamExpense),
            sum(m, IndividualProfitabilityMetrics::projectExpense), sum(m, IndividualProfitabilityMetrics::totalCost), profit,
            sum(m, IndividualProfitabilityMetrics::selfInsurance), sum(m, IndividualProfitabilityMetrics::withholdingTax),
            sum(m, IndividualProfitabilityMetrics::incomeTax), sum(m, IndividualProfitabilityMetrics::netPay), mm, rate(profit, revenue));
    }
    public ProfitabilitySummary summary(Sources s, MonthlyCostsData common, List<IndividualProfitabilityPerson> filtered) {
        Map<PersonMonth, MonthlyPersonCost> index = new HashMap<>();
        common.items().forEach(m -> index.put(new PersonMonth(m.month(), m.personId()), m));
        int count = 0, leaveCount = 0, h = 0, u = 0, f = 0, monthCount = 0;
        if (!s.period().noData()) {
            String last = s.period().toMonth();
            LocalDate date = last.equals(month(YearMonth.from(s.asOfDate()))) ? s.asOfDate() : parseMonth(last).atEndOfMonth();
            for (Person p : s.people()) if (p.employed(last)
                && direct(p, index.get(new PersonMonth(last, p.personId())))) { count++; if (leaveAt(s, p, date)) leaveCount++; }
            for (YearMonth ym = parseMonth(s.period().fromMonth()); !ym.isAfter(parseMonth(last)); ym = ym.plusMonths(1)) {
                monthCount++; String month = month(ym);
                for (Person p : s.people()) {
                    var m = index.get(new PersonMonth(month, p.personId()));
                    if (!p.employed(month) || !direct(p, m)) continue;
                    h++;
                    if (m == null || MonthlyCostCalculation.fullMonthLeave(s.states(), p.personId(), month)) continue;
                    if (actual(m).stream().anyMatch(r -> number(r.assignmentMm()).signum() > 0)) u++;
                    if (actual(m).stream().anyMatch(r -> Set.of("MO", "DP").contains(Objects.toString(r.contractType(), ""))
                        && number(r.assignmentMm()).subtract(number(r.freeMm())).signum() > 0
                        && !Set.of("RND", "IDLE", "INDRCT").contains(r.costClass()))) f++;
                }
            }
        }
        String ah = headAverage(h, monthCount), au = headAverage(u, monthCount), af = headAverage(f, monthCount);
        var totals = totals(filtered);
        int unknownCost = (int) filtered.stream().filter(p -> p.metrics().totalCost() == null).count();
        int unknownRevenue = (int) filtered.stream().filter(p -> !p.completeness().revenueComplete()).count();
        int validRevenue = (int) filtered.stream().filter(p -> p.metrics().revenue() != null).count();
        return new ProfitabilitySummary(count, leaveCount, ah, au, af, rate(au, ah), rate(af, ah),
            unknownRevenue > 0 ? null : average(totals.revenue(), validRevenue, true),
            average(totals.totalCost(), filtered.size(), true), average(totals.profit(), filtered.size(), true),
            (int) filtered.stream().filter(p -> p.metrics().profit() != null && number(p.metrics().profit()).signum() < 0).count(),
            unknownCost, unknownRevenue, validRevenue);
    }
    private static String headAverage(int count, int months) {
        return months == 0 ? null : BigDecimal.valueOf(count).divide(BigDecimal.valueOf(months), 1, RoundingMode.HALF_UP).toPlainString();
    }
    public List<ProfitabilityContribution> contributions(List<IndividualProfitabilityPerson> people,
        List<com.sicost.shared.SharedQueryDtos.CodeLookup> jobs, List<com.sicost.shared.SharedQueryDtos.CodeLookup> grades) {
        Map<String, String> jobNames = new LinkedHashMap<>(), gradeNames = new LinkedHashMap<>();
        jobs.forEach(j -> jobNames.put(j.code(), j.name())); grades.forEach(g -> gradeNames.put(g.code(), g.name()));
        gradeNames.put("UNCLASSIFIED", "미분류"); gradeNames.put("ALL", "전체");
        people.forEach(p -> jobNames.putIfAbsent(p.jobCode(), p.jobCode()));
        var result = new ArrayList<ProfitabilityContribution>();
        jobNames.forEach((job, jobName) -> gradeNames.forEach((grade, gradeName) -> {
            var members = people.stream().filter(p -> job.equals(p.jobCode()) && ("ALL".equals(grade)
                || grade.equals(p.gradeCode()) || "UNCLASSIFIED".equals(grade) && !gradeNames.containsKey(p.gradeCode()))).toList();
            int unknown = (int) members.stream().filter(p -> p.metrics().profit() == null).count();
            result.add(new ProfitabilityContribution(job, jobName, grade, gradeName, members.size(), unknown,
                average(sum(members, p -> p.metrics().profit()), members.size(), true)));
        }));
        return List.copyOf(result);
    }
}
