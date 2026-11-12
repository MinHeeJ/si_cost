package com.sicost.b9;

import com.sicost.b7.B7Dtos.Code;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.function.Consumer;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

final class ProjectWorkbook {
    private ProjectWorkbook() {}
    static MockMultipartFile valid() throws Exception { return file(book->validRow(book,1)); }
    static MockMultipartFile file(Consumer<XSSFWorkbook> edit) throws Exception {
        try(var book=new XSSFWorkbook();var bytes=new ByteArrayOutputStream()) {
            var header=book.createSheet("프로젝트").createRow(0);
            for(int i=0;i<B9ImportDefinition.HEADERS.size();i++)header.createCell(i).setCellValue(B9ImportDefinition.HEADERS.get(i));
            edit.accept(book);book.write(bytes);
            return new MockMultipartFile("file","projects.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes.toByteArray());
        }
    }
    static void validRow(XSSFWorkbook book,int number) { row(book,number,"","검사 프로젝트","고객","턴키","2026-01-01","2027-12-31","본사(직접 영업)","서울","","","1,000 원","","0","","특징"); }
    static void row(XSSFWorkbook book,int number,String... values) {var row=book.getSheetAt(0).createRow(number);for(int i=0;i<values.length;i++)row.createCell(i).setCellValue(values[i]);}
    static List<Code> codes(){return List.of(new Code("CTRT_TYPE","TK","턴키",true,null),new Code("CTRT_TYPE","IN","내부(자체 개발)",true,null),
        new Code("PROJ_STAT","ING","진행",true,null),new Code("PROJ_STAT","PRE","수주예정",true,null),new Code("PROJ_STAT","END","종료",true,null));}
}
