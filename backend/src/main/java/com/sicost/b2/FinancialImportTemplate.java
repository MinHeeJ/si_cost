package com.sicost.b2;

import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/** B.2-only validations; other download templates keep their existing behavior. */
final class FinancialImportTemplate {
    private FinancialImportTemplate() {}

    static void configure(SXSSFWorkbook workbook, List<FinancialImportDtos.Code> codes) {
        var sheet = workbook.getSheetAt(0);
        var helper = sheet.getDataValidationHelper();
        var lists = workbook.createSheet("재무입력목록");
        lists.setRandomAccessWindowSize(-1);
        for (int column = 0; column < 2; column++) {
            String group = column == 0 ? "FIN_CLS" : "SGA_ITEM";
            List<String> names = codes.stream().filter(code -> group.equals(code.groupId()))
                .filter(code -> !"FIN_CLS".equals(group) || Set.of("SGA", "NOPR_IN", "NOPR_OUT").contains(code.code()))
                .map(FinancialImportDtos.Code::name).distinct().toList();
            for (int index = 0; index < Math.max(1, names.size()); index++) {
                var row = lists.getRow(index);
                if (row == null) row = lists.createRow(index);
                row.createCell(column).setCellValue(names.isEmpty() ? "" : names.get(index));
            }
            var range = workbook.createName();
            range.setNameName(column == 0 ? "B2FinancialClasses" : "B2SgaItems");
            String letter = column == 0 ? "A" : "B";
            range.setRefersToFormula("'재무입력목록'!$" + letter + "$1:$" + letter + "$" + Math.max(1, names.size()));
        }
        workbook.setSheetHidden(workbook.getSheetIndex(lists), true);
        validation(sheet, helper.createFormulaListConstraint("B2FinancialClasses"), 0, false, "구분 목록에서 선택해 주세요.");
        validation(sheet, helper.createFormulaListConstraint("B2SgaItems"), 1, true, "사용 중인 판관비 항목 목록에서 선택해 주세요. 빈칸도 허용합니다.");
        validation(sheet, helper.createExplicitListConstraint(new String[]{"Y", "N"}), 6, true, "Y 또는 N을 선택해 주세요.");
        validation(sheet, helper.createIntegerConstraint(DataValidationConstraint.OperatorType.BETWEEN, "0", "999999999999999"), 3, false, "금액은 0 이상 원 정수만 입력해 주세요.");
        validation(sheet, helper.createIntegerConstraint(DataValidationConstraint.OperatorType.BETWEEN, "0", "999999999999999"), 4, true, "부가세는 0 이상 원 정수만 입력해 주세요.");
        validation(sheet, helper.createCustomConstraint("AND(ISNUMBER(F2),F2=INT(F2),F2>=DATE(1900,1,1),F2<=DATE(9999,12,31))"), 5, true, "날짜만 YYYY-MM-DD 형식으로 입력해 주세요.");
        var date = workbook.createCellStyle();
        date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
        sheet.setDefaultColumnStyle(5, date);
    }

    private static void validation(org.apache.poi.ss.usermodel.Sheet sheet, DataValidationConstraint constraint,
        int column, boolean optional, String message) {
        var rule = sheet.getDataValidationHelper().createValidation(constraint, new CellRangeAddressList(1, 5000, column, column));
        rule.setErrorStyle(DataValidation.ErrorStyle.STOP);
        rule.setEmptyCellAllowed(optional);
        rule.setShowErrorBox(true);
        rule.createErrorBox("입력값 확인", message);
        rule.setShowPromptBox(true);
        rule.createPromptBox("입력 안내", message);
        sheet.addValidationData(rule);
    }
}
