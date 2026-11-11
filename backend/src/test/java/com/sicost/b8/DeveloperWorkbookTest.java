package com.sicost.b8;

import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.ApiException;
import java.io.ByteArrayOutputStream;
import java.util.zip.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

class DeveloperWorkbookTest {
    final ExcelImportService parser=new ExcelImportService();
    @Test void existingB7ParserStillAcceptsItsElevenColumnsAndBusinessNumberAlias() throws Exception {
        try(var book=new XSSFWorkbook();var bytes=new ByteArrayOutputStream()) {
            var sheet=book.createSheet();var h=sheet.createRow(0);for(int i=0;i<ExcelImportService.HEADERS.size();i++)h.createCell(i).setCellValue(ExcelImportService.HEADERS.get(i));
            h.getCell(2).setCellValue("사업자번호");var r=sheet.createRow(1);r.createCell(0).setCellValue("고객사");r.createCell(1).setCellValue("회사");r.createCell(2).setCellValue("0012345678");book.write(bytes);
            var parsed=parser.parse(new MockMultipartFile("file","clients.xlsx",null,bytes.toByteArray()));assertThat(parsed.columns()).hasSize(11);assertThat(parsed.rows().get(0).cells().get("사업자등록번호").text()).isEqualTo("0012345678");
        }
    }
    @Test void damagedZipIs400ButOrdinaryZipRenamedWorkbookIs415() throws Exception {
        var damaged=new MockMultipartFile("file","damaged.xlsx",null,new byte[]{'P','K',0,0});assertStatus(damaged,400);
        var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("notes.txt"));zip.write(new byte[]{1});zip.closeEntry();}
        assertStatus(new MockMultipartFile("file","renamed.xlsx",null,bytes.toByteArray()),415);
    }
    @Test void oleEncryptedWorkbookIs415AndWrongExtensionIs415() {
        assertStatus(new MockMultipartFile("file","protected.xlsx",null,new byte[]{(byte)0xd0,(byte)0xcf,0,0,0,0,0,0}),415);
        assertStatus(new MockMultipartFile("file","wrong.csv",null,new byte[]{1}),415);
    }
    @Test void filenameLength200Accepted201AndEmptyRejectedAndPathStripped() throws Exception {
        var valid=DeveloperWorkbook.valid();String name="가".repeat(195)+".xlsx";
        assertThat(parser.parse(new MockMultipartFile("file",name,null,valid.getBytes()),B8ImportDefinition.FORMAT).filename()).isEqualTo(name);
        assertStatus(new MockMultipartFile("file","가".repeat(196)+".xlsx",null,valid.getBytes()),400);assertStatus(new MockMultipartFile("file","",null,valid.getBytes()),400);
        assertThat(parser.parse(new MockMultipartFile("file","C:\\uploads\\developers.XLSX",null,valid.getBytes()),B8ImportDefinition.FORMAT).filename()).isEqualTo("developers.XLSX");
    }
    @Test void exactly5000RowsIncludingHiddenAreAcceptedBlankRowsAndOtherSheetsIgnored() throws Exception {
        var file=DeveloperWorkbook.file(b->{for(int i=1;i<=5000;i++){DeveloperWorkbook.validRow(b,i);b.getSheetAt(0).getRow(i).setZeroHeight(true);}b.getSheetAt(0).createRow(5001);b.createSheet("다른 시트").createRow(0).createCell(0).setCellValue("폐기");});
        assertThat(parser.parse(file,B8ImportDefinition.FORMAT).rows()).hasSize(5000);
    }
    @Test void unknownSystemStatusAndRetireColumnsBecomeFileWarningsNotWritableFields() throws Exception {
        var file=DeveloperWorkbook.file(b->{DeveloperWorkbook.validRow(b,1);b.getSheetAt(0).getRow(0).createCell(17).setCellValue("상태");b.getSheetAt(0).getRow(0).createCell(18).setCellValue("퇴사일");});
        var parsed=parser.parse(file,B8ImportDefinition.FORMAT);assertThat(parsed.fileIssues()).hasSize(2);assertThat(parsed.rows().get(0).cells()).doesNotContainKeys("상태","퇴사일");
    }
    private void assertStatus(MockMultipartFile file,int status){assertThatThrownBy(()->parser.parse(file,B8ImportDefinition.FORMAT)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(status));}
}
