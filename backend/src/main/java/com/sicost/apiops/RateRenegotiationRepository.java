package com.sicost.apiops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.assignments.AssignmentMonthlyService.Month;
import com.sicost.common.assignments.AssignmentMonthlyService.Source;
import com.sicost.profitability.MonthlyCostRepository;
import com.sicost.profitability.MonthlyCostService;
import com.sicost.profitability.MonthlyCostDtos.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RateRenegotiationRepository {
    private final MonthlyCostRepository costs;
    private final MonthlyCostService monthlyCosts;
    private final AssignmentMonthlyService monthly;
    private final RateRenegotiationMapper mapper;
    private final Clock clock;
    public RateRenegotiationRepository(MonthlyCostRepository costs, MonthlyCostService monthlyCosts,
        AssignmentMonthlyService monthly, RateRenegotiationMapper mapper, Clock clock) {
        this.costs = costs; this.monthlyCosts = monthlyCosts; this.monthly = monthly; this.mapper = mapper; this.clock = clock;
    }
    public record Candidate(Source source, Month month) {}
    public record Snapshot(Sources sources, List<Candidate> candidates, MonthlyCostsData costs) {}
    public Snapshot load() {
        YearMonth current = YearMonth.now(clock.withZone(TimeConfiguration.SEOUL));
        Sources s = costs.loadMonth(current.getYear(), current.getMonthValue());
        var candidates = new ArrayList<Candidate>();
        var projected = new ArrayList<Assignment>();
        Map<String, Project> projects = new HashMap<>();
        s.projects().forEach(p -> projects.put(p.projectId(), p));
        for (Source source : mapper.assignments(s.asOfDate())) {
            Project project = projects.get(source.projectId());
            if (project == null) continue;
            var histories = monthly.mapper().histories(source.id());
            var months = monthly.preview(source, histories, project.status(), project.contractType());
            boolean present = false;
            for (Month month : months) {
                if (!current.toString().equals(month.month())) continue;
                present = true;
                projected.add(new Assignment(current.toString().replace("-", ""), source.developerId(), source.projectId(), source.id(),
                    decimal(month.monthlyMm()), decimal(month.appliedUnitPrice()), decimal(month.salesAmount()), month.fixed(),
                    source.jobCode(), source.inputType(), source.contractDeveloperId(), source.status(), source.startDate(), source.endDate()));
                candidates.add(new Candidate(source, month));
            }
            if (!present && !current.isBefore(YearMonth.from(source.startDate())) && !current.isAfter(YearMonth.from(source.endDate()))
                && "ACT".equals(source.status())) {
                BigDecimal contract = com.sicost.projectsops.ProjectAssignmentMonths.value(histories, current, true, source.contractMm());
                candidates.add(new Candidate(source, new Month(current.toString(), source.projectId(), source.developerId(), 0,
                    contract == null ? null : contract.toPlainString(), null, null, null, null)));
            }
        }
        Sources effective = new Sources(s.period(), s.actor(), s.asOfDate(), s.people(), s.prices(), s.states(), s.payroll(),
            List.copyOf(projected), s.projectMonths(), s.projects(), s.projectParts(), s.turnkey(), s.storedCosts(), s.standards(),
            s.parts(), s.closes(), s.allowanceCodes());
        return new Snapshot(effective, List.copyOf(candidates), monthlyCosts.calculate(effective));
    }
    private static BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
}
