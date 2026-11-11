package com.sicost.developersops;

import java.time.LocalDate;
import java.util.*;
import org.apache.ibatis.annotations.*;
import static com.sicost.developersops.DeveloperActionsDtos.*;

@Mapper
public interface DeveloperActionsMapper {
    Base developer(@Param("id") String id, @Param("lock") boolean lock);
    List<Map<String,Object>> prices(@Param("id") String id, @Param("lock") boolean lock);
    List<Map<String,Object>> skills(@Param("id") String id, @Param("lock") boolean lock);
    List<Map<String,Object>> states(@Param("id") String id, @Param("lock") boolean lock);
    List<Base> selection(Filters filters);
    List<String> ids();
    String calculatedStatus(@Param("id") String id, @Param("today") LocalDate today, @Param("threshold") LocalDate threshold);
    List<Project> blockingProjects(@Param("id") String id, @Param("today") LocalDate today);
    List<Assignment> assignments(AssignmentQuery query);
    long assignmentCount(String id);
    int status(Mutation mutation);
    int recalculate(Mutation mutation);
    int activeJob(String code);
}
