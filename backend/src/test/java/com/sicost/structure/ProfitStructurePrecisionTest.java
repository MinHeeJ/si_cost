package com.sicost.structure;

import com.sicost.businessplansops.BusinessPlanActuals;
import com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProfitStructurePrecisionTest {
    Actual month(String month,String sales,String profit) {
        var n=new BigDecimal(profit);var z=BigDecimal.ZERO;
        return new BusinessPlanActuals().stored(new Snapshot(month.replace("-",""),"Y","Y",new BigDecimal(sales),z,z,z,z,n,z,z,z,z,z,n,z,z,z,n),true,null,OffsetDateTime.parse("2026-11-14T09:00:00+09:00"));
    }
    @Test void percentagePointDifferenceRoundsOnlyOnceOnEitherSideOfHalf() {
        var current=month("2026-01","2000","1");
        for(String profit:List.of("1","-1")) {
            var previous=month("2025-01","999999999999999",profit);
            var result=ProfitStructureService.compare("operatingProfitRate",List.of(current.month()),List.of(previous.month()),Map.of(current.month(),current,previous.month(),previous));
            // 0.05 minus/plus 100/999999999999999 is just below/above the half boundary.
            assertThat(result.percentagePointChange()).isEqualTo(profit.equals("1")?"0.0":"0.1");
            assertThat(result.direction()).isEqualTo(profit.equals("1")?"NEUTRAL":"FAVORABLE");
        }
    }
    @Test void inconsistentHistoricalSubtotalsNeverConnectInventedChanges() {
        var values=new LinkedHashMap<>(month("2025-02","100","50").amounts());
        values.put("salesCostAmt","20");values.put("grossPrftAmt","999");values.put("sgaTotAmt","10");
        var waterfall=ProfitStructureService.waterfall(values);
        assertThat(waterfall.stream().filter(w->Set.of("salesCostAmt","sgaTotAmt","prevAdjAmt").contains(w.metric())))
            .allMatch(w->!w.connected());
        assertThat(waterfall.stream().filter(w->w.metric().equals("grossPrftAmt")).findFirst().orElseThrow().amount()).isEqualTo("999");
    }
    @Test void reconciledSignedTaxAndAdjustmentStillConnectAndMissingValuesBreakTheirSegment() {
        var values=new LinkedHashMap<>(month("2026-01","100","80").amounts());
        values.put("salesCostAmt","20");values.put("grossPrftAmt","80");values.put("prevAdjAmt","-5");values.put("oprtPrftAmt","75");values.put("corpTaxAmt","-2");values.put("netPrftAmt","77");
        assertThat(ProfitStructureService.waterfall(values)).allMatch(w->w.connected());
        values.put("grossPrftAmt",null);
        assertThat(ProfitStructureService.waterfall(values).stream().filter(w->w.metric().equals("salesCostAmt")).findFirst().orElseThrow().connected()).isFalse();
    }
}
