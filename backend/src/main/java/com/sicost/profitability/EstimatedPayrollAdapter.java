package com.sicost.profitability;

import com.sicost.common.payroll.PayrollCalculation;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.sicost.profitability.MonthlyCostDtos.*;

/** Resolves stored payroll before considering an effective price. Never calls an upload/synchronizer. */
@Component
public class EstimatedPayrollAdapter {
    public record Resolution(MonthlyLaborSource source, BigDecimal labor, List<MonthlyCostIssue> issues) {}

    public Resolution resolve(Sources sources, Person person, String month) {
        var uploaded = sources.payroll().stream().filter(p -> p.personId().equals(person.personId()) && p.month().equals(month)).findFirst();
        if (uploaded.isPresent()) return uploaded(uploaded.get());
        if (sources.protectedPersonMonth(month, person.personId()))
            return missing(person, month, "보호월 급여 자료가 없습니다. 급여 사본을 확인해 주세요.", false);
        var price = sources.prices().stream().filter(p -> p.personId().equals(person.personId()) && p.effective(month))
            .max(Comparator.comparing(Price::fromMonth));
        if (price.isEmpty() || price.get().monthlyPay() == null)
            return missing(person, month, "유효 기본 급여가 없습니다. 급여 업로드를 확인해 주세요.", true);
        try {
            Map<String, BigDecimal> rates = new HashMap<>();
            for (var code : sources.allowanceCodes()) if (code.codeVal() != null) rates.put(code.code(), new BigDecimal(code.codeVal()));
            boolean os = "OS".equals(person.employeeType());
            var value = PayrollCalculation.calculate(new PayrollCalculation.Classification(person.employeeType(), person.directClass(), person.positionCode()),
                new PayrollCalculation.Amounts(os ? BigDecimal.ZERO : price.get().monthlyPay(), null, BigDecimal.ZERO,
                    BigDecimal.ZERO, os ? price.get().monthlyPay() : BigDecimal.ZERO), rates);
            var payroll = new Payroll(month, person.personId(), value.pay(), value.allowance(), value.companyInsurance(),
                value.selfInsurance(), value.retirement(), value.incomeTax(), value.withholding(), value.realPay(),
                value.vendorSupply(), value.employeeType(), value.directClass(), null, null, null, null, null);
            return project(payroll, true);
        } catch (IllegalArgumentException | ArithmeticException e) {
            return missing(person, month, "예정 급여 계산 기준 오류: " + e.getMessage() + " 급여 업로드를 확인해 주세요.", true);
        }
    }

    public Resolution uploaded(Payroll payroll) { return project(payroll, false); }

    private Resolution project(Payroll p, boolean estimated) {
        List<MonthlyCostIssue> issues = new ArrayList<>();
        BigDecimal labor = null;
        try {
            labor = switch (p.employeeType()) {
                case "FT" -> required(p.pay()).add(required(p.allowance())).add(required(p.companyInsurance())).add(required(p.retirementReserve()));
                case "CT" -> required(p.pay()).add(required(p.allowance())).add(required(p.companyInsurance()));
                case "FL" -> required(p.pay());
                case "OS" -> required(p.vendorSupply());
                default -> throw new IllegalArgumentException("직원구분을 확인해 주세요.");
            };
            required(labor);
        } catch (IllegalArgumentException | ArithmeticException e) {
            BigDecimal sourceAmount = labor != null ? labor : "OS".equals(p.employeeType()) ? p.vendorSupply() : p.pay();
            labor = null;
            issues.add(issue(p.personId(), p.month(), "인건비 미확인: " + e.getMessage() + " 급여 업로드를 확인해 주세요.",
                sourceAmount == null ? null : sourceAmount.toPlainString()));
        }
        boolean os = "OS".equals(p.employeeType());
        var source = new MonthlyLaborSource(p.personId(), p.month(), p.employeeType(), p.directClass(),
            estimated ? "급여 미업로드·예정" : "업로드 급여", money(labor), reference(os ? p.vendorSupply() : p.pay()),
            reference(p.allowance()), reference(p.companyInsurance()), reference(p.retirementReserve()), reference(p.selfInsurance()),
            reference(p.withholdingTax()), reference(p.incomeTax()), reference(os ? p.vendorSupply() : p.netPay()), estimated);
        return new Resolution(source, labor, List.copyOf(issues));
    }
    private Resolution missing(Person person, String month, String reason, boolean estimated) {
        return new Resolution(new MonthlyLaborSource(person.personId(), month, person.employeeType(), person.directClass(),
            "자료 없음", null, null, null, null, null, null, null, null, null, estimated), null,
            List.of(issue(person.personId(), month, reason, null)));
    }
    static MonthlyCostIssue issue(String personId, String month, String reason, String amount) {
        return new MonthlyCostIssue(personId, month, reason, "TB_HR_MON_PAY", "UI-02", amount);
    }
    static BigDecimal required(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("필수 금액이 없습니다.");
        if (value.abs().compareTo(PayrollCalculation.MAX_AMOUNT) > 0)
            throw new IllegalArgumentException("계산 결과가 NUMERIC(15) 범위를 초과합니다.");
        return value.setScale(0, java.math.RoundingMode.UNNECESSARY);
    }
    private static String reference(BigDecimal value) {
        if (value == null) return null;
        try { return money(required(value)); } catch (IllegalArgumentException | ArithmeticException e) { return null; }
    }
}
