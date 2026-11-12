package com.sicost.b1;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

final class B1Workbook {
    static MockMultipartFile file(Consumer<XSSFWorkbook> edit) throws Exception {
        try(var book=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            var sheet=book.createSheet("급여");var header=sheet.createRow(0);
            for(int i=0;i<B1ImportDefinition.HEADERS.size();i++)header.createCell(i).setCellValue(B1ImportDefinition.HEADERS.get(i)+(i==0?"*":""));
            edit.accept(book);book.write(output);return new MockMultipartFile("file","payroll.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
        }
    }
    static void row(XSSFWorkbook book,int index,String... values) {
        var row=book.getSheetAt(0).createRow(index);for(int i=0;i<values.length;i++)row.createCell(i).setCellValue(values[i]);
    }
    static MockMultipartFile valid() throws Exception { return file(b->row(b,1,"0001","","100","0","0","0","")); }
}
