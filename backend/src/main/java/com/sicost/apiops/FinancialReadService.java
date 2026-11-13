package com.sicost.apiops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.context.CurrentUserProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.apiops.FinancialReadDtos.*;

@Service
public class FinancialReadService {
    private final FinancialReadRepository repository;
    private final CurrentUserProvider users;
    private final Clock clock;
    public FinancialReadService(FinancialReadRepository repository,CurrentUserProvider users,Clock clock){this.repository=repository;this.users=users;this.clock=clock;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Contributions getFinancialContributions(Period period){
        validate(period);SourceTotals totals=repository.totals(period);List<String> warnings=new ArrayList<>();
        String allocation=allocation(period,repository.estimatedTax(period),repository.taxMonths(period),warnings);
        if(totals.cashTaxPayment()==null)warnings.add("납부 예상액을 확인할 수 없습니다.");
        return new Contributions(money(totals.sgaAmount()),money(totals.sgaVatAmount()),money(totals.nonOperatingIncome()),
            money(totals.nonOperatingIncomeVat()),money(totals.nonOperatingExpense()),money(totals.nonOperatingExpenseVat()),
            money(totals.adjustmentProfit()),money(totals.cashFinancialExpense()),money(totals.cashMiscIncome()),
            money(totals.cashCardExpense()),money(totals.cashTransferExpense()),money(totals.cashUnpaidLabor()),money(totals.transferTotal()),
            money(totals.cashLoanIn()),money(totals.cashLoanOut()),money(totals.cashLoanIn().subtract(totals.cashLoanOut())),
            period.month(),allocation,money(totals.cashTaxPayment()),warnings.isEmpty(),List.copyOf(warnings));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public HistoryPage getFinancialHistory(HistoryQuery query){validate(query.period());return repository.history(query);}
    private void validate(Period period){
        users.currentUser();
        if(YearMonth.parse(period.month()).isAfter(YearMonth.from(LocalDate.now(clock.withZone(TimeConfiguration.SEOUL)))))throw FinancialReadInput.invalid("month");
    }
    private String allocation(Period period,BigDecimal annual,List<TaxMonth> months,List<String> warnings){
        var result=com.sicost.common.financial.FinancialCalculation.allocate(period.year(),annual,months.stream().map(m->
            new com.sicost.common.financial.FinancialCalculation.TaxMonth(com.sicost.common.financial.FinancialCalculation.apiMonth(m.month()),m.closeStatus(),m.amount(),null,null)).toList());
        warnings.addAll(result.warnings());
        var selected=result.months().stream().filter(m->m.month().equals(period.month())).findFirst();
        if(selected.isEmpty()){warnings.add("운영 시작 이전 월의 법인세 월할은 미확인입니다.");return null;}
        return selected.get().amount();
    }
    private static String money(BigDecimal value){return value==null?null:value.stripTrailingZeros().toPlainString();}
}
