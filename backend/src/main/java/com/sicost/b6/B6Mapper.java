package com.sicost.b6;

import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.B7Dtos.IssueWrite;
import com.sicost.b9.B9Dtos.Upload;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.b6.B6Dtos.*;

@Mapper
public interface B6Mapper {
    Project project(@Param("id") String id);
    List<Developer> developers();
    List<Cell> cells(@Param("id") String id);
    List<Actual> actuals(@Param("id") String id);
    List<String> protectedMonths(@Param("id") String id);
    void lockClose();
    int insertUpload(B6UploadDraft draft);
    int replaceSummary(UploadWrite write);
    int deleteIssues(@Param("uploadId") long id);
    int insertIssue(IssueWrite write);
    List<Issue> issues(@Param("uploadId") long id);
    Upload lockUpload(@Param("uploadId") long id);
    String context(@Param("uploadId") long id);
    int deleteStage(@Param("id") String id,@Param("step") String step);
    int insertCell(CellWrite write);
    int markApplied(@Param("uploadId") long id,@Param("actor") String actor,@Param("now") LocalDateTime now);
}
