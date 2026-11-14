package com.sicost.salespartsettlements;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;
import static com.sicost.salespartsettlements.SalesPartSettlementDtos.*;

public final class SalesPartSettlementCalculator {
    private SalesPartSettlementCalculator() {}
    public record Amounts(String pay, String expense, String retirement, String cost, String profit, String share) {}
    public static Amounts amounts(String employeeType, BigDecimal pay, BigDecimal revenue, Standard standard) {
        BigDecimal expense="OS".equals(employeeType)?BigDecimal.ZERO:decimal(standard.salesExpenseAmount());
        BigDecimal retirement="FT".equals(employeeType)?(pay==null?null:pay.divide(BigDecimal.valueOf(12),0,RoundingMode.DOWN)):BigDecimal.ZERO;
        BigDecimal cost=pay==null||expense==null||retirement==null?null:pay.add(expense).add(retirement);
        BigDecimal profit=revenue==null||cost==null?null:revenue.subtract(cost);
        BigDecimal rate=decimal(standard.distributionRate());
        BigDecimal share=profit==null||rate==null?null:profit.multiply(rate).divide(BigDecimal.valueOf(100),0,RoundingMode.DOWN);
        return new Amounts(money(pay),money(expense),money(retirement),money(cost),money(profit),money(share));
    }
    public static Totals totals(List<Row> rows, String card, String savedProfit, boolean snapshot) {
        String share=sum(rows,Row::profitShareAmount);
        String part=snapshot?savedProfit:share==null?null:money(decimal(share).subtract(decimal(card)));
        return new Totals(rows.size(),(int)rows.stream().filter(r->r.profitAmount()!=null&&decimal(r.profitAmount()).signum()<0).count(),
            sum(rows,Row::mm),sum(rows,Row::unitPrice),sum(rows,Row::payAmount),sum(rows,Row::salesExpenseAmount),
            sum(rows,Row::retirementAmount),sum(rows,Row::costAmount),sum(rows,Row::profitAmount),share,card,part);
    }
    public static String sum(List<Row> rows,Function<Row,String> field) {
        if(rows.stream().anyMatch(r->field.apply(r)==null))return null;
        return rows.stream().map(field).map(BigDecimal::new).reduce(BigDecimal.ZERO,BigDecimal::add).toPlainString();
    }
    public static BigDecimal decimal(String value) {return value==null?null:new BigDecimal(value);}
    public static String money(BigDecimal value) {return value==null?null:value.setScale(0,RoundingMode.DOWN).toPlainString();}
}
