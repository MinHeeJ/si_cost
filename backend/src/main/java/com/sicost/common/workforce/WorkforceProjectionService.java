package com.sicost.common.workforce;

import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.persistence.DatabaseMonthCodec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.common.workforce.WorkforceProjectionDtos.*;

@Service
public class WorkforceProjectionService {
    private final WorkforceProjectionMapper mapper;
    private final AssignmentMonthlyService monthly;
    public WorkforceProjectionService(WorkforceProjectionMapper mapper, AssignmentMonthlyService monthly) {
        this.mapper = mapper; this.monthly = monthly;
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Result forecast(Query query) {
        if (query.includeUnfilled()) throw new IllegalArgumentException("프로젝트 미충원 투영은 이 조회에 포함하지 않습니다.");
        if (query.months()<1 || query.months()>12) throw new IllegalArgumentException("예측 개월을 확인해 주세요.");
        var people=mapper.people(); var leaves=mapper.leaves(); var demand=mapper.opportunityDemand();
        Map<String,BigDecimal> standards=new HashMap<>();
        mapper.standards(DatabaseMonthCodec.toDatabase(query.standardMonth().toString())).forEach(s->standards.put(s.code(),s.value()));
        var issues=new ArrayList<Issue>();
        var sources=new ArrayList<AssignmentMonthlyService.Source>();
        Map<String,List<AssignmentMonthlyService.Month>> projections=new HashMap<>();
        Map<String,String> statuses=new HashMap<>();
        for (Assignment assignment:mapper.assignments()) {
            var source=monthly.required(assignment.id()); sources.add(source); statuses.put(source.id(),assignment.projectStatus());
            projections.put(source.id(),monthly.preview(source,monthly.mapper().histories(source.id()),assignment.projectStatus(),assignment.contractType()));
        }
        var jobs=new TreeSet<String>(); people.forEach(p->jobs.add(p.jobCode())); demand.forEach(d->jobs.add(d.jobCode()));
        var result=new ArrayList<Month>();
        for (int index=0;index<query.months();index++) {
            YearMonth month=query.from().plusMonths(index);
            for (String job:jobs) {
                BigDecimal supply=BigDecimal.ZERO, needed=BigDecimal.ZERO; boolean known=true;
                for (Person person:people) {
                    if (!job.equals(person.jobCode()) || person.joinDate()!=null && YearMonth.from(person.joinDate()).isAfter(month)
                        || person.retireDate()!=null && YearMonth.from(person.retireDate()).isBefore(month)) continue;
                    if (leaves.stream().anyMatch(l->l.developerId().equals(person.id()) && !l.startDate().isAfter(month.atEndOfMonth())
                        && (l.endDate()==null || !l.endDate().isBefore(month.atDay(1))))) continue;
                    BigDecimal committed=BigDecimal.ZERO, extension=BigDecimal.ZERO;
                    for (var source:sources) {
                        if (!source.developerId().equals(person.id())) continue;
                        for (var projected:projections.get(source.id())) if (month.toString().equals(projected.month()))
                            committed=committed.add(new BigDecimal(projected.monthlyMm()));
                        YearMonth end=YearMonth.from(source.endDate());
                        if (!end.isBefore(query.standardMonth()) && end.isBefore(month) && !"LOSE".equals(statuses.get(source.id()))) {
                            String key="EXT_PRBL_"+(source.extensionPossibility()==null?"NULL":source.extensionPossibility());
                            BigDecimal probability=standards.get(key);
                            if (probability==null || probability.signum()<0 || probability.compareTo(new BigDecimal("100"))>0) {
                                known=false; issues.add(new Issue(key,"연장 확률 기준 미확인"));
                            } else extension=extension.add(source.contractMm().multiply(probability).movePointLeft(2));
                        }
                    }
                    supply=supply.add(BigDecimal.ONE.subtract(committed).subtract(extension).max(BigDecimal.ZERO));
                }
                for (Demand row:demand) {
                    if (!job.equals(row.jobCode())) continue;
                    YearMonth start=YearMonth.parse(DatabaseMonthCodec.toApi(row.startMonth()));
                    if (!month.isBefore(start) && month.isBefore(start.plusMonths(row.months())))
                        needed=needed.add(row.monthlyMm().multiply(BigDecimal.valueOf(row.count())).multiply(row.probability()).movePointLeft(2));
                }
                BigDecimal displayedSupply=known?supply.setScale(1,RoundingMode.HALF_UP):null;
                BigDecimal displayedDemand=needed.setScale(1,RoundingMode.HALF_UP);
                result.add(new Month(job,month,displayedSupply,displayedDemand,known?needed.subtract(supply):null));
            }
        }
        return new Result(List.copyOf(result),issues.stream().distinct().toList());
    }
}
