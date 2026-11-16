package com.sicost.analysis;

import com.sicost.cashflowops.*;
import com.sicost.common.concurrent.RevisionService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.sicost.analysis.AnalysisAssistantDtos.*;

class AnalysisDataQueriesTest {
    @Test void operatingAndNetRatesKeepTheirCommonDecimalValuesAndPercentUnits(){
        var company=mock(com.sicost.structure.ProfitStructureRepository.class);var revisions=mock(RevisionService.class);when(revisions.revision(any())).thenReturn("unit-closed-source");
        var at=OffsetDateTime.parse("2026-11-16T10:00:00+09:00");var completeness=new com.sicost.businessplansops.BusinessPlanPerformanceDtos.Completeness(true,true,true,List.of(),List.of(),List.of(),List.of(),1,1,List.of());
        var actual=new com.sicost.businessplansops.BusinessPlanPerformanceDtos.Actual("2026-10","SAVED","CLOSE",true,false,false,Map.of("oprtPrftAmt","4876547","netPrftAmt","3625547","operatingProfitRate","48.8","netProfitRate","36.3"),completeness,List.of(),at);
        when(company.read(any())).thenReturn(new com.sicost.structure.ProfitStructureRepository.Read(null,List.of(actual),Map.of("2026-10",actual),Map.of(),List.of(),List.of()));
        var queries=new AnalysisDataQueries(company,null,null,null,null,null,null,null,null,null,mock(AnalysisAssistantMapper.class),(user,scope)->true,revisions,null,null,null,null,null);
        for(String metric:List.of("operatingProfitRate","netProfitRate")){
            String label=metric.equals("operatingProfitRate")?"영업이익률":"순이익률";var question=new Question("2026년 10월 회사 "+label,new Context("UI-26",null,null,null));var intent=new AnalysisQuestionInterpreter().interpret(question.question(),question.context(),at.toLocalDate());var result=queries.read("admin",question,intent,at);
            assertThat(result.status()).isEqualTo("OK");assertThat(result.tables().get(0).columns()).contains(new Column(metric,label,"DECIMAL","%"));assertThat(result.tables().get(0).rows().get(0).cells()).contains(new Cell(metric,actual.amounts().get(metric),null));
        }
    }
    @Test void nonTurnkeyProjectsWithoutForecastStillExposeTheirCommonActualCost(){
        for(String type:List.of("MO","DP","IN")){
            var projects=mock(com.sicost.costanalysis.ProjectCostService.class);var mapper=mock(AnalysisAssistantMapper.class);var revisions=mock(RevisionService.class);when(revisions.revision(any())).thenReturn("unit-project-source");when(mapper.projects()).thenReturn(List.of(new AnalysisAssistantMapper.Entity("QA_MO","QA 프로젝트","QA_MO")));
            var row=mock(com.sicost.costanalysis.ProjectCostDtos.Row.class);when(row.contractType()).thenReturn(type);when(row.status()).thenReturn("ING");when(row.contractAmount()).thenReturn("10000000");when(row.cumulativeCost()).thenReturn("666000");
            var completeness=new com.sicost.costanalysis.ProjectCostDtos.Completeness(true,true,false,List.of(),0,"2026-11","2026-11",null);when(projects.getProjectCostAnalysis("QA_MO","2026-11")).thenReturn(new com.sicost.costanalysis.ProjectCostDtos.Detail("QA_MO","2026-11",LocalDate.of(2026,11,30),"ING",row,null,null,"공통 원가",completeness));
            var queries=new AnalysisDataQueries(null,null,null,projects,null,null,null,null,null,null,mapper,(user,scope)->true,revisions,null,null,null,null,null);var question=new Question("이번 달 프로젝트 QA_MO 원가",new Context("UI-26",null,null,null));var at=OffsetDateTime.parse("2026-11-16T10:00:00+09:00");var result=queries.read("admin",question,new AnalysisQuestionInterpreter().interpret(question.question(),question.context(),at.toLocalDate()),at);
            assertThat(result.status()).isEqualTo("OK");assertThat(result.tables().get(0).rows().get(0).cells()).contains(new Cell("cost","666000",null));assertThat(result.unknownReasons()).anyMatch(reason->reason.contains("턴키 전용"));verify(projects).getProjectCostAnalysis("QA_MO","2026-11");
        }
    }
    @Test void preContractTurnkeyWithNormalizedInProgressRowIsNotAnEligibleForecast(){
        var projects=mock(com.sicost.costanalysis.ProjectCostService.class);var mapper=mock(AnalysisAssistantMapper.class);
        when(mapper.projects()).thenReturn(List.of(new AnalysisAssistantMapper.Entity("QA_TKPRE","QA 미계약턴키","QA_TKPRE")));
        var row=mock(com.sicost.costanalysis.ProjectCostDtos.Row.class);when(row.contractType()).thenReturn("TK");when(row.status()).thenReturn("ING");when(row.contractAmount()).thenReturn("1000000");
        var completeness=new com.sicost.costanalysis.ProjectCostDtos.Completeness(true,true,false,List.of(),0,"2026-11","2026-11",null);
        when(projects.getProjectCostAnalysis("QA_TKPRE","2026-11")).thenReturn(new com.sicost.costanalysis.ProjectCostDtos.Detail("QA_TKPRE","2026-11",LocalDate.of(2026,11,30),"PRE",row,null,null,"공통 조회",completeness));
        var queries=new AnalysisDataQueries(null,null,null,projects,null,null,null,null,null,null,mapper,(user,scope)->true,mock(RevisionService.class),null,null,null,null,null);
        var question=new Question("이번 달 턴키 QA_TKPRE 최종 마진율",new Context("UI-26",null,null,null));var at=OffsetDateTime.parse("2026-11-16T10:00:00+09:00");
        var result=queries.read("admin",question,new AnalysisQuestionInterpreter().interpret(question.question(),question.context(),at.toLocalDate()),at);
        assertThat(result.status()).isEqualTo("NO_ANS");assertThat(result.tables()).isEmpty();assertThat(result.reason()).contains("계약 전");
        verify(projects,never()).getProjectCostWeeklyProgress(anyString(),anyString());
    }
    @Test void ordinaryPersonalEvidenceDoesNotClaimAnUnappliedLossFilter(){
        var individuals=mock(com.sicost.profitability.IndividualProfitabilityService.class);var mapper=mock(AnalysisAssistantMapper.class);var revisions=mock(RevisionService.class);
        when(revisions.revision(any())).thenReturn("unit-person-source");when(mapper.people()).thenReturn(List.of(new AnalysisAssistantMapper.Entity("QA_D","QA 개발자","QA_D")));
        var snapshot=mock(com.sicost.profitability.IndividualProfitabilityService.Snapshot.class);var data=mock(com.sicost.profitability.MonthlyCostDtos.IndividualProfitabilityData.class);
        when(data.items()).thenReturn(List.of());when(individuals.snapshot(any())).thenReturn(snapshot);when(individuals.project(any(),any(),eq(true))).thenReturn(data);
        var queries=new AnalysisDataQueries(null,null,individuals,null,null,null,null,null,null,null,mapper,(user,scope)->true,revisions,null,null,null,null,null);var at=OffsetDateTime.parse("2026-11-16T10:00:00+09:00");
        for(boolean loss:List.of(false,true)){
            var question=new Question(loss?"이번 달 적자 인력":"이번 달 개인 QA_D 원가",new Context("UI-26",null,null,null));var result=queries.read("admin",question,new AnalysisQuestionInterpreter().interpret(question.question(),question.context(),at.toLocalDate()),at);
            assertThat(result.usedData().get(0).calculationBasis().contains("영업이익 < 0")).isEqualTo(loss);
        }
    }
    @Test void historicalCashForecastCannotBeRelabelledAsCurrentOrPassedToActualQueries(){
        var cash=mock(CashFlowService.class);var mapper=mock(AnalysisAssistantMapper.class);var queries=new AnalysisDataQueries(null,null,null,null,null,null,null,cash,null,null,mapper,(user,scope)->true,mock(RevisionService.class),null,null,null,null,null);
        var result=queries.read("admin",new Question("지난달 자금 전망",new Context("UI-26",null,null,null)),new AnalysisQuestionInterpreter.Intent(AnalysisQuestionInterpreter.Function.CASH_FORECAST,YearMonth.of(2026,10),YearMonth.of(2026,10),true,null,null),OffsetDateTime.parse("2026-11-16T10:00:00+09:00"));
        assertThat(result.status()).isEqualTo("NO_ANS");assertThat(result.reason()).contains("2026-11 ~ 2027-01");verifyNoInteractions(cash);
    }
    @Test void cashRangeVisitsBothAnnualServicesAndKeepsTheirValuesAndMinimumCriteria(){
        var cash=mock(CashFlowService.class);var mapper=mock(AnalysisAssistantMapper.class);var revisions=mock(RevisionService.class);when(revisions.revision(any())).thenReturn("unit-source-revision");
        var december=new CashFlowDtos.Month("2025-12","ACTUAL","100",List.of(),"10","20","90",true,false,List.of());
        var january=new CashFlowDtos.Month("2026-01","ACTUAL","90",List.of(),"20","5","105",true,false,List.of());
        when(cash.calculateCashFlow(new CashFlowDtos.Query(2025,CashFlowDtos.Unit.WON))).thenReturn(annual(2025,december));
        when(cash.calculateCashFlow(new CashFlowDtos.Query(2026,CashFlowDtos.Unit.WON))).thenReturn(annual(2026,january));
        var queries=new AnalysisDataQueries(null,null,null,null,null,null,null,cash,null,null,mapper,(user,scope)->true,revisions,null,null,null,null,null);
        var result=queries.read("admin",new Question("2025-12부터 2026-01 자금",new Context("UI-26",null,null,null)),new AnalysisQuestionInterpreter.Intent(AnalysisQuestionInterpreter.Function.CASH,YearMonth.of(2025,12),YearMonth.of(2026,1),false,null,null),OffsetDateTime.parse("2026-11-16T10:00:00+09:00"));
        assertThat(result.status()).isEqualTo("OK");assertThat(result.tables().get(0).rows()).hasSize(2);assertThat(result.tables().get(0).rows().get(1).cells()).contains(new Cell("closing","105",null));
        assertThat(result.assumptions()).anyMatch(text->text.contains("50원")&&text.contains("2026-01"));assertThat(result.usedData().get(0).queryName()).isEqualTo("CashFlowService.calculateCashFlow");
        verify(cash).calculateCashFlow(new CashFlowDtos.Query(2025,CashFlowDtos.Unit.WON));
    }
    private static CashFlowDtos.Result annual(int year,CashFlowDtos.Month month){return new CashFlowDtos.Result(year,CashFlowDtos.Unit.WON,LocalDate.of(2026,11,16),"Asia/Seoul","2025-01","100","50","2026-01",List.of(month),List.of(),List.of(),List.of(),List.of());}
}
