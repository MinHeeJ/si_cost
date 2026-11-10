package com.sicost.codegroups;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class CodeGroupDtos {
    private CodeGroupDtos() {}
    public record Group(String groupId, String name, String sysCodeYn, String valCodeYn,
                        String useYn, int sortOrder, long codeCount) {}
    public record Groups(List<Group> items) {}
    public record StoredGroup(String groupId, String name, String sysCodeYn, String valCodeYn,
                              int sortOrder, String useYn, String regUserId, LocalDateTime regDttm,
                              String modUserId, LocalDateTime modDttm) {}
    public record StoredCode(String groupId, String code, String name, int sortOrder, String useYn,
                             String delYn, BigDecimal codeVal, String unitNm, String addVal1,
                             String addVal2, String addVal3, String regUserId, LocalDateTime regDttm,
                             String modUserId, LocalDateTime modDttm) {
        public Code view() { return new Code(code,name,sortOrder,useYn,addVal1,addVal2,addVal3,
                codeVal == null ? null : codeVal.toPlainString(),unitNm); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Code(String code, String name, int sortOrder, String useYn, String addVal1,
                       String addVal2, String addVal3, String codeVal, String unitNm) {}
    public record Page(List<Code> items, long total, int page, int size, String groupRevision) {}
    public record SaveResult(boolean changed, String message, String groupRevision, List<Code> items) {}
    public record Snapshot(StoredGroup group, List<StoredCode> codes) {}
    public record Create(String groupId, String name) {}
    public record Change(String code, boolean isNew, Map<String, JsonNode> values, String field) {}
    public record Save(String expectedRevision, List<Change> changes) {}
    public record Mutation(StoredCode row, String actor, LocalDateTime now) {}
    public record GroupInsert(Create group, int sortOrder, String actor, LocalDateTime now) {}
    public record Reference(String type, long count) {}
}
