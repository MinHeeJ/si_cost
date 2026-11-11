package com.sicost.b8;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.sicost.b7.B7Dtos.Code;
import com.sicost.b7.B7Dtos.Upload;
import com.sicost.b7.B7Dtos.UploadWrite;
import com.sicost.b7.B7Dtos.Issue;
import com.sicost.b7.B7Dtos.IssueWrite;
import com.sicost.b8.B8Dtos.*;

@Mapper
public interface B8Mapper {
    List<Code> codes();
    List<Existing> developers();
    List<Company> companies();
    List<Part> parts();
    List<Area> areas();
    List<Hdong> hdongs();
    BigInteger managementPreview();
    Upload lockUpload(long uploadId);
    int insertUpload(UploadDraft draft);
    int replaceSummary(UploadWrite write);
    int deleteIssues(long uploadId);
    int insertIssue(IssueWrite write);
    List<Issue> issues(long uploadId);
    int insertDeveloper(DeveloperWrite write);
    int insertPrice(DeveloperWrite write);
    int insertSkill(SkillWrite write);
    int markApplied(@Param("uploadId") long uploadId, @Param("actor") String actor, @Param("now") LocalDateTime now);
    List<Recovered> recovered(String reason);
}
