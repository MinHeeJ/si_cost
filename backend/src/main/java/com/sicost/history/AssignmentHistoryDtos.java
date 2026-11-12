package com.sicost.history;

import java.time.*;
import java.util.List;

public final class AssignmentHistoryDtos {
    private AssignmentHistoryDtos() {}
    public record Entry(int sequence,String type,LocalDate applyDate,String before,String after,String reasonCode,
        String memo,String newUnitPrice,String newContractMm,String retroactive,String closeCross,String corrected,
        Integer correctionTargetSequence,String actor,String registeredAt) {}
    public record Page(List<Entry> items,long total,int page,int size) {}
    public record Query(String id,String type,int page,int size,long offset) {}
    public record Correction(int year,String expectedRevision,String correctContent,String reasonCode) {}
    public record Result(Entry original,Entry correction,String revision,boolean changed) {}
    public record CorrectionWrite(String id,int sequence,int target,LocalDate date,String before,String content,
        String reason,String actor,LocalDateTime now) {}
}
