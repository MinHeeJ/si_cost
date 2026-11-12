package com.sicost.b9;

import com.sicost.b7.ExcelImportService;
import com.sicost.b7.B7Dtos.Code;
import com.sicost.common.api.ApiException;
import com.sicost.common.projects.ProjectRegistrationService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sicost.b9.B9Dtos.*;

class ProjectImportTest {
    final B9Mapper mapper=mock(B9Mapper.class);
    final ExcelImportService parser=new ExcelImportService();
    final Clock clock=Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));
    final B9ImportDefinition definition=new B9ImportDefinition(mapper,clock);
    @BeforeEach void setup() {
        when(mapper.codes()).thenReturn(ProjectWorkbook.codes());
        when(mapper.customers()).thenReturn(List.of(new Candidate("C-1","고객","0012345678")));
        when(mapper.parts()).thenReturn(List.of(new Part("HQ","본사",true)));
        when(mapper.defaults("202606")).thenReturn(new Defaults("202501","20","50000","20000"));
    }
    private Checked check(org.springframework.mock.web.MockMultipartFile file){return definition.validate(parser.parse(file,B9ImportDefinition.FORMAT),2026);}
    @Test void mapsAll15ColumnsDefaultsAmountsAndCrossYearWithoutBusinessWrites() throws Exception {
        var checked=check(ProjectWorkbook.valid());assertThat(checked.summary()).isEqualTo(new Summary(1,1,0,0,0,0));
        var p=checked.projects().get(0).project();assertThat(p.name()).isEqualTo("검사 프로젝트");assertThat(p.customerId()).isEqualTo("C-1");assertThat(p.partId()).isEqualTo("HQ");
        assertThat(p.startDate()).isEqualTo(LocalDate.of(2026,1,1));assertThat(p.endDate()).isEqualTo(LocalDate.of(2027,12,31));assertThat(p.status()).isEqualTo("ING");
        assertThat(p.contractAmount()).isEqualByComparingTo("1000");assertThat(p.budgetAmount()).isEqualByComparingTo("800");assertThat(p.budgetManual()).isEqualTo("N");
        assertThat(p.estimateAmount()).isNull();assertThat(p.teamExpenseAmount()).isZero();assertThat(p.projectExpenseAmount()).isEqualByComparingTo("20000");
        assertThat(p.feature()).isEqualTo("특징");assertThat(p.workplace()).isEqualTo("서울");verify(mapper,never()).insertUpload(any());
    }
    @Test void existingAllStatusIdsAndEveryDuplicateRowFailCaseSensitively() throws Exception {
        when(mapper.existingIds()).thenReturn(List.of("Lose-ID"));
        var f=ProjectWorkbook.file(b->{for(int i=1;i<=3;i++)ProjectWorkbook.validRow(b,i);b.getSheetAt(0).getRow(1).getCell(0).setCellValue("Lose-ID");b.getSheetAt(0).getRow(2).getCell(0).setCellValue("Lose-ID");b.getSheetAt(0).getRow(3).getCell(0).setCellValue("lose-id");});
        var c=check(f);assertThat(c.summary().errorRows()).isEqualTo(2);assertThat(c.summary().successRows()).isEqualTo(1);
        assertThat(c.issues()).filteredOn(i->i.reason().contains("파일 안")).extracting(Problem::rowNumber).containsExactly(2,3);
    }
    @ParameterizedTest @ValueSource(strings={"한글","has space","_invalid","ABCDEFGHIJKLMNOPQRSTU"})
    void explicitIdsUseExactAllowlist(String id) throws Exception {
        var c=check(ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);b.getSheetAt(0).getRow(1).getCell(0).setCellValue(id);}));
        assertThat(c.summary().errorRows()).isEqualTo(1);
    }
    @Test void customerNumberNormalizesAndAmbiguousNameExposesEveryCandidateWithoutFirstSelection() throws Exception {
        when(mapper.customers()).thenReturn(List.of(new Candidate("C-1","고객","0012345678"),new Candidate("C-2","고객","0012345679")));
        var ambiguous=check(ProjectWorkbook.valid());assertThat(ambiguous.projects().get(0).project().customerId()).isNull();
        assertThat(ambiguous.issues()).filteredOn(p->p.columnName().equals("고객사")).singleElement().satisfies(p->{assertThat(p.candidates()).hasSize(2);assertThat(p.reason()).hasSizeLessThanOrEqualTo(300).contains("사업자번호");});
        var normalized=check(ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);b.getSheetAt(0).getRow(1).getCell(2).setCellValue(" 001-23-45678 ");}));
        assertThat(normalized.projects().get(0).project().customerId()).isEqualTo("C-1");assertThat(normalized.summary().errorRows()).isZero();
    }
    @Test void duplicatePartNameShowsDistinctIdsWithoutChoosingFirst() throws Exception {
        when(mapper.parts()).thenReturn(List.of(new Part("PART-1","중복",false),new Part("PART-2","중복",false)));
        var c=check(ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);b.getSheetAt(0).getRow(1).getCell(6).setCellValue("중복");}));
        assertThat(c.projects().get(0).project().partId()).isNull();assertThat(c.issues().get(0).candidates()).extracting(Candidate::id).containsExactly("PART-1","PART-2");
    }
    @Test void codeNameWinsMappingWarnsCodeIdIsNotDisplayName() throws Exception {
        var codes=new ArrayList<>(ProjectWorkbook.codes());codes.add(new Code("CTRT_TYPE","TK","턴키",true,"옛턴키"));codes.add(new Code("CTRT_TYPE","IN","내부(자체 개발)",true,"턴키"));when(mapper.codes()).thenReturn(codes);
        assertThat(check(ProjectWorkbook.valid()).projects().get(0).project().contractType()).isEqualTo("TK");
        var mapped=check(ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);b.getSheetAt(0).getRow(1).getCell(3).setCellValue("옛턴키");}));assertThat(mapped.summary().warningRows()).isEqualTo(1);
        var raw=check(ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);b.getSheetAt(0).getRow(1).getCell(3).setCellValue("TK");}));assertThat(raw.summary().errorRows()).isEqualTo(1);
    }
    @Test void internalDiscardsHiddenFieldsIncludingInvalidCachedValuesWithWarningsOnly() throws Exception {
        var c=check(ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);var r=b.getSheetAt(0).getRow(1);r.getCell(3).setCellValue("내부(자체 개발)");r.getCell(2).setCellValue("x".repeat(200));r.getCell(6).setCellValue("없는파트");r.getCell(7).setCellValue("x".repeat(300));r.getCell(11).setCellValue("-bad");r.getCell(10).setCellValue("");}));
        assertThat(c.summary().warningRows()).isEqualTo(1);assertThat(c.summary().errorRows()).isZero();assertThat(c.issues()).hasSize(4);
        var p=c.projects().get(0).project();assertThat(p.customerId()).isNull();assertThat(p.partId()).isNull();assertThat(p.workplace()).isNull();assertThat(p.budgetAmount()).isNull();assertThat(p.budgetManual()).isEqualTo("N");assertThat(p.contractAmount()).isZero();
    }
    @Test void missingMarginAllowsManualZeroAndMissingAllowanceRequiresExplicitValue() throws Exception {
        when(mapper.defaults(any())).thenReturn(new Defaults("202501",null,null,null));
        assertThat(check(ProjectWorkbook.valid()).issues()).extracting(Problem::columnName).contains("예산","프로젝트경비");
        var manual=check(ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);var r=b.getSheetAt(0).getRow(1);r.getCell(11).setCellValue("0");r.getCell(13).setCellValue("0");}));
        assertThat(manual.summary().errorRows()).isZero();assertThat(manual.projects().get(0).project().budgetManual()).isEqualTo("Y");assertThat(manual.projects().get(0).project().budgetAmount()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.1","1e3","10%","1000000000000000"})
    void rejectsInvalidIntegerMoneyWithoutRounding(String amount) throws Exception {
        var c=check(ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);b.getSheetAt(0).getRow(1).getCell(10).setCellValue(amount);}));assertThat(c.issues()).anyMatch(p->p.columnName().equals("계약금액")&&p.severity().equals("ERR"));
    }
    @Test void maxMoneyManualZeroWhitespaceAndLongBusinessTextAreNotTruncated() throws Exception {
        var c=check(ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);var r=b.getSheetAt(0).getRow(1);r.getCell(1).setCellValue("가".repeat(201));r.getCell(10).setCellValue("999,999,999,999,999 원");r.getCell(11).setCellValue("0");}));
        assertThat(c.projects().get(0).project().name()).hasSize(201);assertThat(c.projects().get(0).project().contractAmount()).isEqualByComparingTo("999999999999999");assertThat(c.issues()).anyMatch(i->i.inputValue().length()==200);
    }
    @Test void actualDateAndRangeValidationPastEndNormalizationDoNotCompareSelectedYear() throws Exception {
        var bad=check(ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);var r=b.getSheetAt(0).getRow(1);r.getCell(4).setCellValue("2026-02-30");}));assertThat(bad.summary().errorRows()).isEqualTo(1);
        var reversed=check(ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);b.getSheetAt(0).getRow(1).getCell(5).setCellValue("2025-12-31");}));assertThat(reversed.summary().errorRows()).isEqualTo(1);
        var past=check(ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);var r=b.getSheetAt(0).getRow(1);r.getCell(4).setCellValue("2024-01-01");r.getCell(5).setCellValue("2025-01-01");}));assertThat(past.summary().errorRows()).isZero();assertThat(past.projects().get(0).project().status()).isEqualTo("END");
    }
    @Test void yearBoundsAndPastYearAreIndependentOfContractDates() {
        assertThatThrownBy(()->definition.requireYear(2025)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("READ_ONLY_YEAR"));
        assertThatThrownBy(()->definition.requireYear(2024)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("INVALID_INPUT"));
        assertThatThrownBy(()->definition.requireYear(2028)).isInstanceOf(ApiException.class);
    }
    @Test void cacheOnlyNoExternalFormulaExecutionAndHiddenRowsIncludedBlankRowsExcluded() throws Exception {
        var f=ProjectWorkbook.file(b->{ProjectWorkbook.validRow(b,1);b.getSheetAt(0).getRow(1).setZeroHeight(true);b.getSheetAt(0).createRow(2);ProjectWorkbook.validRow(b,3);var cell=b.getSheetAt(0).getRow(3).getCell(1);cell.setCellFormula("WEBSERVICE(\"https://invalid.test\")");if(cell.getCTCell().isSetV())cell.getCTCell().unsetV();});
        var c=check(f);assertThat(c.summary().totalRows()).isEqualTo(2);assertThat(c.summary().errorRows()).isEqualTo(1);assertThat(c.issues()).anyMatch(p->p.reason().contains("계산값"));
    }
    @Test void explicitReservationUsesNumericMaximumNotCountAndNeverCollides() {
        var reserved=new HashSet<>(List.of("P-0009","P-0010","custom","P-0002"));
        assertThat(ProjectRegistrationService.allocate(reserved)).isEqualTo("P-0011");assertThat(ProjectRegistrationService.allocate(reserved)).isEqualTo("P-0012");
        var exhausted=new HashSet<>(List.of("P-999999999999999999"));assertThatThrownBy(()->ProjectRegistrationService.allocate(exhausted)).isInstanceOf(ApiException.class);
    }
    @Test void preservesExistingB7ParserContract(){assertThat(ExcelImportService.HEADERS).hasSize(11).contains("사업자등록번호");}
}
