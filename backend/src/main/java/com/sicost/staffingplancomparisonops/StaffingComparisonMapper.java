package com.sicost.staffingplancomparisonops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.staffingplancomparisonops.StaffingComparisonDtos.*;

@Mapper
public interface StaffingComparisonMapper {
    List<Project> projects(Query query);
    List<Plan> plans(String projectId);
    List<String> assignmentIds(String projectId);
}
