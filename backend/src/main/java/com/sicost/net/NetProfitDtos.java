package com.sicost.net;

import com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;
import com.sicost.operating.OperatingProfitDtos.StatementRow;
import com.sicost.structure.ProfitStructureDtos.Card;
import com.sicost.structure.ProfitStructureDtos.Composition;
import java.util.List;

public final class NetProfitDtos {
    private NetProfitDtos() {}
    public record Data(Context context,List<Actual> months,List<Card> summary,List<StatementRow> rows,
        boolean showPreviousYear,boolean cumulativeProvisional,Completeness completeness,List<String> notices,
        String contributionMonth,Composition contributions) {}
}
