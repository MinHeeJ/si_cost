package com.sicost.b8;

import com.sicost.b7.B7Dtos.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class B8Dtos {
    private B8Dtos() {}
    public record NumberRow(int rowNumber, String managementNo, String id) {}
    public record Validation(long uploadId, Summary summary, boolean canApply, IssuesPage issues, List<NumberRow> expectedNumbers) {}
    public record Applied(Summary summary, List<NumberRow> finalNumbers) {}
    public record Existing(String managementNo, String name, LocalDate hireDate) {}
    public record Company(String id, String name, String businessNo) {}
    public record Part(String id, String name, boolean headquarters) {}
    public record Area(String code, String level, String parentCode, String sido, String fullName, String sgg, String emd) {}
    public record Hdong(String name, String areaCode) {}
    public record Address(String code, String text) {}
    public record Developer(int rowNumber, String managementNo, String name, String employeeType, String companyId,
        String gradeCode, String jobCode, String subJobCode, String positionCode, String directClass, String partId,
        LocalDate hireDate, BigDecimal monthlyRate, BigDecimal monthlyPay, List<String> skills, Address address) {}
    public record Checked(List<Developer> developers, Summary summary, List<Issue> issues, List<NumberRow> expectedNumbers) {}
    public record DeveloperWrite(String id, String managementNo, Developer developer, String actor, LocalDateTime now, String effectiveMonth) {}
    public record SkillWrite(String id, String skill, int order, String actor, LocalDateTime now) {}
    public record Recovered(String id, String managementNo) {}
    public static final class UploadDraft {
        private Long uploadId;
        private final UploadWrite write;
        public UploadDraft(UploadWrite write) { this.write=write; }
        public Long getUploadId() { return uploadId; }
        public void setUploadId(Long uploadId) { this.uploadId=uploadId; }
        public UploadWrite getWrite() { return write; }
    }
}
