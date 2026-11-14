package com.sicost.candidatesops;

import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.PeopleInputPolicy;
import com.sicost.apiops.WorkforceForecastService;
import com.sicost.forecast.SalesForecastDtos;
import com.sicost.workforceforecastsops.WorkforceForecastInput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.candidatesops.CandidateMatchingDtos.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class CandidateMatchingService {
    public record Issue(String field, Integer needSn, String reason, String status) {}
    public record Read<T>(T data,List<Issue> issues) {}
    private record Ranked(Position position,BigDecimal numerator,BigDecimal denominator) {}
    private final CandidateMatchingRepository repository;
    private final WorkforceForecastService forecasts;
    private final CurrentUserProvider users;
    private final Clock clock;
    public CandidateMatchingService(CandidateMatchingRepository repository,WorkforceForecastService forecasts,CurrentUserProvider users,Clock clock) {
        this.repository=repository;this.forecasts=forecasts;this.users=users;this.clock=clock;
    }
    public Read<ShortagePage> shortages(CandidateMatchingInput.Query query) {
        users.currentUser(); YearMonth current=YearMonth.from(PeopleInputPolicy.today(clock)), from=current.plusMonths(1);
        var forecast=forecasts.forecast(new WorkforceForecastInput.Query(from,6,false,"THOUSAND"));
        var issues=new ArrayList<Issue>();
        forecast.issues().forEach(i->issues.add(issue(i.field(),null,i.reason())));
        BigDecimal threshold=repository.standard("SHORT_PERSON",db(current));
        if (threshold==null || threshold.signum()<0) issues.add(issue("SHORT_PERSON",null,"인력 부족 기준 미확인"));
        Map<String,List<SalesForecastDtos.JobMonth>> byJob=new TreeMap<>();
        if (threshold!=null && threshold.signum()>=0) for (var month:forecast.jobDiagnostics()) {
            if (month.month().difference()!=null && new BigDecimal(month.month().difference()).negate().compareTo(threshold)>0)
                byJob.computeIfAbsent(month.jobCode(),key->new ArrayList<>()).add(month);
        }
        var rows=new ArrayList<Shortage>();
        byJob.forEach((job,months)->{
            YearMonth first=months.stream().map(m->YearMonth.parse(m.month().month())).min(Comparator.naturalOrder()).orElseThrow();
            BigDecimal max=months.stream().map(m->new BigDecimal(m.month().difference()).negate()).max(Comparator.naturalOrder()).orElseThrow();
            var counts=repository.counts(job,first.atEndOfMonth());
            rows.add(new Shortage(job,first.toString(),max.setScale(1,RoundingMode.HALF_UP).toPlainString(),first.atEndOfMonth(),counts.available(),counts.active()));
        });
        Map<String,Integer> orders=new HashMap<>(); repository.jobs().forEach(j->orders.put(j.jobCode(),j.sortOrder()));
        Comparator<Shortage> order=switch(query.sort()==null?"firstShortageMonth":query.sort()) {
            case "maximumShortage" -> Comparator.comparing(r->new BigDecimal(r.maximumShortage()));
            case "jobCode" -> Comparator.comparing(Shortage::jobCode);
            default -> Comparator.comparing(Shortage::firstShortageMonth);
        };
        if ("DESC".equals(query.direction())) order=order.reversed();
        if (query.sort()==null) order=order.thenComparing(r->orders.get(r.jobCode()),Comparator.nullsLast(Comparator.naturalOrder()));
        rows.sort(order.thenComparing(Shortage::jobCode));
        var items=rows.stream().skip((long)(query.page()-1)*query.size()).limit(query.size()).toList();
        return new Read<>(new ShortagePage(items,rows.size(),query.page(),query.size(),false,from.toString(),6),List.copyOf(issues));
    }
    public Read<PositionsResult> positions(String id) {
        users.currentUser(); Candidate candidate=repository.required(id);
        var issues=new ArrayList<Issue>(); var rows=new ArrayList<Ranked>();
        if (!Set.of("HOLD","CTRT").contains(candidate.contactStatus())) {
            BigDecimal cost=candidate.desiredRate();
            if ("FL".equals(candidate.employeeType())) {
                Map<String,BigDecimal> allowances=new HashMap<>(); repository.allowances().forEach(c->allowances.put(c.code(),c.value()));
                for (String code:List.of("TEAM_FEE","PEXP_FEE")) {
                    BigDecimal value=allowances.get(code);
                    if (value==null || value.signum()<0 || value.stripTrailingZeros().scale()>0) {
                        issues.add(issue(code,null,"공통 기본경비 미확인")); cost=null;
                    } else if (cost!=null) cost=cost.add(value);
                }
            }
            YearMonth current=YearMonth.from(PeopleInputPolicy.today(clock));
            BigDecimal threshold=repository.standard("QUOTE_APRV_RT",db(current));
            if (threshold==null || threshold.signum()<0 || threshold.compareTo(new BigDecimal("100"))>0) {
                issues.add(issue("QUOTE_APRV_RT",null,"견적 승인 기준 미확인")); threshold=null;
            }
            for (Need need:repository.needs(candidate.jobCode())) {
                YearMonth start=YearMonth.parse(DatabaseMonthCodec.toApi(need.startMonth()));
                YearMonth end=start.plusMonths(need.months()-1L);
                if (end.isBefore(YearMonth.from(candidate.availableDate()))) continue;
                BigDecimal rate=need.expectedRate();
                if (rate==null) rate=positiveRate(need.defaultRate());
                if (rate==null || rate.signum()<=0) { issues.add(issue("expectedRate",need.needSn(),"유효 자리단가 미확인")); continue; }
                if (cost==null || threshold==null) continue;
                Integer distance=need.gradeCode()==null || need.gradeOrder()==null || candidate.gradeOrder()==null
                    ?null:Math.abs(need.gradeOrder()-candidate.gradeOrder());
                BigDecimal numerator=rate.subtract(cost).multiply(new BigDecimal("100"));
                boolean below=numerator.compareTo(threshold.multiply(rate))<0;
                String profit=numerator.divide(rate,1,RoundingMode.HALF_UP).toPlainString();
                YearMonth effectiveStart=start.isBefore(YearMonth.from(candidate.availableDate()))?YearMonth.from(candidate.availableDate()):start;
                Position position=new Position(need.opportunityId(),need.name(),need.needSn(),need.probability().stripTrailingZeros().toPlainString(),
                    need.jobCode(),need.gradeCode(),effectiveStart.toString(),end.toString(),candidate.availableDate(),money(rate),money(cost),profit,below,distance);
                rows.add(new Ranked(position,numerator,rate));
            }
        }
        rows.sort(Comparator.comparing((Ranked r)->r.position().gradeDistance(),Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing((a,b)->b.numerator().multiply(a.denominator()).compareTo(a.numerator().multiply(b.denominator())))
            .thenComparing(r->r.position().opportunityId()).thenComparingInt(r->r.position().needSn()));
        List<Position> items=rows.stream().limit(4).map(Ranked::position).toList();
        return new Read<>(new PositionsResult(id,items,items.size(),4),List.copyOf(issues));
    }
    private static BigDecimal positiveRate(String value) {
        if (value==null || !value.matches("[1-9][0-9]{0,14}")) return null;
        return new BigDecimal(value);
    }
    private static String money(BigDecimal value) { return value.setScale(0,RoundingMode.DOWN).toPlainString(); }
    private static String db(YearMonth month) { return DatabaseMonthCodec.toDatabase(month.toString()); }
    private static Issue issue(String field,Integer needSn,String reason) { return new Issue(field,needSn,reason,"UNKNOWN"); }
}
