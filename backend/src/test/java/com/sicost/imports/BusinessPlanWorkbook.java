package com.sicost.imports;

import java.io.ByteArrayOutputStream;
import java.time.YearMonth;
import java.util.function.Consumer;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

final class BusinessPlanWorkbook {
    static MockMultipartFile valid() throws Exception { return file(book -> {}); }
    static MockMultipartFile file(Consumer<XSSFWorkbook> customize) throws Exception {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("목표"); var header = sheet.createRow(0);
            for (int c = 0; c < 3; c++) header.createCell(c).setCellValue(BusinessPlanImportDefinition.HEADERS.get(c));
            for (int m = 1; m <= 12; m++) {
                var row = sheet.createRow(m); row.createCell(0).setCellValue(YearMonth.of(2080, m).toString());
                row.createCell(1).setCellValue(1000 + m); row.createCell(2).setCellValue(-m);
            }
            customize.accept(book); book.write(output);
            return new MockMultipartFile("file", "targets.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
