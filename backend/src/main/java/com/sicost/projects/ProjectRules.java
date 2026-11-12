package com.sicost.projects;

import com.sicost.budget.ProjectBudgetCalculation;
import com.sicost.common.api.ApiException;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.sicost.projects.ProjectDtos.*;

public final class ProjectRules {
    private ProjectRules() {}
    public static String effectiveStatus(Project project,LocalDate today) {
        return "LOSE".equals(project.status()) ? "LOSE" : project.endDate().isBefore(today) ? "END" : project.status();
    }
    public static String savedStatus(String previous,LocalDate end,LocalDate today) {
        return "LOSE".equals(previous) ? "LOSE" : end.isBefore(today) ? "END" : "END".equals(previous) ? "ING" : previous;
    }
    public static YearMonth cashBase(DefaultsRow defaults) {
        if (defaults==null || defaults.cashBase()==null) throw new IllegalStateException("운영 시작월을 확인해 주세요.");
        try {
            String month=defaults.cashBase().toBigIntegerExact().toString();
            if (!month.matches("[0-9]{6}")) throw new IllegalArgumentException();
            return ProjectInputPolicy.month(month.substring(0,4)+"-"+month.substring(4),"year");
        } catch (RuntimeException e) { throw new IllegalStateException("운영 시작월을 확인해 주세요."); }
    }
    public static ProjectDefaults defaults(DefaultsRow row,String type,BigDecimal contract,YearMonth month) {
        BigDecimal rate=ProjectBudgetCalculation.validRate(row.marginRate());
        BigDecimal budget="IN".equals(type)?null:ProjectBudgetCalculation.preview(contract,rate);
        BigDecimal team=ProjectBudgetCalculation.allowanceDefault(row.teamExpense());
        BigDecimal expense=ProjectBudgetCalculation.allowanceDefault(row.projectExpense());
        List<String> warnings=new ArrayList<>();
        if (!"IN".equals(type) && budget==null) warnings.add("유효한 자동 예산 기준이 없습니다. 예산을 수기로 입력해 주세요.");
        if (team==null) warnings.add("유효한 팀비 기본값이 없습니다. 팀비를 직접 입력해 주세요.");
        if (expense==null) warnings.add("유효한 프로젝트경비 기본값이 없습니다. 프로젝트경비를 직접 입력해 주세요.");
        return new ProjectDefaults(money(contract),type,money(budget),money(rate),money(team),money(expense),month.toString(),List.copyOf(warnings));
    }
    public static BigDecimal allowance(BigDecimal explicit,BigDecimal fallback,String field) {
        BigDecimal value=explicit==null?ProjectBudgetCalculation.allowanceDefault(fallback):explicit;
        if (value==null) throw new ApiException(400,"INVALID_INPUT","유효한 기본값이 없습니다. 원 정수를 직접 입력해 주세요.",field,null);
        return value;
    }
    public static void internalFields(Fields fields,String partId) {
        if (fields.customerId()!=null) throw ProjectInputPolicy.invalid("customerId");
        if (partId!=null) throw ProjectInputPolicy.invalid("partId");
        if (fields.workplace()!=null) throw ProjectInputPolicy.invalid("workplace");
        if (fields.budgetAmount()!=null || "MANUAL".equals(fields.budgetMode())) throw ProjectInputPolicy.invalid("budgetAmount");
    }
    public static String money(BigDecimal value) { return ProjectInputPolicy.amountString(value); }
    public static BigDecimal number(String value) { return value==null?null:new BigDecimal(value); }
    public static List<ProjectChange> changes(Project old,Project next,Reference oldCustomer,Reference nextCustomer) {
        var result=new ArrayList<ProjectChange>();
        change(result,"ETC","프로젝트명",old.name(),next.name());
        if (!Objects.equals(old.customerId(),next.customerId())) change(result,"ETC","고객사",oldCustomer==null?null:oldCustomer.name(),nextCustomer==null?null:nextCustomer.name());
        change(result,"PERIOD","계약 시작일",old.startDate().toString(),next.startDate().toString());
        change(result,"PERIOD","계약 종료일",old.endDate().toString(),next.endDate().toString());
        if (!Objects.equals(old.contractAmount(),next.contractAmount())) {
            boolean original=!Objects.equals(old.originalContractAmount(),next.originalContractAmount());
            change(result,"AMT","계약금액",old.contractAmount()+(original?" · 원 계약금액: "+Objects.toString(old.originalContractAmount(),old.contractAmount()):""),
                next.contractAmount()+(original?" · 원 계약금액: "+next.originalContractAmount():""));
        }
        change(result,"LOC","근무지",old.workplace(),next.workplace());
        if (!Objects.equals(old.budgetAmount(),next.budgetAmount()) || !Objects.equals(old.budgetManual(),next.budgetManual()))
            change(result,"BUDGET","예산",budgetValue(old),budgetValue(next));
        change(result,"TEAM","팀비",old.teamExpenseAmount(),next.teamExpenseAmount());
        change(result,"PEXP","프로젝트경비",old.projectExpenseAmount(),next.projectExpenseAmount());
        change(result,"ETC","견적 금액",old.estimateAmount(),next.estimateAmount());
        change(result,"ETC","특징",old.feature(),next.feature());
        change(result,"ETC","상태",old.status(),next.status());
        return result;
    }
    private static String budgetValue(Project p) { return "budgetAmount="+Objects.toString(p.budgetAmount(),"NULL")+",budgetManual="+p.budgetManual(); }
    private static void change(List<ProjectChange> changes,String code,String name,String before,String after) {
        if (!Objects.equals(before,after)) changes.add(new ProjectChange(code,name,before,after));
    }
    public static List<LocalDate> intersection(LocalDate start,LocalDate end,YearMonth month) {
        LocalDate from=start.isAfter(month.atDay(1))?start:month.atDay(1), to=end.isBefore(month.atEndOfMonth())?end:month.atEndOfMonth();
        return from.isAfter(to)?Collections.emptyList():List.of(from,to);
    }
}
