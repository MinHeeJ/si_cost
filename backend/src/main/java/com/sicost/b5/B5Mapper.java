package com.sicost.b5;

import com.sicost.b7.B7Dtos.Code;
import com.sicost.b7.B7Dtos.Issue;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.b5.B5Dtos.*;

@Mapper
public interface B5Mapper {
    List<DeveloperNumber> developers();
    List<Code> codes();
    String snapshot();
    Upload upload(long uploadId);
    Upload lockUpload(long uploadId);
    int insertUpload(Draft draft);
    int replaceSummary(UploadWrite write);
    int deleteIssues(long uploadId);
    int insertIssue(IssueWrite write);
    List<Issue> issues(long uploadId);
    List<Receipt> receipts(@Param("uploadId") long uploadId,@Param("reason") String reason);
    int markApplied(@Param("uploadId") long uploadId,@Param("actor") String actor,@Param("now") LocalDateTime now);
}
