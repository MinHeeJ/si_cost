package com.sicost.b9;

import com.sicost.b7.B7Dtos.Code;
import com.sicost.b7.B7Dtos.Issue;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.b9.B9Dtos.*;

@Mapper
public interface B9Mapper {
    List<Code> codes();
    List<String> existingIds();
    List<Candidate> customers();
    List<Part> parts();
    Defaults defaults(@Param("month") String month);
    int insertUpload(UploadDraft draft);
    int replaceSummary(UploadWrite write);
    int deleteIssues(@Param("uploadId") long uploadId);
    int insertIssue(IssueWrite write);
    List<Issue> issues(@Param("uploadId") long uploadId);
    Upload lockUpload(@Param("uploadId") long uploadId);
    String context(@Param("uploadId") long uploadId);
    List<Receipt> results(@Param("reasonPrefix") String reasonPrefix);
    int markApplied(@Param("uploadId") long uploadId, @Param("actor") String actor, @Param("now") LocalDateTime now);
}
