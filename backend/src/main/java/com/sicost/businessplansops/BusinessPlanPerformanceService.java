package com.sicost.businessplansops;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;
import static com.sicost.businessplansops.BusinessPlanActuals.*;

@Service
public class BusinessPlanPerformanceService {
    private final BusinessPlanPerformanceRepository repository;
    public BusinessPlanPerformanceService(BusinessPlanPerformanceRepository repository) {this.repository=repository;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Data getBusinessPlanPerformance(Query query) {
        var read=repository.read(query);Map<String,Target> targets=new HashMap<>();
        read.targets().forEach(t->targets.put(api(t.month()),t));
        String status=targets.isEmpty()?"UNREGISTERED":targets.size()!=12?"INCOMPLETE":read.targets().stream().allMatch(t->zero(t.sales())&&zero(t.profit()))?"ZERO_TARGETS":"REGISTERED";
        List<Month> months=new ArrayList<>();List<String> notices=new ArrayList<>(read.notices());
        if("UNREGISTERED".equals(status))notices.add(query.year()+"년 사업계획 목표가 아직 없습니다. [목표 업로드]로 올려 주세요.");
        if("INCOMPLETE".equals(status))notices.add("일부 월 사업계획 목표가 없습니다. 누계 목표·달성률은 미확인입니다. 12개월 목표를 업로드해 주세요.");
        if("ZERO_TARGETS".equals(status))notices.add("12개월 목표가 모두 0으로 등록되었습니다. 목표 없음 (미등록 아님).");
        for(Actual actual:read.months()) {
            Target target=targets.get(actual.month());String sales=actual.amounts().get("salesAmt"),profit=actual.amounts().get("oprtPrftAmt");
            String salesTarget=target==null?null:money(target.sales()),profitTarget=target==null?null:money(target.profit());
            Margin margin=margin(sales,profit,salesTarget,profitTarget);
            months.add(new Month(actual.month(),salesTarget,profitTarget,sales,profit,ratio(sales,salesTarget,true),margin.rate(),under(sales,salesTarget),margin.adverse(),target!=null,actual));
        }
        var cumulative=months.stream().filter(m->m.month().compareTo(query.month())<=0).toList();
        Month current=months.stream().filter(m->m.month().equals(query.month())).findFirst().orElseThrow();
        List<Summary> summary=List.of(summary("CUMULATIVE_SALES",actualTotal(cumulative,Month::salesActualAmt),sum(cumulative,Month::salesTargetAmt)),
            summary("CUMULATIVE_OPERATING_PROFIT",actualTotal(cumulative,Month::operatingProfitActualAmt),sum(cumulative,Month::operatingProfitTargetAmt)),
            summary("CURRENT_MONTH_SALES",current.salesActualAmt(),current.salesTargetAmt()));
        if(summary.stream().anyMatch(s->s.targetAmt()!=null&&new BigDecimal(s.targetAmt()).signum()<=0))notices.add("목표 없음: 0 또는 음수 목표는 달성률을 계산하지 않습니다.");
        long available=cumulative.stream().filter(m->!"MISSING".equals(m.actual().sourceKind())).count();
        if(available<cumulative.size())notices.add("자료 "+available+"/"+cumulative.size()+"개월 · 빠진 월: "+String.join(", ",cumulative.stream().filter(m->"MISSING".equals(m.actual().sourceKind())).map(Month::month).toList()));
        return new Data(read.context(),status,List.copyOf(months),summary,List.copyOf(notices));
    }
    private String actualTotal(List<Month> months,Function<Month,String> field) {
        // Only absent historical migration months are omitted; operating unknowns remain NULL.
        var present=months.stream().filter(m->m.actual().completeness().issues().stream().noneMatch(i->"MIGRATED_MONTH_MISSING".equals(i.code()))).toList();
        return present.isEmpty()?null:sum(present,field);
    }
    private Summary summary(String metric,String actual,String target) {return new Summary(metric,actual,target,ratio(actual,target,true),under(actual,target));}
    private static boolean zero(BigDecimal n) {return n!=null&&n.signum()==0;}
    static Boolean under(String actual,String target) {return actual==null||target==null||new BigDecimal(target).signum()<=0?null:new BigDecimal(actual).compareTo(new BigDecimal(target))<0;}
    record Margin(String rate,Boolean adverse) {}
    static Margin margin(String sales,String profit,String targetSales,String targetProfit) {
        if(sales==null||profit==null||targetSales==null||targetProfit==null||new BigDecimal(sales).signum()==0||new BigDecimal(targetSales).signum()<=0)return new Margin(null,null);
        BigDecimal s=new BigDecimal(sales),p=new BigDecimal(profit),ts=new BigDecimal(targetSales),tp=new BigDecimal(targetProfit);
        BigDecimal numerator=p.multiply(ts).subtract(tp.multiply(s)).multiply(new BigDecimal("100")),denominator=s.multiply(ts);
        return new Margin(numerator.divide(denominator,1,RoundingMode.HALF_UP).toPlainString(),numerator.signum()*denominator.signum()<0);
    }
}
