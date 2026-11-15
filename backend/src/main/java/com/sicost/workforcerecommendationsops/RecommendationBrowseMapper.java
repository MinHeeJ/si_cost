package com.sicost.workforcerecommendationsops;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.workforcerecommendationsops.RecommendationBrowseDtos.*;

@Mapper
public interface RecommendationBrowseMapper {
    List<HistoryItem> history(@Param("query") HistoryQuery query,@Param("offset") long offset);
    long historyCount(HistoryQuery query);
    List<TargetSource> targets(LocalDate today);
    List<NeedSource> needs(String id);
    String projectJob(String id);
    String firstJob();
    String cashBase();
    String opportunitySnapshot(String id);
    String projectSnapshot(String id);
}
