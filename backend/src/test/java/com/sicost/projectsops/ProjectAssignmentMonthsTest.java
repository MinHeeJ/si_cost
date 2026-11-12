package com.sicost.projectsops;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.sicost.projectsops.ProjectStatusDtos.*;
import static org.assertj.core.api.Assertions.*;

class ProjectAssignmentMonthsTest {
    final YearMonth month=YearMonth.of(2028,2);
    ValueHistory h(int serial,String type,String date,String mm,String price,String corrected,Integer target) {
        return new ValueHistory(serial,type,LocalDate.parse(date),mm==null?null:new BigDecimal(mm),price==null?null:new BigDecimal(price),corrected,target);
    }
    @Test void inclusiveIntersectionAndLeapMonthUseHalfUpTwo() {
        int days=ProjectAssignmentMonths.days(month,LocalDate.of(2028,2,15),LocalDate.of(2028,3,20));
        assertThat(days).isEqualTo(15);assertThat(ProjectAssignmentMonths.mm(BigDecimal.ONE,days,month)).isEqualByComparingTo("0.52");
        assertThat(ProjectAssignmentMonths.days(month,LocalDate.of(2028,3,1),LocalDate.of(2028,3,2))).isZero();
    }
    @Test void latestEffectiveDateAndSerialWinIndependentlyForMmAndPrice() {
        var histories=List.of(h(1,"INPUT","2028-01-01","1","100","N",null),h(2,"RATE","2028-02-01","2",null,"N",null),
            h(3,"RATE","2028-02-01","3",null,"N",null),h(4,"UPRC","2028-02-01",null,"200","N",null),h(5,"RATE","2028-02-02","9",null,"N",null));
        assertThat(ProjectAssignmentMonths.value(histories,month,true,BigDecimal.ZERO)).isEqualByComparingTo("3");
        assertThat(ProjectAssignmentMonths.value(histories,month,false,BigDecimal.ZERO)).isEqualByComparingTo("200");
    }
    @Test void correctionRowsRemoveOriginalButDoNotSupplyReplacementValues() {
        var histories=List.of(h(1,"INPUT","2028-01-01","1","100","N",null),h(2,"RATE","2028-02-01","3",null,"N",null),h(3,"CRCT","2028-02-01","9","900","Y",2));
        assertThat(ProjectAssignmentMonths.value(histories,month,true,BigDecimal.ZERO)).isEqualByComparingTo("1");
    }
    @Test void missingPriorValueUsesEarliestUncorrectedInputThenAssignmentFallback() {
        var histories=List.of(h(1,"INPUT","2028-02-15","2","300","N",null),h(2,"INPUT","2028-03-15","3","400","N",null));
        assertThat(ProjectAssignmentMonths.value(histories,month,true,BigDecimal.ONE)).isEqualByComparingTo("2");
        assertThat(ProjectAssignmentMonths.value(List.of(),month,false,new BigDecimal("123"))).isEqualByComparingTo("123");
        assertThat(ProjectAssignmentMonths.value(List.of(),month,false,null)).isNull();
    }
    @Test void historyMemoFitsTwoHundredCodePointsAndKeepsLossMeaning() {
        String memo=ProjectAssignmentMonths.eventMemo("가".repeat(500));assertThat(memo.codePointCount(0,memo.length())).isEqualTo(200);assertThat(memo).startsWith("프로젝트 실주: ");
    }
}
