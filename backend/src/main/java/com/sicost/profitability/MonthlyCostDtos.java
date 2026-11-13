package com.sicost.profitability;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.List;

/** Shared read model. Database sources remain distinct from calculated/public projections. */
public final class MonthlyCostDtos {
    private MonthlyCostDtos() {}

    /** All four snapshot identity fields are significant, including synthetic IDLE/INDRCT rows. */
    public record CostKey(String month, String personId, String projectId, String assignmentId) {}
    public record PersonMonth(String month, String personId) {}
    public record Range(String fromMonth, String toMonth) {
        public Range {
            parseMonth(fromMonth); parseMonth(toMonth);
            if (fromMonth.compareTo(toMonth) > 0) throw new IllegalArgumentException("조회 기간의 순서를 확인해 주세요.");
        }
        public LocalDate fromDate() { return parseMonth(fromMonth).atDay(1); }
        public LocalDate toDate() { return parseMonth(toMonth).atEndOfMonth(); }
    }
    public record Person(String personId, String managementNo, String name, String employeeType,
        String companyId, String companyName, String gradeCode, String jobCode, String positionCode,
        String position, String directClass, String personPartId, String personPartName,
        LocalDate hireDate, LocalDate retireDate, String workStatus, String useYn) {
        /** Inclusive month boundaries; stopped/retired current status does not remove history. */
        public boolean employed(String month) {
            return employmentIncludes(hireDate, retireDate, month);
        }
        /** Person filters use DEV.PART_ID, never the nullable project sales part on a cost row. */
        public boolean matchesPart(String partId) {
            return partId == null || ("UNASSIGNED".equals(partId)
                ? personPartId == null : partId.equals(personPartId));
        }
    }
    public record Price(String personId, String fromMonth, String toMonth,
        BigDecimal monthlyPay, BigDecimal monthlyRate) {
        public boolean effective(String month) {
            return fromMonth.compareTo(month) <= 0 && (toMonth == null || toMonth.compareTo(month) >= 0);
        }
    }
    public record WorkState(String personId, String sequence, String status, LocalDate fromDate, LocalDate toDate) {
        public boolean includes(LocalDate date) {
            return !fromDate.isAfter(date) && (toDate == null || !toDate.isBefore(date));
        }
        public boolean fullMonthLeave(String month) {
            YearMonth value = parseMonth(month);
            return "LEAVE".equals(status) && includes(value.atDay(1)) && includes(value.atEndOfMonth());
        }
    }
    /** Uploaded classifications and nullable deductions must never be filled from today's master. */
    public record Payroll(String month, String personId, BigDecimal pay, BigDecimal allowance,
        BigDecimal companyInsurance, BigDecimal selfInsurance, BigDecimal retirementReserve,
        BigDecimal incomeTax, BigDecimal withholdingTax, BigDecimal netPay, BigDecimal vendorSupply,
        String employeeType, String directClass, String uploadId,
        String regUserId, LocalDateTime regDttm, String modUserId, LocalDateTime modDttm) {}
    public record Assignment(String month, String personId, String projectId, String assignmentId,
        BigDecimal assignmentMm, BigDecimal appliedUnitPrice, BigDecimal salesAmount, String fixed,
        String jobCode, String inputType, String contractPersonId, String status,
        LocalDate fromDate, LocalDate toDate) {}
    public record Project(String projectId, String name, String contractType, String status,
        LocalDate loseDate, LocalDate fromDate, LocalDate toDate, BigDecimal contractAmount, BigDecimal budget,
        BigDecimal teamExpense, BigDecimal projectExpense) {}
    public record ProjectPart(String projectId, String fromMonth, String partId) {}
    /** Read every stage/month: stage selection is project-wide, not month-local. */
    public record TurnkeyCell(String projectId, String step, String sequence, String month,
        String rowType, String personId, String itemName, BigDecimal amount, String uploadId) {
        @org.apache.ibatis.annotations.AutomapConstructor
        public TurnkeyCell {}
        public TurnkeyCell(String projectId, String step, String sequence, String month,
            String rowType, String personId, String itemName, BigDecimal amount) {
            this(projectId, step, sequence, month, rowType, personId, itemName, amount, null);
        }
    }
    public record Standard(String code, String fromMonth, BigDecimal value) {}
    public record Part(String partId, String name, String headquarters, String useYn) {}
    public record Company(String companyId, String name) {}

    public record StoredCost(String month, String personId, String projectId, String assignmentId,
        BigDecimal assignmentMm, BigDecimal freeMm, BigDecimal personalRevenue, BigDecimal laborAllocated,
        BigDecimal teamExpense, BigDecimal projectExpense, BigDecimal directExpense,
        String costClass, String contractType, String projectSalesPartId, String fixed,
        String regUserId, LocalDateTime regDttm, String modUserId, LocalDateTime modDttm,
        String projectName, String assignmentJobCode, String inputType) {
        public CostKey key() { return new CostKey(month, personId, projectId, assignmentId); }
        /** Stored amounts are authoritative; derived fields are not database columns. */
        public MonthlyCostRow toRow() {
            BigDecimal cost = laborAllocated == null || directExpense == null ? null : laborAllocated.add(directExpense);
            BigDecimal profit = personalRevenue == null || cost == null ? null : personalRevenue.subtract(cost);
            BigDecimal rate = profit == null || personalRevenue.signum() <= 0 ? null
                : profit.multiply(BigDecimal.valueOf(100)).divide(personalRevenue, 1, RoundingMode.HALF_UP);
            return new MonthlyCostRow(personId, projectId, assignmentId, month, mm(assignmentMm), mm(freeMm),
                money(personalRevenue), money(laborAllocated), money(teamExpense), money(projectExpense), money(directExpense),
                money(cost), money(profit), costClass, contractType, projectSalesPartId, fixed, projectName,
                assignmentJobCode, inputType, rate == null ? null : rate.toPlainString(), false,
                cost != null, personalRevenue != null, false, regUserId, auditTime(regDttm), modUserId, auditTime(modDttm));
        }
    }
    /** Complete company sources, never a UI-filtered subset. No source synchronization or cache. */
    public record Sources(ProfitabilityPeriod period, String actor, LocalDate asOfDate,
        List<Person> people, List<Price> prices, List<WorkState> states, List<Payroll> payroll,
        List<Assignment> assignments, List<Assignment> projectMonths, List<Project> projects,
        List<ProjectPart> projectParts, List<TurnkeyCell> turnkey, List<StoredCost> storedCosts,
        List<Standard> standards, List<Part> parts, List<com.sicost.shared.SharedQueryDtos.CloseRow> closes,
        List<com.sicost.shared.SharedQueryDtos.CodeLookup> allowanceCodes) {
        public boolean protectedMonth(String month) {
            return month.compareTo(period.baseMonth()) < 0
                || closes.stream().anyMatch(c -> c.month().equals(month) && "CLOSE".equals(c.status()));
        }
        public boolean protectedPersonMonth(String month, String personId) {
            return protectedMonth(month) || storedCosts.stream().anyMatch(c -> c.month().equals(month)
                && c.personId().equals(personId) && "Y".equals(c.fixed()));
        }
        /** Read-only protected projection, including PRE and zero-valued snapshots.
         * An empty result is missing evidence, not permission to calculate or generate a zero row.
         * The application service supplies completeness diagnostics; no current master reclassification.
         */
        public List<MonthlyCostRow> protectedRows(String month, String personId) {
            parseMonth(month);
            if (!protectedPersonMonth(month, personId))
                throw new IllegalArgumentException("미마감월은 월별 원가 계산을 사용해 주세요.");
            return storedCosts.stream().filter(c -> c.month().equals(month) && c.personId().equals(personId))
                .sorted(java.util.Comparator.comparing(StoredCost::projectId).thenComparing(StoredCost::assignmentId))
                .map(StoredCost::toRow).toList();
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProfitabilityPeriod(int year, String baseMonth, String fromMonth, String toMonth,
        OffsetDateTime calculatedAt, boolean noData) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MonthlyCostIssue(String personId, String month, String message, String source,
        String relatedScreen, String sourceAmount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MonthlyCostCompleteness(boolean costComplete, boolean revenueComplete,
        boolean estimated, boolean allocationComplete, List<MonthlyCostIssue> issues) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MonthlyLaborSource(String personId, String month, String employeeType, String directClass,
        String source, String laborCost, String pay, String allowance, String companyInsurance,
        String retirementReserve, String selfInsurance, String withholdingTax, String incomeTax,
        String netPay, boolean estimated) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MonthlyCostRow(String personId, String projectId, String assignmentId, String month,
        String assignmentMm, String freeMm, String personalRevenue, String laborAllocated,
        String teamExpense, String projectExpense, String directExpense, String totalCost, String profit,
        String costClass, String contractType, String projectSalesPartId, String fixed, String projectName,
        String assignmentJobCode, String inputType, String profitRate, boolean estimated, boolean costComplete,
        boolean revenueComplete, boolean rowGenerated, String regUserId, OffsetDateTime regDttm,
        String modUserId, OffsetDateTime modDttm) {
        public CostKey key() { return new CostKey(month, personId, projectId, assignmentId); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MonthlyPersonCost(String personId, String month, List<MonthlyCostRow> rows,
        MonthlyLaborSource laborSource, MonthlyCostCompleteness completeness, String knownLaborAllocated,
        String knownPersonalRevenue, String assignmentMm, boolean idleRowGenerated, boolean indirectRowGenerated) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MonthlyCostsData(ProfitabilityPeriod period, List<MonthlyPersonCost> items) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record IndividualProfitabilityMetrics(String revenue, String pay, String allowance,
        String companyInsurance, String retirementReserve, String personalCost, String teamExpense,
        String projectExpense, String totalCost, String profit, String selfInsurance, String withholdingTax,
        String incomeTax, String netPay, String mm, String profitRate) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProfitabilityMonthBasis(String month, String assignmentMm, int assignmentCount,
        boolean averageMonthIncluded, boolean employmentMonthIncluded, boolean utilizedMonthIncluded,
        String explanation) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProfitabilitySummary(int companyHeadcount, int companyLeaveCount, String averageHeadcount,
        String averageUtilizedHeadcount, String averageEarnedHeadcount, String companyUtilization,
        String companyEarnedRate, String averageRevenue, String averageCost, String averageProfit,
        int confirmedLossCount, int unknownCostCount, int unknownRevenueCount, int validRevenuePersonCount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProfitabilityContribution(String jobCode, String jobName, String gradeCode,
        String gradeName, int personCount, int unknownCount, String averageProfit) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProfitabilityViewContext(ProfitabilityPeriod period, String view, boolean includeTurnkey) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record IndividualProfitabilityPerson(String personId, String managementNo, String name,
        String companyName, String companyId, String personPartId, String personPartName, String position,
        String employeeType, LocalDate hireDate, String jobCode, String gradeCode,
        IndividualProfitabilityMetrics metrics, String utilization, int workedMonths, int employmentMonths,
        int utilizedMonths, boolean turnkeyOnlyExcluded, List<ProfitabilityMonthBasis> monthBasis,
        MonthlyCostCompleteness completeness) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record IndividualProfitabilityData(List<IndividualProfitabilityPerson> items, long total,
        int page, int size, ProfitabilityViewContext context, ProfitabilitySummary summary,
        IndividualProfitabilityMetrics totals, List<ProfitabilityContribution> contributions,
        int unknownCount, int estimatedCount, List<String> calculationBasis) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record IndividualProfitabilityPersonData(IndividualProfitabilityPerson person,
        ProfitabilityViewContext context, List<MonthlyPersonCost> months, List<MonthlyCostIssue> issues,
        List<String> calculationBasis) {}

    public static YearMonth parseMonth(String value) {
        if (value == null || !value.matches("(?!0000)[0-9]{6}")) throw new IllegalArgumentException("연월 형식을 확인해 주세요.");
        return YearMonth.of(Integer.parseInt(value.substring(0, 4)), Integer.parseInt(value.substring(4)));
    }
    public static boolean employmentIncludes(LocalDate hireDate, LocalDate retireDate, String month) {
        YearMonth value = parseMonth(month);
        return (hireDate == null || !value.isBefore(YearMonth.from(hireDate)))
            && (retireDate == null || !value.isAfter(YearMonth.from(retireDate)));
    }
    public static String money(BigDecimal value) { return value == null ? null : value.setScale(0, RoundingMode.UNNECESSARY).toPlainString(); }
    public static String mm(BigDecimal value) { return value == null ? null : value.setScale(2, RoundingMode.UNNECESSARY).toPlainString(); }
    private static OffsetDateTime auditTime(LocalDateTime value) {
        return value == null ? null : value.atZone(com.sicost.common.TimeConfiguration.SEOUL).toOffsetDateTime();
    }
}
