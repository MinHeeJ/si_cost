package com.sicost.salesopportunitiesops;

import com.sicost.common.api.ApiException;
import com.sicost.profitability.MonthlyCostRepository;
import com.sicost.profitability.MonthlyCostService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.salesopportunitiesops.SalesCommandDtos.*;

@Repository
public class SalesCommandRepository {
    private final SalesCommandMapper mapper;
    private final MonthlyCostRepository costs;
    private final MonthlyCostService calculation;
    public SalesCommandRepository(SalesCommandMapper mapper,MonthlyCostRepository costs,MonthlyCostService calculation){this.mapper=mapper;this.costs=costs;this.calculation=calculation;}
    public Opportunity required(String id){Opportunity row=mapper.find(id);if(row==null)throw new ApiException(404,"NOT_FOUND","영업 기회를 찾을 수 없습니다.");return row;}
    public String snapshot(String id){required(id);return mapper.snapshot(id);}
    public List<DraftNeed> needs(String id){return mapper.needs(id);}
    public List<Tentative> tentatives(String id){return mapper.tentatives(id);}
    public List<String> keys(String id){var keys=new ArrayList<>(mapper.sourceLockKeys());keys.add("OPP:"+id);keys.add("PROJECT:CREATE");tentatives(id).forEach(t->{keys.add("DEV:"+t.developerId());keys.add("TENT:"+t.id());});return keys.stream().distinct().sorted().toList();}
    public void lockOpportunity(String id){if(mapper.lockOpportunity(id)==null)throw new ApiException(404,"NOT_FOUND","영업 기회를 찾을 수 없습니다.");}
    public QuoteSources quoteSources(YearMonth current){
        String db=current.toString().replace("-","");String latest=mapper.latestPayrollMonth(db);
        var people=new ArrayList<PersonCost>();String basis=latest==null?null:latest.substring(0,4)+"-"+latest.substring(4);
        if(latest!=null){
            YearMonth month=YearMonth.parse(basis);var source=costs.loadMonth(month.getYear(),month.getMonthValue());
            var payrollIds=new HashSet<String>();source.payroll().stream().filter(p->latest.equals(p.month())).forEach(p->payrollIds.add(p.personId()));
            var masters=new HashMap<String,com.sicost.profitability.MonthlyCostDtos.Person>();source.people().forEach(p->masters.put(p.personId(),p));
            for(var row:calculation.calculate(source).items()){
                var person=masters.get(row.personId());
                boolean leave=source.states().stream().anyMatch(s->s.personId().equals(row.personId())&&s.status().equals("LEAVE")&&!s.fromDate().isAfter(month.atEndOfMonth())&&(s.toDate()==null||!s.toDate().isBefore(month.atDay(1))));
                if(person==null||!payrollIds.contains(row.personId())||leave||!row.completeness().costComplete()||!row.completeness().allocationComplete()||row.rows().isEmpty())continue;
                boolean known=row.rows().stream().allMatch(r->r.laborAllocated()!=null&&r.teamExpense()!=null&&r.projectExpense()!=null);
                if(!known)continue;
                BigDecimal total=row.rows().stream().map(r->new BigDecimal(r.laborAllocated()).add(new BigDecimal(r.teamExpense())).add(new BigDecimal(r.projectExpense()))).reduce(BigDecimal.ZERO,BigDecimal::add);
                people.add(new PersonCost(person.personId(),person.jobCode(),person.gradeCode(),total));
            }
        }
        return new QuoteSources(current.toString(),basis,mapper.standards(db),mapper.codes(),List.copyOf(people));
    }
    public void update(Mutation value){one(mapper.update(value));}
    public void transition(TentativeMutation value){one(mapper.transitionTentative(value));}
    public int nextHistory(String id){int sn=mapper.greatestHistorySn(id)+1;if(sn>99999)throw new ApiException(409,"ID_EXHAUSTED","승인 이력 순번을 모두 사용했습니다.");return sn;}
    public void approval(ApprovalEvent e){one(mapper.approvalEvent(e));}
    public void notify(Notification n){one(mapper.notification(n));}
    public void plan(Plan p){one(mapper.insertOpportunityPlan(p));}
    public boolean activeLossReason(String code){return mapper.activeLossReason(code);}
    private void one(int count){if(count!=1)throw new IllegalStateException("영업 처리 결과를 저장하지 못했습니다.");}
}
