package com.sicost.b8;

import com.sicost.b7.B7Dtos.Code;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.function.Consumer;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

final class DeveloperWorkbook {
    private DeveloperWorkbook() {}
    static MockMultipartFile valid() throws Exception {return file(book->validRow(book,1));}
    static MockMultipartFile file(Consumer<XSSFWorkbook> edit) throws Exception {
        try(var book=new XSSFWorkbook();var bytes=new ByteArrayOutputStream()) {
            var header=book.createSheet("개발자").createRow(0);
            for(int i=0;i<B8ImportDefinition.HEADERS.size();i++)header.createCell(i).setCellValue(B8ImportDefinition.HEADERS.get(i));
            edit.accept(book);book.write(bytes);
            return new MockMultipartFile("file","developers.XLSX","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes.toByteArray());
        }
    }
    static void validRow(XSSFWorkbook book,int number) {row(book,number,"","검사 개발자","정규직","자사","중급","백엔드 개발","백엔드 개발","","직접","HQ","2020-02-29","0","","Java, SQL,Java");}
    static void row(XSSFWorkbook book,int number,String... values) {
        var row=book.getSheetAt(0).createRow(number);for(int i=0;i<values.length;i++)row.createCell(i).setCellValue(values[i]);
    }
    static List<Code> codes(){return List.of(new Code("EMP_TYPE","FT","정규직",true,null),new Code("EMP_TYPE","OS","외주",true,null),
        new Code("GRADE","MD","중급",true,null),new Code("JOB","BE","백엔드 개발",true,null),new Code("JBPS","MEMBER","팀원",true,null),new Code("DRCT_CLS","DRCT","직접",true,null));}
}
