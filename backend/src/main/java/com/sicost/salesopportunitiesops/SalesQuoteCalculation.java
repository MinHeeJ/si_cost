package com.sicost.salesopportunitiesops;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.sicost.salesopportunitiesops.SalesCommandDtos.*;

@Component
public class SalesQuoteCalculation {
    private static final BigDecimal HUNDRED=new BigDecimal("100"),TEN_THOUSAND=new BigDecimal("10000"),MILLION=new BigDecimal("1000000");
    public static final String FORMULA="Q=인원×월MM×개월; 행 매출=DOWN(Q×유효단가); 행 원가=DOWN(Q×1인월원가); 이익률=(매출−원가)/매출×100; 손해(<0) → 승인필요(<승인율) → 목표미달(<목표율) → 기준충족";
    public Quote calculate(List<DraftNeed> needs,QuoteSources source) {
        var issues=new ArrayList<Issue>();var rows=new ArrayList<QuoteRow>();
        BigDecimal approval=standard(source,"QUOTE_APRV_RT",false),gap=standard(source,"QUOTE_REAPRV_GAP",true),target=standard(source,"TGT_MRGN_RT",false);
        for(String key:List.of("QUOTE_APRV_RT","QUOTE_REAPRV_GAP","TGT_MRGN_RT"))if(standard(source,key,key.equals("QUOTE_REAPRV_GAP"))==null)issues.add(new Issue(key,null,"유효한 운영 기준이 없습니다.","UNKNOWN"));
        BigDecimal revenue=BigDecimal.ZERO,cost=BigDecimal.ZERO;boolean unknown=false,incomplete=needs.isEmpty();
        for(DraftNeed n:needs){
            boolean complete=n.jobCode()!=null&&n.count()!=null&&n.startMonth()!=null&&n.months()!=null&&n.monthlyMm()!=null;
            if(!complete){incomplete=true;issues.add(new Issue("needs",n.needSn(),"필수값이 빠진 미완료 행은 계산에서 제외합니다.","INCOMPLETE"));rows.add(new QuoteRow(n.needSn(),n.jobCode(),n.gradeCode(),null,null,null,null,null,null,null,source.costMonth(),null,0,false,false,"미완료"));continue;}
            String grade=n.gradeCode()==null?source.codes().stream().filter(c->c.group().equals("GRADE")&&c.code().equals("MD")).map(Code::code).findFirst().orElse(null):n.gradeCode();
            String rate=n.expectedRate();if(rate==null)rate=source.codes().stream().filter(c->c.group().equals("JOB")&&c.code().equals(n.jobCode())).map(Code::defaultRate).filter(Objects::nonNull).findFirst().orElse(null);
            if(rate!=null&&!rate.matches("[1-9][0-9]{0,14}"))rate=null;
            List<PersonCost> sameGrade=grade==null?Collections.emptyList():source.people().stream().filter(p->grade.equals(p.gradeCode())).toList();
            List<PersonCost> exact=sameGrade.stream().filter(p->n.jobCode().equals(p.jobCode())).toList();boolean fallback=exact.size()<2;
            List<PersonCost> cohort=fallback?sameGrade:exact;
            BigDecimal unit=cohort.isEmpty()?null:cohort.stream().map(PersonCost::cost).reduce(BigDecimal.ZERO,BigDecimal::add)
                .divide(TEN_THOUSAND.multiply(BigDecimal.valueOf(cohort.size())),0,RoundingMode.HALF_UP).multiply(TEN_THOUSAND);
            BigDecimal q=BigDecimal.valueOf(n.count()).multiply(new BigDecimal(n.monthlyMm())).multiply(BigDecimal.valueOf(n.months()));
            BigDecimal rowRevenue=rate==null?null:q.multiply(new BigDecimal(rate)).setScale(0,RoundingMode.DOWN),rowCost=unit==null?null:q.multiply(unit).setScale(0,RoundingMode.DOWN);
            String reason=grade==null?"중급 등급 기준 미확인":rate==null?"유효 예상단가 미확인":unit==null?"견적 원가 미확인":null;
            if(reason!=null){unknown=true;issues.add(new Issue(rate==null?"expectedRate":"cost",n.needSn(),reason,"UNKNOWN"));}
            if(rowRevenue!=null)revenue=revenue.add(rowRevenue);if(rowCost!=null)cost=cost.add(rowCost);
            rows.add(new QuoteRow(n.needSn(),n.jobCode(),grade,str(q),rate,str(unit),str(rowRevenue),str(rowCost),str(unit),str(target(unit,target,TEN_THOUSAND)),source.costMonth(),cohort.isEmpty()?null:fallback?"GRADE":"JOB_GRADE",cohort.size(),fallback,true,reason));
        }
        BigDecimal numerator=revenue.subtract(cost).multiply(HUNDRED);
        boolean numeric=!unknown&&revenue.signum()>0&&!rows.isEmpty()&&rows.stream().anyMatch(QuoteRow::complete);
        String judgement=!numeric?incomplete&&!unknown?"INCOMPLETE":"UNKNOWN":incomplete?"INCOMPLETE":
            numerator.signum()<0?"LOSS":approval==null?"UNKNOWN":numerator.compareTo(revenue.multiply(approval))<0?"APPROVAL_REQUIRED":
            target==null?"UNKNOWN":numerator.compareTo(revenue.multiply(target))<0?"BELOW_TARGET":"MET";
        String unavailable=target!=null&&target.compareTo(HUNDRED)==0?"목표 이익률이 100%이므로 목표값을 계산할 수 없습니다.":target==null?"목표 이익률 미확인":null;
        return new Quote(List.copyOf(rows),List.copyOf(issues),str(revenue),unknown?null:str(cost),numeric?str(numerator.divide(revenue,18,RoundingMode.HALF_UP)):null,
            numeric?str(numerator.divide(revenue,2,RoundingMode.HALF_UP)):null,unknown?null:str(cost),unknown?null:str(target(cost,target,MILLION)),unavailable,judgement,
            source.month(),str(approval),str(gap),str(target),FORMULA);
    }
    public static BigDecimal standard(QuoteSources s,String code,boolean unbounded){return s.standards().stream().filter(v->v.code().equals(code)).map(Standard::value).filter(Objects::nonNull).filter(v->v.signum()>=0&&(unbounded||v.compareTo(HUNDRED)<=0)).findFirst().orElse(null);}
    private BigDecimal target(BigDecimal cost,BigDecimal margin,BigDecimal quantum){return cost==null||margin==null||margin.compareTo(HUNDRED)>=0?null:cost.multiply(HUNDRED).divide(HUNDRED.subtract(margin).multiply(quantum),0,RoundingMode.CEILING).multiply(quantum);}
    public static String str(BigDecimal value){return value==null?null:value.toPlainString();}
}
