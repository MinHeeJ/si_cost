package com.sicost.common.notifications;

import com.sicost.common.validation.InputValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class NotificationEventKeys {
    private NotificationEventKeys() {}
    public static String automaticDunning(String billId, int step) {
        if (step < 1 || step > 3) throw InputValidator.invalid("step");
        return "DUN:A:" + id(billId, 20) + ":" + step;
    }
    public static String manualDunning(String billId, UUID operationId) {
        if (operationId == null) throw InputValidator.invalid("operationId");
        return "DUN:M:" + id(billId, 20) + ":" + operationId;
    }
    public static String withdrawal(String assignmentId, LocalDate date, BigDecimal noticeDays) {
        if (date == null || noticeDays == null || noticeDays.signum() <= 0 || noticeDays.stripTrailingZeros().scale() > 0) throw InputValidator.invalid("noticeDays");
        return "WTHDR:" + id(assignmentId, 20) + ":" + date + ":" + noticeDays.toBigIntegerExact();
    }
    public static String cash(LocalDate date) {
        if (date == null) throw InputValidator.invalid("date");
        return "CASH:" + date;
    }
    public static String upload(String uploadId) { return "UPLD:" + numeric(uploadId) + ":APPLIED"; }
    public static String prediction(String questionId) { return "AI:" + numeric(questionId) + ":DONE"; }
    public static String quoteRejected(String opportunityId, int historySequence) {
        if (historySequence < 1 || historySequence > 99999) throw InputValidator.invalid("historySequence");
        return "QUOTE:" + id(opportunityId, 20) + ":" + historySequence + ":REJ";
    }
    private static String numeric(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,11}")) throw InputValidator.invalid("id");
        return value;
    }
    private static String id(String value, int max) {
        if (value == null || value.length() > max || !value.matches("[A-Za-z0-9_-]+")) throw InputValidator.invalid("id");
        return value;
    }
}
