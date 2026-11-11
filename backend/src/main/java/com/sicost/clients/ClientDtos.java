package com.sicost.clients;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ClientDtos {
    private ClientDtos() {}
    public record Stored(String id, String type, String name, String businessNo, String ceo, String industryCode,
                         String contactName, String phone, String taxEmail, String bankCode, String encryptedAccount,
                         String depositor, String useYn, String regUserId, LocalDateTime regDttm,
                         String modUserId, LocalDateTime modDttm) {}
    public record Labels(String industry, Boolean industryActive, String bankName, Boolean bankActive) {}
    public record Counts(long projectCount, long personCount) {}
    public record Code(String code, String name, String useYn, String delYn) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record Client(String type, String name, String businessNo, String ceo, String industryCode,
                         String contactName, String phone, String taxEmail, String bankCode, String depositor,
                         String id, String industry, String bankName, Boolean industryActive, Boolean bankActive,
                         String account, String useYn, long projectCount, long personCount, LocalDate basisDate,
                         String revision, long historyCount) {}
    public record Page(List<Client> items, long total, int page, int size, LocalDate basisDate) {}
    public record Warning(String field, String message) {}
    public record Result(String id, boolean changed, String message, List<Warning> warnings, Client client) {}
    public record Query(String type, String useYn, String q, String sort, String direction, int page, int size) {}
    public record Selection(Query filters, LocalDate basisDate, long offset) {}
    public record Fields(String type, String name, String ceo, String industryCode, String contactName,
                         String phone, String taxEmail, String bankCode, String accountNumber, String depositor) {}
    public record Save(Fields fields, String businessNo, String accountAction, String expectedRevision,
                       List<Warning> warnings) {}
    public record Mutation(Stored row) {}
}
