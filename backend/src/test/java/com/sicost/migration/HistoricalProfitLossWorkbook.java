package com.sicost.migration;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

final class HistoricalProfitLossWorkbook {
    static MockMultipartFile valid() throws Exception {return file(b->{});}
    static MockMultipartFile file(Consumer<XSSFWorkbook> customize) throws Exception {
        try(var book=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            var sheet=book.createSheet();var header=sheet.createRow(0);
            for(int i=0;i<HistoricalProfitLossImportDefinition.HEADERS.size();i++)header.createCell(i).setCellValue(HistoricalProfitLossImportDefinition.HEADERS.get(i)+(i<5?"*":""));
            var row=sheet.createRow(1);row.createCell(0).setCellValue("2079-01");
            for(int i=1;i<17;i++)row.createCell(i).setBlank();
            row.getCell(1).setCellValue(1000);row.getCell(2).setCellValue(500);row.getCell(3).setCellValue(200);row.getCell(4).setCellValue(300);
            customize.accept(book);book.write(output);
            return new MockMultipartFile("file","history.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
        }
    }
}
