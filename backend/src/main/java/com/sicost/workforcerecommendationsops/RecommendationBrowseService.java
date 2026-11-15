package com.sicost.workforcerecommendationsops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.workforcerecommendationsops.RecommendationBrowseDtos.*;

@Service
public class RecommendationBrowseService {
    private final RecommendationBrowseRepository repository;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final Clock clock;
    public RecommendationBrowseService(RecommendationBrowseRepository repository,RevisionService revisions,CurrentUserProvider users,Clock clock){
        this.repository=repository;this.revisions=revisions;this.users=users;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public HistoryData history(HistoryQuery query){users.currentUser();return repository.history(query);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public TargetsData targets(){
        users.currentUser();LocalDate today=LocalDate.now(clock.withZone(TimeConfiguration.SEOUL));
        String base=repository.cashBase();
        if(base==null||!base.matches("(?!0000)[0-9]{4}(0[1-9]|1[0-2])"))
            throw new ApiException(409,"STANDARD_NOT_FOUND","이관 시작월 기준을 확인할 수 없습니다.");
        String fallback=repository.firstJob();
        List<Target> items=repository.targets(today).stream()
            .sorted(Comparator.comparing(TargetSource::targetType).thenComparing(TargetSource::name).thenComparing(TargetSource::targetId))
            .map(source->{
                boolean opportunity="OPPORTUNITY".equals(source.targetType());
                List<Need> needs=opportunity?repository.needs(source.targetId()).stream()
                    .sorted(Comparator.comparing(n->new BigInteger(n.needSn())))
                    .map(n->new Need(n.needSn(),n.jobCode(),n.gradeCode(),n.needCount(),n.startMonth(),n.periodMonths(),n.monthlyMm(),
                        n.activeCount(),Math.max(0,n.needCount()-n.activeCount()),revisions.revision(n.snapshot()))).toList():List.of();
                String job=opportunity?(needs.isEmpty()?fallback:needs.get(0).jobCode()):repository.projectJob(source.targetId());
                if(job==null)job=fallback;
                return new Target(source.targetType(),source.targetId(),source.name(),source.contractTypeCode(),source.projectIdAfterWin(),
                    source.contractStartDate(),source.contractEndDate(),needs,
                    new Defaults(job,source.startMonth(),"SR",needs.isEmpty()?null:needs.get(0).needSn()),revisions.revision(repository.snapshot(source)));
            }).toList();
        return new TargetsData(items,items.isEmpty()?null:items.get(0).targetId(),today,YearMonth.from(today).toString(),
            base.substring(0,4)+"-"+base.substring(4),revisions.revision(items));
    }
}
