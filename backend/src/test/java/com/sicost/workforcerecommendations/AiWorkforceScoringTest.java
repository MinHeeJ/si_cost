package com.sicost.workforcerecommendations;

import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sicost.common.assignments.AssignmentMonthlyService.Source;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.workforcerecommendations.WorkforceRecommendationDtos.*;
import static com.sicost.workforcerecommendations.AiWorkforceScoring.*;

class AiWorkforceScoringTest {
    final AiWorkforceScoring scoring=new AiWorkforceScoring();
    @ParameterizedTest @ValueSource(strings={"서울시", "서울특별시", "서울"})
    void locationNormalizesOfficialShortAndShortPlusCityNames(String province) {
        var result=calculate(person("BE",null,"MD"),context(null,province+" 중구",List.of(),List.of(),codes()));
        assertThat(result.detail().e().l()).isEqualTo("S");
        assertThat(result.detail().b().get(7)).isEqualByComparingTo("10");
        assertThat(result.detail().s().get(7)).isEqualByComparingTo("10");
    }
    final List<BigDecimal> weights=List.of(25,20,15,10,5,5,10,10).stream().map(BigDecimal::valueOf).toList();
    final LocalDate day=LocalDate.of(2026,7,15);
    Person person(String job,String sub,String grade){return new Person("D-1","이름","01",job,sub,grade,"FT","Y","DRCT","WORK",null,null,"11","서울시 강남구");}
    List<Code> codes(){return List.of(new Code("JOB","BE","백엔드",1," FE, FE, INVALID, "),new Code("JOB","FE","프론트",2,null),new Code("JOB","PM","관리",3,"BE"),new Code("GRADE","JR","초급",1,null),new Code("GRADE","MD","중급",2,null),new Code("GRADE","SR","고급",3,null),new Code("GRADE","EX","특급",4,null));}
    Context context(String feature,String address,List<Skill> skills,List<History> history,List<Code> codes){return new Context(new Condition("PROJECT","P-1","BE","2026-08","MD",null),new Target("P-1","MO","C-1","IT",address,feature,null,null,"ING"),day,codes,skills,List.of(new Area("11","서울","서울특별시")),history);}
    Calculation calculate(Person p,Context c){return scoring.calculate(p,c,weights,new Labor("202607",null,false),null);}
    History history(String id,LocalDate start,LocalDate end,int ext,boolean hr,String possibility,String customer,String industry){return new History(new Assignment(id,"D-1",customer,industry,"ING","MO",hr),new Source(id,"P-2","D-1","BE",start,end,new BigDecimal("0.5"),null,"NEW",null,possibility,ext,"N","ACT"));}
    @Test void exactPrimarySecondaryRelatedAndDirectionalOther(){var c=context(null,null,List.of(),List.of(),codes());assertThat(calculate(person("BE",null,"MD"),c).detail().b().get(0)).isEqualByComparingTo("25");assertThat(calculate(person("PM","BE","MD"),c).detail().e().j()).isEqualTo("S");assertThat(calculate(person("FE",null,"MD"),c).detail().b().get(0)).isEqualByComparingTo("12");assertThat(calculate(person("PM",null,"MD"),c).detail().b().get(0)).isEqualByComparingTo("3");}
    @Test void normalizedSkillNamesDeduplicateAndFeatureUsesOwnedNamesNotCodes(){var list=new ArrayList<>(codes());list.add(new Code("SKILL","JAVA_CODE","Ｊａｖａ",1,"BE"));list.add(new Code("SKILL","JAVA_OTHER"," java ",2,"BE"));var c=context("JAVA 기반",null,List.of(new Skill("D-1"," java "),new Skill("D-1","ＪＡＶＡ")),List.of(),list);var d=calculate(person("BE",null,"MD"),c).detail();assertThat(d.e().sk()).containsExactly(1,1,1);assertThat(d.b().get(1)).isEqualByComparingTo("20");}
    @Test void noRequiredSkillsStillAllowsFeatureFour(){var d=calculate(person("BE",null,"MD"),context("Java",null,List.of(new Skill("D-1","JAVA")),List.of(),codes())).detail();assertThat(d.b().get(1)).isEqualByComparingTo("4");assertThat(calculate(person("BE",null,"MD"),context(null,null,List.of(),List.of(),codes())).detail().b().get(1)).isZero();}
    @Test void unknownGradeIsNotAdjacent(){var c=context(null,null,List.of(),List.of(),codes());assertThat(calculate(person("BE",null,null),c).detail().b().get(3)).isEqualByComparingTo("2");assertThat(calculate(person("BE",null,"JR"),c).detail().b().get(3)).isEqualByComparingTo("5");assertThat(calculate(person("BE",null,"MD"),c).detail().b().get(3)).isEqualByComparingTo("10");}
    @Test void customerExperienceExcludesFutureReservationsAndEmptyIndustry(){var future=history("A-1",day.plusDays(1),day.plusMonths(3),0,false,null,"C-1",null);assertThat(calculate(person("BE",null,"MD"),context(null,null,List.of(),List.of(future),codes())).detail().b().get(2)).isEqualByComparingTo("6");var same=history("A-2",day.minusMonths(1),day.plusMonths(1),0,false,null,"C-1",null);assertThat(calculate(person("BE",null,"MD"),context(null,null,List.of(),List.of(same),codes())).detail().b().get(2)).isEqualByComparingTo("15");var industry=history("A-3",day.minusMonths(1),day.plusMonths(1),0,false,null,"C-2","IT");assertThat(calculate(person("BE",null,"MD"),context(null,null,List.of(),List.of(industry),codes())).detail().b().get(2)).isEqualByComparingTo("9");}
    @Test void performanceCountsAssignmentsNotExtensionsAndClamps(){var extended=history("A-1",day.minusMonths(3),day.minusMonths(1),3,false,null,null,null);var c=context(null,null,List.of(),List.of(extended),codes());var d=calculate(person("BE",null,"MD"),c).detail();assertThat(d.e().p()).containsExactly(1,1,0);assertThat(d.b().get(4)).isEqualByComparingTo("5");var hr=history("A-2",day.minusMonths(3),day.minusMonths(1),0,true,null,null,null);assertThat(calculate(person("BE",null,"MD"),context(null,null,List.of(),List.of(hr),codes())).detail().b().get(4)).isZero();assertThat(calculate(person("BE",null,"MD"),context(null,null,List.of(),List.of(),codes())).detail().b().get(4)).isEqualByComparingTo("3");}
    @Test void costKeepsKnownZeroAndUnknownSeparateAndUsesCurrentMonth(){var p=person("BE",null,"MD");var c=context(null,null,List.of(),List.of(),codes());var d=scoring.calculate(p,c,weights,new Labor("202607","60",true),new BigDecimal("100")).detail();assertThat(d.b().get(5)).isEqualByComparingTo("5");assertThat(d.m()).isEqualTo("202607");assertThat(d.f()).isEqualTo(1);assertThat(scoring.calculate(p,c,weights,new Labor("202607","0",false),new BigDecimal("100")).detail().b().get(5)).isEqualByComparingTo("5");assertThat(scoring.calculate(p,c,weights,new Labor("202607",null,false),new BigDecimal("100")).detail().b().get(5)).isZero();assertThat(scoring.calculate(p,c,weights,new Labor("202607","60",false),BigDecimal.ZERO).detail().b().get(5)).isZero();}
    @Test void availabilityUsesWholeCurrentAssignmentLatestEndAndMixedTie(){var p=person("BE",null,"MD");var old=history("A-1",day.minusMonths(1),day.plusDays(1),0,false,"H",null,null);assertThat(calculate(p,context(null,null,List.of(),List.of(old),codes())).detail().b().get(6)).isEqualByComparingTo("10");var low=history("A-2",day.minusMonths(1),LocalDate.of(2026,8,10),0,false,"L",null,null);assertThat(calculate(p,context(null,null,List.of(),List.of(low),codes())).detail().b().get(6)).isEqualByComparingTo("6");var medium=history("A-3",day.minusMonths(1),LocalDate.of(2026,8,10),0,false,"M",null,null);assertThat(calculate(p,context(null,null,List.of(),List.of(low,medium),codes())).detail().b().get(6)).isEqualByComparingTo("2");}
    @Test void locationUsesOfficialAndShortProvinceButNotRemoteWord(){var p=person("BE",null,"MD");assertThat(calculate(p,context(null,"서울특별시 강남구",List.of(),List.of(),codes())).detail().b().get(7)).isEqualByComparingTo("10");assertThat(calculate(p,context(null,null,List.of(),List.of(),codes())).detail().b().get(7)).isEqualByComparingTo("5");assertThat(calculate(p,context(null,"재택",List.of(),List.of(),codes())).detail().b().get(7)).isEqualByComparingTo("6");}
    @Test void fractionalWeightsPreserveRawScoresAndRoundTheTotalOnce() {
        var w=List.of("24.4444","20.5556","14.1234","10.8766","4.5555","5.4445","9.1234","10.8766").stream().map(BigDecimal::new).toList();
        var codes=new ArrayList<>(codes());
        for(int i=1;i<=5;i++)codes.add(new Code("SKILL","SK"+i,"skill"+i,i,"BE"));
        var h=history("A-1",day.minusMonths(1),LocalDate.of(2026,8,10),0,false,"L","C-2","IT");
        var c=context("skill1", "판별불가",List.of(new Skill("D-1","skill1")),List.of(h),codes);
        var result=scoring.calculate(person("FE",null,"JR"),c,w,new Labor("202607","70",false),new BigDecimal("100"));
        assertThat(result.detail().b()).usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("12"),new BigDecimal("7"),new BigDecimal("9"),new BigDecimal("5"),new BigDecimal("3"),new BigDecimal("4"),new BigDecimal("6"),new BigDecimal("6"));
        assertThat(result.detail().s()).usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("11.733312"),new BigDecimal("7.19446"),new BigDecimal("8.47404"),new BigDecimal("5.4383"),new BigDecimal("2.7333"),new BigDecimal("4.3556"),new BigDecimal("5.47404"),new BigDecimal("6.52596"));
        assertThat(result.totalScore()).isEqualTo("51.9");
    }
    @Test void costHalfUpBoundaryAndClampUseExactLaborRatio() {
        var c=context(null,null,List.of(),List.of(),codes());var p=person("BE",null,"MD");
        for(String[] row:new String[][]{{"649999","5"},{"650000","5"},{"650001","4"},{"1000000","1"},{"2000000","1"}})
            assertThat(scoring.calculate(p,c,weights,new Labor("202607",row[0],false),new BigDecimal("1000000")).detail().b().get(5)).isEqualByComparingTo(row[1]);
    }
    @Test void scalingPreservesSixDecimalsZeroWeightAndOnlyRoundsTotal(){var w=List.of(new BigDecimal("0"),new BigDecimal("45"),new BigDecimal("15"),new BigDecimal("10"),new BigDecimal("5"),new BigDecimal("5"),new BigDecimal("10"),new BigDecimal("10"));var d=scoring.calculate(person("BE",null,"MD"),context(null,null,List.of(),List.of(),codes()),w,new Labor("202607",null,false),null).detail();assertThat(d.s().get(0)).isZero();assertThat(scoring.items(d).get(0).weight()).isEqualTo("0");assertThat(scoring.items(d).get(0).score()).isEqualTo("0");}
}
