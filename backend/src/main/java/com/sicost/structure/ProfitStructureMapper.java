package com.sicost.structure;

import com.sicost.businessplansops.BusinessPlanPerformanceDtos.YearRange;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.structure.ProfitStructureDtos.*;

@Mapper
public interface ProfitStructureMapper {
    List<ProjectCustomer> customers();
    List<ProjectAmount> bills(YearRange range);
    List<ProjectAmount> expenses(YearRange range);
}
