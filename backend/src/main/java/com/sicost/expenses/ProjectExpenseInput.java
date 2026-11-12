package com.sicost.expenses;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Map;
import java.util.Set;
import static com.sicost.expenses.ProjectExpenseDtos.*;

public final class ProjectExpenseInput {
    private ProjectExpenseInput() {}
    public static String id(String id) { return ProjectInputPolicy.text(id,20,"id"); }
    public static void query(Map<String,String> query) {
        query.keySet().forEach(key -> { throw ProjectInputPolicy.invalid(key); });
    }
    public static CreateProjectExpenseRequest create(JsonNode body) {
        allow(body,Set.of("year","expectedRevision","expenseMonth","itemCode","amount","memo"));
        String memo=null;
        if (body.hasNonNull("memo")) memo=ProjectInputPolicy.optionalText(text(body,"memo"),500,"memo");
        return new CreateProjectExpenseRequest(year(body),revision(body),
            ProjectInputPolicy.month(text(body,"expenseMonth"),"expenseMonth").toString(),
            ProjectInputPolicy.text(text(body,"itemCode"),10,"itemCode"),
            ProjectInputPolicy.expenseAmount(text(body,"amount")).toPlainString(),memo);
    }
    public static DeleteProjectExpenseRequest delete(JsonNode body) {
        allow(body,Set.of("year","expectedRevision"));
        return new DeleteProjectExpenseRequest(year(body),revision(body));
    }
    public static ExpenseKey key(String id,String month,String sn) {
        int serial;
        try {
            if (!sn.matches("[0-9]{1,5}")) throw ProjectInputPolicy.invalid("expenseSn");
            serial=Integer.parseInt(sn);
        } catch (NumberFormatException e) { throw ProjectInputPolicy.invalid("expenseSn"); }
        if (serial<1) throw ProjectInputPolicy.invalid("expenseSn");
        return new ExpenseKey(id(id),ProjectInputPolicy.month(month,"expenseMonth").toString().replace("-",""),serial);
    }
    private static String revision(JsonNode body) { return ProjectInputPolicy.expectedRevision(text(body,"expectedRevision")); }
    private static Integer year(JsonNode body) {
        JsonNode value=body.get("year");
        if (value==null || !value.isIntegralNumber() || !value.canConvertToInt()) throw ProjectInputPolicy.invalid("year");
        return value.intValue();
    }
    private static String text(JsonNode body,String field) {
        JsonNode value=body.get(field);
        if (value==null || !value.isTextual()) throw ProjectInputPolicy.invalid(field);
        return value.textValue();
    }
    private static void allow(JsonNode body,Set<String> fields) {
        if (body==null || !body.isObject()) throw ProjectInputPolicy.invalid("body");
        body.fieldNames().forEachRemaining(key->{ if (!fields.contains(key)) throw ProjectInputPolicy.invalid(key); });
    }
}
