package com.sicost.dashboardops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

final class DashboardExportFixture {
    static ObjectNode data(ObjectMapper json) throws Exception {
        ObjectNode data=(ObjectNode)json.readTree("""
            {"selectedMonth":"2026-07","today":"2026-07-15","currentMonth":"2026-07","cashBaseMonth":"2026-01",
             "lastClosedMonth":"2026-06","selectedMonthCloseStatus":"WORK","provisional":true,"observedAt":"2026-07-15T00:00:00+09:00","scope":"ALL",
             "actionTypeCount":1,"urgentActionTypeCount":1,"salesPartExplanation":"회사 비용 임의 안분 없음",
             "metrics":[],"actions":[{"actionCode":"OVERDUE_RECEIVABLE","count":2,"urgent":true,"amount":"1100","reason":"=SUM(A1:A2)","months":[]}],
             "closeProgress":{"closeStatus":"WORK","passedCheckCount":3,"totalCheckCount":6,"snapshot":false,"remainingChecks":[{"checkCode":"MM_OVER","status":"WARN","count":1,"reason":"검사 필요"}]},
             "projectAlerts":[{"projectId":"000012","projectName":"+계약 검토","contractTypeCode":"MO","alertType":"LOW_MARGIN","reason":"원가 확인","marginRate":"-1.2","budgetConsumptionRate":"100.0"}],
             "salesCostTrend":[],"supplyForecast":[],"salesParts":[{"partId":"00001","partName":"@담당","salesAmount":"0","projectCostAmount":"-1001","contributionMarginRate":null}],
             "pipeline":{"opportunityCount":1,"weightedExpectedAmount":"1000","startingSoon":[{"opportunityId":"00003","opportunityName":"-검토","startMonth":"2026-08","winProbabilityRate":"50.0","expectedAmount":"2000"}]},
             "cashForecast":[]}
            """);
        ObjectNode basis=(ObjectNode)json.readTree("""
            {"basisMonth":"2026-07","basisDate":"2026-07-15","sourceService":"공유 도메인 조회","observedAt":"2026-07-15T00:00:00+09:00","status":"UNCONFIRMED","reasons":["급여 원천 미확인"]}
            """);
        for(String key:List.of("actionsBasis","projectAlertsBasis","salesCostTrendBasis","supplyBasis","salesPartsBasis","cashBasis"))data.set(key,basis.deepCopy());
        for(String code:List.of("SALES","OPERATING_PROFIT","YTD_OPERATING_PROFIT","UTILIZATION","RECEIVABLE")) {
            var card=data.withArray("metrics").addObject();card.put("metricCode",code);card.put("unit",code.equals("UTILIZATION")?"PERCENT":"KRW");
            for(String key:List.of("value","previousValue","difference","changeRate","targetValue","achievementRate","profitRate","targetGapPercentagePoints","idlePersonCount","overdueCount","overdueAmount"))card.putNull(key);
            card.put("value",code.equals("UTILIZATION")?"0.0":"-1234");card.set("basis",basis.deepCopy());
            card.putObject("link").put("screenId",code.equals("UTILIZATION")?"UI-28":code.equals("YTD_OPERATING_PROFIT")?"UI-18":code.equals("RECEIVABLE")?"UI-13":"UI-08").put("linkKey",code.equals("RECEIVABLE")?null:"2026-07");
        }
        for(int i=1;i<=12;i++) {
            var row=data.withArray("salesCostTrend").addObject();row.put("month","2026-"+String.format("%02d",i));
            if(i<=7)row.put("salesAmount","1234567");else row.putNull("salesAmount");row.put("salesCostAmount","0");row.put("salesTargetAmount","2000000");
        }
        for(int i=1;i<=6;i++) {
            var row=data.withArray("supplyForecast").addObject();row.put("month",java.time.YearMonth.of(2026,7).plusMonths(i).toString());
            row.put("requiredPersons","10.0");row.put("availablePersons","8.5");row.put("differencePersons","-1.5");
        }
        for(int i=0;i<3;i++) {
            var row=data.withArray("cashForecast").addObject();row.put("month",java.time.YearMonth.of(2026,12).plusMonths(i).toString());
            row.putNull("closingBalance");row.put("minimumCashAmount","0");row.putNull("shortage");row.putArray("unconfirmedReasons").add("급여 원천 미확인");
        }
        return data;
    }
}
