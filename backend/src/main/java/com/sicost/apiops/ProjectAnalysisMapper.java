package com.sicost.apiops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.apiops.ProjectAnalysisDtos.*;

@Mapper
public interface ProjectAnalysisMapper {
    List<ProjectEvidence> evidence();
    List<Expense> expenses();
    List<Stage> stages();
    List<Memo> memos();
    List<Issue> issues();
    List<ProjectManager> managers();
    List<String> assignmentIds();
    List<StageTemplate> templates();
}
