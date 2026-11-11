package com.sicost.b8;

import com.sicost.b7.ExcelImportService;
import com.sicost.b7.B7Dtos.Code;
import com.sicost.b7.B7Dtos.Issue;
import com.sicost.common.api.ApiException;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sicost.b8.B8Dtos.*;

class DeveloperImportTest {
    final B8Mapper mapper=mock(B8Mapper.class);
    final ExcelImportService parser=new ExcelImportService();
    final B8ImportDefinition definition=new B8ImportDefinition(mapper);
    @BeforeEach void setup(){when(mapper.codes()).thenReturn(DeveloperWorkbook.codes());when(mapper.managementPreview()).thenReturn(BigInteger.ONE);
        when(mapper.parts()).thenReturn(List.of(new Part("HQ","본사",true)));}
    private Checked check(MockMultipartFile file){return definition.validate(parser.parse(file,B8ImportDefinition.FORMAT));}
    @Test void normalizesOptionalValuesDefaultPositionPriceDateSkillsAndNeverReservesNumbers() throws Exception {
        var checked=check(DeveloperWorkbook.valid());assertThat(checked.summary().successRows()).isEqualTo(1);
        var d=checked.developers().get(0);assertThat(d.positionCode()).isEqualTo("MEMBER");assertThat(d.companyId()).isNull();assertThat(d.subJobCode()).isNull();
        assertThat(d.monthlyRate()).isZero();assertThat(d.monthlyPay()).isNull();assertThat(d.hireDate()).isEqualTo(LocalDate.of(2020,2,29));assertThat(d.skills()).containsExactly("Java","SQL");
        assertThat(checked.expectedNumbers()).containsExactly(new NumberRow(2,"K-0001",null));verify(mapper,never()).insertDeveloper(any());
    }
    @Test void preservesB7DefaultHeadersAndDefinitionDoesNotReplaceThem(){assertThat(ExcelImportService.HEADERS).hasSize(11).contains("사업자등록번호");}
    @Test void duplicateManagementFlagsEveryRowIncludingStoppedExistingAndPredictionsSkipFileDirectValues() throws Exception {
        when(mapper.developers()).thenReturn(List.of(new B8Dtos.Existing("Taken","기존",null)));
        var f=DeveloperWorkbook.file(b->{for(int i=1;i<=4;i++)DeveloperWorkbook.validRow(b,i);b.getSheetAt(0).getRow(1).getCell(0).setCellValue("Taken");b.getSheetAt(0).getRow(2).getCell(0).setCellValue("Taken");b.getSheetAt(0).getRow(3).getCell(0).setCellValue("K-0001");});
        var checked=check(f);assertThat(checked.summary().errorRows()).isEqualTo(2);assertThat(checked.issues()).filteredOn(i->i.reason().startsWith("파일 안")).extracting(Issue::rowNumber).containsExactly(2,3);
        assertThat(checked.expectedNumbers().get(3).managementNo()).isEqualTo("K-0002");
    }
    @Test void sameNameAndHireDateWarnsWithExistingNumber() throws Exception {
        when(mapper.developers()).thenReturn(List.of(new B8Dtos.Existing("Earlier","검사 개발자",LocalDate.of(2020,2,29))));
        var c=check(DeveloperWorkbook.valid());assertThat(c.summary().warningRows()).isEqualTo(1);assertThat(c.issues()).anyMatch(i->i.reason().contains("Earlier"));
    }
    @Test void physicalTitleOrderAliasesHeaderPreambleAndFileWarningArePreserved() throws Exception {
        var f=DeveloperWorkbook.file(b->{b.getSheetAt(0).shiftRows(0,0,2);var h=b.getSheetAt(0).getRow(2);h.getCell(0).setCellValue("管理番号");h.getCell(1).setCellValue("직원\n구분 *");h.getCell(2).setCellValue("이름");h.getCell(12).setCellValue("월 급여");h.createCell(17).setCellValue("추가열");DeveloperWorkbook.validRow(b,3);b.getSheetAt(0).getRow(3).getCell(1).setCellValue("정규직");b.getSheetAt(0).getRow(3).getCell(2).setCellValue("이름");});
        var c=check(f);assertThat(c.summary().fileWarningCount()).isEqualTo(2);assertThat(c.summary().successRows()).isEqualTo(1);assertThat(c.issues()).allMatch(i->i.rowNumber()==3&&i.columnName().equals("[파일]"));
    }
    @Test void duplicateTitlesAndMissingRequiredTitlesFailBeforeLookup() throws Exception {
        var duplicate=DeveloperWorkbook.file(b->{DeveloperWorkbook.validRow(b,1);b.getSheetAt(0).getRow(0).createCell(17).setCellValue("이름 *");});
        assertThatThrownBy(()->check(duplicate)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("INVALID_FILE_HEADER"));
        var missing=DeveloperWorkbook.file(b->{DeveloperWorkbook.validRow(b,1);b.getSheetAt(0).getRow(0).getCell(5).setCellValue("없음");});
        assertThatThrownBy(()->check(missing)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(400));verifyNoInteractions(mapper);
    }
    @Test void missingOptionalHeadersDoNotSupplyRequiredDirectOrPartValues() throws Exception {
        var f=DeveloperWorkbook.file(b->{DeveloperWorkbook.validRow(b,1);var h=b.getSheetAt(0).getRow(0);for(int i=4;i<17;i++)if(i!=5)h.removeCell(h.getCell(i));});
        var c=check(f);assertThat(c.issues()).extracting(Issue::columnName).contains("직접/간접","영업 파트");assertThat(c.summary().errorRows()).isEqualTo(1);
    }
    @Test void uniqueActiveCompanyNameAndNormalizedBusinessNoResolveButDuplicateNameAndOutsourceOwnCompanyFail() throws Exception {
        when(mapper.companies()).thenReturn(List.of(new Company("C-001","업체","001-23-45678"),new Company("C-002","업체","001-23-45679")));
        var f=DeveloperWorkbook.file(b->{DeveloperWorkbook.validRow(b,1);b.getSheetAt(0).getRow(1).getCell(3).setCellValue("0012345678");});
        assertThat(check(f).developers().get(0).companyId()).isEqualTo("C-001");
        var ambiguous=DeveloperWorkbook.file(b->{DeveloperWorkbook.validRow(b,1);b.getSheetAt(0).getRow(1).getCell(3).setCellValue("업체");});
        assertThat(check(ambiguous).issues()).anyMatch(i->i.reason().contains("사업자번호를 사용"));
        var own=DeveloperWorkbook.file(b->{DeveloperWorkbook.validRow(b,1);b.getSheetAt(0).getRow(1).getCell(2).setCellValue("외주");});assertThat(check(own).summary().errorRows()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"본사","본사(직접 영업)","HQ"})
    void headquartersAliasesResolveToDatabaseId(String name) throws Exception {
        var f=DeveloperWorkbook.file(b->{DeveloperWorkbook.validRow(b,1);b.getSheetAt(0).getRow(1).getCell(9).setCellValue(name);});assertThat(check(f).developers().get(0).partId()).isEqualTo("HQ");
    }
    @Test void activeNameWinsMappingWarnsStoppedMappingAndCodeIdsFail() throws Exception {
        var codes=new ArrayList<>(DeveloperWorkbook.codes());codes.add(new Code("EMP_TYPE","FT","정규직",true,"옛직원"));codes.add(new Code("EMP_TYPE","STOP","중지",false,"옛중지"));when(mapper.codes()).thenReturn(codes);
        var f=DeveloperWorkbook.file(b->{for(int i=1;i<=3;i++)DeveloperWorkbook.validRow(b,i);b.getSheetAt(0).getRow(1).getCell(2).setCellValue("옛직원");b.getSheetAt(0).getRow(2).getCell(2).setCellValue("옛중지");b.getSheetAt(0).getRow(3).getCell(2).setCellValue("FT");});
        var c=check(f);assertThat(c.summary().warningRows()).isEqualTo(1);assertThat(c.summary().errorRows()).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.2","1e3","1000000000000000","abc"})
    void invalidAmountIsAnErrorAndNotRounded(String amount) throws Exception {
        var f=DeveloperWorkbook.file(b->{DeveloperWorkbook.validRow(b,1);b.getSheetAt(0).getRow(1).getCell(11).setCellValue(amount);});assertThat(check(f).issues()).anyMatch(i->i.columnName().equals("월 단가")&&i.severity().equals("ERR"));
    }
    @Test void datesNormalizeRealExcelDateAndDottedDateButRejectImpossibleDate() throws Exception {
        var f=DeveloperWorkbook.file(b->{for(int i=1;i<=3;i++)DeveloperWorkbook.validRow(b,i);b.getSheetAt(0).getRow(1).getCell(10).setCellValue("2024.02.29");var cell=b.getSheetAt(0).getRow(2).getCell(10);cell.setCellValue(LocalDate.of(2024,2,29));var style=b.createCellStyle();style.setDataFormat(b.createDataFormat().getFormat("yyyy/mm/dd"));cell.setCellStyle(style);b.getSheetAt(0).getRow(3).getCell(10).setCellValue("2023-02-29");});
        var c=check(f);assertThat(c.developers().get(0).hireDate()).isEqualTo(LocalDate.of(2024,2,29));assertThat(c.developers().get(1).hireDate()).isEqualTo(LocalDate.of(2024,2,29));assertThat(c.summary().errorRows()).isEqualTo(1);
    }
    @Test void cachedFormulaNeverEvaluatesMissingCacheIsErrorAndIdentifier16DigitsIsError() throws Exception {
        var f=DeveloperWorkbook.file(b->{DeveloperWorkbook.validRow(b,1);var r=b.getSheetAt(0).getRow(1);r.getCell(0).setCellValue(1234567890123456d);r.getCell(1).setCellFormula("\"name\"");r.getCell(1).setCellValue("저장 이름");r.createCell(14).setCellFormula("WEBSERVICE(\"https://invalid.test\")");if(r.getCell(14).getCTCell().isSetV())r.getCell(14).getCTCell().unsetV();});
        var c=check(f);assertThat(c.developers().get(0).name()).isEqualTo("저장 이름");assertThat(c.issues()).anyMatch(i->i.reason().contains("텍스트 형식"));
        var missing=DeveloperWorkbook.file(b->{DeveloperWorkbook.validRow(b,1);var cell=b.getSheetAt(0).getRow(1).getCell(1);cell.setCellFormula("1+1");if(cell.getCTCell().isSetV())cell.getCTCell().unsetV();});assertThat(check(missing).issues()).anyMatch(i->i.reason().contains("계산값"));
    }
    @Test void textLeadingZeroTrimAndDiagnosticExcerptsDoNotTruncateBusinessValues() throws Exception {
        var f=DeveloperWorkbook.file(b->{DeveloperWorkbook.validRow(b,1);var r=b.getSheetAt(0).getRow(1);r.getCell(0).setCellValue(" 001234 ");r.getCell(1).setCellValue("가".repeat(201));});
        var c=check(f);assertThat(c.developers().get(0).name()).hasSize(201);assertThat(c.developers().get(0).managementNo()).isEqualTo("001234");assertThat(c.issues()).anyMatch(i->i.inputValue().equals("가".repeat(197)+"..."));
    }
    @Test void hiddenRowsCountAnd5001RowsFailBeforeDatabase() throws Exception {
        var f=DeveloperWorkbook.file(b->{for(int i=1;i<=5001;i++){DeveloperWorkbook.validRow(b,i);b.getSheetAt(0).getRow(i).setZeroHeight(true);}});
        assertThatThrownBy(()->check(f)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(413));verifyNoInteractions(mapper);
    }
}
