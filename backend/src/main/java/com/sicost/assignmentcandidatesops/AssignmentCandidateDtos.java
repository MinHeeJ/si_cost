package com.sicost.assignmentcandidatesops;

import com.sicost.assignmentsops.AssignmentActionsDtos.Create;
import java.time.LocalDateTime;
import java.util.List;

public final class AssignmentCandidateDtos {
    private AssignmentCandidateDtos() {}
    public record Confirmation(Create assignment,String sourceTentId,String expectedRevision) {}
    public record Origin(String id,String opportunityId,String projectId,String developerId,String jobCode,
                         String status,String developerUseYn,String projectStatus,java.time.LocalDate projectEndDate,LocalDateTime processedAt) {}
    public record Mutation(String id,String opportunityId,String status,String actor,LocalDateTime now) {}
    public record ConfirmationResult(boolean changed,String revision,List<String> warnings,String opportunityId,String tentativeId,String assignmentId) {}
    public record DropResult(boolean changed,String revision,List<String> warnings,String opportunityId,String tentativeId) {}
}
