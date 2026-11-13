package com.sicost.common.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;

/** Pure B1 derivation shared by uploads and read-only estimates. No database defaults or writes. */
public final class PayrollCalculation {
    private PayrollCalculation() {}
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999999");
    public record Classification(String employeeType, String directClass, String position) {}
    /** null companyInsurance means derive; explicit zero is an authoritative input. */
    public record Amounts(BigDecimal pay, BigDecimal companyInsurance, BigDecimal selfInsurance,
        BigDecimal incomeTax, BigDecimal vendorSupply) {}
    public record Values(BigDecimal pay, BigDecimal allowance, BigDecimal companyInsurance,
        BigDecimal selfInsurance, BigDecimal retirement, BigDecimal incomeTax, BigDecimal withholding,
        BigDecimal realPay, BigDecimal vendorSupply, String employeeType, String directClass) {}

    public static Values calculate(Classification person, Amounts input, Map<String, BigDecimal> rates) {
        String type = person.employeeType();
        if (type == null || !Set.of("FT", "CT", "FL", "OS").contains(type))
            throw new IllegalArgumentException("직원구분은 FT/CT/FL/OS여야 합니다.");
        BigDecimal zero = BigDecimal.ZERO;
        if (type.equals("OS")) return new Values(zero, zero, zero, zero, zero, zero, zero, null,
            amount(input.vendorSupply()), type, person.directClass());
        BigDecimal pay = amount(input.pay()), income = amount(input.incomeTax());
        BigDecimal allowance = zero, company = zero, self = zero, retire = zero;
        if (!type.equals("FL")) {
            if ("TEAM".equals(person.position())) allowance = rate(rates, "ALLOW_TEAM");
            else if ("GROUP".equals(person.position())) allowance = rate(rates, "ALLOW_GROUP");
            self = amount(input.selfInsurance());
            company = input.companyInsurance() == null
                ? down(pay.multiply(rate(rates, "INS_RATE")).movePointLeft(2)) : amount(input.companyInsurance());
            if (type.equals("FT")) retire = down(pay.add(allowance).multiply(rate(rates, "SEV_RATE")).movePointLeft(2));
        }
        BigDecimal withholding = type.equals("FT") ? zero : down(pay.add(allowance).multiply(new BigDecimal("0.033")));
        BigDecimal net = pay.add(allowance).subtract(self).subtract(withholding).subtract(income);
        for (BigDecimal value : new BigDecimal[]{pay, allowance, company, self, retire, income, withholding, net}) amount(value);
        return new Values(pay, allowance, company, self, retire, income, withholding, net, zero, type, person.directClass());
    }
    private static BigDecimal rate(Map<String, BigDecimal> rates, String code) {
        BigDecimal value = rates.get(code);
        if (value == null || value.signum() < 0)
            throw new IllegalArgumentException("현재 활성 ALWNC " + code + " 기준값을 확인해 주세요.");
        if (code.startsWith("ALLOW_") && value.stripTrailingZeros().scale() > 0)
            throw new IllegalArgumentException("수당은 원 단위 정수여야 합니다.");
        return value;
    }
    private static BigDecimal amount(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("급여 입력값이 없습니다.");
        if (value.abs().compareTo(MAX_AMOUNT) > 0)
            throw new IllegalArgumentException("계산 결과가 NUMERIC(15) 범위를 초과합니다.");
        return value.setScale(0, RoundingMode.UNNECESSARY);
    }
    private static BigDecimal down(BigDecimal value) { return value.setScale(0, RoundingMode.DOWN); }
}
