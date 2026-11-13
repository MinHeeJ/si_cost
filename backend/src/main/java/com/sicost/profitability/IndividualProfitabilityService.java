package com.sicost.profitability;

import com.sicost.common.api.ApiException;
import com.sicost.common.validation.ProjectInputPolicy;
import com.sicost.shared.SharedQueriesService;
import com.sicost.shared.SharedQueryDtos.CodeLookup;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static com.sicost.profitability.IndividualProfitabilityInput.Query;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class IndividualProfitabilityService {
    private final MonthlyCostService monthly;
    private final IndividualProfitabilityCalculation calculation;
    private final SharedQueriesService shared;
    private final MonthlyCostMapper mapper;
    public IndividualProfitabilityService(MonthlyCostService monthly, IndividualProfitabilityCalculation calculation,
        SharedQueriesService shared, MonthlyCostMapper mapper) {
        this.monthly = monthly; this.calculation = calculation; this.shared = shared; this.mapper = mapper;
    }
    /** Reusable export seam: call once inside its existing read-only snapshot; derive both TK views from it. */
    public Snapshot snapshot(Query query) {
        Sources sources = monthly.loadYear(query.year());
        var jobs = shared.getCodes("JOB", true).items();
        var grades = shared.getCodes("GRADE", true).items();
        validateCode(query.jobCode(), jobs, "jobCode");
        validateCode(query.employeeType(), shared.getCodes("EMP_TYPE", true).items(), "employeeType");
        if (query.companyId() != null && !"SELF".equals(query.companyId())
            && mapper.companies().stream().noneMatch(c -> c.companyId().equals(query.companyId()))) throw ProjectInputPolicy.invalid("companyId");
        if (query.partId() != null && !"UNASSIGNED".equals(query.partId())
            && sources.parts().stream().noneMatch(p -> p.partId().equals(query.partId()))) throw ProjectInputPolicy.invalid("partId");
        return new Snapshot(sources, monthly.calculate(sources), jobs, grades);
    }
    public record Snapshot(Sources sources, MonthlyCostsData common, List<CodeLookup> jobs, List<CodeLookup> grades) {}
    public IndividualProfitabilityData getIndividualProfitability(Query query) { return project(snapshot(query), query, false); }
    /** All pages/totals/summary/contributions are derived from one immutable source set. */
    public IndividualProfitabilityData project(Snapshot snapshot, Query query, boolean allRows) {
        var people = calculation.people(snapshot.sources(), snapshot.common(), query);
        long offset = ((long) query.page() - 1) * query.size();
        var page = allRows ? people : people.stream().skip(offset).limit(query.size()).toList();
        return new IndividualProfitabilityData(page, people.size(), query.page(), query.size(),
            new ProfitabilityViewContext(snapshot.sources().period(), query.view(), query.includeTurnkey()),
            calculation.summary(snapshot.sources(), snapshot.common(), people), calculation.totals(people),
            calculation.contributions(people, snapshot.jobs(), snapshot.grades()),
            (int) people.stream().filter(p -> !p.completeness().costComplete() || !p.completeness().revenueComplete()).count(),
            (int) people.stream().filter(p -> p.completeness().estimated()).count(), IndividualProfitabilityCalculation.BASIS);
    }
    public IndividualProfitabilityPersonData getIndividualProfitabilityPerson(String personId, Query query) {
        ProjectInputPolicy.text(personId, 20, "personId");
        Snapshot snapshot = snapshot(query);
        var person = calculation.people(snapshot.sources(), snapshot.common(), query).stream()
            .filter(p -> p.personId().equals(personId)).findFirst()
            .orElseThrow(() -> new ApiException(404, "PERSON_NOT_FOUND", "조회 조건에 해당하는 인력이 없습니다."));
        // Original monthly values (pre-average) are preserved even when the parent is an average.
        var months = detailMonths(snapshot, personId, query.includeTurnkey());
        return new IndividualProfitabilityPersonData(person,
            new ProfitabilityViewContext(snapshot.sources().period(), query.view(), query.includeTurnkey()), months,
            months.stream().flatMap(m -> m.completeness().issues().stream()).distinct().toList(), IndividualProfitabilityCalculation.BASIS);
    }
    /** Original monthly projection shared by the drawer and export without another source read. */
    public List<MonthlyPersonCost> detailMonths(Snapshot snapshot, String personId, boolean includeTurnkey) {
        return snapshot.common().items().stream().filter(m -> m.personId().equals(personId))
            .map(m -> calculation.detailMonth(m, includeTurnkey))
            .sorted(Comparator.comparing(MonthlyPersonCost::month)).toList();
    }
    private static void validateCode(String value, List<CodeLookup> codes, String field) {
        if (value != null && codes.stream().noneMatch(c -> value.equals(c.code()))) throw ProjectInputPolicy.invalid(field);
    }
}
