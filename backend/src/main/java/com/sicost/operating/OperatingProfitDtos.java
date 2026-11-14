package com.sicost.operating;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;
import com.sicost.structure.ProfitStructureDtos.Card;
import com.sicost.structure.ProfitStructureDtos.Comparison;
import java.util.List;

public final class OperatingProfitDtos {
    private OperatingProfitDtos() {}
    public record Query(int year,String month,String amountUnit) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StatementRow(String metric,String label,int depth,boolean isRate,List<String> monthlyValues,
        String cumulativeValue,String previousYearCumulativeValue,Comparison comparison) {}
    public record Data(Context context,List<Actual> months,List<Card> summary,List<StatementRow> rows,
        boolean showPreviousYear,boolean cumulativeProvisional,Completeness completeness,List<String> notices) {}
}
