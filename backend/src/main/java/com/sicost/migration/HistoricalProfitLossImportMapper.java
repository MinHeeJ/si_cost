package com.sicost.migration;

import com.sicost.b7.B7Dtos.Issue;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.migration.HistoricalProfitLossImportDtos.*;

@Mapper
public interface HistoricalProfitLossImportMapper {
    List<Stored> profits();
    String snapshot();
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
    int insertProfit(ProfitLossWrite write);
    int updateProfit(ProfitLossWrite write);
    int markApplied(UploadWrite write);
}
