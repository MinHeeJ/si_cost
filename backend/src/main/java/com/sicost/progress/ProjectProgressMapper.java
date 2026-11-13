package com.sicost.progress;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.progress.ProjectProgressDtos.*;

@Mapper
public interface ProjectProgressMapper {
    ProjectRow project(@Param("id") String id, @Param("today") LocalDate today);
    List<StepRow> steps(String id);
    List<MemoRow> memos(String id);
    List<IssueRow> issues(String id);
    List<Actor> pm(String id);
    List<Template> templates();
    List<HistoryRow> history(String id);
    BigDecimal additionalExpense(@Param("id") String id, @Param("fromMonth") String fromMonth, @Param("toMonth") String toMonth);
    int insertStep(StepWrite write);
    int updateDates(StepWrite write);
    int complete(StepWrite write);
    int insertMemo(MemoWrite write);
    int updateMemo(MemoWrite write);
}
