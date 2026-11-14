package com.sicost.common.workforce;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.common.workforce.WorkforceProjectionDtos.*;

@Mapper
public interface WorkforceProjectionMapper {
    List<Person> people();
    List<Leave> leaves();
    List<Assignment> assignments();
    List<Demand> opportunityDemand();
    List<Standard> standards(String month);
}
