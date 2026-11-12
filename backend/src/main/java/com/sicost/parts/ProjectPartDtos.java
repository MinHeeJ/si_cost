package com.sicost.parts;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ProjectPartDtos {
    private ProjectPartDtos() {}
    public record ProjectPart(String effectiveMonth, String partId, String partName, String reason, String actor, boolean scheduled) {}
    public record ProjectPartsData(List<ProjectPart> items, List<ProjectPart> replacementTargets, ProjectPart retainedBasePart,
                                   String minimumMonth, String maximumMonth, String revision) {}
    public record SaveProjectPartRequest(Integer year, String expectedRevision, String partId, String effectiveMonth, String reason) {}
    public record SaveProjectPartResult(ProjectPartsData value, String revision, boolean changed) {}
    public record ProjectSource(String id, String contractType, String status, LocalDate startDate, LocalDate endDate) {}
    public record PartSource(String month, String partId, String partName, String reason, String actor) {}
    public record PartReference(String id, String name, String useYn) {}
    public record PartInsert(String id, String month, String partId, String reason, String actor, LocalDateTime now) {}
    public record PartHistory(String id, int historySn, String before, String after, String actor, LocalDateTime now) {}
    public record PartPeriod(String effectiveMonth, String endMonth, String partId, String partName) {}
}
