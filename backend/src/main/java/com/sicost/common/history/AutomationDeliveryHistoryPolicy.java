package com.sicost.common.history;

import com.sicost.common.persistence.AutomationDeliveryPersistence;
import com.sicost.common.validation.InputValidator;
import java.util.Set;
import java.util.UUID;

public final class AutomationDeliveryHistoryPolicy {
    private AutomationDeliveryHistoryPolicy() {}
    public static boolean supports(String table) {
        return Set.of("TB_SY_AUTOMATION_RUN", "TB_SY_DELIVERY_TASK", "TB_SY_NOTI", "TB_PJ_PROJ", "TB_PJ_ASGN").contains(table);
    }
    public static ChangeHistoryWriter.Change requireAllowed(ChangeHistoryWriter.Change change) {
        if (change == null || change.item() == null || change.targetKey() == null || !Set.of("NEW", "UPD", "DEL").contains(change.type())) throw InputValidator.invalid("history");
        boolean creation = "NEW".equals(change.type());
        String fields;
        boolean key;
        switch (change.tableName()) {
            case "TB_SY_AUTOMATION_RUN" -> {
                fields = creation ? AutomationDeliveryPersistence.RUN_FIELDS : AutomationDeliveryPersistence.RUN_CHANGES;
                key = change.targetKey().matches("(?:DAILY_OPERATIONS|RECEIVABLE_DUNNING):[0-9]{4}-[0-9]{2}-[0-9]{2}");
            }
            case "TB_SY_DELIVERY_TASK" -> {
                fields = creation ? AutomationDeliveryPersistence.TASK_FIELDS : AutomationDeliveryPersistence.TASK_CHANGES;
                key = validUuid(change.targetKey());
            }
            case "TB_SY_NOTI" -> {
                fields = creation ? AutomationDeliveryPersistence.NOTIFICATION_FIELDS : "READ_YN|READ_DTTM|MOD_USER_ID|MOD_DTTM";
                key = change.targetKey().matches("[0-9]{1,12}");
            }
            case "TB_PJ_PROJ" -> {
                fields = "PROJ_STAT_CD";
                key = !creation && change.targetKey().matches("[^\\s:]{1,20}");
            }
            case "TB_PJ_ASGN" -> {
                fields = "CTRT_MM|BILL_UPRC";
                key = !creation && change.targetKey().matches("[^\\s:]{1,20}");
            }
            default -> throw InputValidator.invalid("history");
        }
        boolean cleanup = change.tableName().equals("TB_SY_NOTI") && change.type().equals("DEL");
        if (!key || !change.item().matches(cleanup ? AutomationDeliveryPersistence.NOTIFICATION_FIELDS : fields)
                || change.type().equals("DEL") && !cleanup
                || creation && change.before() != null || cleanup && change.after() != null) throw InputValidator.invalid("history");
        if (change.tableName().equals("TB_SY_DELIVERY_TASK") && change.item().equals("MAIL_BODY") && creation && change.after() != null) {
            return new ChangeHistoryWriter.Change(change.tableName(), change.targetKey(), change.type(), change.item(), null,
                change.targetKey() + ":MAIL_BODY", change.reason());
        }
        return change;
    }
    private static boolean validUuid(String value) {
        try { return UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException e) { return false; }
    }
}
