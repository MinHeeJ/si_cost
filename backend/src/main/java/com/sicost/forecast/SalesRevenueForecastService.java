package com.sicost.forecast;

import com.sicost.common.context.CurrentUserProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.forecast.SalesForecastSources.*;
import static com.sicost.forecast.SalesForecastDtos.*;
import static com.sicost.forecast.SalesWorkforceProjectionService.*;

@Service
public class SalesRevenueForecastService {
    private final SalesForecastRepository repository;
    private final SalesWorkforceProjectionService workforce;
    private final CurrentUserProvider users;
    private final Clock clock;
    public SalesRevenueForecastService(SalesForecastRepository repository, SalesWorkforceProjectionService workforce,
                                       CurrentUserProvider users, Clock clock) {
        this.repository=repository; this.workforce=workforce; this.users=users; this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Result forecast() {
        users.currentUser();
        LocalDate day=LocalDate.ofInstant(clock.instant(),ZoneId.of("Asia/Seoul"));
        return calculate(repository.load(YearMonth.from(day)),day);
    }
    public Result calculate(Snapshot snapshot,LocalDate day) {
        YearMonth from=YearMonth.from(day).plusMonths(1);
        List<RevenueSource> rows=new ArrayList<>();
        List<RevenueMonth> months=new ArrayList<>();
        for (int i=0;i<6;i++) {
            YearMonth month=from.plusMonths(i);
            for (Project project:snapshot.projects()) {
                if (!activeProject(project,day)) continue;
                YearMonth start=max(from,YearMonth.from(project.startDate())), end=YearMonth.from(project.endDate());
                if (month.isBefore(start)||month.isAfter(end)) continue;
                BigDecimal amount=BigDecimal.ZERO;
                String basis;
                if ("TK".equals(project.type())) {
                    long remaining=ChronoUnit.MONTHS.between(start,end)+1;
                    BigDecimal balance=project.amount().subtract(project.billed()).max(BigDecimal.ZERO);
                    BigDecimal share=balance.divide(BigDecimal.valueOf(remaining),0,RoundingMode.DOWN);
                    amount=month.equals(end)?balance.subtract(share.multiply(BigDecimal.valueOf(remaining-1))):share;
                    basis="TK 미취소 등록청구(미래일 포함) 차감 · 잔액 "+balance.toPlainString()+" / 전체잔여 "+remaining+"개월 · 마지막 계약월 끝자리";
                } else {
                    for (Assignment assignment:snapshot.assignments()) if (project.id().equals(assignment.source().projectId())
                        && "ACT".equals(assignment.source().status()) && !"FREE".equals(assignment.source().inputType()))
                        for (var value:assignment.months()) if (month.toString().equals(value.month())) amount=amount.add(new BigDecimal(value.salesAmount()));
                    basis="05 해당월 유상매출 · 철수일까지 · 예상연장 제외";
                }
                rows.add(new RevenueSource("PROJECT",project.id(),project.name(),project.type(),month.toString(),amount.toPlainString(),null,basis));
            }
            for (Opportunity opportunity:snapshot.opportunities()) {
                if (!activeOpportunity(snapshot,opportunity)) continue;
                YearMonth start=parse(opportunity.startMonth());
                int count=opportunity.months()==null?6:opportunity.months();
                if (month.isBefore(start)||month.isAfter(start.plusMonths(count-1))) continue;
                BigDecimal amount=opportunity.amount().multiply(opportunity.probability()).divide(BigDecimal.valueOf(count).multiply(new BigDecimal("100")),0,RoundingMode.DOWN);
                BigDecimal all=opportunity.amount().divide(BigDecimal.valueOf(count),0,RoundingMode.DOWN);
                rows.add(new RevenueSource("OPPORTUNITY",opportunity.id(),opportunity.name(),opportunity.type(),month.toString(),amount.toPlainString(),all.toPlainString(),
                    "DOWN(예상금액 / "+count+"개월 × "+opportunity.probability().toPlainString()+" / 100) · 모두 따면 확률100% 참고"));
            }
            var selected=rows.stream().filter(r -> month.toString().equals(r.month())).toList();
            BigDecimal project=sum(selected.stream().filter(r -> "PROJECT".equals(r.sourceType())).map(RevenueSource::amount).toList());
            BigDecimal opportunity=sum(selected.stream().filter(r -> "OPPORTUNITY".equals(r.sourceType())).map(RevenueSource::amount).toList());
            BigDecimal all=project.add(sum(selected.stream().filter(r -> "OPPORTUNITY".equals(r.sourceType())).map(RevenueSource::allWonAmount).toList()));
            months.add(new RevenueMonth(month.toString(),project.toPlainString(),opportunity.toPlainString(),project.add(opportunity).toPlainString(),all.toPlainString()));
        }
        Workforce people=workforce.calculate(snapshot,day,from,6,true);
        Summary summary=new Summary(sum(months.stream().map(RevenueMonth::totalRevenue).toList()).toPlainString(),
            sum(months.stream().map(RevenueMonth::opportunityRevenue).toList()).toPlainString(),sum(months.stream().map(RevenueMonth::allWonRevenue).toList()).toPlainString(),
            average(people.totals().stream().map(WorkforceMonth::demand).toList()),average(people.totals().stream().map(WorkforceMonth::supply).toList()),people.hiring().size());
        return new Result(from.toString(),6,summary,List.copyOf(months),List.copyOf(rows),people);
    }
    private static BigDecimal sum(List<String> values) {return values.stream().map(BigDecimal::new).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private static String average(List<String> values) {return values.stream().anyMatch(Objects::isNull)?null:sum(values).divide(BigDecimal.valueOf(6),1,RoundingMode.HALF_UP).toPlainString();}
}
