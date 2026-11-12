package com.sicost.projects;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.projects.ProjectDtos.*;

@Mapper
public interface ProjectMapper {
    List<ListRow> list(Query query);
    long count(Query query);
    long unassignedCount(Query query);
    Project find(String id);
    DefaultsRow defaults(String month);
    Reference customer(String id);
    Reference part(String id);
    boolean activeType(String code);
    boolean hasBills(String id);
    List<ProjectPart> parts(@Param("id") String id, @Param("month") String month);
    List<ProjectPm> pm(String id);
    List<ProjectAssignment> assignments(@Param("id") String id, @Param("includeCancelled") boolean includeCancelled);
    List<ProjectHistory> history(String id);
    String actualMmTotal(String id);
    String expenseTotal(String id);
    List<PeriodUse> periodUses(PeriodCheck check);
    List<String> protectedMonths(@Param("id") String id, @Param("from") String from, @Param("to") String to);
    void lockClose();
    int update(Mutation mutation);
    int moveInitialPart(PartMove move);
    int insertHistory(HistoryWrite history);
}
