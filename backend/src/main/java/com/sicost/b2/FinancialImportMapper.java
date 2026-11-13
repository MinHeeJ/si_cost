package com.sicost.b2;

import com.sicost.b7.B7Dtos.Issue;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.b2.FinancialImportDtos.*;

@Mapper
public interface FinancialImportMapper {
    List<StoredItem> items(String month);
    List<Code> codes();
    String snapshot(String month);
    String cashBase();
    boolean closed(String month);
    void lockTimeout();
    Upload upload(long uploadId);
    Upload lockUpload(long uploadId);
    int insertUpload(Draft draft);
    int replaceSummary(UploadWrite write);
    int deleteIssues(long uploadId);
    int insertIssue(IssueWrite write);
    List<Issue> issues(long uploadId);
    List<Metadata> metadata(long uploadId);
    List<ResultItem> resultItems(long uploadId);
    int deleteUploaded(String month);
    int insertItem(ItemWrite write);
    int markApplied(UploadWrite write);
}
