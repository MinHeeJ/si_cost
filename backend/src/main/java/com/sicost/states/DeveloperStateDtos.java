package com.sicost.states;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class DeveloperStateDtos {
    private DeveloperStateDtos() {}
    public record Developer(String id, LocalDate hireDate, LocalDate retireDate, String status, String useYn) {}
    public record Stored(int sn, String state, LocalDate startDate, LocalDate endDate, String reason,
                         Integer closeLeaveSn, LocalDate originalLeaveEndDate, String regUserId,
                         LocalDateTime regDttm, String modUserId, LocalDateTime modDttm) {
        public Stored end(LocalDate value) { return new Stored(sn,state,startDate,value,reason,closeLeaveSn,originalLeaveEndDate,regUserId,regDttm,modUserId,modDttm); }
        public Stored original(LocalDate value) { return new Stored(sn,state,startDate,endDate,reason,closeLeaveSn,value,regUserId,regDttm,modUserId,modDttm); }
        public Stored closure(int leaveSn,LocalDate original) { return new Stored(sn,state,startDate,endDate,reason,leaveSn,original,regUserId,regDttm,modUserId,modDttm); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record State(int sn, String state, LocalDate startDate, LocalDate endDate, String reason,
                        String actorId, String actorName, List<String> allowedActions) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record States(List<State> items, LocalDate hireDate, String revision) {}
    public record Warning(String field, String message) {}
    public record Created(int sn, List<Warning> warnings) {}
    public record Saved(boolean changed) {}
    public record Create(String state, LocalDate startDate, LocalDate endDate, String reason, String expectedRevision) {}
    public record End(LocalDate endDate, String expectedRevision) {}
    public record Cancel(String reason, String expectedRevision) {}
    public record Mutation(String id, Stored row, String actor, LocalDateTime now) {}
    public record DeveloperMutation(String id, LocalDate retireDate, String status, String useYn, String actor, LocalDateTime now) {}
    public record Assignment(String id, String name, LocalDate startDate, LocalDate endDate) {}
    public record Period(LocalDate start, LocalDate end) {}
    public record Calendar(String cashBaseMonth, List<String> closedMonths) {}
}
