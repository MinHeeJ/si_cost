package com.sicost.b5;

import com.sicost.assignmentsops.*;
import com.sicost.assignmentsops.AssignmentActionsDtos.*;
import com.sicost.b7.B7Dtos.Code;
import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.*;
import com.sicost.common.assignments.AssignmentMonthlyService.Source;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import static com.sicost.b5.B5Dtos.*;
import com.sicost.b5.B5Dtos.Selection;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class B5ImportTest {
    final B5Mapper mapper=mock(B5Mapper.class);
    final AssignmentActionsService actions=mock(AssignmentActionsService.class);
    final AssignmentActionsRepository assignments=mock(AssignmentActionsRepository.class);
    final AssignmentActionsMapper sources=mock(AssignmentActionsMapper.class);
    final AssignmentMonthlyService months=mock(AssignmentMonthlyService.class);
    final AssignmentMonthlyMapper monthly=mock(AssignmentMonthlyMapper.class);
    final B5ImportDefinition definition=new B5ImportDefinition(new ExcelImportService(),mapper,actions,assignments,months);
    @BeforeEach void setup() {
        when(assignments.mapper()).thenReturn(sources);when(months.mapper()).thenReturn(monthly);
        when(mapper.developers()).thenReturn(List.of(new DeveloperNumber("D-test","0001"),new DeveloperNumber("D-contract","0002")));
        when(mapper.codes()).thenReturn(List.of(new Code("JOB","DEV","개발",true,null),new Code("INPUT_TYPE","NEW","신규",true,null),new Code("INPUT_TYPE","SUB","대체투입",true,null),new Code("INPUT_TYPE","ADD","추가투입",true,null),new Code("INPUT_TYPE","FREE","무상지원",true,null)));
        when(assignments.project(any())).thenReturn(new Project("P-test","현재 프로젝트","ING","MO",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31)));
        when(actions.preview(any())).thenAnswer(call->{PreviewRequest request=call.getArgument(0);if(request==null)return null;Create c=request.assignment();return new Preview(List.of(),List.of("2026-06"),List.of(),c==null?List.of():List.of(new AssignmentMonthlyService.Month("2026-06",c.projectId(),c.developerId(),30,c.contractMm().toPlainString(),c.contractMm().toPlainString(),"100","100","N")));});
        when(sources.active(any())).thenReturn(true);when(sources.totalMm(any())).thenReturn(BigDecimal.ZERO);when(sources.mmLimit(any())).thenReturn(new BigDecimal("1.00"));
        when(sources.price(any())).thenReturn(new BigDecimal("100"));
        when(actions.substitutionPreview(any())).thenReturn(new SubPreview("sub",LocalDate.of(2026,5,31),List.of(new Item("A-old","revision")),List.of(),List.of(),null));
        when(assignments.required("A-old")).thenReturn(new Source("A-old","P-test","D-contract","DEV",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),BigDecimal.ONE,BigDecimal.TEN,"NEW",null,null,0,"N","ACT"));
        when(months.preview(any(),any(),any(),any())).thenReturn(List.of());
    }
    Checked check(String... values) throws Exception {return definition.check(definition.parse(B5Workbook.file(b->B5Workbook.row(b,1,values))),new Request(2026,null,null,List.of()));}
    @Test void defaultsAreOneMmAndNullExtensionNotInventedCode() throws Exception {var result=definition.check(definition.parse(B5Workbook.valid()),new Request(2026,null,null,List.of()));assertThat(result.summary().successRows()).isEqualTo(1);assertThat(result.rows().get(0).source().contractMm()).isEqualByComparingTo("1.00");assertThat(result.rows().get(0).source().extensionPossibility()).isNull();}
    @Test void subMissingContractIsErr() throws Exception {assertThat(check("P-test","0001","개발","2026-06-01","2026-06-30","1.00","100","대체투입").summary().errorRows()).isEqualTo(1);}
    @Test void hiddenPriceAndNonSubContractAreDiscardedWarn() throws Exception {when(assignments.project(any())).thenReturn(new Project("P-test","턴키","ING","TK",LocalDate.of(2026,1,1),LocalDate.of(2026,12,31)));var result=check("P-test","0001","개발","2026-06-01","2026-06-30","","invalid","신규","unregistered");assertThat(result.summary().warningRows()).isEqualTo(1);assertThat(result.summary().errorRows()).isZero();assertThat(result.rows().get(0).source().price()).isNull();assertThat(result.rows().get(0).source().contractDeveloperId()).isNull();}
    @Test void duplicateSixKeysFlagAllRelatedRowsAndErrWins() throws Exception {var file=B5Workbook.file(b->{B5Workbook.row(b,1,"P-test","0001","개발","2026-06-01","2026-06-30","","100","신규","ignored");B5Workbook.row(b,2,"P-test","0001","개발","2026-06-01","2026-06-30","","100","신규");});var result=definition.check(definition.parse(file),new Request(2026,null,null,List.of()));assertThat(result.summary().errorRows()).isEqualTo(2);assertThat(result.summary().warningRows()).isZero();assertThat(result.issues().stream().filter(i->i.reason().contains("6키")).map(i->i.rowNumber())).containsExactly(2,3);}
    @Test void wholeFileMonthlyTotalWarnsEveryRelatedRowNotOnlyLaterRow() throws Exception {var file=B5Workbook.file(b->{B5Workbook.row(b,1,"P-test","0001","개발","2026-06-01","2026-06-30","0.60","100","신규");B5Workbook.row(b,2,"P-other","0001","개발","2026-06-01","2026-06-30","0.60","100","신규");});var result=definition.check(definition.parse(file),new Request(2026,null,null,List.of()));assertThat(result.summary().warningRows()).isEqualTo(2);assertThat(result.issues()).allMatch(i->i.reason().contains("1.20"));}
    @ParameterizedTest @ValueSource(strings={"0","1.01","0.123","-1","1e0"}) void invalidMmErr(String mm) throws Exception {assertThat(check("P-test","0001","개발","2026-06-01","2026-06-30",mm,"100","신규").summary().errorRows()).isEqualTo(1);}
    @Test void invalidRealDateErr() throws Exception {assertThat(check("P-test","0001","개발","2026-02-30","2026-06-30","","100","신규").summary().errorRows()).isEqualTo(1);}
    @Test void explicitDotDatesAcceptedAndNoTruncation() throws Exception {assertThat(check("P-test","0001","개발","2026.06.01","2026.06.30","","100","신규").summary().errorRows()).isZero();assertThat(check("X".repeat(21),"0001","개발","2026-06-01","2026-06-30","","100","신규").summary().errorRows()).isEqualTo(1);}
    @Test void mappedActiveCodeWarnAndAmbiguousNameErr() throws Exception {when(mapper.codes()).thenReturn(List.of(new Code("JOB","DEV","개발",true,"개발자"),new Code("INPUT_TYPE","NEW","신규",true,null)));assertThat(check("P-test","0001","개발자","2026-06-01","2026-06-30","","100","신규").summary().warningRows()).isEqualTo(1);when(mapper.codes()).thenReturn(List.of(new Code("JOB","D1","개발",true,null),new Code("JOB","D2","개발",true,null)));assertThat(check("P-test","0001","개발","2026-06-01","2026-06-30","","100","신규").summary().errorRows()).isEqualTo(1);}
    @Test void missingSubDefaultUsesExplicitPriceNeverActualDeveloperPrice() throws Exception {when(actions.preview(any())).thenThrow(new ApiException(400,"INVALID_INPUT","단가를 입력해 주세요.","billingUnitPrice",null));assertThat(check("P-test","0001","개발","2026-06-01","2026-06-30","","","대체투입","0002").summary().errorRows()).isEqualTo(1);verify(sources,never()).price(any());}
    @Test void existingDuplicateFromSharedPolicyIsRowErr() throws Exception {when(actions.preview(any())).thenThrow(new ApiException(409,"DUPLICATE_ASSIGNMENT","동일한 배정이 있습니다."));assertThat(definition.check(definition.parse(B5Workbook.valid()),new Request(2026,null,null,List.of())).summary().errorRows()).isEqualTo(1);}
    @Test void sameDateSelectedWithdrawalIsDeduplicatedAcrossRows() throws Exception {var file=B5Workbook.file(b->{B5Workbook.row(b,1,"P-test","0001","개발","2026-06-01","2026-06-30","","100","대체투입","0002");B5Workbook.row(b,2,"P-other","0001","개발","2026-06-01","2026-06-30","","100","대체투입","0002");});Withdrawal w=new Withdrawal("A-old","revision",LocalDate.of(2026,5,31),"ETC",null);var result=definition.check(definition.parse(file),new Request(2026,null,null,List.of(new Selection(2,List.of(w)),new Selection(3,List.of(w)))));assertThat(result.withdrawals()).hasSize(1);assertThat(result.summary().errorRows()).isZero();}
    @Test void differentDatesSelectedWithdrawalErrsBothRows() throws Exception {when(actions.substitutionPreview(any())).thenAnswer(c->{Create create=c.getArgument(0);return new SubPreview("sub",create.startDate().minusDays(1),List.of(new Item("A-old","revision")),List.of(),List.of(),null);});var file=B5Workbook.file(b->{B5Workbook.row(b,1,"P-test","0001","개발","2026-06-01","2026-06-30","","100","대체투입","0002");B5Workbook.row(b,2,"P-test","0001","개발","2026-06-02","2026-06-30","","100","대체투입","0002");});var result=definition.check(definition.parse(file),new Request(2026,null,null,List.of(new Selection(2,List.of(new Withdrawal("A-old","revision",LocalDate.of(2026,5,31),"ETC",null))),new Selection(3,List.of(new Withdrawal("A-old","revision",LocalDate.of(2026,6,1),"ETC",null))))));assertThat(result.summary().errorRows()).isEqualTo(2);assertThat(result.withdrawals()).isEmpty();}
    @Test void sourceWarningsRetainedAndCountsPartition() throws Exception {when(actions.preview(any())).thenReturn(new Preview(List.of(new Warning("LEAVE","휴직",List.of(),List.of()),new Warning("CLOSE_CROSS","보호월",List.of("2026-06"),List.of())),List.of(),List.of("2026-06"),List.of()));var result=definition.check(definition.parse(B5Workbook.valid()),new Request(2026,null,null,List.of()));assertThat(result.summary().warningRows()).isEqualTo(1);assertThat(result.summary().successRows()+result.summary().warningRows()+result.summary().errorRows()).isEqualTo(result.summary().totalRows());}
    @Test void formulaWithoutCacheIsNotExecuted() throws Exception {var file=B5Workbook.file(b->{B5Workbook.row(b,1,"P-test","0001","개발","2026-06-01","2026-06-30","","100","신규");var c=b.getSheetAt(0).getRow(1).getCell(6);c.setCellFormula("1+2");if(c.getCTCell().isSetV())c.getCTCell().unsetV();});assertThat(definition.check(definition.parse(file),new Request(2026,null,null,List.of())).summary().errorRows()).isEqualTo(1);}
    @Test void longNumericIdentifierErrPreservingTextLeadingZeros() throws Exception {var file=B5Workbook.file(b->{B5Workbook.row(b,1,"P-test","0001","개발","2026-06-01","2026-06-30","","100","신규");b.getSheetAt(0).getRow(1).getCell(1).setCellValue(1234567890123456d);});assertThat(definition.check(definition.parse(file),new Request(2026,null,null,List.of())).summary().errorRows()).isEqualTo(1);}
    @Test void hiddenRowsIncludedUnknownColumnsSeparateEmptyRowsExcluded() throws Exception {var file=B5Workbook.file(b->{B5Workbook.row(b,1,"P-test","0001","개발","2026-06-01","2026-06-30","","100","신규");b.getSheetAt(0).getRow(1).setZeroHeight(true);b.getSheetAt(0).createRow(2);b.getSheetAt(0).getRow(0).createCell(10).setCellValue("미지열");});var result=definition.check(definition.parse(file),new Request(2026,null,null,List.of()));assertThat(result.summary().totalRows()).isEqualTo(1);assertThat(result.summary().successRows()).isEqualTo(1);assertThat(result.summary().fileWarningCount()).isEqualTo(1);}
    @Test void malformedRenamedEmptyAndDuplicateHeadersHaveContractErrors() throws Exception {assertThatThrownBy(()->definition.parse(new MockMultipartFile("file","fake.xlsx",null,new byte[]{1,2,3}))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(415));assertThatThrownBy(()->definition.parse(new MockMultipartFile("file","fake.xlsx",null,new byte[]{80,75,1,2}))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(400));assertThatThrownBy(()->definition.parse(B5Workbook.file(b->{}))).isInstanceOf(ApiException.class);assertThatThrownBy(()->definition.parse(B5Workbook.file(b->{B5Workbook.row(b,1,"x");b.getSheetAt(0).getRow(0).createCell(10).setCellValue("프로젝트\nID *");}))).isInstanceOf(ApiException.class);}
}
