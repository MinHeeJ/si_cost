package com.sicost.profitability;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.profitability.MonthlyCostDtos.*;

/** Select-only adapter. In particular, no assignment/payroll synchronization entry point. */
@Mapper
public interface MonthlyCostMapper {
    List<Payroll> payroll(Range range);
    List<Assignment> assignments(Range range);
    List<Assignment> projectMonths(Range range);
    List<Project> projects();
    List<ProjectPart> projectParts();
    List<TurnkeyCell> turnkey();
    List<StoredCost> storedCosts(Range range);
    List<Standard> standards(Range range);
    List<Part> parts();
    List<Company> companies();
}
