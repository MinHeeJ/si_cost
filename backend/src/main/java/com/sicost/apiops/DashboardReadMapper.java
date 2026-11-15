package com.sicost.apiops;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.apiops.DashboardDtos.*;

@Mapper
public interface DashboardReadMapper {
    ReceivableTotals receivables(LocalDate today);
    TaxDue taxes(LocalDate today);
}
