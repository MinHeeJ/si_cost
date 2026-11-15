package com.sicost.workforcerecommendationsops;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.workforcerecommendationsops.RecommendationBrowseDtos.*;

@Repository
public class RecommendationBrowseRepository {
    private final RecommendationBrowseMapper mapper;
    public RecommendationBrowseRepository(RecommendationBrowseMapper mapper){this.mapper=mapper;}
    public HistoryData history(HistoryQuery query){
        long total=mapper.historyCount(query);
        return new HistoryData(mapper.history(query,query.offset()),query.page(),query.size(),total);
    }
    public List<TargetSource> targets(LocalDate today){return mapper.targets(today);}
    public List<NeedSource> needs(String id){return mapper.needs(id);}
    public String projectJob(String id){return mapper.projectJob(id);}
    public String firstJob(){return mapper.firstJob();}
    public String cashBase(){return mapper.cashBase();}
    public String snapshot(TargetSource target){return "OPPORTUNITY".equals(target.targetType())
        ?mapper.opportunitySnapshot(target.targetId()):mapper.projectSnapshot(target.targetId());}
}
