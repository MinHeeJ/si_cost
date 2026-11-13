package com.sicost.corporatetaxesops;

import java.math.*;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.corporatetaxesops.CorporateTaxDtos.*;

@Service
public class CorporateTaxAllocationService {
    public Allocation calculate(String year,StoredAnnual annual,YearMonth cashBase,List<StoredMonth> stored){
        List<StoredMonth> rows=stored.stream().filter(r->!YearMonth.parse(r.month()).isBefore(cashBase)).toList();
        BigDecimal closed=BigDecimal.ZERO;int open=0;boolean copies=true;
        for(StoredMonth row:rows){if(row.closeStatus().equals("CLOSE")){if(row.amount()==null)copies=false;else closed=closed.add(row.amount());}else open++;}
        boolean verified=copies&&annual!=null;BigDecimal remaining=verified?annual.estimatedTax().subtract(closed):null;
        BigDecimal quotient=remaining!=null&&open>0?remaining.divide(BigDecimal.valueOf(open),0,RoundingMode.DOWN):null;
        List<AllocationMonth> months=new ArrayList<>();int index=0;
        for(StoredMonth row:rows){
            boolean locked=row.closeStatus().equals("CLOSE");BigDecimal amount;
            if(locked)amount=row.amount();else {index++;amount=quotient==null?null:index==open?remaining.subtract(quotient.multiply(BigDecimal.valueOf(open-1))):quotient;}
            months.add(new AllocationMonth(row.month(),row.closeStatus(),money(amount),locked?amount!=null:verified,row.fixed(),row.migrated()));
        }
        List<String> warnings=new ArrayList<>();if(!copies)warnings.add("마감 법인세 자료를 확인할 수 없습니다");if(annual==null)warnings.add("연간 예상 법인세 자료를 확인할 수 없습니다");
        if(remaining!=null&&remaining.signum()<0)warnings.add("금액이 맞는지 확인하세요");
        return new Allocation(year,annual==null?null:money(annual.estimatedTax()),money(closed),money(remaining),open,List.copyOf(months),verified,List.copyOf(warnings));
    }
    public static String money(BigDecimal value){return value==null?null:value.stripTrailingZeros().toPlainString();}
}
