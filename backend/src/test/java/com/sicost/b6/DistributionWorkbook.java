package com.sicost.b6;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Consumer;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;
import static com.sicost.b6.B6Dtos.*;

final class DistributionWorkbook {
    static Project project() {return new Project("P-TEST","시험 프로젝트","TK","ING",LocalDate.of(2026,6,1),LocalDate.of(2026,7,31),new BigDecimal("100"));}
    static MockMultipartFile valid() throws Exception {return file(b->item(b,1,"항목", "100","0"));}
    static MockMultipartFile file(Consumer<XSSFWorkbook> customize) throws Exception {
        try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=book.createSheet("데이터");var header=sheet.createRow(0);int i=0;
            for(String title:B6ImportDefinition.headers(project()))header.createCell(i++).setCellValue(title);
            customize.accept(book);book.write(out);return new MockMultipartFile("file","distribution.xlsx",null,out.toByteArray());
        }
    }
    static void item(XSSFWorkbook book,int index,String name,String june,String july) {
        var row=book.getSheetAt(0).createRow(index);String[] values={"항목","","",name,june,july,""};
        for(int i=0;i<values.length;i++)row.createCell(i).setCellValue(values[i]);
    }
    static void person(XSSFWorkbook book,int index,String number,String name) {
        item(book,index,"","100","0");var row=book.getSheetAt(0).getRow(index);
        row.getCell(0).setCellValue("사람");row.getCell(1).setCellValue(number);row.getCell(2).setCellValue(name);
    }
}
