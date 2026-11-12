package com.sicost.b6;

import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.sicost.b6.B6Dtos.*;
import static org.assertj.core.api.Assertions.*;

class DistributionComparisonTest {
    Cell cell(String step,int row,String type,String id,String amount){return new Cell(step,row,"202606",type,type.equals("DEV")?id:null,type.equals("ITEM")?id:null,new BigDecimal(amount));}
    final List<Developer> developers=List.of(new Developer("D-1","000123","인력","Y"));
    @Test void allZeroPlanExistsAndNoCellOrDeveloperFallbackToLowerStage() {
        var rows=List.of(cell("EST",1,"DEV","D-1","100"),cell("PLAN",1,"ITEM","항목","0"));
        var c=B6Comparison.compare(DistributionWorkbook.project(),rows,List.of(),developers);
        assertThat(c.selectedStep()).isEqualTo("PLAN");assertThat(c.totalAmount()).isEqualTo("0");assertThat(c.differenceAmount()).isEqualTo("100");
        assertThat(B6Comparison.selected(rows)).hasSize(1);assertThat(B6Comparison.selected(List.of(cell("CTRT",1,"ITEM","항목","0"),cell("EST",1,"ITEM","항목","100"))).get(0).step()).isEqualTo("CTRT");
    }
    @Test void missingTableIsZeroAndOneWonPositiveOrNegativeDifferenceOnlyWarns() {
        assertThat(B6Comparison.compare(DistributionWorkbook.project(),List.of(),List.of(),developers).selectedStep()).isNull();
        for(String value:List.of("99","101")) {
            var c=B6Comparison.compare(DistributionWorkbook.project(),List.of(cell("PLAN",1,"ITEM","항목",value)),List.of(),developers);
            assertThat(c.warnings()).hasSize(1);assertThat(c.differenceAmount()).isIn("1","-1");
        }
    }
    @Test void zeroDevPresencePreventsMissingDistributionAndMultipleAssignmentsCountOncePerPersonMonthType() {
        var actuals=List.of(new Actual("D-1","000123","인력","202606",new BigDecimal("0.5")),new Actual("D-1","000123","인력","202606",new BigDecimal("0.5")));
        var zero=B6Comparison.compare(DistributionWorkbook.project(),List.of(cell("PLAN",1,"DEV","D-1","0")),actuals,developers);assertThat(zero.mismatchCount()).isZero();
        var absent=B6Comparison.compare(DistributionWorkbook.project(),List.of(cell("PLAN",1,"ITEM","항목","0")),actuals,developers);
        assertThat(absent.mismatchCount()).isEqualTo(1);assertThat(absent.items().get(0).type()).isEqualTo("NO_DISTRIBUTION");assertThat(absent.items().get(0).actualMm()).isEqualTo("1.00");
        var unassigned=B6Comparison.compare(DistributionWorkbook.project(),List.of(cell("PLAN",1,"DEV","D-1","1")),List.of(),developers);
        assertThat(unassigned.mismatchCount()).isEqualTo(1);assertThat(unassigned.items().get(0).type()).isEqualTo("NO_ASSIGNMENT");
    }
    @Test void protectedZeroOnlyPresenceAndDuplicateItemGroupedAmountsAreUnchanged() {
        var before=List.of(cell("EST",1,"DEV","D-1","0"),cell("EST",2,"ITEM","항목","3"),cell("EST",3,"ITEM","항목","7"));
        var after=B6Comparison.substitute(before,"PLAN",List.of(cell("PLAN",1,"ITEM","항목","10")));
        assertThatCode(()->B6Comparison.protect(before,after,Set.of("202606"),YearMonth.of(2026,1))).doesNotThrowAnyException();
        assertThat(after).containsAll(before);assertThat(B6Comparison.selected(after)).hasSize(1);
    }
    @Test void selectedStageChangeZeroToOneIsClosedMonthWithExactDifferences() {
        var before=List.of(cell("EST",1,"DEV","D-1","0"));var after=B6Comparison.substitute(before,"PLAN",List.of(cell("PLAN",1,"DEV","D-1","1")));
        assertThatThrownBy(()->B6Comparison.protect(before,after,Set.of("202606"),YearMonth.of(2026,1))).isInstanceOfSatisfying(ApiException.class,e->{
            assertThat(e.status()).isEqualTo(409);assertThat(e.code()).isEqualTo("CLOSED_MONTH");assertThat(e.details().toString()).contains("2026-06","difference=1");});
    }
    @Test void transferAndFixedProtectButUnprotectedMonthAndUnselectedLowerStageRemainWritable() {
        var before=List.of(cell("PLAN",1,"ITEM","항목","100"));var lower=B6Comparison.substitute(before,"EST",List.of(cell("EST",1,"ITEM","항목","999")));
        assertThatCode(()->B6Comparison.protect(before,lower,Set.of("202606"),YearMonth.of(2026,7))).doesNotThrowAnyException();
        var changed=List.of(cell("PLAN",1,"ITEM","항목","101"));
        assertThatThrownBy(()->B6Comparison.protect(before,changed,Set.of(),YearMonth.of(2026,7))).isInstanceOf(ApiException.class);
        assertThatCode(()->B6Comparison.protect(before,changed,Set.of(),YearMonth.of(2026,1))).doesNotThrowAnyException();
    }
    @Test void validationSeparatesLowerUploadedTotalFromProspectiveSelectedTableWarnings() throws Exception {
        var definition=new B6ImportDefinition(new com.sicost.b7.ExcelImportService());var file=DistributionWorkbook.valid();
        var checked=definition.validate(definition.parse(file,DistributionWorkbook.project()),DistributionWorkbook.project(),"EST",new B6ImportDefinition.B6Sources(List.of(cell("PLAN",1,"DEV","D-1","1")),List.of(),developers));
        assertThat(checked.uploadedTableTotal()).isEqualTo("100");assertThat(checked.comparison().totalAmount()).isEqualTo("1");assertThat(checked.comparison().selectedStep()).isEqualTo("PLAN");
        assertThat(checked.comparison().mismatchCount()).isEqualTo(1);assertThat(checked.summary().tableWarningCount()).isEqualTo(2);assertThat(checked.summary().warningRows()).isZero();
    }
}
