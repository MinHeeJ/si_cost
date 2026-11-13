package com.sicost.common.excel;

import java.math.BigDecimal;
import java.util.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProfitabilitySheetRowsTest {
    @Test void namedSheetSplitsAtExistingExcelLimitAndRepeatsHeadersAndCellTypes() throws Exception {
        var book=new SXSSFWorkbook(10);
        try(book) {
            var columns=List.of(new ExcelExportService.Column("id","ID",ExcelExportService.CellType.TEXT),
                new ExcelExportService.Column("amount","금액",ExcelExportService.CellType.NUMBER));
            var writer=new ExcelExportService.SheetRows(book,columns,"월별배분상세",true);
            // Test the exact boundary without allocating one million throwaway rows.
            writer.row=ExcelExportService.DATA_ROWS_PER_SHEET+1;
            writer.append(Arrays.asList("0001",new BigDecimal("12345678901234567")));
            assertThat(book.getNumberOfSheets()).isEqualTo(2);
            assertThat(book.getSheetAt(1).getSheetName()).isEqualTo("월별배분상세_2");
            assertThat(book.getSheetAt(1).getRow(0).getCell(0).getStringCellValue()).isEqualTo("ID");
            assertThat(book.getSheetAt(1).getRow(1).getCell(0).getStringCellValue()).isEqualTo("0001");
            assertThat(book.getSheetAt(1).getRow(1).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            writer.append(Arrays.asList("@text",null));
            assertThat(book.getSheetAt(1).getRow(2).getCell(1).getCellType()).isEqualTo(CellType.BLANK);
        } finally {book.dispose();}
    }
}
