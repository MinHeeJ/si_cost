package com.sicost.apiops;

import java.util.List;

public final class PayrollDtos {
    private PayrollDtos() {}
    public record Query(String month, String q, int page, int size, long offset) {}
    public record Row(String month, String developerId, String managementNo, String developerName,
        String employeeType, String directClass, String payAmount, String allowanceAmount,
        String companyInsuranceAmount, String selfInsuranceAmount, String retirementReserveAmount,
        String incomeTaxAmount, String withholdingTaxAmount, String realPayAmount,
        String vendorSupplyAmount, Long uploadId) {}
    public record Page(List<Row> items, long total, int page, int size, String month, String revision) {}
}
