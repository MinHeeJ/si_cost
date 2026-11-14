package com.sicost.apiops;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;
import static com.sicost.apiops.RateRenegotiationDtos.*;

@Component
public class RateRenegotiationCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    public record Amounts(BigDecimal revenue, BigDecimal labor, BigDecimal unitPrice, BigDecimal monthlyMm,
                          BigDecimal contractMm, BigDecimal target, BigDecimal increase) {}
    public record Metrics(String marginRate, String targetUnitPrice, String requiredIncreaseRate,
                          String annualGain, String targetAnnualGain) {}
    public Metrics calculate(Amounts a) {
        if (!positive(a.revenue()) || !positive(a.unitPrice()) || !positive(a.monthlyMm()) || a.labor() == null || a.contractMm() == null)
            throw new IllegalArgumentException("재협상 계산 원천을 확인해 주세요.");
        String margin = percent(a.revenue().subtract(a.labor()), a.revenue());
        String annual = whole(a.unitPrice().multiply(a.increase()).multiply(a.contractMm()).multiply(new BigDecimal("12")).divide(HUNDRED));
        if (!validTarget(a.target())) return new Metrics(margin, null, null, annual, null);
        // Keep the full numerator/denominator until the final 10,000-won ceiling.
        BigDecimal denominator = a.monthlyMm().multiply(HUNDRED.subtract(a.target())).multiply(new BigDecimal("10000"));
        BigDecimal target = a.labor().multiply(HUNDRED).divide(denominator, 0, RoundingMode.CEILING).multiply(new BigDecimal("10000"));
        BigDecimal delta = target.subtract(a.unitPrice()).max(BigDecimal.ZERO);
        return new Metrics(margin, whole(target), percent(delta, a.unitPrice()), annual,
            whole(delta.multiply(a.contractMm()).multiply(new BigDecimal("12"))));
    }
    public boolean marginBelow(BigDecimal revenue, BigDecimal labor, String below) {
        return "ALL".equals(below) || revenue.subtract(labor).multiply(HUNDRED).compareTo(revenue.multiply(new BigDecimal(below))) < 0;
    }
    public Summary summarize(List<Row> rows) {
        BigDecimal revenue = rows.stream().map(r -> new BigDecimal(r.monthlyRevenue())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal labor = rows.stream().map(r -> new BigDecimal(r.monthlyLaborCost())).reduce(BigDecimal.ZERO, BigDecimal::add);
        String annual = whole(rows.stream().map(r -> new BigDecimal(r.annualGain())).reduce(BigDecimal.ZERO, BigDecimal::add));
        String target = rows.stream().anyMatch(r -> r.targetAnnualGain() == null) ? null
            : whole(rows.stream().map(r -> new BigDecimal(r.targetAnnualGain())).reduce(BigDecimal.ZERO, BigDecimal::add));
        return new Summary((int) rows.stream().map(Row::developerId).distinct().count(), rows.size(),
            revenue.signum() > 0 ? percent(revenue.subtract(labor), revenue) : null, annual, target);
    }
    public static boolean validTarget(BigDecimal target) { return target != null && target.signum() >= 0 && target.compareTo(HUNDRED) < 0; }
    public static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    public static String whole(BigDecimal value) { return value == null ? null : value.setScale(0, RoundingMode.DOWN).toPlainString(); }
    private static String percent(BigDecimal numerator, BigDecimal denominator) {
        return numerator.multiply(HUNDRED).divide(denominator, 1, RoundingMode.HALF_UP).toPlainString();
    }
}
