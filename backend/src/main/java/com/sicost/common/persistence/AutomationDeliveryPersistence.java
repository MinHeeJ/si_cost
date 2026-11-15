package com.sicost.common.persistence;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Narrow write grammar for retained delivery evidence and recipient-bounded notifications. */
public final class AutomationDeliveryPersistence {
    private AutomationDeliveryPersistence() {}
    private static final Pattern TARGET = Pattern.compile("(?is)^\\s*(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+(TB_SY_AUTOMATION_RUN|TB_SY_DELIVERY_TASK|TB_BL_DUN_HIST|TB_SY_NOTI)\\b(.*)$");
    private static final String VALUE = "(?:\\?|NULL|DEFAULT|CURRENT_TIMESTAMP|'[A-Z_0-9-]+'|[0-9]+|CAST\\(\\?\\s+AS\\s+(?:UUID|NUMERIC|DATE|TIMESTAMP)\\))";
    public static final String RUN_FIELDS = "JOB_CD|RUN_DT|STATE_CD|LAST_STEP|START_DTTM|END_DTTM|ERROR_CN|REG_USER_ID|REG_DTTM|MOD_USER_ID|MOD_DTTM";
    public static final String RUN_CHANGES = "STATE_CD|LAST_STEP|END_DTTM|ERROR_CN|MOD_USER_ID|MOD_DTTM";
    public static final String TASK_FIELDS = "TASK_ID|EVENT_KEY|RCVR_USER_ID|RCVR_NM|CHANNEL_CD|STATE_CD|NOTI_TYPE_CD|NOTI_CN|LINK_SCRN_ID|LINK_KEY|MAIL_TO|MAIL_SUBJECT|MAIL_BODY|MESSAGE_ID|NOTI_ID|BILL_ID|DUN_STEP|DUN_SN|RESEND_YN|SENT_DTTM|ERROR_CD|ERROR_CN|REG_USER_ID|REG_DTTM|MOD_USER_ID|MOD_DTTM";
    public static final String TASK_CHANGES = "STATE_CD|NOTI_ID|MESSAGE_ID|SENT_DTTM|ERROR_CD|ERROR_CN|MOD_USER_ID|MOD_DTTM";
    public static final String NOTIFICATION_FIELDS = "NOTI_ID|RCVR_USER_ID|NOTI_TYPE_CD|NOTI_CN|LINK_SCRN_ID|LINK_KEY|SEND_MTHD_CD|READ_YN|READ_DTTM|NOTI_DTTM|REG_USER_ID|REG_DTTM|MOD_USER_ID|MOD_DTTM";
    public static final String HISTORY_FIELDS = "BILL_ID|DUN_SN|DUN_STEP|SEND_DT|RCVR_DESC|SEND_MTHD_CD|RESEND_YN|REG_USER_ID|REG_DTTM";

    public static boolean supports(String sql) { return TARGET.matcher(sql).matches(); }

    public static void requireAllowed(String sql) {
        var match = TARGET.matcher(sql);
        if (!match.matches()) throw denied();
        String operation = match.group(1).replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        String table = match.group(2).toUpperCase(Locale.ROOT);
        String rest = match.group(3).strip().toUpperCase(Locale.ROOT);
        boolean allowed;
        if (operation.equals("INSERT INTO")) {
            String fields = switch (table) {
                case "TB_SY_AUTOMATION_RUN" -> RUN_FIELDS;
                case "TB_SY_DELIVERY_TASK" -> TASK_FIELDS;
                case "TB_BL_DUN_HIST" -> HISTORY_FIELDS;
                default -> NOTIFICATION_FIELDS;
            };
            String required = switch (table) {
                case "TB_SY_AUTOMATION_RUN" -> "JOB_CD|RUN_DT|STATE_CD|REG_USER_ID|REG_DTTM";
                case "TB_SY_DELIVERY_TASK" -> "TASK_ID|EVENT_KEY|RCVR_USER_ID|RCVR_NM|CHANNEL_CD|NOTI_TYPE_CD|NOTI_CN|REG_USER_ID|REG_DTTM";
                case "TB_BL_DUN_HIST" -> HISTORY_FIELDS;
                default -> "RCVR_USER_ID|NOTI_TYPE_CD|NOTI_CN|LINK_SCRN_ID|LINK_KEY|SEND_MTHD_CD|READ_YN|READ_DTTM|NOTI_DTTM|REG_USER_ID|REG_DTTM";
            };
            String suffix = switch (table) {
                case "TB_SY_DELIVERY_TASK" -> "(?:\\s+ON\\s+CONFLICT\\s*\\(\\s*EVENT_KEY\\s*,\\s*RCVR_USER_ID\\s*,\\s*CHANNEL_CD\\s*\\)\\s+DO\\s+NOTHING)?(?:\\s+RETURNING\\s+TASK_ID)?";
                case "TB_SY_AUTOMATION_RUN" -> "(?:\\s+ON\\s+CONFLICT\\s*\\(\\s*JOB_CD\\s*,\\s*RUN_DT\\s*\\)\\s+DO\\s+NOTHING)?";
                case "TB_SY_NOTI" -> "(?:\\s+RETURNING\\s+NOTI_ID)?";
                default -> "";
            };
            allowed = insert(rest, fields, required, suffix);
        } else if (operation.equals("UPDATE")) {
            allowed = switch (table) {
                case "TB_SY_AUTOMATION_RUN" -> update(rest, RUN_CHANGES, "JOB_CD\\s*=\\s*\\?\\s+AND\\s+RUN_DT\\s*=\\s*\\?");
                case "TB_SY_DELIVERY_TASK" -> update(rest, TASK_CHANGES, "TASK_ID\\s*=\\s*(?:\\?|CAST\\(\\?\\s+AS\\s+UUID\\))(?:\\s+AND\\s+STATE_CD\\s*=\\s*" + VALUE + ")?");
                case "TB_SY_NOTI" -> rest.matches("SET\\s+READ_YN\\s*=\\s*'Y'\\s*,\\s*READ_DTTM\\s*=\\s*\\?\\s*,\\s*MOD_USER_ID\\s*=\\s*\\?\\s*,\\s*MOD_DTTM\\s*=\\s*\\?\\s+WHERE\\s+RCVR_USER_ID\\s*=\\s*\\?\\s+AND\\s+" +
                    "(?:NOTI_ID\\s*=\\s*\\?\\s+AND\\s+)?READ_YN\\s*=\\s*'N'\\s+AND\\s+SEND_MTHD_CD\\s+IN\\s*\\(\\s*'SYS'\\s*,\\s*'BOTH'\\s*\\)(?:\\s+AND\\s+NOTI_DTTM\\s*<=\\s*\\?)?");
                default -> false;
            };
        } else {
            allowed = table.equals("TB_SY_NOTI") && rest.matches("WHERE\\s+NOTI_DTTM\\s*<\\s*\\?");
        }
        if (!allowed) throw denied();
    }

    private static boolean insert(String sql, String fields, String required, String suffix) {
        var match = Pattern.compile("^\\(\\s*([A-Z_,\\s]+)\\)\\s+VALUES\\s*\\(\\s*" + VALUE + "(?:\\s*,\\s*" + VALUE + ")*\\s*\\)" + suffix + "$").matcher(sql);
        if (!match.matches()) return false;
        var columns = Arrays.stream(match.group(1).split(",")).map(String::strip).toList();
        return columns.stream().distinct().count() == columns.size()
            && columns.stream().allMatch(c -> c.matches(fields))
            && columns.containsAll(Arrays.asList(required.split("\\|")));
    }

    private static boolean update(String sql, String fields, String key) {
        String assignment = "(?:" + fields + ")\\s*=\\s*" + VALUE;
        return sql.matches("SET\\s+" + assignment + "(?:\\s*,\\s*" + assignment + ")*\\s+WHERE\\s+" + key);
    }
    private static IllegalStateException denied() { return new IllegalStateException("전달 증거의 불변값과 지정된 상태 변경 범위를 확인해 주세요."); }
}
