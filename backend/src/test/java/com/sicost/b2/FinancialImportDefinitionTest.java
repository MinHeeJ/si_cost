package com.sicost.b2;

import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.ApiException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import static com.sicost.b2.FinancialImportDtos.*;
import static org.assertj.core.api.Assertions.*;

class FinancialImportDefinitionTest {
    final FinancialImportDefinition definition=new FinancialImportDefinition(new ExcelImportService());
    final List<Code> codes=List.of(new Code("FIN_CLS","SGA","판관비",null),new Code("SGA_ITEM","RENT","임대료","월세"));
    Checked check(List<List<String>> rows)throws Exception { return definition.check(definition.parse(FinancialWorkbook.file(rows)),codes,3,4); }
    @Test void zeroBlankVatLowercaseRepeatAndDuplicateRowsRemainIndependent()throws Exception {
        var row=List.of("판관비","월세","선택명과 다른 실제 항목","0","","","n","");var checked=check(List.of(row,row));
        assertThat(checked.summary()).isEqualTo(new Summary(2,2,0,0,3,4));assertThat(checked.rows()).hasSize(2);
        var value=checked.rows().get(0).value();assertThat(value.sgaItemCode()).isEqualTo("RENT");assertThat(value.itemName()).isEqualTo("선택명과 다른 실제 항목");assertThat(value.amount()).isZero();assertThat(value.vatAmount()).isZero();assertThat(value.ioDate()).isNull();assertThat(value.repeat()).isEqualTo("N");
    }
    @ParameterizedTest @ValueSource(strings={"","1.5","1e2","-1","1000000000000000","ABC"}) void invalidAmountIsCheckedError(String value)throws Exception { assertThat(check(List.of(List.of("판관비","","입력",value))).summary().errorRows()).isEqualTo(1); }
    @Test void sumAboveMaximumIsError()throws Exception { assertThat(check(List.of(List.of("판관비","","입력","999999999999999","1"))).summary().errorRows()).isEqualTo(1); }
    @ParameterizedTest @ValueSource(strings={"2026-02-30","0000-01-01","2026/06/01"}) void invalidCalendarDateIsError(String value)throws Exception { assertThat(check(List.of(List.of("판관비","","입력","1","",value))).summary().errorRows()).isEqualTo(1); }
    @Test void invalidRepeatIsError()throws Exception { assertThat(check(List.of(List.of("판관비","","입력","1","","","yes"))).summary().errorRows()).isEqualTo(1); }
    @Test void requiredNameAndRemarkLengthAreCheckedWithoutTruncation()throws Exception { assertThat(check(List.of(List.of("판관비","","","1"))).summary().errorRows()).isEqualTo(1);assertThat(check(List.of(List.of("판관비","","입력","1","","","N","한".repeat(501)))).summary().errorRows()).isEqualTo(1); }
    @Test void ambiguousExplicitMappingIsErrorNotGuessed()throws Exception {
        var all=new ArrayList<>(codes);all.add(new Code("SGA_ITEM","OTHER","다른 항목","월세"));
        var result=definition.check(definition.parse(FinancialWorkbook.file(List.of(List.of("판관비","월세","입력","1")))),all,0,0);assertThat(result.summary().errorRows()).isEqualTo(1);
    }
    @Test void missingRequiredHeaderIs400()throws Exception { assertThatThrownBy(()->mutated(book->book.getSheetAt(0).getRow(0).getCell(3).setCellValue("다른열"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(400)); }
    @Test void duplicateRequiredHeaderIs400()throws Exception { assertThatThrownBy(()->mutated(book->book.getSheetAt(0).getRow(0).createCell(8).setCellValue("금액*"))).isInstanceOf(ApiException.class); }
    @Test void unknownHeaderIsFileWarningNotErrorAndHiddenRowsIncluded()throws Exception {
        var parsed=mutated(book->{book.getSheetAt(0).getRow(0).createCell(8).setCellValue("알수없는열");book.getSheetAt(0).getRow(1).setZeroHeight(true);});
        var checked=definition.check(parsed,codes,0,0);assertThat(checked.summary().errorRows()).isZero();assertThat(checked.summary().successRows()).isEqualTo(1);assertThat(checked.issues()).anyMatch(i->i.severity().equals("WARN")&&i.columnName().equals("[파일]"));
    }
    @Test void formulaWithoutCacheAndCellErrorsAreNeverExecuted()throws Exception {
        var parsed=mutated(book->{var cell=book.getSheetAt(0).getRow(1).getCell(3);cell.setCellFormula("1+1");((org.apache.poi.xssf.usermodel.XSSFCell)cell).getCTCell().unsetV();});
        assertThat(definition.check(parsed,codes,0,0).summary().errorRows()).isEqualTo(1);
    }
    @Test void cachedFormulaIsReadWithoutEvaluation()throws Exception {
        var parsed=mutated(book->{var cell=book.getSheetAt(0).getRow(1).getCell(3);cell.setCellFormula("1+1");cell.setCellValue(5);});
        var checked=definition.check(parsed,codes,0,0);assertThat(checked.rows().get(0).value().amount()).isEqualByComparingTo("5");
    }
    @Test void tooManyRowsIs413()throws Exception { var rows=new ArrayList<List<String>>();for(int i=0;i<5001;i++)rows.add(List.of("판관비","","입력","1"));var file=FinancialWorkbook.file(rows);assertThatThrownBy(()->definition.parse(file)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(413)); }
    com.sicost.b7.B7Dtos.Parsed mutated(java.util.function.Consumer<XSSFWorkbook> change)throws Exception {
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(FinancialWorkbook.valid().getBytes()));var out=new ByteArrayOutputStream()) { change.accept(book);book.write(out);return definition.parse(new MockMultipartFile("file","financial.xlsx","application/octet-stream",out.toByteArray())); }
    }
}
