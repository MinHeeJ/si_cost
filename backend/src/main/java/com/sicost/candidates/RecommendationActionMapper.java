package com.sicost.candidates;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.candidates.RecommendationActionDtos.*;

@Mapper
public interface RecommendationActionMapper {
    Row row(Key key);
    Row lockRow(Key key);
    List<AuditValue> auditValues(Key key);
    String snapshot(String id);
    List<String> assignmentIds(String developerId);
    List<String> payrollMonths(String developerId);
    List<String> activeOpportunityIds(String developerId);
    String lockOpportunity(String id);
    String lockDeveloper(String id);
    String lockProject(String id);
    List<String> lockAssignments(String id);
    List<String> lockNeeds(String id);
    void lockClose();
    Need need(Slot slot);
    Developer developer(String id);
    MessageNames messageNames(MessageContext context);
    Withdrawal withdrawal(String id);
    boolean hasConfirmedNext(@Param("developerId") String developerId,@Param("date") java.time.LocalDate date);
    boolean hasConvertedNext(String developerId);
    boolean leave(Period period);
    boolean activeReason(String code);
    boolean excluded(Row row);
    LocalDateTime resumed(Row row);
    boolean targetOpen(Row row);
    java.time.LocalDate progressingEnd(@Param("developerId") String developerId,@Param("today") java.time.LocalDate today);
    int choose(Choice choice);
}
