package com.sicost.monthclosesops;

import com.sicost.apiops.BillingCheckService;
import com.sicost.apiops.BillingReviewRepository;
import com.sicost.profitability.MonthlyCostDtos.Sources;
import com.sicost.salespartsettlementsops.SettlementClosingService;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.monthclosesops.MonthCloseDtos.*;
import static com.sicost.monthclosesops.MonthCloseRepository.*;

@Service
public class CloseCheckService {
    public static final List<String> CODES=List.of("PAY","MM_OVER","BILL","FI","SETL","BILL_MISS");
    public static final List<String> NAMES=List.of("급여 업로드","MM 1.0 초과자","세금계산서 번호 미입력","재무 항목","영업 파트 정산","청구 누락 점검");
    private final BillingCheckService billing;
    private final BillingReviewRepository bills;
    private final SettlementClosingService settlements;
    public CloseCheckService(BillingCheckService billing,BillingReviewRepository bills,SettlementClosingService settlements){this.billing=billing;this.bills=bills;this.settlements=settlements;}
    public List<Check> calculate(String month,Map<String,List<Map<String,Object>>> data,Sources sources) {
        String db=month.replace("-","");
        long pay=data.get("TB_HR_MON_PAY").stream().filter(r->db.equals(s(r,"pay_ym"))).map(r->s(r,"dev_id")).distinct().count();
        BigDecimal limit=bills.standard("MM_OVER",db);
        if(limit==null||limit.signum()<0)throw conflict("STATE_CONFLICT","월 MM 초과 기준을 확인해 주세요.");
        Map<String,BigDecimal> totals=new TreeMap<>();
        sources.assignments().stream().filter(a->db.equals(a.month())&&!"CNCL".equals(a.status())&&a.assignmentMm()!=null)
            .forEach(a->totals.merge(a.personId(),a.assignmentMm(),BigDecimal::add));
        long over=totals.values().stream().filter(mm->mm.compareTo(limit)>0).count();
        long missing=data.get("TB_BL_BILL").stream().filter(r->db.equals(s(r,"bill_ym"))&&!"Y".equals(s(r,"cncl_yn"))&&(s(r,"tax_invc_no")==null||s(r,"tax_invc_no").isBlank())).count();
        long fi=data.get("TB_FI_ITEM").stream().filter(r->db.equals(s(r,"fin_ym"))).count();
        var tabs=settlements.listEligibleParts(month);
        long pending=tabs.parts().stream().filter(p->p.eligible()&&"WORK".equals(p.status())).count();
        BigDecimal min=bills.standard("BILL_MISS_MIN",db),rate=bills.standard("BILL_MISS_RT",db);
        if(min==null||rate==null||min.signum()<0||rate.signum()<0)throw conflict("STATE_CONFLICT","청구 누락 허용오차 기준을 확인해 주세요.");
        long misses=bills.projects().stream().filter(p->Set.of("MO","DP").contains(p.contractType())&&!Set.of("PRE","LOSE").contains(p.status()))
            .map(p->billing.evaluate(p,month,min,rate)).filter(Objects::nonNull).filter(r->!"맞음".equals(r.verdict())).count();
        String first=tabs.parts().stream().filter(p->p.eligible()&&"WORK".equals(p.status())).map(p->p.partId()).findFirst().orElse("");
        long eligible=tabs.parts().stream().filter(p->p.eligible()).count();
        return List.of(check(0,pay,month,null,"",pay+"명 업로드 완료"),check(1,over,month,"UI-12","MM_OVER",over+"명 초과"),check(2,missing,month,"UI-13","BILL_NUMBER_MISSING",missing+"건 미입력"),
            check(3,fi,month,"UI-10","",fi+"건 입력"),check(4,pending,month,"UI-19",first,eligible+"개 파트 중 "+pending+"개 미확정"),check(5,misses,month,"UI-13","BILL_MISS",misses+"개 프로젝트 확인 필요"));
    }
    private static Check check(int i,long count,String month,String screen,String filter,String description){return new Check(CODES.get(i),NAMES.get(i),i!=0&&i!=3&&count>0?"WARN":"OK",count,description,null,screen,month,filter);}
    public static void validate(List<Check> checks) {
        if(checks.size()!=6||!checks.stream().map(Check::code).toList().equals(CODES))throw conflict("STATE_CONFLICT","고정 순서의 6개 점검이 필요합니다.");
        for(Check check:checks)if(check.count()!=null&&(check.count()<0||check.count()>9999999)||check.description()!=null&&check.description().codePointCount(0,check.description().length())>300)
            throw conflict("STATE_CONFLICT","점검 설명 또는 건수의 저장 범위를 초과했습니다.");
    }
}
