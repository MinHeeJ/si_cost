package com.sicost.plans;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.plans.ProjectPlanDtos.*;

@Mapper
public interface ProjectPlanMapper {
    List<StoredPlan> plans(@Param("id") String id);
    List<String> activeJobs();
    int insert(PlanMutation mutation);
    int update(PlanMutation mutation);
}
