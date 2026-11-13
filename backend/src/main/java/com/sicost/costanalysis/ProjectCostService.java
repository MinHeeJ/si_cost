package com.sicost.costanalysis;

import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.costanalysis.ProjectCostDtos.*;
import static com.sicost.costanalysis.ProjectCostCalculation.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ProjectCostService {
    private final ProjectCostRepository repository;
    private final ProjectCostCalculation calculation;
    public ProjectCostService(ProjectCostRepository repository,ProjectCostCalculation calculation) {this.repository=repository;this.calculation=calculation;}
    public Detail getProjectCostAnalysis(String id,String month) {return calculation.detail(calculation.view(repository.load(id,month)));}
    public Months getProjectCostMonths(String id,String month) {return calculation.months(calculation.view(repository.load(id,month)));}
    public Assignments getProjectCostAssignments(String id,String month) {return calculation.assignments(calculation.view(repository.load(id,month)));}
    public Causes getProjectCostCauses(String id,String month) {return calculation.causes(calculation.view(repository.load(id,month)));}
    public SimulationResult createProjectCostSimulation(String id,SimulationInput input) {
        var view=calculation.view(repository.load(id,input.month()));var detail=calculation.detail(view);
        if(!detail.simulationDefaults().enabled())throw invalid("c",detail.simulationDefaults().reason());
        boolean tk="TK".equals(detail.row().contractType());
        if(!tk&&input.r()==null)throw invalid("r","월단위·파견은 1인 월단가를 입력해 주세요.");
        int m=input.m()==null?detail.simulationDefaults().m():input.m();
        BigDecimal n=BigDecimal.valueOf(input.n()),c=number(input.c());
        BigDecimal revenue,cost,virtualRevenue,virtualCost;
        if(tk) {
            revenue=number(detail.row().contractAmount());
            BigDecimal remaining=number(detail.forecast().knownRemainingCost());
            BigDecimal virtualRemaining=remaining.add(n.multiply(c).multiply(BigDecimal.valueOf(m)));
            if(virtualRemaining.signum()<0)throw invalid("n","가상 남은 비용은 0원 이상이어야 합니다. 인원·원가·개월의 유효 범위를 확인해 주세요.");
            cost=number(detail.row().cumulativeCost()).add(remaining);virtualRevenue=revenue;
            virtualCost=number(detail.row().cumulativeCost()).add(virtualRemaining);
        } else {
            var current=calculation.months(view).items().stream().filter(x->detail.month().equals(x.month())).findFirst().orElseThrow();
            revenue=number(current.revenue());cost=number(current.totalCost());
            virtualRevenue=revenue.add(n.multiply(number(input.r())));virtualCost=cost.add(n.multiply(c));
        }
        if(!positive(virtualRevenue))throw invalid("n","가상 매출은 0원보다 커야 합니다. 인원·단가의 유효 범위를 확인해 주세요.");
        if(virtualCost.signum()<0)throw invalid("n","가상 비용은 0원 이상이어야 합니다. 인원·원가의 유효 범위를 확인해 주세요.");
        BigDecimal beforeProfit=revenue.subtract(cost),afterProfit=virtualRevenue.subtract(virtualCost);
        BigDecimal target=number(detail.row().targetMargin());
        String difference=afterProfit.multiply(revenue).subtract(beforeProfit.multiply(virtualRevenue)).multiply(new BigDecimal("100"))
            .divide(revenue.multiply(virtualRevenue),1,RoundingMode.HALF_UP).toPlainString();
        return new SimulationResult(id,detail.month(),margin(revenue,cost),margin(virtualRevenue,virtualCost),difference,
            targetGap(beforeProfit,revenue,target),targetGap(afterProfit,virtualRevenue,target),money(virtualRevenue),money(virtualCost),false,
            tk?"가상·저장되지 않음":"가상·저장되지 않음 · 월단위·파견은 한 달 기준이며 적용개월(m)은 결과에 사용하지 않습니다.",detail.completeness());
    }
    private static String targetGap(BigDecimal profit,BigDecimal revenue,BigDecimal target) {
        return target==null?null:profit.multiply(new BigDecimal("100")).subtract(target.multiply(revenue)).divide(revenue,1,RoundingMode.HALF_UP).toPlainString();
    }
    private static ApiException invalid(String field,String message) {return new ApiException(400,"INVALID_INPUT",message,field,null);}
}
