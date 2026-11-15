package com.sicost.withdrawalsops;

import com.sicost.apiops.WithdrawalDtos.Source;
import com.sicost.workforcerecommendations.WorkforceRecommendationDtos.Row;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NextPositionMapper {
    Source source(String assignmentId);
    List<String> sourceKeys(String developerId);
    String lockAssignment(String assignmentId);
    List<String> lockOpportunities();
    List<String> lockNeeds();
    String lockDeveloper(String developerId);
    String nextId();
    int insert(Row row);
}
