package com.sicost.b1;

import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.sicost.b1.B1Dtos.*;
import static org.assertj.core.api.Assertions.*;

class B1ImportTest {
    final B1ImportDefinition definition=new B1ImportDefinition(new ExcelImportService());
    final List<Rate> rates=List.of(new Rate("ALLOW_TEAM",new BigDecimal("10")),new Rate("ALLOW_GROUP",new BigDecimal("20")),new Rate("SEV_RATE",new BigDecimal("8.3333")),new Rate("INS_RATE",new BigDecimal("10")));
    Developer developer(String type,String position) { return new Developer("D-test","0001","현재이름",type,"INDRCT",position); }
    Checked check(String type,String position,String... values) throws Exception {
        return definition.check(definition.parse(B1Workbook.file(b->B1Workbook.row(b,1,values))),List.of(developer(type,position)),rates);
    }
    @Test void ftUsesParenthesizedRetirementExplicitZeroInsuranceAndNegativeNet() throws Exception {
        var value=check("FT","TEAM","0001","","100","0","200","1","").rows().get(0).values();
        assertThat(value.companyInsurance()).isEqualByComparingTo("0");assertThat(value.allowance()).isEqualByComparingTo("10");
        assertThat(value.retirement()).isEqualByComparingTo("9");assertThat(value.realPay()).isEqualByComparingTo("-91");
    }
    @Test void ctWithholdingUsesSalaryAndAllowanceWithoutRetirement() throws Exception {
        var value=check("CT","GROUP","0001","","100","","2","3","").rows().get(0).values();
        assertThat(value.companyInsurance()).isEqualByComparingTo("10");assertThat(value.withholding()).isEqualByComparingTo("3");assertThat(value.retirement()).isZero();assertThat(value.realPay()).isEqualByComparingTo("112");
    }
    @Test void flDiscardsInvalidInsuranceAndSupplyWithoutParsingThem() throws Exception {
        var result=check("FL","TEAM","0001","","100","=bad","invalid","1","-bad");var value=result.rows().get(0).values();
        assertThat(result.summary().errorRows()).isZero();assertThat(result.summary().warningRows()).isEqualTo(1);assertThat(value.allowance()).isZero();assertThat(value.companyInsurance()).isZero();assertThat(value.selfInsurance()).isZero();assertThat(value.withholding()).isEqualByComparingTo("3");assertThat(value.realPay()).isEqualByComparingTo("96");
    }
    @Test void osDiscardsInvalidSalaryTaxesInsuranceAndKeepsNullNet() throws Exception {
        var result=check("OS","TEAM","0001","","bad","bad","bad","bad","1,000원");var value=result.rows().get(0).values();
        assertThat(result.summary().errorRows()).isZero();assertThat(result.summary().warningRows()).isEqualTo(1);assertThat(value.realPay()).isNull();assertThat(value.vendorSupply()).isEqualByComparingTo("1000");assertThat(value.pay()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"","UNKNOWN"}) void unknownEmployeeTypeIsErrNotCoerced(String type) throws Exception {
        assertThat(check(type,null,"0001").summary().errorRows()).isEqualTo(1);
    }
    @Test void nullEmployeeTypeIsErr() throws Exception { assertThat(check(null,null,"0001").summary().errorRows()).isEqualTo(1); }
    @Test void duplicateDevFlagsBothRowsAndErrorWinsOverWarning() throws Exception {
        var parsed=definition.parse(B1Workbook.file(b->{B1Workbook.row(b,1,"0001","","100","0","0","0","ignored");B1Workbook.row(b,2,"0001");}));
        var result=definition.check(parsed,List.of(developer("FT",null)),rates);
        assertThat(result.summary().errorRows()).isEqualTo(2);assertThat(result.summary().warningRows()).isZero();assertThat(result.summary().successRows()).isZero();
    }
    @Test void nonblankNameMustEqualCurrentNameButBlankIsAllowed() throws Exception {
        assertThat(check("FT",null,"0001","wrong").summary().errorRows()).isEqualTo(1);assertThat(check("FT",null,"0001","").summary().successRows()).isEqualTo(1);
    }
    @Test void unregisteredManagementNumberIsErr() throws Exception { assertThat(check("FT",null,"missing").summary().errorRows()).isEqualTo(1); }
    @Test void requiredRatesHaveNoFallback() throws Exception {
        var parsed=definition.parse(B1Workbook.file(b->B1Workbook.row(b,1,"0001")));
        assertThat(definition.check(parsed,List.of(developer("FT","TEAM")),Collections.emptyList()).summary().errorRows()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"-1","1.5","1e2","1000000000000000"}) void invalidAppliedAmountsAreErr(String amount) throws Exception {
        assertThat(check("FT",null,"0001","",amount).summary().errorRows()).isEqualTo(1);
    }
    @Test void derivedOverflowIsErr() throws Exception { assertThat(check("FT","TEAM","0001","","999999999999999").summary().errorRows()).isEqualTo(1); }
    @Test void hiddenRowsIncludedUnknownColumnsWarnSeparatelyAndEmptyRowsExcluded() throws Exception {
        var file=B1Workbook.file(b->{b.getSheetAt(0).getRow(0).createCell(7).setCellValue("미지열");B1Workbook.row(b,1,"0001");b.getSheetAt(0).getRow(1).setZeroHeight(true);b.getSheetAt(0).createRow(2);});
        var result=definition.check(definition.parse(file),List.of(developer("FL",null)),rates);
        assertThat(result.summary().totalRows()).isEqualTo(1);assertThat(result.summary().successRows()).isEqualTo(1);assertThat(result.summary().fileWarningCount()).isEqualTo(1);
    }
    @Test void formulaWithoutCacheIsNeverEvaluatedAndNumericLongIdentifierIsErr() throws Exception {
        var file=B1Workbook.file(b->{B1Workbook.row(b,1,"0001");b.getSheetAt(0).getRow(1).createCell(2).setCellFormula("1+2");var cell=b.getSheetAt(0).getRow(1).getCell(2).getCTCell();if(cell.isSetV())cell.unsetV();});
        assertThat(definition.check(definition.parse(file),List.of(developer("FL",null)),rates).summary().errorRows()).isEqualTo(1);
        var id=B1Workbook.file(b->{B1Workbook.row(b,1,"0001");b.getSheetAt(0).getRow(1).getCell(0).setCellValue(1234567890123456d);});
        assertThat(definition.check(definition.parse(id),List.of(developer("FL",null)),rates).summary().errorRows()).isEqualTo(1);
    }
    @Test void malformedRenamedAndEmptyWorkbooksHaveContractErrors() throws Exception {
        assertThatThrownBy(()->definition.parse(new org.springframework.mock.web.MockMultipartFile("file","fake.xlsx",null,new byte[]{1,2,3}))).isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.status()).isEqualTo(415);assertThat(e.code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");});
        assertThatThrownBy(()->definition.parse(B1Workbook.file(b->{}))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("INVALID_INPUT"));
    }
    @Test void duplicateNormalizedHeadersAre400AndColumnOrderIsFree() throws Exception {
        var duplicate=B1Workbook.file(b->{b.getSheetAt(0).getRow(0).createCell(7).setCellValue(" 관리\n번호 *");B1Workbook.row(b,1,"0001");});
        assertThatThrownBy(()->definition.parse(duplicate)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(400));
        var reordered=B1Workbook.file(b->{var h=b.getSheetAt(0).getRow(0);h.getCell(0).setCellValue("급여");h.getCell(2).setCellValue("관리번호*");B1Workbook.row(b,1,"100","","0001");});
        assertThat(definition.check(definition.parse(reordered),List.of(developer("FL",null)),rates).summary().successRows()).isEqualTo(1);
    }
}
