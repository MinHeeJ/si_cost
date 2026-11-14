package com.sicost.apiops;

import com.sicost.common.api.ApiResponse;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.ProjectInputPolicy;
import com.sicost.staffingplancomparisonops.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class StaffingPlanComparisonController {
    private final StaffingPlanComparisonService service;
    public StaffingPlanComparisonController(StaffingPlanComparisonService service){this.service=service;}
    @GetMapping("/api/staffing-plan-comparison")
    public ApiResponse<ComparisonPage> getStaffingPlanComparison(@RequestParam MultiValueMap<String,String> q,@RequestBody(required=false) String body){
        StaffingQueryInput.validate(q,Set.of("year","projectId","difference","page","size","sort","direction"),body);
        return ApiResponse.ok(service.list(q));
    }
    public record Row(String projectId,String projectName,String jobCode,String planMm,String actualMm,String differenceMm){}
    public record Totals(String planMm,String actualMm,String differenceMm){}
    public record ComparisonPage(List<Row> items,int total,int page,int size,int year,Totals totals){}
}

@Service
class StaffingPlanComparisonService {
    private final StaffingComparisonRepository repository;
    private final CurrentUserProvider users;
    private final Clock clock;
    StaffingPlanComparisonService(StaffingComparisonRepository repository,CurrentUserProvider users,Clock clock){this.repository=repository;this.users=users;this.clock=clock;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public StaffingPlanComparisonController.ComparisonPage list(MultiValueMap<String,String> q){
        users.currentUser();
        int year=StaffingQueryInput.integer(q.getFirst("year"),ProjectInputPolicy.today(clock).getYear(),"year");
        if(year<repository.cashBase().getYear()||year>Math.min(9999,ProjectInputPolicy.today(clock).getYear()+1))throw StaffingQueryInput.invalid("year","INVALID_PERIOD");
        var paging=StaffingQueryInput.paging(q,Set.of("differenceMm","projectId","jobCode"),"differenceMm");
        String project=StaffingQueryInput.text(q,"projectId",20),difference=StaffingQueryInput.choice(q,"difference",Set.of("ALL","SHORT","EXCESS","MATCH"),"ALL");
        var rows=new ArrayList<>(repository.rows(new StaffingComparisonDtos.Query(year,project,difference,"THOUSAND")));
        Comparator<StaffingComparisonDtos.Row> order=switch(paging.sort()){
            case "projectId"->Comparator.comparing(StaffingComparisonDtos.Row::projectId);
            case "jobCode"->Comparator.comparing(StaffingComparisonDtos.Row::jobCode);
            default->Comparator.comparing(StaffingComparisonDtos.Row::differenceMm);
        };
        if("DESC".equals(paging.direction()))order=order.reversed();
        rows.sort(order.thenComparing(StaffingComparisonDtos.Row::projectId).thenComparing(StaffingComparisonDtos.Row::jobCode));
        BigDecimal plan=BigDecimal.ZERO.setScale(2),actual=plan;
        for(var r:rows){plan=plan.add(r.planMm());actual=actual.add(r.actualMm());}
        var items=StaffingQueryInput.page(rows,paging).stream().map(r->new StaffingPlanComparisonController.Row(r.projectId(),r.projectName(),r.jobCode(),r.planMm().toPlainString(),r.actualMm().toPlainString(),r.differenceMm().toPlainString())).toList();
        return new StaffingPlanComparisonController.ComparisonPage(items,rows.size(),paging.page(),paging.size(),year,new StaffingPlanComparisonController.Totals(plan.toPlainString(),actual.toPlainString(),actual.subtract(plan).toPlainString()));
    }
}
