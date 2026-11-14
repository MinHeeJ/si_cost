package com.sicost.projectstatusboardops;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.projectstatusboardops.ProjectStatusDtos.*;

@Mapper
public interface ProjectStatusIssueMapper {
    List<IssueSource> eligibleIssues(@Param("today") LocalDate today);
    String projectStatus(@Param("projectId") String projectId,@Param("today") LocalDate today);
}
