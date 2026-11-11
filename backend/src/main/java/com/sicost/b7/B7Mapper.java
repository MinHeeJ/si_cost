package com.sicost.b7;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.b7.B7Dtos.*;

@Mapper
public interface B7Mapper {
    List<Code> codes();
    List<Existing> clients();
    long nextUploadId();
    Upload lockUpload(long uploadId);
    int insertUpload(UploadWrite input);
    int replaceSummary(UploadWrite input);
    int deleteIssues(long uploadId);
    int insertIssue(IssueWrite input);
    List<Issue> issues(long uploadId);
    int insertClient(ClientWrite input);
    int markApplied(@Param("uploadId") long uploadId, @Param("actor") String actor, @Param("now") java.time.LocalDateTime now);
}
