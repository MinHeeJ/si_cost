package com.sicost.tentativeassignmentsops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.tentativeassignmentsops.TentativeAssignmentDtos.*;

@Mapper
public interface TentativeAssignmentMapper {
    State state(String id);
    List<String> activeIds(String developerId);
    int release(Mutation mutation);
    int touchOpportunity(Mutation mutation);
}
