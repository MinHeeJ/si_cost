package com.sicost.withdrawalsops;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.apiops.WithdrawalDtos.*;
import com.sicost.apiops.WithdrawalPositionCalculator;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.workforcerecommendations.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NextPositionScoringTest {
    final LocalDate day=LocalDate.of(2026,11,15);
    Source person(String grade,String employee){return new Source("A-001","D-001","인력","001","BE","FE",grade,employee,"P-001","원프로젝트","BE",LocalDate.of(2026,11,20),0,false,null,"ING","Y","DRCT","IDLE",day.minusYears(1),null,"C-001");}
    Position position(String id,int serial,String month){return new Position(id,serial,"BE","MD",month,12,1,"MO",null,"C-001",new BigDecimal("60"));}
    NextPositionScoreDetail detail(Source source,Position position){return NextPositionScoreDetail.from(source,WithdrawalPositionCalculator.calculate(source,day,List.of(position),List.of()).get(0));}
    @ParameterizedTest @CsvSource({"2026-12,0,25","2027-01,1,15","2027-02,2,8","2027-03,3,3"}) void calendarGapsHaveFixedScores(String month,int gap,int score){var d=detail(person("MD","FT"),position("O-1",1,month));assertThat(d.e().gap()).isEqualTo(gap);assertThat(d.s().get(2)).isEqualByComparingTo(Integer.toString(score));}
    @Test void gapFourExcluded(){assertThat(WithdrawalPositionCalculator.calculate(person("MD","FT"),day,List.of(position("O-1",1,"2027-04")),List.of())).isEmpty();}
    @Test void alreadyStartedValidSlotHasZeroGapAndNoWait(){var d=detail(person("MD","FT"),position("O-1",1,"2026-10"));assertThat(d.e().gap()).isZero();assertThat(d.e().waitDays()).isZero();}
    @ParameterizedTest @CsvSource({"2026-12-14,2026-12,0,2027-01", "2026-12-14,2027-01,17,2027-01", "2026-11-20,2026-12,10,2026-12"})
    void formula81UsesOriginalSlotStartWithoutChangingActualStartOrGapScore(LocalDate withdrawal,String month,int expectedWait,String expectedActual) {
        var s=person("MD","FT");
        var source=new Source(s.assignmentId(),s.developerId(),s.name(),s.managementNumber(),s.developerJob(),s.subJobCode(),s.gradeCode(),s.employeeTypeCode(),s.projectId(),s.projectName(),s.jobCode(),withdrawal,0,false,null,s.projectStatus(),s.developerUse(),s.directClass(),s.workStatus(),s.joinDate(),s.retireDate(),s.customerId());
        var result=WithdrawalPositionCalculator.calculate(source,day,List.of(position("O-1",1,month)),List.of()).get(0);
        assertThat(result.waitDays()).isEqualTo(expectedWait);
        assertThat(result.actualStart()).isEqualTo(YearMonth.parse(expectedActual));
        assertThat(result.gap()).isZero();
        assertThat(result.gapScore()).isEqualTo(25);
        assertThat(result.total()).isEqualTo(100);
    }
    @Test void expiredSlotExcluded(){var p=new Position("O-1",1,"BE","MD","2026-10",1,1,"MO",null,"C-001",new BigDecimal("60"));assertThat(WithdrawalPositionCalculator.calculate(person("MD","FT"),day,List.of(p),List.of())).isEmpty();}
    @Test void monthEndWithdrawalHasNoWait(){var s=person("MD","FT");var end=new Source(s.assignmentId(),s.developerId(),s.name(),s.managementNumber(),s.developerJob(),s.subJobCode(),s.gradeCode(),s.employeeTypeCode(),s.projectId(),s.projectName(),s.jobCode(),LocalDate.of(2026,11,30),0,false,null,s.projectStatus(),s.developerUse(),s.directClass(),s.workStatus(),s.joinDate(),s.retireDate(),s.customerId());assertThat(detail(end,position("O-1",1,"2026-12")).e().waitDays()).isZero();}
    @Test void primaryJobScoresFortyDirectionalRelatedTwentyAuxiliaryOnlyExcluded(){var s=person("MD","FT");assertThat(detail(s,position("O-1",1,"2026-12")).s().get(0)).isEqualByComparingTo("40");var related=new Position("O-2",1,"PL","MD","2026-12",12,1,"MO","BE, FE","C-001",new BigDecimal("60"));assertThat(detail(s,related).s().get(0)).isEqualByComparingTo("20");var auxiliary=new Position("O-3",1,"FE","MD","2026-12",12,1,"MO",null,"C-001",new BigDecimal("60"));assertThat(WithdrawalPositionCalculator.calculate(s,day,List.of(auxiliary),List.of())).isEmpty();}
    @ParameterizedTest @CsvSource(value={"MD:20","JR:10","EX:0","NULL:0"},delimiter=':') void gradeDistanceScores(String grade,int points){var d=detail(person(grade.equals("NULL")?null:grade,"FT"),position("O-1",1,"2026-12"));assertThat(d.s().get(1)).isEqualByComparingTo(Integer.toString(points));}
    @ParameterizedTest @NullAndEmptySource void blankSlotGradeMeansMiddle(String grade){var p=new Position("O-1",1,"BE",grade,"2026-12",12,1,"MO",null,"C-001",new BigDecimal("60"));assertThat(detail(person("MD","FT"),p).s().get(1)).isEqualByComparingTo("20");}
    @ParameterizedTest @NullAndEmptySource void snapshotUsesFixedPointsAndTheSameDefaultGrade(String grade){var p=new Position("O-1",1,"BE",grade,"2026-12",12,1,"MO",null,"C-001",new BigDecimal("60"));var d=detail(person("MD","FT"),p);assertThat(d.b()).isEqualTo(d.s());assertThat(d.e().g()).isZero();assertThat(d.b()).containsExactly(new BigDecimal("40"),new BigDecimal("20"),new BigDecimal("25"),new BigDecimal("10"),new BigDecimal("5"));}
    @Test void otherCustomerExperienceDoesNotReceiveOriginalCustomerPoints(){var p=new Position("O-1",1,"BE","MD","2026-12",12,1,"MO",null,"C-other",new BigDecimal("60"));assertThat(detail(person("MD","FT"),p).s().get(3)).isEqualByComparingTo("0");}
    @ParameterizedTest @CsvSource({"59.9,0","60,5"}) void probabilityUsesStoredValueNotRoundedDisplay(String probability,int score){var p=new Position("O-1",1,"BE","MD","2026-12",12,1,"MO",null,"C-001",new BigDecimal(probability));assertThat(detail(person("MD","FT"),p).s().get(4)).isEqualByComparingTo(Integer.toString(score));}
    @ParameterizedTest @ValueSource(strings={"FL","OS"}) void dispatchExcludesFreelanceAndOutsource(String employee){var p=new Position("O-1",1,"BE","MD","2026-12",12,1,"DP",null,"C-001",new BigDecimal("60"));assertThat(WithdrawalPositionCalculator.calculate(person("MD",employee),day,List.of(p),List.of())).isEmpty();}
    @Test void capacityAndStartMonthLeaveExcludeSlots(){var s=person("MD","FT");var full=new Position("O-1",1,"BE","MD","2026-12",12,0,"MO",null,"C-001",new BigDecimal("60"));assertThat(WithdrawalPositionCalculator.calculate(s,day,List.of(full),List.of())).isEmpty();assertThat(WithdrawalPositionCalculator.calculate(s,day,List.of(position("O-1",1,"2026-12")),List.of(new Leave(s.developerId(),LocalDate.of(2026,12,1),LocalDate.of(2026,12,31))))).isEmpty();}
    @Test void topFiveUsesOriginalStartThenNumericCompositeKey(){var s=person("MD","FT");var top=WithdrawalPositionCalculator.calculate(s,day,List.of(position("10",1,"2026-12"),position("2",2,"2026-12"),position("2",1,"2026-12"),position("4",1,"2026-10"),position("5",1,"2026-12"),position("6",1,"2026-12")),List.of());assertThat(top).hasSize(5);assertThat(top).extracting(r->r.position().opportunityId()+":"+r.position().needSn()).containsExactly("4:1","2:1","2:2","5:1","6:1");}
    @Test void compactExactV1RejectsWidthRatherThanTruncating() throws Exception {var repo=new WorkforceRecommendationRepository(mock(WorkforceRecommendationMapper.class),JsonMapper.builder().build(),mock(ChangeHistoryWriter.class),mock(RevisionService.class),mock(SettlementSourceLocks.class),mock(CommitInvalidation.class));var d=detail(person(null,"FT"),position("O-1",1,"2026-12"));String encoded=repo.encode(d,d.reason());var value=repo.decode(encoded);assertThat(value.path("e").has("wait")).isTrue();assertThat(value.path("e").path("g").isNull()).isTrue();assertThat(value.path("b").get(0).isNumber()).isTrue();assertThat(value.path("w").toString()).isEqualTo("[40,20,25,10,5]");assertThat(encoded.length()).isLessThanOrEqualTo(500);assertThatThrownBy(()->repo.encode(Map.of("e","x".repeat(501)),"이유")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("SCORE_DETAIL_TOO_LONG"));assertThatThrownBy(()->repo.encode(d,"가".repeat(301))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("REASON_TOO_LONG"));}
    @Test void realBoundSqlAllowsOnlyNextInsertAndMetadataUpdate() throws Exception {
        Configuration config=new Configuration();for(String file:List.of("mappers/withdrawals/NextPositionMapper.xml","mappers/assignmentsops/AssignmentActionsMapper.xml")){try(var input=getClass().getClassLoader().getResourceAsStream(file)){new XMLMapperBuilder(input,config,file,config.getSqlFragments()).parse();}}
        var s=person("MD","FT");var d=detail(s,position("O-1",1,"2026-12"));var row=new WorkforceRecommendationDtos.Row("123",1,"NEXT","O-1","1",null,"BE","202612","MD",s.developerId(),"100","{}",d.reason(),null,null,null,null,"admin",LocalDateTime.of(2026,11,15,0,0));
        PersistenceBoundary.requireAllowed(config.getMappedStatement("com.sicost.withdrawalsops.NextPositionMapper.insert").getBoundSql(row).getSql());
        var source=new com.sicost.common.assignments.AssignmentMonthlyService.Source("A-1","P-1","D-1","BE",day,day.plusMonths(1),BigDecimal.ONE,null,"NEW",null,null,0,"N","ACT");
        var write=new com.sicost.assignmentsops.AssignmentActionsDtos.Write(source,"admin",LocalDateTime.of(2026,11,15,0,0));
        String sql=config.getMappedStatement("com.sicost.assignmentsops.AssignmentActionsMapper.updateExtension").getBoundSql(write).getSql();PersistenceBoundary.requireAllowed(sql);assertThat(sql).doesNotContain("input_end_dt=","ext_cnt=","ctrt_mm=");
        for(String invalid:List.of("DELETE FROM tb_sl_rcmd_log WHERE rcmd_id=?","UPDATE tb_sl_rcmd_log SET score_dtl=? WHERE rcmd_id=? AND rank_ord=?","UPDATE tb_sl_rcmd_log SET user_act_cd=?,rcmd_excl_cd=?,act_user_id=?,act_dttm=? WHERE rcmd_id=CAST(? AS numeric) AND rank_ord=?"))assertThatThrownBy(()->PersistenceBoundary.requireAllowed(invalid)).isInstanceOf(IllegalStateException.class);
    }
}
