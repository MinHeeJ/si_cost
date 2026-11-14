package com.sicost.projectstatusboardops;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.projectstatusboardops.ProjectStatusDtos.*;

@Repository
public class ProjectStatusIssueRepository {
    private final ProjectStatusIssueMapper mapper;
    public ProjectStatusIssueRepository(ProjectStatusIssueMapper mapper){this.mapper=mapper;}
    public List<IssueSource> eligibleIssues(LocalDate day){return mapper.eligibleIssues(day);}
    public String projectStatus(String projectId,LocalDate day){return mapper.projectStatus(projectId,day);}
}
