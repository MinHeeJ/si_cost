package com.sicost.common.financial;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;

public final class FinancialCalculation {
    private FinancialCalculation() {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TaxMonth(String month,String closeStatus,BigDecimal amount,String fixed,String migrated) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AllocationMonth(String month,String closeStatus,String amount,boolean verified,String fixed,String migrated) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Allocation(String year,String estimatedTax,String closedTotal,String remainingAmount,int openMonthCount,
        List<AllocationMonth> months,boolean verified,List<String> warnings) {}
    public static Allocation allocate(String year,BigDecimal annual,List<TaxMonth> months) {
        List<String> warnings=new ArrayList<>();
        boolean missing=months.stream().anyMatch(m->m.closeStatus().equals("CLOSE")&&m.amount()==null);
        BigDecimal closed=months.stream().filter(m->m.closeStatus().equals("CLOSE")&&m.amount()!=null).map(TaxMonth::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        List<TaxMonth> open=months.stream().filter(m->!m.closeStatus().equals("CLOSE")).toList();
        if(missing)warnings.add("마감월 법인세 사본이 없거나 미확인입니다.");
        if(annual==null)warnings.add("연간 예상 법인세가 없습니다.");
        BigDecimal remaining=annual==null||missing?null:annual.subtract(closed);
        if(remaining!=null&&remaining.signum()<0)warnings.add("연간 예상액보다 마감월 법인세 합계가 큽니다.");
        BigDecimal quotient=remaining==null||open.isEmpty()?null:remaining.divide(BigDecimal.valueOf(open.size()),0,RoundingMode.DOWN);
        List<AllocationMonth> result=new ArrayList<>();
        for(TaxMonth m:months) {
            BigDecimal amount=m.closeStatus().equals("CLOSE")?m.amount():quotient;
            if(quotient!=null&&m.equals(open.get(open.size()-1)))amount=remaining.subtract(quotient.multiply(BigDecimal.valueOf(open.size()-1)));
            result.add(new AllocationMonth(m.month(),m.closeStatus(),money(amount),amount!=null,m.fixed(),m.migrated()));
        }
        return new Allocation(year,money(annual),money(closed),money(remaining),open.size(),List.copyOf(result),!missing&&annual!=null,List.copyOf(warnings));
    }
    public static Map<String,Object> contributions(JsonNode snapshot,String month,Allocation allocation) {
        Map<String,BigDecimal> totals=new LinkedHashMap<>();
        for(String key:List.of("sgaAmount","sgaVatAmount","nonOperatingIncome","nonOperatingIncomeVat","nonOperatingExpense","nonOperatingExpenseVat","adjustmentProfit","cashFinancialExpense","cashMiscIncome","cashCardExpense","cashTransferExpense","cashUnpaidLabor","transferTotal","cashLoanIn","cashLoanOut","cashLoanNet"))totals.put(key,BigDecimal.ZERO);
        for(JsonNode row:snapshot.path("items")) {
            String cls=text(row,"fin_cls_cd");BigDecimal amount=number(row,"amt"),vat=number(row,"vat_amt");
            if(month.replace("-","").equals(text(row,"fin_ym"))) {
                String key=switch(cls){case "SGA"->"sga";case "NOPR_IN"->"nonOperatingIncome";default->"nonOperatingExpense";};
                add(totals,key.equals("sga")?"sgaAmount":key,amount);add(totals,key.equals("sga")?"sgaVatAmount":key+"Vat",vat);
            }
            String date=text(row,"io_dt"),cashMonth=date==null?apiMonth(text(row,"fin_ym")):date.substring(0,7);
            if(month.equals(cashMonth)&&!cls.equals("NOPR_IN"))add(totals,"cashFinancialExpense",amount.add(vat));
            if(date!=null&&month.equals(cashMonth)&&cls.equals("NOPR_IN"))add(totals,"cashMiscIncome",amount.add(vat));
        }
        for(JsonNode r:snapshot.path("adjustments"))add(totals,"adjustmentProfit",number(r,"adj_amt"));
        for(JsonNode r:snapshot.path("cardMonths"))add(totals,"cashCardExpense",number(r,"cash_amt"));
        for(JsonNode r:snapshot.path("transfers")){BigDecimal amount=number(r,"amt");add(totals,"transferTotal",amount);add(totals,"EXP".equals(text(r,"xfer_cls_cd"))?"cashTransferExpense":"cashUnpaidLabor",amount);}
        for(JsonNode r:snapshot.path("loans"))add(totals,"IN".equals(text(r,"io_cd"))?"cashLoanIn":"cashLoanOut",number(r,"amt"));
        totals.put("cashLoanNet",totals.get("cashLoanIn").subtract(totals.get("cashLoanOut")));
        BigDecimal cashTax=BigDecimal.ZERO;boolean known=true;
        for(JsonNode r:snapshot.path("taxPayments")) {
            String date=text(r,"Y".equals(text(r,"pay_yn"))?"pay_dt":"pay_due_dt");
            if(date==null||!date.startsWith(month))continue;
            BigDecimal amount=nullableNumber(r,"pay_amt");
            if(amount==null&&"MID".equals(text(r,"pay_type_cd")))amount=BigDecimal.ZERO;
            if(amount==null) {
                BigDecimal annual=null,mid=BigDecimal.ZERO;
                for(JsonNode t:snapshot.path("taxes"))if(Objects.equals(text(t,"tax_yr"),text(r,"tax_yr")))annual=number(t,"est_tax_amt");
                for(JsonNode t:snapshot.path("taxPayments"))if(Objects.equals(text(t,"tax_yr"),text(r,"tax_yr"))&&"MID".equals(text(t,"pay_type_cd")))mid=number(t,"pay_amt");
                if(annual!=null)amount=annual.subtract(mid).max(BigDecimal.ZERO);
            }
            if(amount==null)known=false;else cashTax=cashTax.add(amount);
        }
        Map<String,Object> result=new LinkedHashMap<>();totals.forEach((k,v)->result.put(k,money(v)));
        List<String> warnings=new ArrayList<>(allocation.warnings());if(!known)warnings.add("납부 예상액을 확인할 수 없습니다.");
        result.put("month",month);result.put("corporateTaxAllocation",allocation.months().stream().filter(m->m.month().equals(month)).map(AllocationMonth::amount).filter(Objects::nonNull).findFirst().orElse(null));
        result.put("cashTaxPayment",known?money(cashTax):null);result.put("verified",allocation.verified()&&known);result.put("warnings",warnings);return result;
    }
    private static void add(Map<String,BigDecimal> values,String key,BigDecimal amount){values.put(key,values.get(key).add(amount));}
    public static String text(JsonNode row,String key){return row.path(key).isNull()||row.path(key).isMissingNode()?null:row.path(key).asText();}
    public static BigDecimal nullableNumber(JsonNode row,String key){String value=text(row,key);return value==null?null:new BigDecimal(value);}
    public static BigDecimal number(JsonNode row,String key){BigDecimal n=nullableNumber(row,key);return n==null?BigDecimal.ZERO:n;}
    public static String money(BigDecimal n){return n==null?null:n.stripTrailingZeros().toPlainString();}
    public static String apiMonth(String month){return month==null?null:month.substring(0,4)+"-"+month.substring(4);}
}
