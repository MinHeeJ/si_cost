package com.sicost.imports;

import com.sicost.b7.B7Dtos.Issue;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.imports.BusinessPlanImportDtos.*;

@Mapper
public interface BusinessPlanImportMapper {
    List<Stored> targets(@Param("year") int year);
    List<Close> closes(@Param("year") int year);
    String snapshot(@Param("year") int year);
    String cashBase();
    void lockTimeout();
    Upload upload(@Param("uploadId") long uploadId);
    Upload lockUpload(@Param("uploadId") long uploadId);
    int insertUpload(Draft draft);
    int replaceSummary(UploadWrite write);
    int deleteIssues(@Param("uploadId") long uploadId);
    int insertIssue(IssueWrite write);
    List<Issue> issues(@Param("uploadId") long uploadId);
    List<String> changedMonths(@Param("uploadId") long uploadId);
    int insertTarget(TargetWrite write);
    int updateTarget(TargetWrite write);
    int markApplied(UploadWrite write);
}
