package com.sicost.imports;

import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.imports.BusinessPlanImportDtos.*;

class BusinessPlanImportDefinitionTest {
    final BusinessPlanImportDefinition definition = new BusinessPlanImportDefinition(new ExcelImportService());
    Checked check(java.util.function.Consumer<org.apache.poi.xssf.usermodel.XSSFWorkbook> change) throws Exception {
        return definition.check(definition.parse(BusinessPlanWorkbook.file(change)), 2080, Collections.emptyList(), Collections.emptyList());
    }
    @Test void allTwelveMonthsIncludingFutureAndBeforeBaseArePreservedWithoutProfitCorrection() throws Exception {
        var result = check(book -> book.getSheetAt(0).getRow(1).getCell(2).setCellValue(2000));
        assertThat(result.canApply()).isTrue(); assertThat(result.summary()).isEqualTo(new Summary(12,12,0,0,0));
        assertThat(result.changes()).hasSize(12); assertThat(result.rows().get(0).profit()).isEqualByComparingTo("2000");
        assertThat(result.rows().get(11).profit()).isEqualByComparingTo("-12");
    }
    @Test void blankTargetsWarnOnEachColumnAndHaveMonthSummary() throws Exception {
        var result = check(book -> { for (int m : List.of(3,4)) { book.getSheetAt(0).getRow(m).getCell(1).setBlank(); book.getSheetAt(0).getRow(m).getCell(2).setBlank(); } });
        assertThat(result.summary()).isEqualTo(new Summary(12,10,2,0,0)); assertThat(result.blankTargetMonths()).containsExactly("2080-03","2080-04");
        assertThat(result.warnings()).containsExactly("목표가 빈 달: 3월, 4월");
        assertThat(result.issues()).extracting(i -> i.columnName()).contains("매출 목표","영업이익 목표");
        assertThat(result.rows().get(2).sales()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.5","1000000000000000","1e2","NaN","1,00","₩10","10원","1만원","=1+1","@SUM(A1)","+bad"})
    void invalidSalesNeverSalvaged(String amount) throws Exception {
        var result = check(book -> book.getSheetAt(0).getRow(1).getCell(1).setCellValue(amount));
        assertThat(result.canApply()).isFalse(); assertThat(result.summary().errorRows()).isEqualTo(1);
        assertThat(result.issues()).anyMatch(i -> i.rowNumber()==2 && i.columnName().equals("매출 목표") && i.severity().equals("ERR"));
    }
    @ParameterizedTest @ValueSource(strings={"0","+1,000"," 999,999,999,999,999 ","0001"})
    void integerTextAndExplicitZeroAccepted(String amount) throws Exception { assertThat(check(book -> book.getSheetAt(0).getRow(1).getCell(1).setCellValue(amount)).canApply()).isTrue(); }
    @Test void signedProfitMinimumAccepted() throws Exception {
        assertThat(check(book -> book.getSheetAt(0).getRow(1).getCell(2).setCellValue("-999,999,999,999,999")).canApply()).isTrue();
    }
    @Test void missingMonthRejectsWholeFile() throws Exception {
        var result = check(book -> book.getSheetAt(0).removeRow(book.getSheetAt(0).getRow(12)));
        assertThat(result.canApply()).isFalse(); assertThat(result.summary().errorRows()).isEqualTo(11);
    }
    @Test void duplicateMonthRejectsWholeFile() throws Exception {
        var result = check(book -> book.getSheetAt(0).getRow(12).getCell(0).setCellValue("2080-11"));
        assertThat(result.canApply()).isFalse(); assertThat(result.issues()).anyMatch(i -> i.reason().contains("중복"));
    }
    @Test void otherYearIsError() throws Exception { assertThat(check(book -> book.getSheetAt(0).getRow(12).getCell(0).setCellValue("2079-12")).canApply()).isFalse(); }
    @Test void stringFullDateIsNotMonth() throws Exception { assertThat(check(book -> book.getSheetAt(0).getRow(1).getCell(0).setCellValue("2080-01-31")).canApply()).isFalse(); }
    @Test void actualExcelDateKeepsLocalMonth() throws Exception {
        var result = check(book -> { var cell = book.getSheetAt(0).getRow(1).getCell(0); cell.setCellValue(java.time.LocalDateTime.of(2080,1,31,23,59));
            var style=book.createCellStyle(); style.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd")); cell.setCellStyle(style); });
        assertThat(result.canApply()).isTrue(); assertThat(result.rows().get(0).month()).isEqualTo("2080-01");
    }
    @Test void headingsNormalizeAndMapByTitleWithHiddenRowsAndFileWarning() throws Exception {
        var result = check(book -> { var sheet=book.getSheetAt(0); var header=sheet.getRow(0);
            header.getCell(1).setCellValue(" 영업 이익\n목표 * "); header.getCell(2).setCellValue(" 매출 목표* "); header.createCell(3).setCellValue("추가열");
            for(int m=1;m<=12;m++){sheet.getRow(m).getCell(1).setCellValue(-m);sheet.getRow(m).getCell(2).setCellValue(m);} sheet.getRow(1).setZeroHeight(true); });
        assertThat(result.canApply()).isTrue(); assertThat(result.summary().fileWarningCount()).isEqualTo(1);
        assertThat(result.summary().successRows()).isEqualTo(12); assertThat(result.rows().get(0).sales()).isEqualByComparingTo("1");
    }
    @Test void missingHeaderIs400() { assertThatThrownBy(() -> check(book -> book.getSheetAt(0).getRow(0).getCell(2).setCellValue("other"))).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.status()).isEqualTo(400)); }
    @Test void duplicateHeaderIs400() { assertThatThrownBy(() -> check(book -> book.getSheetAt(0).getRow(0).createCell(3).setCellValue("매출목표*"))).isInstanceOf(ApiException.class); }
    @Test void cachedFormulaReadWithoutEvaluation() throws Exception {
        var result=check(book -> {var cell=book.getSheetAt(0).getRow(1).getCell(1);cell.setCellFormula("1+1");cell.setCellValue(17);});
        assertThat(result.rows().get(0).sales()).isEqualByComparingTo("17");
    }
    @Test void missingFormulaCacheIsError() throws Exception {
        assertThat(check(book -> {var cell=book.getSheetAt(0).getRow(1).getCell(1);cell.setCellFormula("1+1");cell.getCTCell().unsetV();}).canApply()).isFalse();
    }
    @Test void renamedTypeIs415() { assertThatThrownBy(() -> definition.parse(new MockMultipartFile("file","bad.xlsx","application/octet-stream","text".getBytes()))).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.status()).isEqualTo(415)); }
    @Test void filenamePrecheckUsesTheSameErrorContractAsParsing() {
        var tooLarge=new MockMultipartFile("file","targets.xlsx","application/octet-stream",new byte[0]) {
            @Override public long getSize() { return 10_485_761; }
        };
        assertThatThrownBy(()->definition.filename(tooLarge)).isInstanceOfSatisfying(ApiException.class,e->{
            assertThat(e.status()).isEqualTo(413);assertThat(e.code()).isEqualTo("PAYLOAD_TOO_LARGE");
        });
        assertThatThrownBy(()->definition.filename(new MockMultipartFile("file","targets.csv","text/csv",new byte[0])))
            .isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.status()).isEqualTo(415);assertThat(e.code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");});
        assertThatThrownBy(()->definition.filename(null)).isInstanceOfSatisfying(ApiException.class,e->{
            assertThat(e.status()).isEqualTo(400);assertThat(e.code()).isEqualTo("VALIDATION_ERROR");
        });
    }
    @Test void corruptZipIs400() { assertThatThrownBy(() -> definition.parse(new MockMultipartFile("file","bad.xlsx","application/octet-stream",new byte[]{'P','K',1,2,3}))).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.status()).isEqualTo(400)); }
    @Test void tooManyRowsIs413() {
        assertThatThrownBy(() -> check(book -> {var sheet=book.getSheetAt(0);for(int m=13;m<=5001;m++)sheet.createRow(m).createCell(0).setCellValue("2080-01");})).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.status()).isEqualTo(413));
    }
    @Test void emptyFileIs400() { assertThatThrownBy(() -> check(book -> {var sheet=book.getSheetAt(0);for(int m=1;m<=12;m++)sheet.removeRow(sheet.getRow(m));})).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.status()).isEqualTo(400)); }
}
