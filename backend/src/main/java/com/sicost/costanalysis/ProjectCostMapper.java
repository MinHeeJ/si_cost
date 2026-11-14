package com.sicost.costanalysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectCostMapper {
    record Expense(String month,BigDecimal amount) {}
    record ProgressQuery(String projectId,LocalDate baseDate) {}
    List<Expense> expenses(String id);
    BigDecimal planMm(String id);
    BigDecimal actualProgress(ProgressQuery query);
    List<String> assignmentIds(String id);
    List<com.sicost.profitability.MonthlyCostDtos.Price> prices(String id);
    String jobName(String code);
}
