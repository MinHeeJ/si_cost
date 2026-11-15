package com.sicost.common.history;

import com.sicost.common.validation.InputValidator;
import java.util.Set;
import static com.sicost.common.history.ChangeHistoryWriter.Change;

/** Logical command receipts and ownership use existing physical audit columns, one value per row. */
public final class MonthCloseHistoryPolicy {
    private MonthCloseHistoryPolicy() {}
    private static final Set<String> SNAPSHOTS = Set.of("TB_PJ_ASGN_MON", "TB_CO_MON_COST", "TB_CO_MON_PL",
        "TB_CO_PART_SETL", "TB_CO_PART_SETL_DTL");
    public static void requireAllowed(Change change) {
        if (change == null || change.targetKey() == null || change.item() == null) throw InputValidator.invalid("history");
        if ("TB_CL_CLOSE_HIST".equals(change.tableName())) {
            if (!historyKey(change.targetKey()) || !"NEW".equals(change.type()) || change.before() != null
                    || change.after() == null || change.reason() != null) throw InputValidator.invalid("history");
            boolean valid = switch (change.item()) {
                case "operationId" -> change.after().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
                case "expectedRevision", "revision" -> !change.after().isBlank() && change.after().length() <= 500;
                case "ACT_CD" -> Set.of("CLOSE", "OPEN").contains(change.after());
                case "status" -> Set.of("WORK", "CLOSE").contains(change.after());
                case "acceptWarnings", "acceptAdjustments", "settlementsPreserved" -> Set.of("true", "false").contains(change.after());
                default -> countItem(change.item()) && change.after().matches("0|[1-9][0-9]{0,9}")
                    && Long.parseLong(change.after()) <= Integer.MAX_VALUE;
            };
            if (!valid) throw InputValidator.invalid("history");
            return;
        }
        String key = switch (change.tableName()) {
            case "TB_PJ_ASGN_MON" -> "[^\\s:]{1,20}:" + month();
            case "TB_CO_MON_COST" -> month() + ":[^\\s:]{1,20}:[^\\s:]{1,20}:[^\\s:]{1,20}";
            case "TB_CO_MON_PL" -> month();
            default -> "(?!)";
        };
        if (!"FIX_YN".equals(change.item())) {
            String fields=switch(change.tableName()) {
                case "TB_PJ_ASGN_MON" -> "DEV_ID|PROJ_ID|INPUT_DAY_CNT|MON_MM|APLY_UPRC|SALES_AMT";
                case "TB_CO_MON_COST" -> "ASGN_MM|FREE_MM|SALES_AMT|LABOR_ALLOC_AMT|TEAM_EXPS_AMT|PROJ_EXPS_AMT|DRCT_EXPS_AMT|COST_CLS_CD|CTRT_TYPE_CD|PART_ID";
                case "TB_CO_MON_PL" -> "SALES_AMT|ALLOC_COST_AMT|IDLE_COST_AMT|DRCT_EXPS_AMT|SALES_COST_AMT|GROSS_PRFT_AMT|SGA_INPUT_AMT|INDRCT_LABOR_AMT|RND_AMT|SGA_TOT_AMT|PREV_ADJ_AMT|OPRT_PRFT_AMT|NOPR_INCM_AMT|NOPR_EXPS_AMT|CORP_TAX_AMT|NET_PRFT_AMT|MIGR_YN";
                default -> "(?!)";
            };
            String selected=change.tableName().equals("TB_PJ_ASGN_MON")
                ? change.targetKey().substring(change.targetKey().lastIndexOf(':')+1) : change.targetKey().substring(0,Math.min(6,change.targetKey().length()));
            if(!change.targetKey().matches(key)||!Set.of("NEW","UPD","DEL").contains(change.type())
                    ||!change.item().matches(fields)||!historyKey(change.reason())||!change.reason().startsWith(selected+":"))throw InputValidator.invalid("history");
            return;
        }
        if (!change.targetKey().matches(key) || !"UPD".equals(change.type()) || !"FIX_YN".equals(change.item())
                || !("N".equals(change.before()) && "Y".equals(change.after())
                    || "Y".equals(change.before()) && "N".equals(change.after()))
                || !historyKey(change.reason())) throw InputValidator.invalid("history");
        String month = change.tableName().equals("TB_PJ_ASGN_MON")
            ? change.targetKey().substring(change.targetKey().lastIndexOf(':') + 1)
            : change.targetKey().substring(0,6);
        if (!change.reason().startsWith(month + ":")) throw InputValidator.invalid("history");
    }
    private static boolean countItem(String item) {
        int dot = item.indexOf('.');
        return dot > 0 && SNAPSHOTS.contains(item.substring(0,dot))
            && Set.of("rowCount", "preservedIndependentFixedCount").contains(item.substring(dot+1));
    }
    private static String month() { return "(?!0000)[0-9]{4}(0[1-9]|1[0-2])"; }
    public static boolean historyKey(String key) { return key != null && key.matches(month() + ":[1-9][0-9]{0,4}"); }
}
