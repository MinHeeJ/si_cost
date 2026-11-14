package com.sicost.apiops;

import com.sicost.common.assignments.AssignmentMonthlyService.Source;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RateRenegotiationMapper {
    List<Source> assignments(LocalDate today);
}
