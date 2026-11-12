package com.sicost.common.assignments;

import com.sicost.projectsops.ProjectStatusDtos.ValueHistory;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.common.assignments.AssignmentMonthlyService.*;

@Mapper
public interface AssignmentMonthlyMapper {
    Source source(String id);
    List<ValueHistory> histories(String id);
    List<StoredMonth> months(String id);
    String cashBase();
    String closeStatus(String month);
    void lockClose();
    int deleteMonth(@Param("id") String id,@Param("month") String month);
    int saveMonth(MonthWrite write);
    int project(Projection write);
}
