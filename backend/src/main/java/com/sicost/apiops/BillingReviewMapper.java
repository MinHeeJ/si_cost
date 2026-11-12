package com.sicost.apiops;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.apiops.BillingReviewDtos.*;

@Mapper
public interface BillingReviewMapper {
    List<Project> checkProjects();
    Project project(@Param("id") String id);
    List<AssignmentReference> assignments(@Param("id") String id);
    List<CheckBill> checkBills(CheckKey key);
    String checkSnapshot(CheckKey key);
    String billSnapshot(@Param("id") String id);
    List<OverdueBill> overdue(@Param("today") LocalDate today);
    List<DunningHistory> sentHistory(@Param("id") String id);
    List<RecipientRole> eligibleRoles();
    List<RecipientScope> recipientScopes();
    ProjectPart projectPart(@Param("id") String id,@Param("month") String month);
}
