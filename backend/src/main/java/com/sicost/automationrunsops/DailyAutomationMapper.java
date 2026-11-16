package com.sicost.automationrunsops;

import java.time.*;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.sicost.projectsops.ProjectStatusDtos.ValueHistory;
import static com.sicost.automationrunsops.DailyAutomationDtos.*;

@Mapper
public interface DailyAutomationMapper {
    Stored run(Key key);
    int insertRun(RunWrite write);
    int updateRun(RunWrite write);
    List<String> developerIds();
    List<String> projectIds(LocalDate today);
    Project project(String id);
    int endProject(ProjectEnd write);
    int projectSerial(String id);
    int projectEvent(ProjectEnd write);
    List<Item> assignmentIds(LocalDate today);
    Item assignmentIdentity(String id);
    String lastClosedMonth();
    Assignment assignment(String id);
    List<ValueHistory> valueHistory(String id);
    int pointer(Pointer pointer);
    boolean protectedMonth(String month);
    List<String> expiredNotificationIds(LocalDateTime cutoff);
    Notification notification(String id);
    int cleanup(LocalDateTime cutoff);
    com.sicost.common.notifications.NotificationRecipients.Part projectPart(@Param("id") String id,@Param("month") String month);
}
