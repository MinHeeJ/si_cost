package com.sicost.apiops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.apiops.WithdrawalDtos.*;

@Mapper
public interface WithdrawalMapper {
    List<Source> sources(Window window);
    List<NextAssignment> nextAssignments(NextQuery query);
    List<Position> positions(String developerId);
    List<Leave> leaves(String developerId);
    boolean activeJob(String job);
}
