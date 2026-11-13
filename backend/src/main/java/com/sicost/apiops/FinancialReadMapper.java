package com.sicost.apiops;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.apiops.FinancialReadDtos.*;

@Mapper
public interface FinancialReadMapper {
    SourceTotals sourceTotals(Period period);
    BigDecimal estimatedTax(Period period);
    List<TaxMonth> taxMonths(Period period);
    List<HistoryEntry> history(HistoryQuery query);
    long historyCount(HistoryQuery query);
}
