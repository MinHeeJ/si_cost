package com.sicost.projectsops;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.projectsops.ProjectStatusDtos.*;

@Mapper
public interface ProjectStatusMapper {
    Project project(String id);
    List<Option> options(Selection selection);
    BigDecimal allowance(String code);
    String reason(String code);
    List<Assignment> assignments(String id);
    List<AssignmentRow> assignmentRows(String id);
    String assignmentSnapshot(String id);
    List<ValueHistory> valueHistory(String id);
    List<MonthRow> months(String id);
    int assignmentSerial(String id);
    List<String> costMonths(String id);
    void lockClose();
    int updateProject(ProjectWrite write);
    int insertEvent(Event event);
    int updateAssignment(AssignmentWrite write);
    int insertAssignmentEvent(AssignmentEvent event);
    int deleteMonth(@Param("id") String id,@Param("month") String month);
    int saveMonth(MonthWrite write);
    int classifyCost(CostWrite write);
}
