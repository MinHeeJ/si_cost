package com.sicost.businessplansops;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;

@Mapper
public interface BusinessPlanPerformanceMapper {
    String baseMonth();
    List<Target> targets(YearRange range);
    List<Snapshot> snapshots(YearRange range);
    List<Close> closes(YearRange range);
    List<Source> financialSources(YearRange range);
    BigDecimal annualTax(String year);
}
