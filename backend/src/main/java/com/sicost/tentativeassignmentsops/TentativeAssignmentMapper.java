package com.sicost.tentativeassignmentsops;

import org.apache.ibatis.annotations.Mapper;
import static com.sicost.tentativeassignmentsops.TentativeAssignmentDtos.*;

@Mapper
public interface TentativeAssignmentMapper {
    State state(String id);
    int release(Mutation mutation);
    int touchOpportunity(Mutation mutation);
}
