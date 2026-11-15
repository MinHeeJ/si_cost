package com.sicost.common.assignments;

import com.sicost.assignmentsops.AssignmentActionsDtos.Create;
import com.sicost.assignmentsops.AssignmentActionsDtos.Save;
import java.util.function.Supplier;

public interface RecommendationAssignmentHandler {
    Save create(Create input,String assignmentId,Supplier<Save> ordinary);
}
