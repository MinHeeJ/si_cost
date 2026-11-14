package com.sicost.migration;

import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import static com.sicost.migration.HistoricalProfitLossImportDefinition.*;
import static org.assertj.core.api.Assertions.*;

class HistoricalProfitLossImportDefinitionTest {
    final HistoricalProfitLossImportDefinition definition=new HistoricalProfitLossImportDefinition(new ExcelImportService());
    HistoricalProfitLossImportDtos.Checked check(MockMultipartFile file)throws Exception {return definition.check(definition.parse(file),"208007",List.of());}
    @Test void januaryAndDecemberBaseHaveDifferentAllowedRanges() {
        assertThat(months("202601")).hasSize(12).startsWith("2025-01").endsWith("2025-12");
        assertThat(months("202612")).hasSize(23).startsWith("2025-01").endsWith("2026-11");
    }
    @Test void oneMonthAndRequiredZeroDoNotDeriveOptionalValues()throws Exception {
        var checked=check(HistoricalProfitLossWorkbook.file(b->{for(int i=1;i<=4;i++)b.getSheetAt(0).getRow(1).getCell(i).setCellValue(0);}));
        assertThat(checked.canApply()).isTrue();assertThat(checked.summary().successRows()).isEqualTo(1);
        assertThat(checked.rows().get(0).amounts().get("salesAmt")).isZero();assertThat(checked.rows().get(0).amounts().get("grossPrftAmt")).isNull();
        assertThat(checked.changes().get(0).after()).containsEntry("grossPrftAmt",null).containsEntry("grossProfitRate",null);
    }
    @Test void previewRatesUseTheSameDenominatorsAndSignsAsCompanyProfitLoss() {
        Map<String,java.math.BigDecimal> amounts=new LinkedHashMap<>();
        FIELDS.forEach(k->amounts.put(k,null));
        amounts.put("salesAmt",new java.math.BigDecimal("-200"));
        amounts.put("salesCostAmt",new java.math.BigDecimal("80"));
        amounts.put("idleCostAmt",new java.math.BigDecimal("20"));
        amounts.put("oprtPrftAmt",new java.math.BigDecimal("-50"));
        assertThat(wire(amounts)).containsEntry("operatingProfitRate","25.0").containsEntry("idleCostShare","25.0");
        amounts.put("salesCostAmt",java.math.BigDecimal.ZERO);
        amounts.put("salesAmt",java.math.BigDecimal.ZERO);
        assertThat(wire(amounts)).containsEntry("operatingProfitRate",null).containsEntry("idleCostShare",null);
    }
    @ParameterizedTest @ValueSource(strings={"","–","1.1","1000000000000000","₩1","1원","1만원","1e3","1,00","NaN","=1+1","@SUM(1)","+bad"})
    void invalidRequiredMoneyIsRowError(String input)throws Exception {
        var result=check(HistoricalProfitLossWorkbook.file(b->b.getSheetAt(0).getRow(1).getCell(1).setCellValue(input)));
        assertThat(result.canApply()).isFalse();assertThat(result.summary().errorRows()).isEqualTo(1);
        assertThat(result.issues()).anyMatch(i->i.rowNumber()==2&&i.columnName().equals("매출")&&i.severity().equals("ERR"));
    }
    @ParameterizedTest @ValueSource(strings={"-999999999999999","+999,999,999,999,999","  -1,234  ","0"})
    void signedIntegersPreserveRawAmount(String input)throws Exception {
        var result=check(HistoricalProfitLossWorkbook.file(b->b.getSheetAt(0).getRow(1).getCell(1).setCellValue(input)));
        assertThat(result.canApply()).isTrue();assertThat(result.rows().get(0).amounts().get("salesAmt")).isEqualByComparingTo(input.strip().replace(",",""));
    }
    @Test void absentOptionalHeadingsAndEnDashAreNull()throws Exception {
        var result=check(HistoricalProfitLossWorkbook.file(b->{var s=b.getSheetAt(0);s.getRow(1).getCell(5).setCellValue("–");for(int i=6;i<17;i++)s.getRow(0).removeCell(s.getRow(0).getCell(i));}));
        assertThat(result.canApply()).isTrue();assertThat(result.rows().get(0).amounts().get("allocCostAmt")).isNull();assertThat(result.rows().get(0).amounts().get("netPrftAmt")).isNull();
    }
    @Test void onlyCompleteFormulaDifferencesWarnWithoutCorrection()throws Exception {
        var result=check(HistoricalProfitLossWorkbook.file(b->b.getSheetAt(0).getRow(1).getCell(8).setCellValue(900)));
        assertThat(result.canApply()).isTrue();assertThat(result.summary().warningRows()).isEqualTo(1);assertThat(result.summary().errorRows()).isZero();
        assertThat(result.rows().get(0).amounts().get("grossPrftAmt")).isEqualByComparingTo("900");
        assertThat(result.issues()).anyMatch(i->i.columnName().equals("매출총이익")&&i.severity().equals("WARN"));
    }
    @ParameterizedTest @ValueSource(strings={"2080-07","2078-12","2080-13","2080-02-30","2080-1",""})
    void operatingMonthOutOfRangeAndInvalidDatesFail(String input)throws Exception {
        assertThat(check(HistoricalProfitLossWorkbook.file(b->b.getSheetAt(0).getRow(1).getCell(0).setCellValue(input))).canApply()).isFalse();
    }
    @Test void duplicateMonthsMarkBothRowsAndNeverPartialApply()throws Exception {
        var result=check(HistoricalProfitLossWorkbook.file(b->{var r=b.getSheetAt(0).createRow(2);r.createCell(0).setCellValue("2079-01");for(int i=1;i<=4;i++)r.createCell(i).setCellValue(0);}));
        assertThat(result.summary().totalRows()).isEqualTo(2);assertThat(result.summary().errorRows()).isEqualTo(2);assertThat(result.changes()).isEmpty();
    }
    @Test void headerMappingNormalizesAndIgnoresOrder()throws Exception {
        var result=check(HistoricalProfitLossWorkbook.file(b->{var s=b.getSheetAt(0);s.getRow(0).getCell(1).setCellValue("영업\n이익 *");s.getRow(0).getCell(4).setCellValue(" 매 출 * ");}));
        assertThat(result.canApply()).isTrue();assertThat(result.rows().get(0).amounts().get("salesAmt")).isEqualByComparingTo("300");
    }
    @Test void missingRequiredHeaderFailsFile()throws Exception {
        var file=HistoricalProfitLossWorkbook.file(b->b.getSheetAt(0).getRow(0).getCell(4).setCellValue("unknown"));
        assertThatThrownBy(()->definition.parse(file)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(400));
    }
    @Test void duplicateHeaderFailsFile()throws Exception {
        var file=HistoricalProfitLossWorkbook.file(b->b.getSheetAt(0).getRow(0).createCell(17).setCellValue("매출*"));
        assertThatThrownBy(()->definition.parse(file)).isInstanceOf(ApiException.class);
    }
    @Test void unknownColumnsWarnAtFileLevelAndHiddenRowsStillCount()throws Exception {
        var result=check(HistoricalProfitLossWorkbook.file(b->{b.getSheetAt(0).getRow(0).createCell(17).setCellValue("미정의");b.getSheetAt(0).getRow(1).setZeroHeight(true);}));
        assertThat(result.summary().totalRows()).isEqualTo(1);assertThat(result.summary().fileWarningCount()).isEqualTo(1);assertThat(result.summary().successRows()).isEqualTo(1);
    }
    @Test void realExcelDateUsesLocalMonth()throws Exception {
        var result=check(HistoricalProfitLossWorkbook.file(b->{var c=b.getSheetAt(0).getRow(1).getCell(0);c.setCellValue(java.time.LocalDateTime.of(2079,2,1,0,0));var style=b.createCellStyle();style.setDataFormat(b.createDataFormat().getFormat("yyyy-mm-dd"));c.setCellStyle(style);}));
        assertThat(result.rows().get(0).month()).isEqualTo("2079-02");
    }
    @Test void formulaCacheMissingIsErrorAndFormulaNeverEvaluated()throws Exception {
        var result=check(HistoricalProfitLossWorkbook.file(b->{var c=(org.apache.poi.xssf.usermodel.XSSFCell)b.getSheetAt(0).getRow(1).getCell(1);c.setCellFormula("1+1");c.getCTCell().unsetV();}));
        assertThat(result.canApply()).isFalse();assertThat(result.issues()).anyMatch(i->i.reason().contains("계산값"));
    }
    @Test void formulaCachedValueIsReadWithoutExecutingFormula()throws Exception {
        var result=check(HistoricalProfitLossWorkbook.file(b->{var c=b.getSheetAt(0).getRow(1).getCell(1);c.setCellFormula("1+1");((org.apache.poi.xssf.usermodel.XSSFCell)c).getCTCell().setV("99");}));
        assertThat(result.rows().get(0).amounts().get("salesAmt")).isEqualByComparingTo("99");
    }
    @Test void renamedTextIs415AndBrokenZipIs400() {
        assertThatThrownBy(()->definition.parse(new MockMultipartFile("file","bad.xlsx","application/octet-stream","plain".getBytes()))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(415));
        assertThatThrownBy(()->definition.parse(new MockMultipartFile("file","bad.xlsx","application/octet-stream",new byte[]{'P','K',0,0}))).isInstanceOf(ApiException.class);
    }
    @Test void emptyRowsAreNotZeroBusinessRows()throws Exception {
        var result=check(HistoricalProfitLossWorkbook.file(b->{for(int i=1;i<17;i++)b.getSheetAt(0).getRow(1).getCell(i).setBlank();}));
        assertThat(result.canApply()).isFalse();assertThat(result.summary().errorRows()).isEqualTo(1);
    }
    @Test void fiveThousandRowLimitIs413()throws Exception {
        var file=HistoricalProfitLossWorkbook.file(b->{for(int r=2;r<=5001;r++)b.getSheetAt(0).createRow(r).createCell(0).setCellValue("2079-01");});
        assertThatThrownBy(()->definition.parse(file)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(413));
    }
}
