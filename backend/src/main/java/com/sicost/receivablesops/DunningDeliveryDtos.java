package com.sicost.receivablesops;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class DunningDeliveryDtos {
    private DunningDeliveryDtos() {}
    public record BillSource(String billId, String projectId, String projectName, BigDecimal unpaidAmount, LocalDate dueDate, boolean cancelled) {}
    public record DeliveryQuery(int page, int size) {}
    public record ReminderInput(String expectedRevision, UUID operationId) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DeliveryRecipient(String userId, String userName, String emailAddress, boolean emailMissing, List<String> qualificationCodes) {}
    public record DeliveryPreview(String billId, String projectId, String projectName, String unpaidAmount, LocalDate dueDate,
        int currentStep, List<DeliveryRecipient> recipients, List<String> unassignedReasons, String sendMethodCode,
        boolean mailConfigured, boolean inProgress, boolean canRemind, List<String> unavailableReasons, String revision, OffsetDateTime observedAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DeliveryTask(String taskId, String userId, String userName, String emailAddress, String channelCode, String state,
        String notificationId, String messageId, OffsetDateTime sentAt, String errorCode, String errorMessage) {}
    public record DeliveryHistory(int sn, int step, LocalDate sendDate, String receiverDescription, String sendMethodCode, boolean resend) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DeliveryRound(String eventKey, int reservedSn, int step, boolean resend, DeliveryHistory history, List<DeliveryTask> tasks, boolean terminal) {}
    public record DeliveryPage(String billId, List<DeliveryRound> rounds, int page, int size, int total, boolean hasMore, OffsetDateTime observedAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CommandResult(String billId, String eventKey, UUID operationId, int reservedSn, boolean accepted, boolean completed, List<DeliveryTask> tasks, DeliveryHistory history) {}
    public record RoundSource(String eventKey, int reservedSn, int step, boolean resend) {}
    public record DeliveryWork(String billId,String eventKey) {}
    public record TaskSource(String taskId, String eventKey, String billId, int reservedSn, int step, boolean resend,
        String userId, String userName, String emailAddress, String channelCode, String state, String notificationId,
        String messageId, LocalDateTime sentAt, String errorCode, String errorMessage, String subject, String body, LocalDateTime registeredAt,
        String modifiedBy, LocalDateTime modifiedAt) {
        public DeliveryTask view() { return new DeliveryTask(taskId,userId,userName,emailAddress,channelCode,state,notificationId,messageId,
            sentAt==null?null:sentAt.atOffset(ZoneOffset.ofHours(9)),errorCode,errorMessage); }
    }
    public record MailReservation(String taskId, String eventKey, String billId, int serial, int step, boolean resend,
        String userId, String userName, String emailAddress, String state, String errorCode, String errorMessage,
        String content, String subject, String body, String messageId, LocalDateTime at) {}
    public record TaskTransition(String taskId, String expectedState, String state, String messageId, LocalDateTime sentAt,
        String errorCode, String errorMessage, LocalDateTime at) {}
    public record HistoryInsert(String billId, int sn, int step, LocalDate sendDate, String receiverDescription,
        String sendMethodCode, String resend, LocalDateTime at) {}
    public static boolean terminal(String state) { return !Set.of("PENDING","SENDING").contains(state); }
}
