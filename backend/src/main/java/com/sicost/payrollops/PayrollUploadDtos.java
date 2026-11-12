package com.sicost.payrollops;

import com.sicost.b9.B9Dtos.Summary;
import java.time.LocalDateTime;
import java.util.List;

public final class PayrollUploadDtos {
    private PayrollUploadDtos() {}
    public record Query(String month,int page,int size,long offset) {}
    public record Stored(long uploadId,String month,String fileName,String actor,LocalDateTime registeredAt,
                         String status,LocalDateTime appliedAt,int totalRows,int successRows,int warningRows,
                         int errorRows,int fileWarningCount,int tableWarningCount) {}
    public record Record(long uploadId,String month,String fileName,String actor,String registeredAt,
                         String status,Summary summary,String appliedAt) {}
    public record Page(List<Record> items,long total,int page,int size) {}
}
