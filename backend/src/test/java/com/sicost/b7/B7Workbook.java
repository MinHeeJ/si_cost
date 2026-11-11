package com.sicost.b7;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.function.Consumer;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

final class B7Workbook {
    private B7Workbook() {}
    static MockMultipartFile valid() throws Exception {return file(book->row(book,1,"고객사","검사 거래처","123-45-67890","","","","010-0010-0020","finance@example.test","","",""));}
    static MockMultipartFile file(Consumer<XSSFWorkbook> edit) throws Exception {
        try(var book=new XSSFWorkbook();var bytes=new ByteArrayOutputStream()) {
            var header=book.createSheet("거래처").createRow(0);
            for(int i=0;i<ExcelImportService.HEADERS.size();i++)header.createCell(i).setCellValue(ExcelImportService.HEADERS.get(i));
            edit.accept(book);book.write(bytes);
            return new MockMultipartFile("file","clients.XLSX","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes.toByteArray());
        }
    }
    static void row(XSSFWorkbook book,int number,String... values) {
        var row=book.getSheetAt(0).createRow(number);
        for(int i=0;i<values.length;i++)row.createCell(i).setCellValue(values[i]);
    }
    static List<B7Dtos.Code> codes() {
        return List.of(new B7Dtos.Code("CLNT_TYPE","CUST","고객사",true,null),new B7Dtos.Code("CLNT_TYPE","VNDR","협력사",true,null),
            new B7Dtos.Code("CLNT_TYPE","BOTH","고객·협력",true,null),new B7Dtos.Code("BANK","KB","KB국민은행",true,null));
    }
}
