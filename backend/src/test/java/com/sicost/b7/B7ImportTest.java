package com.sicost.b7;

import com.sicost.common.api.ApiException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import static com.sicost.b7.B7Dtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class B7ImportTest {
    final B7Mapper mapper=mock(B7Mapper.class);
    final ExcelImportService parser=new ExcelImportService();
    final B7ImportDefinition definition=new B7ImportDefinition(mapper);
    @BeforeEach void setup(){when(mapper.codes()).thenReturn(B7Workbook.codes());when(mapper.clients()).thenReturn(List.of());}
    @Test void filePathIsStrippedCaseInsensitiveAndLeadingZeroPreserved() throws Exception {
        var original=B7Workbook.valid();var file=new MockMultipartFile("file","C:\\uploads\\clients.XLSX",null,original.getBytes());
        var parsed=parser.parse(file);assertThat(parsed.filename()).isEqualTo("clients.XLSX");assertThat(parsed.rows().get(0).cells().get("연락처").text()).isEqualTo("010-0010-0020");
    }
    @Test void repeatParsingDoesNotEraseCallerOwnedFileBytes() throws Exception {
        var file=B7Workbook.valid();byte[] original=file.getBytes().clone();
        assertThat(parser.parse(file).rows()).hasSize(1);
        assertThat(file.getBytes()).containsExactly(original);
        assertThat(parser.parse(file).rows()).hasSize(1);
    }
    @Test void duplicateHeadersAreRequestFailure() throws Exception {
        var file=B7Workbook.file(b->{b.getSheetAt(0).getRow(0).createCell(11).setCellValue("상호*");B7Workbook.row(b,1,"고객사","상호","1234567890");});
        assertThatThrownBy(()->parser.parse(file)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("INVALID_FILE_HEADER"));
    }
    @Test void aliasesReorderedTitlesAndBlankBeforeHeaderAreAccepted() throws Exception {
        var file=B7Workbook.file(b->{var s=b.getSheetAt(0);s.shiftRows(0,0,2);var h=s.getRow(2);h.getCell(0).setCellValue("상호 *");h.getCell(1).setCellValue("구\n분");h.getCell(2).setCellValue("사업자번호");B7Workbook.row(b,3,"이름","고객사","0012345678","","","","","finance@example.test");});
        var checked=definition.validate(parser.parse(file));assertThat(checked.summary().errorRows()).isZero();assertThat(checked.clients().get(0).businessNo()).isEqualTo("001-23-45678");
    }
    @Test void moreThan5000HiddenRowsStillFails413() throws Exception {
        var file=B7Workbook.file(b->{for(int n=1;n<=5001;n++){B7Workbook.row(b,n,"고객사","이름","1234567890");b.getSheetAt(0).getRow(n).setZeroHeight(true);}});
        assertThatThrownBy(()->parser.parse(file)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(413));
    }
    @Test void encryptedOleIs415() {
        var file=new MockMultipartFile("file","protected.xlsx",null,new byte[]{(byte)0xd0,(byte)0xcf,0,0,0,0,0,0});
        assertThatThrownBy(()->parser.parse(file)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(415));
    }
    @Test void damagedZipIs400() {
        var file=new MockMultipartFile("file","broken.xlsx",null,new byte[]{'P','K',0,0});
        assertThatThrownBy(()->parser.parse(file)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(400));
    }
    @Test void ordinaryZipRenamedXlsxIs415() throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("notes.txt"));zip.write("not a workbook".getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
        }
        var file=new MockMultipartFile("file","renamed.xlsx",null,bytes.toByteArray());
        assertThatThrownBy(()->parser.parse(file)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(415));
        verifyNoInteractions(mapper);
    }
    @Test void missingOptionalColumnsAreEmptyAndRequiredConditionalFieldsStillProduceIssues() throws Exception {
        var file=B7Workbook.file(b->{var header=b.getSheetAt(0).getRow(0);for(int i=3;i<11;i++)header.removeCell(header.getCell(i));B7Workbook.row(b,1,"고객사","이름","1234567890");});
        var checked=definition.validate(parser.parse(file));
        assertThat(checked.summary().errorRows()).isEqualTo(1);
        assertThat(checked.issues()).anyMatch(i->i.columnName().equals("세금계산서 이메일")&&i.reason().contains("필수"));
    }
    @Test void diagnosticsFollowPhysicalColumnOrderAndNeverTruncateBusinessValues() throws Exception {
        var file=B7Workbook.file(b->{
            var h=b.getSheetAt(0).getRow(0);h.getCell(0).setCellValue("사업자번호");h.getCell(1).setCellValue("구분");h.getCell(2).setCellValue("상호");
            B7Workbook.row(b,1,"bad-number","고객사","가".repeat(51),"","","","","bad-email");
        });
        var checked=definition.validate(parser.parse(file));
        assertThat(checked.clients().get(0).name()).hasSize(51);
        assertThat(checked.issues()).extracting(Issue::columnName).containsExactly("사업자등록번호","상호","세금계산서 이메일");
        assertThat(checked.issues()).extracting(Issue::sequence).containsExactly(1,2,3);
    }
    @Test void normalizedFileDuplicatesFlagEveryRelatedRowAndNoClientAllocation() throws Exception {
        var file=B7Workbook.file(b->{B7Workbook.row(b,1,"고객사","A","1234567890","","","","","a@example.test");B7Workbook.row(b,2,"고객사","B","123-45-67890","","","","","b@example.test");});
        var checked=definition.validate(parser.parse(file));assertThat(checked.summary().errorRows()).isEqualTo(2);assertThat(checked.issues()).filteredOn(i->i.reason().contains("파일 안")).extracting(Issue::rowNumber).containsExactly(2,3);
        verify(mapper,never()).nextUploadId();
    }
    @Test void stoppedMappedCodeAndLongNumericAccountArePrivateErrors() throws Exception {
        var codes=new ArrayList<>(B7Workbook.codes());codes.add(new Code("BANK","STOP","중지은행",false,"옛은행"));when(mapper.codes()).thenReturn(codes);
        var file=B7Workbook.file(b->{B7Workbook.row(b,1,"협력사","이름","1234567890","","","","","","옛은행","","예금주");b.getSheetAt(0).getRow(1).getCell(9).setCellValue(1234567890123456d);});
        var checked=definition.validate(parser.parse(file));assertThat(checked.summary().errorRows()).isEqualTo(1);
        assertThat(checked.issues()).filteredOn(i->Set.of("은행","계좌번호","예금주").contains(i.columnName())).allMatch(i->i.inputValue().equals("[비공개]"));
        assertThat(checked.issues()).anyMatch(i->i.reason().equals("텍스트 형식으로 입력하세요"));
    }
    @Test void irrelevantAccountErrorsAreDiscardedWithoutValidationAndSecretInput() throws Exception {
        var file=B7Workbook.file(b->{B7Workbook.row(b,1,"고객사","이름","1234567890","","","","","a@example.test","not-a-code","","invalid");b.getSheetAt(0).getRow(1).getCell(9).setCellFormula("1/0");});
        var checked=definition.validate(parser.parse(file));assertThat(checked.summary().errorRows()).isZero();assertThat(checked.summary().warningRows()).isEqualTo(1);
        assertThat(checked.clients().get(0).accountNumber()).isNull();assertThat(checked.issues()).allMatch(i->i.inputValue().equals("[비공개]"));
    }
    @Test void cachedFormulasAreReadWithoutEvaluationMissingCacheAndCellErrorAreErrors() throws Exception {
        var file=B7Workbook.file(b->{B7Workbook.row(b,1,"고객사","","1234567890","","","","","a@example.test");var c=b.getSheetAt(0).getRow(1).getCell(1);c.setCellFormula("\"stored\"");c.setCellValue("저장된 이름");});
        assertThat(parser.parse(file).rows().get(0).cells().get("상호").text()).isEqualTo("저장된 이름");
        var missing=B7Workbook.file(b->{
            B7Workbook.row(b,1,"고객사","","1234567890");
            var row=b.getSheetAt(0).getRow(1);row.removeCell(row.getCell(1));var formula=row.createCell(1);
            formula.setCellFormula("WEBSERVICE(\"https://invalid.test\")");
            if(formula.getCTCell().isSetV())formula.getCTCell().unsetV();
        });
        assertThat(definition.validate(parser.parse(missing)).issues()).anyMatch(i->i.reason().contains("계산값"));
    }
    @Test void unknownHeaderHasOneFileWarningLaterHeaderIsDataAndSecondSheetIgnored() throws Exception {
        var file=B7Workbook.file(b->{b.getSheetAt(0).getRow(0).createCell(11).setCellValue("추가열");B7Workbook.row(b,1,"고객사","이름","1234567890","","","","","a@example.test");B7Workbook.row(b,2,"구분","상호","사업자등록번호");b.createSheet("ignored").createRow(0).createCell(0).setCellValue("ignored");});
        var checked=definition.validate(parser.parse(file));assertThat(checked.summary().totalRows()).isEqualTo(2);assertThat(checked.summary().fileWarningCount()).isEqualTo(1);assertThat(checked.summary().errorRows()).isEqualTo(1);
        assertThat(checked.issues()).filteredOn(i->i.columnName().equals("[파일]")).singleElement().satisfies(issue->{
            assertThat(issue.rowNumber()).isEqualTo(1);assertThat(issue.inputValue()).isEqualTo("추가열");assertThat(issue.severity()).isEqualTo("WARN");
        });
    }
    @Test void diagnosticExcerptsMarkTruncationWithoutSplittingUnicode() {
        String value="😀".repeat(201);
        String excerpt=ExcelImportService.excerpt(value,200);
        assertThat(excerpt.codePointCount(0,excerpt.length())).isEqualTo(200);
        assertThat(excerpt).isEqualTo("😀".repeat(197)+"...");
        assertThat(ExcelImportService.excerpt("가".repeat(301),300)).isEqualTo("가".repeat(297)+"...");
    }
    @Test void activeNameWinsOverMappingAndMappedNameWarnsAndCodeIdIsNotName() throws Exception {
        var codes=new ArrayList<>(B7Workbook.codes());codes.add(new Code("CLNT_TYPE","CUST","고객사",true,"옛고객"));when(mapper.codes()).thenReturn(codes);
        var file=B7Workbook.file(b->B7Workbook.row(b,1,"옛고객","이름","1234567890","","","","","a@example.test"));
        assertThat(definition.validate(parser.parse(file)).summary().warningRows()).isEqualTo(1);
        var id=B7Workbook.file(b->B7Workbook.row(b,1,"CUST","이름","1234567890"));assertThat(definition.validate(parser.parse(id)).summary().errorRows()).isEqualTo(1);
    }
}
