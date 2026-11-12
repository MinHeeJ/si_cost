package com.sicost.billsops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.billsops.BillOperationsDtos.*;

@Service
public class BillEstimateService {
    private final BillOperationsRepository repository;
    private final AssignmentMonthlyService monthly;
    private final Clock clock;
    public BillEstimateService(BillOperationsRepository repository,AssignmentMonthlyService monthly,Clock clock) {
        this.repository=repository;this.monthly=monthly;this.clock=clock;
    }
    public LocalDate today() {return ProjectInputPolicy.today(clock);}
    public LocalDateTime now() {return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);}
    public Clock clock() {return clock;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public BillEstimate estimate(String projectId,String month) {
        Project project=repository.project(projectId);YearMonth target=YearMonth.parse(month);
        List<String> warnings=new ArrayList<>();List<BillingSource> sources=new ArrayList<>();
        if(Set.of("MO","DP").contains(project.contractType())) {
            for(String id:repository.assignments(projectId)) {
                var source=monthly.required(id);
                if("FREE".equals(source.inputType()))continue;
                var history=monthly.mapper().histories(id);
                var rows=monthly.preview(source,history,project.status(),project.contractType());
                for(var row:rows)if(row.month().equals(month)) {
                    boolean protectedMonth=monthly.protectedMonth(month,row.fixed());
                    if(!protectedMonth&&!"ACT".equals(source.status()))continue;
                    if(row.appliedUnitPrice()==null)throw new IllegalStateException("월별 적용 단가를 확인해 주세요.");
                    BigDecimal price=new BigDecimal(row.appliedUnitPrice()),mm=new BigDecimal(row.monthlyMm());
                    BigDecimal amount=price.multiply(mm).setScale(0,RoundingMode.DOWN);
                    var developer=repository.developer(row.developerId());
                    sources.add(new BillingSource(id,developer.id(),developer.name(),developer.managementNo(),month,
                        price.toPlainString(),mm.toPlainString(),amount.toPlainString(),protectedMonth));
                }
            }
        }
        BigDecimal sum=sources.stream().map(s->new BigDecimal(s.supplyAmount())).reduce(BigDecimal.ZERO,BigDecimal::add);
        String supply=Set.of("MO","DP").contains(project.contractType())?sum.toPlainString():null;
        if(supply==null)warnings.add("공급가를 직접 입력해 주세요.");
        else if(sum.signum()==0)warnings.add("해당월 유상 투입이 없어 추정 공급가가 0원입니다. 공급가를 직접 입력해 주세요.");
        BigDecimal rate=repository.standard("VAT_RT",DatabaseMonthCodec.toDatabase(month));
        if(!validRate(rate)){rate=null;warnings.add("부가세율 기본 설정이 유효하지 않습니다. 0~100, 소수 둘째 자리 이내로 직접 입력해 주세요.");}
        LocalDate due=target.plusMonths(1).atDay(25);
        if(due.isBefore(today()))warnings.add("기본 입금예정일이 청구일보다 빠릅니다. 날짜를 수정해 주세요.");
        return new BillEstimate(projectId,month,supply,rate==null?null:rate.stripTrailingZeros().toPlainString(),today(),due,List.copyOf(sources),List.copyOf(warnings));
    }
    public static boolean validRate(BigDecimal rate) {return rate!=null&&rate.signum()>=0&&rate.compareTo(new BigDecimal("100"))<=0&&rate.stripTrailingZeros().scale()<=2;}
}
