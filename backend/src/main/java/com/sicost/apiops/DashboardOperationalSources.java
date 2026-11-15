package com.sicost.apiops;

import com.sicost.forecast.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class DashboardOperationalSources {
    private final ProjectAnalysisRepository projects;
    private final ProjectCostCalculation costs;
    private final ProjectStatusCalculation statuses;
    private final SalesForecastRepository forecast;
    private final SalesWorkforceProjectionService projections;
    private final WithdrawalService withdrawals;
    public DashboardOperationalSources(ProjectAnalysisRepository projects,ProjectCostCalculation costs,ProjectStatusCalculation statuses,
        SalesForecastRepository forecast,SalesWorkforceProjectionService projections,WithdrawalService withdrawals) {
        this.projects=projects;this.costs=costs;this.statuses=statuses;this.forecast=forecast;this.projections=projections;this.withdrawals=withdrawals;
    }
    public record Projects(ProjectAnalysisDtos.Snapshot snapshot,List<Map<String,Object>> costs,List<Map<String,Object>> board) {}
    public Projects projects(LocalDate day) {
        YearMonth month=YearMonth.from(day);var snapshot=projects.load(month);
        List<Map<String,Object>> rows=new ArrayList<>(),board=new ArrayList<>();
        for(var project:snapshot.sources().projects()) {
            var cost=costs.row(snapshot,project,month);
            if(ProjectAnalysisService.costEligible(project,month))rows.add(cost);
            if(Set.of("ING","PRE").contains(project.status())&&!project.toDate().isBefore(day))board.add(statuses.row(snapshot,project,cost,day));
        }
        return new Projects(snapshot,List.copyOf(rows),List.copyOf(board));
    }
    public record Forecast(SalesForecastSources.Snapshot sources,SalesForecastDtos.Workforce workforce) {}
    public Forecast forecast(LocalDate day) {
        YearMonth month=YearMonth.from(day);var sources=forecast.load(month);
        return new Forecast(sources,projections.calculate(sources,day,month.plusMonths(1),6,true));
    }
    public WithdrawalDtos.Guidance withdrawals(LocalDate day) {
        return withdrawals.listAll(new WithdrawalInput.Query(day.getYear(),"ALL","ALL",null,1,100)).guidance();
    }
}
