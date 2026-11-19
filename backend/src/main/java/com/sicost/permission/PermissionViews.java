package com.sicost.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.salespartsettlements.SalesPartSettlementDtos.*;
import com.sicost.salespartsettlementsops.SettlementTrendService;
import java.math.BigDecimal;
import java.util.*;

/** Allowlist projections are applied only after the original calculators have completed. */
public final class PermissionViews {
    private PermissionViews() {}
    public static void fields(ObjectNode value,String... fields) {
        value.retain(Arrays.asList(fields));value.put("projection","RESTRICTED");available(value);
    }
    public static void available(ObjectNode value) {
        var fields=value.putArray("availableFields");value.fieldNames().forEachRemaining(key->{if(!key.equals("availableFields"))fields.add(key);});
    }
    public static void rate(ObjectNode value) {
        if(value.path("summary") instanceof ObjectNode summary)fields(summary,"personCount","assignmentCount","weightedMarginRate","annualGain","targetAnnualGain");
        for(var node:value.path("items"))if(node instanceof ObjectNode row)fields(row,"assignmentId","developerId","name","managementNo","employeeType","projectId","projectName","renewalDate","daysUntilRenewal","planned","contractMm","monthlyMm","unitPrice","marginRate","targetUnitPrice","requiredIncreaseRate","annualGain","targetAnnualGain");
        fields(value,"baseMonth","calculatedAt","appliedIncreaseRate","targetMarginRate","summary","items","pagination","unevaluatedCount");
    }
    private static void standard(ObjectNode node) {fields(node,"leaderName");}
    public static void settlement(ObjectNode value,Object original,ObjectMapper json) {
        if(original instanceof TabsResult) {
            for(var node:value.path("parts"))if(node instanceof ObjectNode tab) {
                if(tab.path("standard") instanceof ObjectNode std)standard(std);
                fields(tab,"partId","partName","eligible","historyOnly","status","standard");
            }
            fields(value,"month","baseMonth","currentMonth","parts","eligibleCount","confirmedCount","unconfirmedCount");
        } else if(original instanceof Result source) {
            if(value.path("standard") instanceof ObjectNode std)standard(std);
            for(String key:List.of("summary","totals"))if(value.path(key) instanceof ObjectNode total) {
                fields(total,"personCount","deficitPersonCount");
                Object surplus=PermissionViewMetadata.get(source.totals()).get("surplusPersonCount");
                if(surplus instanceof Number number)total.put("surplusPersonCount",number.longValue());
                available(total);
            }
            for(int i=0;i<source.items().size();i++)if(value.path("items").path(i) instanceof ObjectNode row) {
                var originalRow=source.items().get(i);String profit=originalRow.profitAmount();
                fields(row,"name","projectNames","mm","unitPrice");
                if(originalRow.legacyProjectText()!=null)row.putArray("projectNames").add(originalRow.legacyProjectText());
                if(profit!=null && new BigDecimal(profit).signum()!=0)row.put("profitStatus",new BigDecimal(profit).signum()>0?"SURPLUS":"DEFICIT");
                available(row);
            }
            value.put("readOnly",true);for(String flag:List.of("canSaveCard","canConfirm","canCancel"))value.put(flag,false);
            value.putArray("readOnlyReasons").add("수정 권한이 없어 보기만 할 수 있습니다(권한 › 메뉴×동작 표의 수정).");
            fields(value,"partId","month","status","standard","readOnly","readOnlyReasons","canSaveCard","canConfirm","canCancel","summary","totals","items","pagination");
        } else if(original instanceof SettlementTrendService.Trend source) {
            var months=value.putArray("monthlyTotals");
            for(var total:source.monthlyTotals()) {
                var facts=PermissionViewMetadata.get(total);if(facts.isEmpty())continue;
                ObjectNode node=json.valueToTree(facts);available(node);months.add(node);
            }
            fields(value,"partId","month","months","monthlyTotals");
        }
    }
}
