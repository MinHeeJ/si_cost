package com.sicost.b5;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

final class B5Workbook {
    static MockMultipartFile file(Consumer<XSSFWorkbook> edit) throws Exception {
        try(var book=new XSSFWorkbook();var output=new ByteArrayOutputStream()) {
            var header=book.createSheet("배정").createRow(0);
            for(int i=0;i<B5ImportDefinition.HEADERS.size();i++)header.createCell(i).setCellValue(B5ImportDefinition.HEADERS.get(i));
            edit.accept(book);book.write(output);return new MockMultipartFile("file","assignments.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
        }
    }
    static void row(XSSFWorkbook book,int index,String... values) {var row=book.getSheetAt(0).createRow(index);for(int i=0;i<values.length;i++)row.createCell(i).setCellValue(values[i]);}
    static MockMultipartFile valid() throws Exception {return file(b->row(b,1,"P-test","0001","개발","2026-06-01","2026-06-30","","100","신규","",""));}
}
