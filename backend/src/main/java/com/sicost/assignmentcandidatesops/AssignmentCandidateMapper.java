package com.sicost.assignmentcandidatesops;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.assignmentcandidatesops.AssignmentCandidateDtos.*;

@Mapper
public interface AssignmentCandidateMapper {
    Origin origin(String id);
    String snapshot(@Param("id") String opportunityId);
    String confirmedAssignment(String id);
    String lockOpportunity(String id);
    String lockTentative(String id);
    int transition(Mutation mutation);
    int touchOpportunity(Mutation mutation);
}
