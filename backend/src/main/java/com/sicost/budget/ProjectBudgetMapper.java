package com.sicost.budget;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.budget.ProjectBudgetDtos.*;

@Mapper
public interface ProjectBudgetMapper {
    Stored find(@Param("id") String id);
    String snapshot(@Param("id") String id);
    BigDecimal operatingValue(@Param("key") String key,@Param("month") String month);
    int greatestHistorySn(@Param("id") String id);
    int update(Mutation mutation);
    int insertHistory(BudgetHistory history);
}
