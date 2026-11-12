package com.sicost.assignmentsops;

import com.sicost.common.assignments.AssignmentMonthlyService.Source;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.assignmentsops.AssignmentActionsDtos.*;

@Mapper
public interface AssignmentActionsMapper {
    Project project(String id);
    Developer developer(String id);
    Labels labels(String id);
    String snapshot(String id);
    List<String> matchingIds(Filter filter);
    List<CandidateSource> candidates(CandidateQuery query);
    List<String> overlaps(Overlap query);
    String substitutionContext(String projectId);
    BigDecimal price(PriceQuery query);
    BigDecimal mmLimit(String month);
    BigDecimal totalMm(TotalQuery query);
    String projectPart(PartQuery query);
    boolean leave(LeaveQuery query);
    boolean active(CodeQuery query);
    boolean duplicate(Source source);
    boolean turnkey(Overlap query);
    int serial(String id);
    int insert(Write write);
    int update(Write write);
    int event(Event event);
}
