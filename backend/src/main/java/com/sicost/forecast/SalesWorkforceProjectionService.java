package com.sicost.forecast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.forecast.SalesForecastSources.*;
import static com.sicost.forecast.SalesForecastDtos.*;

@Service
public class SalesWorkforceProjectionService {
    private record MonthValues(String month,BigDecimal supply,BigDecimal opportunity,BigDecimal unfilled,BigDecimal demand,
        String rawSupply,String rawOpportunity,String rawUnfilled) {}
    private record Quantity(BigDecimal numerator, BigDecimal denominator) {
        Quantity add(Quantity q) { return new Quantity(numerator.multiply(q.denominator).add(q.numerator.multiply(denominator)), denominator.multiply(q.denominator)); }
        BigDecimal rounded() { return numerator.divide(denominator, 1, RoundingMode.HALF_UP); }
        String raw() { return numerator.divide(denominator, 16, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
    }
    private static Quantity quantity(BigDecimal n) { return new Quantity(n, BigDecimal.ONE); }
    public Workforce calculate(Snapshot sources, LocalDate day, YearMonth from, int months, boolean includeUnfilled) {
        return calculate(sources, day, from, months, includeUnfilled, null, null);
    }
    public Workforce calculate(Snapshot sources, LocalDate day, YearMonth from, int months, boolean includeUnfilled,
                               Boolean includeOpportunities, BigDecimal minimumWinProbability) {
        if (months < 1 || months > 12 || from.isBefore(YearMonth.from(day)) || from.plusMonths(months-1).getYear() > 9999)
            throw new IllegalArgumentException("예측 기간을 확인해 주세요.");
        YearMonth current = YearMonth.from(day);
        List<Issue> issues = new ArrayList<>();
        BigDecimal shortage = standard(sources, "SHORT_PERSON", false, issues);
        BigDecimal surplus = standard(sources, "SURPLUS_PERSON", false, issues);
        BigDecimal lead = standard(sources, "HIRE_LEAD_MON", true, issues);
        Map<String, Project> projects = new HashMap<>(); sources.projects().forEach(p -> projects.put(p.id(),p));
        Map<String, Opportunity> opportunities = new HashMap<>(); sources.opportunities().stream()
            .filter(o -> !Boolean.FALSE.equals(includeOpportunities) && activeOpportunity(sources,o))
            .filter(o -> minimumWinProbability == null || o.probability().compareTo(minimumWinProbability) >= 0)
            .forEach(o -> opportunities.put(o.id(),o));
        Map<String,Integer> order = new HashMap<>(); sources.jobs().forEach(j -> order.put(j.code(),j.sortOrder()));
        Set<String> jobs = new TreeSet<>(Comparator.comparingInt((String j) -> order.getOrDefault(j,Integer.MAX_VALUE)).thenComparing(j -> j));
        sources.people().forEach(p -> jobs.add(p.jobCode())); sources.needs().stream().filter(n -> includeOpportunities == null || opportunities.containsKey(n.opportunityId())).forEach(n -> jobs.add(n.jobCode()));
        sources.plans().forEach(p -> jobs.add(p.jobCode()));
        String basis = includeOpportunities == null ? "NEED 확률"
            : includeOpportunities ? "수주 확률 조건 충족 기회의 필요 MM 전량" : "영업 기회 제외";
        List<JobMonth> diagnostics = new ArrayList<>();
        List<Hiring> hiring = new ArrayList<>();
        String mostJob = null; BigDecimal most = BigDecimal.ZERO;
        for (String job : jobs) {
            List<WorkforceMonth> vector = new ArrayList<>();
            for (int index=0; index<months; index++) {
                YearMonth month = from.plusMonths(index);
                BigDecimal supply = BigDecimal.ZERO; boolean known = true;
                for (Person person : sources.people()) {
                    if (!job.equals(person.jobCode()) || !eligible(sources, person, month)) continue;
                    BigDecimal confirmed = BigDecimal.ZERO, extension = BigDecimal.ZERO;
                    for (Assignment assignment : sources.assignments()) {
                        var a = assignment.source();
                        if (!person.id().equals(a.developerId()) || !"ACT".equals(a.status())) continue;
                        for (var m : assignment.months()) if (m.month().equals(month.toString())) confirmed = confirmed.add(new BigDecimal(m.monthlyMm()));
                        YearMonth end = YearMonth.from(a.endDate());
                        Project p = projects.get(a.projectId());
                        if (!end.isBefore(current) && end.isBefore(month) && p!=null && !"LOSE".equals(p.status())) {
                            String key = "EXT_PRBL_" + (a.extensionPossibility()==null ? "NULL" : a.extensionPossibility());
                            BigDecimal rate = sources.standards().get(key);
                            if (rate==null || rate.signum()<0 || rate.compareTo(new BigDecimal("100"))>0) {
                                known=false; issue(issues,key,"연장 확률 기준 미확인");
                            } else extension = extension.add(a.contractMm().multiply(rate).movePointLeft(2));
                        }
                    }
                    supply = supply.add(BigDecimal.ONE.subtract(confirmed).subtract(extension).max(BigDecimal.ZERO));
                }
                BigDecimal opportunity = BigDecimal.ZERO;
                for (Need need : sources.needs()) {
                    Opportunity o=opportunities.get(need.opportunityId());
                    if (o!=null && job.equals(need.jobCode()) && contains(month,parse(need.startMonth()),need.months())) {
                        BigDecimal required = need.monthlyMm().multiply(BigDecimal.valueOf(need.count()));
                        if (includeOpportunities == null) required = required.multiply(o.probability()).movePointLeft(2);
                        opportunity=opportunity.add(required);
                    }
                }
                Quantity unfilled=quantity(BigDecimal.ZERO);
                if (includeUnfilled) for (Plan plan : sources.plans()) {
                    Project p=projects.get(plan.projectId());
                    if (!job.equals(plan.jobCode()) || !activeProject(p,day)) continue;
                    YearMonth start=max(current.plusMonths(1),YearMonth.from(p.startDate()));
                    long count=ChronoUnit.MONTHS.between(start,YearMonth.from(p.endDate()))+1;
                    if (count<=0 || month.isBefore(start) || month.isAfter(YearMonth.from(p.endDate()))) continue;
                    BigDecimal actual=BigDecimal.ZERO;
                    for (Assignment a : sources.assignments()) if ("ACT".equals(a.source().status()) && p.id().equals(a.source().projectId()) && job.equals(a.source().jobCode()))
                        for (var m : a.months()) actual=actual.add(new BigDecimal(m.monthlyMm()));
                    unfilled=unfilled.add(new Quantity(plan.mm().subtract(actual).max(BigDecimal.ZERO),BigDecimal.valueOf(count)));
                }
                Quantity demand=quantity(opportunity).add(unfilled);
                BigDecimal s=known?supply.setScale(1,RoundingMode.HALF_UP):null, d=demand.rounded();
                vector.add(month(new MonthValues(month.toString(),s,opportunity.setScale(1,RoundingMode.HALF_UP),unfilled.rounded(),d,
                    known?supply.toPlainString():null,opportunity.toPlainString(),unfilled.raw()),shortage,surplus));
            }
            YearMonth first=null; BigDecimal maximum=BigDecimal.ZERO;
            for (WorkforceMonth value : vector) {
                diagnostics.add(new JobMonth(job,value,"활성 직접 주직무 · 05 확정월MM · 종료후 연장 · " + basis + " · 전체계약 미충원"));
                if (value.difference()==null) continue;
                BigDecimal missing=new BigDecimal(value.difference()).negate();
                if (missing.compareTo(most)>0) {most=missing;mostJob=job;}
                maximum=maximum.max(missing);
                if (first==null && shortage!=null && missing.compareTo(shortage)>0) first=YearMonth.parse(value.month());
            }
            if (first!=null && lead!=null) {
                YearMonth recommended=first.minusMonths(lead.longValueExact());
                if (recommended.getYear()<1) {issue(issues,"HIRE_LEAD_MON","권장 시작월 범위 미확인");continue;}
                int candidates=(int)sources.candidates().stream().filter(c -> job.equals(c.jobCode())).count();
                hiring.add(new Hiring(job,first.toString(),maximum.toPlainString(),recommended.toString(),!recommended.isAfter(current),candidates));
            }
        }
        List<WorkforceMonth> totals=new ArrayList<>();
        for (int i=0;i<months;i++) {
            String key=from.plusMonths(i).toString();
            var rows=diagnostics.stream().map(JobMonth::month).filter(m -> key.equals(m.month())).toList();
            BigDecimal supply=sum(rows,WorkforceMonth::supply),opportunity=sum(rows,WorkforceMonth::opportunityDemand),
                unfilled=sum(rows,WorkforceMonth::unfilledDemand),demand=sum(rows,WorkforceMonth::demand);
            totals.add(month(new MonthValues(key,supply,opportunity,unfilled,demand,sumText(rows,WorkforceMonth::rawSupply),
                sumText(rows,WorkforceMonth::rawOpportunityDemand),sumText(rows,WorkforceMonth::rawUnfilledDemand)),shortage,surplus));
        }
        var last=totals.get(totals.size()-1);
        return new Workforce(from.toString(),months,includeUnfilled,day.toString(),current.toString(),List.copyOf(totals),
            List.copyOf(diagnostics),List.copyOf(hiring),List.copyOf(issues),last.supply(),last.demand(),mostJob,mostJob==null?null:most.toPlainString());
    }
    private WorkforceMonth month(MonthValues values,BigDecimal shortage,BigDecimal surplus) {
        BigDecimal difference=values.supply()==null||values.demand()==null?null:values.supply().subtract(values.demand());
        String status=difference==null||shortage==null||surplus==null?"UNKNOWN":difference.compareTo(shortage.negate())<0?"SHORT":difference.compareTo(surplus)>0?"SURPLUS":"BALANCED";
        return new WorkforceMonth(values.month(),text(values.supply()),text(values.opportunity()),text(values.unfilled()),text(values.demand()),text(difference),values.rawSupply(),values.rawOpportunity(),values.rawUnfilled(),status);
    }
    private BigDecimal standard(Snapshot sources,String code,boolean integer,List<Issue> issues) {
        BigDecimal value=sources.standards().get(code);
        if (value==null || value.signum()<0 || integer && value.stripTrailingZeros().scale()>0) {issue(issues,code,"예측 기준 미확인");return null;}
        return value;
    }
    private static void issue(List<Issue> issues,String field,String reason) {
        Issue issue=new Issue(field,null,reason,"UNKNOWN");if(!issues.contains(issue))issues.add(issue);
    }
    private boolean eligible(Snapshot s,Person p,YearMonth m) {
        return "Y".equals(p.useYn()) && "DRCT".equals(p.directClass())
            && (p.joinDate()==null || !YearMonth.from(p.joinDate()).isAfter(m))
            && (p.retireDate()==null || !YearMonth.from(p.retireDate()).isBefore(m))
            && s.leaves().stream().noneMatch(l -> p.id().equals(l.developerId()) && !l.startDate().isAfter(m.atEndOfMonth()) && (l.endDate()==null || !l.endDate().isBefore(m.atDay(1))));
    }
    public static boolean activeOpportunity(Snapshot s,Opportunity o) {
        return "ING".equals(o.status()) && o.projectId()==null && s.projects().stream().noneMatch(p -> o.id().equals(p.opportunityId()));
    }
    public static boolean activeProject(Project p,LocalDate day) {
        return p!=null && "ING".equals(p.status()) && Set.of("TK","MO","DP").contains(p.type()) && !p.endDate().isBefore(day);
    }
    public static YearMonth parse(String dbMonth) {return YearMonth.parse(com.sicost.common.persistence.DatabaseMonthCodec.toApi(dbMonth));}
    public static YearMonth max(YearMonth a,YearMonth b) {return a.isAfter(b)?a:b;}
    private static boolean contains(YearMonth m,YearMonth start,int count) {return !m.isBefore(start)&&!m.isAfter(start.plusMonths(count-1));}
    private static String text(BigDecimal value) {return value==null?null:value.toPlainString();}
    private static BigDecimal sum(List<WorkforceMonth> rows,java.util.function.Function<WorkforceMonth,String> get) {
        BigDecimal total=BigDecimal.ZERO.setScale(1);for(var row:rows){String value=get.apply(row);if(value==null)return null;total=total.add(new BigDecimal(value));}return total;
    }
    private static String sumText(List<WorkforceMonth> rows,java.util.function.Function<WorkforceMonth,String> get) {return text(sum(rows,get));}
}
