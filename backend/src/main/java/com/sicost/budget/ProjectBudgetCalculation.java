package com.sicost.budget;

import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.util.Objects;

public final class ProjectBudgetCalculation {
    private ProjectBudgetCalculation() {}
    public record Budget(BigDecimal amount, String manual) {}
    public static BigDecimal validRate(BigDecimal rate) {
        return rate!=null && rate.signum()>=0 && rate.compareTo(new BigDecimal("100"))<=0 ? rate : null;
    }
    public static BigDecimal preview(BigDecimal contractAmount,BigDecimal rate) {
        BigDecimal valid=validRate(rate);
        return contractAmount==null || valid==null ? null : ProjectInputPolicy.calculatedAmount(
            contractAmount.multiply(BigDecimal.ONE.subtract(valid.movePointLeft(2))));
    }
    public static Budget resolve(String contractType,BigDecimal contractAmount,Budget previous,
                                 BigDecimal previousContractAmount,String mode,BigDecimal explicitAmount,BigDecimal rate) {
        if ("IN".equals(contractType)) {
            if (explicitAmount!=null || "MANUAL".equals(mode)) throw ProjectInputPolicy.invalid("budgetAmount");
            return new Budget(null,"N");
        }
        if (mode!=null && !mode.equals("AUTO") && !mode.equals("MANUAL")) throw ProjectInputPolicy.invalid("budgetMode");
        if ("MANUAL".equals(mode)) {
            if (explicitAmount==null) throw ProjectInputPolicy.invalid("budgetAmount");
            return new Budget(ProjectInputPolicy.amount(explicitAmount.toPlainString(),"budgetAmount"),"Y");
        }
        if (previous!=null && "Y".equals(previous.manual())) {
            if ("AUTO".equals(mode)) throw ProjectInputPolicy.invalid("budgetMode");
            return previous;
        }
        if (explicitAmount!=null) throw ProjectInputPolicy.invalid("budgetMode");
        if (previous!=null && Objects.equals(contractAmount,previousContractAmount)) return previous;
        if (previous!=null && contractAmount!=null && previousContractAmount!=null
                && contractAmount.compareTo(previousContractAmount)==0) return previous;
        BigDecimal automatic=preview(contractAmount,rate);
        if (automatic==null) throw ProjectInputPolicy.invalid("budgetAmount");
        return new Budget(automatic,"N");
    }
    public static BigDecimal allowanceDefault(BigDecimal value) {
        if (value==null || value.signum()<0 || value.stripTrailingZeros().scale()>0
                || value.compareTo(new BigDecimal("999999999999999"))>0) return null;
        return value.setScale(0);
    }
}
