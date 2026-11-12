package com.sicost.b1;

import com.sicost.b7.B7Dtos.Issue;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.b1.B1Dtos.*;

@Mapper
public interface B1Mapper {
    List<Developer> developers();
    List<String> lockDevelopers(@Param("ids") List<String> ids);
    List<Rate> rates();
    List<Payroll> payroll(String month);
    List<Removed> members(String month);
    String cashBase();
    boolean closed(String month);
    void lockClose();
    Upload upload(long uploadId);
    Upload lockUpload(long uploadId);
    int insertUpload(Draft draft);
    int replaceSummary(UploadWrite write);
    int deleteIssues(long uploadId);
    int insertIssue(IssueWrite write);
    List<Issue> issues(long uploadId);
    List<Receipt> receipts(@Param("uploadId") long uploadId,@Param("reason") String reason);
    int insertPay(PayWrite write);
    int updatePay(PayWrite write);
    int deletePay(@Param("month") String month,@Param("developerId") String developerId);
    int markApplied(@Param("uploadId") long uploadId,@Param("actor") String actor,@Param("now") LocalDateTime now);
}
