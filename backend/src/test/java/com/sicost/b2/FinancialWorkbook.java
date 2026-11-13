package com.sicost.b2;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

final class FinancialWorkbook {
    static MockMultipartFile valid() throws Exception { return file(List.of(List.of("판관비","","임대료","0","","","n",""))); }
    static MockMultipartFile file(List<List<String>> rows) throws Exception {
        try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=book.createSheet("재무");var header=sheet.createRow(0);
            for(int i=0;i<8;i++)header.createCell(i).setCellValue(FinancialImportDefinition.HEADERS.get(i)+(i==0||i==2||i==3?"*":""));
            for(int i=0;i<rows.size();i++){var row=sheet.createRow(i+1);for(int j=0;j<rows.get(i).size();j++)row.createCell(j).setCellValue(rows.get(i).get(j));}
            book.write(out);return new MockMultipartFile("file","financial.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",out.toByteArray());
        }
    }
}
