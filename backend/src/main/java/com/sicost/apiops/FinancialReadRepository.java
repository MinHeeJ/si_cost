package com.sicost.apiops;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.apiops.FinancialReadDtos.*;

@Repository
public class FinancialReadRepository {
    private final FinancialReadMapper mapper;
    public FinancialReadRepository(FinancialReadMapper mapper){this.mapper=mapper;}
    public SourceTotals totals(Period period){return mapper.sourceTotals(period);}
    public BigDecimal estimatedTax(Period period){return mapper.estimatedTax(period);}
    public List<TaxMonth> taxMonths(Period period){return mapper.taxMonths(period);}
    public HistoryPage history(HistoryQuery query){return new HistoryPage(mapper.history(query),mapper.historyCount(query),query.page(),query.size());}
}
