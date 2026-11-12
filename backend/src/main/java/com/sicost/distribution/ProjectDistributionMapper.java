package com.sicost.distribution;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.distribution.ProjectDistributionDtos.*;

@Mapper
public interface ProjectDistributionMapper {
    Project project(ProjectLookup lookup);
    List<StoredCell> cells(@Param("id") String id);
    List<Job> jobs(@Param("id") String id);
    List<Actual> actuals(@Param("id") String id);
    List<UploadRecord> uploads(@Param("id") String id);
    Aggregate aggregate(@Param("id") String id);
}
