package com.sicost.b6;

import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.ApiException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import static com.sicost.b6.B6Dtos.*;
import static org.assertj.core.api.Assertions.*;

class DistributionImportTest {
    final B6ImportDefinition definition=new B6ImportDefinition(new ExcelImportService());
    final Project project=DistributionWorkbook.project();
    Checked check(MockMultipartFile file) {return definition.validate(definition.parse(file,project),project,"PLAN",new B6ImportDefinition.B6Sources(List.of(),List.of(),List.of(new Developer("D-1","000123","현재 이름","Y"))));}
    @Test void validTableRetainsEveryMonthZeroAndSequentialRowIdentityNoTotalCell() throws Exception {
        var checked=check(DistributionWorkbook.file(b->{DistributionWorkbook.item(b,1,"같은 항목","100","");DistributionWorkbook.item(b,3,"같은 항목","0","1");b.getSheetAt(0).getRow(3).setZeroHeight(true);}));
        assertThat(checked.cells()).hasSize(4);assertThat(checked.cells()).extracting(Cell::rowSn).containsExactly(1,1,2,2);
        assertThat(checked.cells()).extracting(Cell::month).containsExactly("202606","202607","202606","202607");
        assertThat(checked.cells().get(1).amount()).isZero();assertThat(checked.uploadedTableTotal()).isEqualTo("101");assertThat(checked.summary().successRows()).isEqualTo(2);
    }
    @Test void actualManagementNumberAndCurrentNameIdentifyDeveloperNotId() throws Exception {
        var good=check(DistributionWorkbook.file(b->DistributionWorkbook.person(b,1,"000123","현재 이름")));
        assertThat(good.cells().get(0).developerId()).isEqualTo("D-1");assertThat(good.summary().errorRows()).isZero();
        var id=check(DistributionWorkbook.file(b->DistributionWorkbook.person(b,1,"D-1","현재 이름")));assertThat(id.summary().errorRows()).isEqualTo(1);
        var old=check(DistributionWorkbook.file(b->DistributionWorkbook.person(b,1,"000123","이전 이름")));assertThat(old.summary().errorRows()).isEqualTo(1);
    }
    @Test void stoppedDeveloperIsAllowedWithVisibleWarningAndDiscardedItem() throws Exception {
        var file=DistributionWorkbook.file(b->{DistributionWorkbook.person(b,1,"000123","현재 이름");b.getSheetAt(0).getRow(1).getCell(3).setCellValue("폐기");});
        var checked=definition.validate(definition.parse(file,project),project,"EST",new B6ImportDefinition.B6Sources(List.of(),List.of(),List.of(new Developer("D-1","000123","현재 이름","N"))));
        assertThat(checked.summary().warningRows()).isEqualTo(1);assertThat(checked.summary().errorRows()).isZero();assertThat(checked.cells()).allSatisfy(c->assertThat(c.itemName()).isNull());
        assertThat(checked.issues()).extracting(com.sicost.b7.B7Dtos.Issue::reason).anyMatch(s->s.contains("중지"));
    }
    @Test void duplicateDeveloperMarksEveryImplicatedRowAndErrorOverridesWarnings() throws Exception {
        var c=check(DistributionWorkbook.file(b->{for(int i=1;i<=3;i++)DistributionWorkbook.person(b,i,"000123","현재 이름");b.getSheetAt(0).getRow(1).getCell(3).setCellValue("폐기");}));
        assertThat(c.summary().errorRows()).isEqualTo(3);assertThat(c.summary().warningRows()).isZero();assertThat(c.summary().successRows()).isZero();
        assertThat(c.issues()).filteredOn(i->i.reason().contains("중복")).extracting(com.sicost.b7.B7Dtos.Issue::rowNumber).containsExactly(2,3,4);
    }
    @Test void itemRequiresOneToFiftyCharactersAndDiscardsPersonFieldsWithoutTruncation() throws Exception {
        var c=check(DistributionWorkbook.file(b->{DistributionWorkbook.item(b,1,"가".repeat(51),"1","0");var row=b.getSheetAt(0).getRow(1);row.getCell(1).setCellValue("forged");row.getCell(2).setCellValue("forged");}));
        assertThat(c.summary().errorRows()).isEqualTo(1);assertThat(c.cells().get(0).itemName()).hasSize(51);assertThat(c.cells().get(0).developerId()).isNull();
        assertThat(c.issues()).filteredOn(i->i.severity().equals("WARN")).hasSize(2);
    }
    @ParameterizedTest @ValueSource(strings={"-1","0.1","1e3","1%","1000000000000000","bad"})
    void invalidMonthAmountsNeverRoundOrClamp(String amount) throws Exception {var c=check(DistributionWorkbook.file(b->DistributionWorkbook.item(b,1,"항목",amount,"0")));assertThat(c.summary().errorRows()).isEqualTo(1);}
    @Test void numericFormattingCannotHideFractionAndIntegerNumericCacheIsAccepted() throws Exception {
        var file=DistributionWorkbook.file(b->{DistributionWorkbook.item(b,1,"항목","0","0");var cell=b.getSheetAt(0).getRow(1).getCell(4);cell.setCellValue(1.1);var style=b.createCellStyle();style.setDataFormat(b.createDataFormat().getFormat("0"));cell.setCellStyle(style);});
        assertThat(check(file).summary().errorRows()).isEqualTo(1);
        assertThat(check(DistributionWorkbook.file(b->{DistributionWorkbook.item(b,1,"항목","0","0");b.getSheetAt(0).getRow(1).getCell(4).setCellValue(100);})).summary().errorRows()).isZero();
    }
    @Test void totalsBeyondCellMaximumRemainExactAndInputTotalMismatchIsWarningOnly() throws Exception {
        var c=check(DistributionWorkbook.file(b->{DistributionWorkbook.item(b,1,"항목","999999999999999","999999999999999");b.getSheetAt(0).getRow(1).getCell(6).setCellValue("0");}));
        assertThat(c.uploadedTableTotal()).isEqualTo("1999999999999998");assertThat(c.summary().errorRows()).isZero();assertThat(c.summary().warningRows()).isEqualTo(1);
        assertThat(c.summary().tableWarningCount()).isEqualTo(1);assertThat(c.cells()).hasSize(2);
    }
    @Test void suppliedTotalUsesSameIntegerValidation() throws Exception {
        var c=check(DistributionWorkbook.file(b->{DistributionWorkbook.item(b,1,"항목","100","0");b.getSheetAt(0).getRow(1).getCell(6).setCellValue("-1");}));assertThat(c.summary().errorRows()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"missing","duplicate","outside","malformed"})
    void missingDuplicateOutsideMalformedMonthTitlesAreFile400(String mode) throws Exception {
        var f=DistributionWorkbook.file(b->{DistributionWorkbook.item(b,1,"항목","100","0");var row=b.getSheetAt(0).getRow(0);
            switch(mode){case "missing"->row.removeCell(row.getCell(4));case "duplicate"->row.createCell(7).setCellValue("2026-06");case "outside"->row.createCell(7).setCellValue("2026-08");default->row.createCell(7).setCellValue("2026-6");}});
        assertThatThrownBy(()->check(f)).isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.status()).isEqualTo(400);assertThat(e.code()).isEqualTo("INVALID_INPUT");assertThat(e.field()).isEqualTo("file");});
    }
    @Test void reorderedColumnsWorkAndUnknownNonMonthColumnIsSeparateWarning() throws Exception {
        var f=DistributionWorkbook.file(b->{DistributionWorkbook.item(b,1,"항목","100","0");var s=b.getSheetAt(0);s.getRow(0).getCell(4).setCellValue("2026-07");s.getRow(0).getCell(5).setCellValue("2026-06");s.getRow(0).createCell(7).setCellValue("메모");});
        var c=check(f);assertThat(c.cells().get(1).amount()).isEqualByComparingTo("100");assertThat(c.summary().fileWarningCount()).isEqualTo(1);assertThat(c.summary().warningRows()).isZero();
    }
    @Test void formulasOnlyUseCacheAndNoCacheBecomesRowErrorWithoutExternalExecution() throws Exception {
        var f=DistributionWorkbook.file(b->{DistributionWorkbook.item(b,1,"항목","0","0");var cell=b.getSheetAt(0).getRow(1).getCell(4);cell.setCellFormula("WEBSERVICE(\"https://invalid.test\")");if(cell.getCTCell().isSetV())cell.getCTCell().unsetV();});
        assertThat(check(f).issues()).anyMatch(i->i.severity().equals("ERR")&&i.reason().contains("계산값"));
    }
    @Test void diagnosisExcerptsDoNotTruncateBusinessValue() throws Exception {
        var c=check(DistributionWorkbook.file(b->DistributionWorkbook.item(b,1,"가".repeat(400),"100","0")));
        assertThat(c.cells().get(0).itemName()).hasSize(400);assertThat(c.issues()).allSatisfy(i->{assertThat(i.inputValue().length()).isLessThanOrEqualTo(200);assertThat(i.reason().length()).isLessThanOrEqualTo(300);});
    }
}
