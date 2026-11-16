package com.sicost.analysis;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.analysis.AnalysisQuestionInterpreter.Function.*;

class AnalysisQuestionInterpreterTest {
    final AnalysisQuestionInterpreter parser=new AnalysisQuestionInterpreter();
    final AnalysisAssistantDtos.Context context=new AnalysisAssistantDtos.Context("UI-04","2020-01",null,null);
    final LocalDate day=LocalDate.of(2026,11,16);
    @Test void nextCalendarQuarterAndSixMonthsIgnoreOldScreenMonth(){var q=parser.interpret("다음 분기 대기 인력",context,day);assertThat(q.function()).isEqualTo(WORKFORCE);assertThat(q.from()).isEqualTo(YearMonth.of(2027,1));assertThat(q.to()).isEqualTo(YearMonth.of(2027,3));var six=parser.interpret("앞으로 6개월 매출·영업이익",context,day);assertThat(six.from()).isEqualTo(YearMonth.of(2026,12));assertThat(six.to()).isEqualTo(YearMonth.of(2027,5));}
    @Test void explicitAndRelativePeriodPrecedeQ(){var last=parser.interpret("지난해 매출",context,day);assertThat(last.from()).isEqualTo(YearMonth.of(2025,1));assertThat(last.to()).isEqualTo(YearMonth.of(2025,12));var explicit=parser.interpret("2026년 3월 매출",context,day);assertThat(explicit.from()).isEqualTo(YearMonth.of(2026,3));}
    @Test void mutationsAreNeverTurnedIntoReads(){for(String text:new String[]{"매출을 확인하고 삭제해줘","마감하고 영업이익 알려줘","delete 매출","이체할 자금","메일 발송하고 미수 알려줘"}){var q=parser.interpret(text,context,day);assertThat(q.function()).isEqualTo(UNKNOWN);assertThat(q.reason()).contains("업무 변경 명령");}}
    @Test void futureActualAndUnknownQuestionsAreNotFakeZero(){assertThat(parser.interpret("2027-01 매출",context,day).reason()).contains("미래");assertThat(parser.interpret("어때?",context,day).function()).isEqualTo(UNKNOWN);}
    @Test void overdueThresholdIsInclusiveAndLocal(){assertThat(parser.interpret("연체 7일 이상 미수",context,day).minimumOverdue()).isEqualTo(7);}
    @Test void overdueConditionIsOrderIndependentAndUnsupportedConditionsAreNeverDiscarded(){
        for(String text:java.util.List.of("60일 이상 연체 미수 목록","연체 60일 이상 미수 목록","미수 목록 연체 60일 이상")){var intent=parser.interpret(text,context,day);assertThat(intent.reason()).isNull();assertThat(intent.minimumOverdue()).isEqualTo(60);}
        for(String text:java.util.List.of("60일 이하 연체 미수","연체 60일 미수","연체 60일 이상 또는 30일 이상 미수"))assertThat(parser.interpret(text,context,day).reason()).isNotNull();
    }
    @Test void explicitMonthCountsAreAppliedExactlyOrRejectedRatherThanReplacedByDefaults(){
        for(String text:java.util.List.of("앞으로 7개월 매출 예측","앞으로 4개월 자금 전망","앞으로 7개월 인력 수급","앞으로 0개월 자금 전망","앞으로 999999999999개월 매출 전망"))assertThat(parser.interpret(text,context,day).reason()).as(text).isNotNull();
        var four=parser.interpret("앞으로 4개월 매출 전망",context,day);assertThat(four.reason()).isNull();assertThat(four.from()).isEqualTo(YearMonth.of(2026,12));assertThat(four.to()).isEqualTo(YearMonth.of(2027,3));
        assertThat(parser.interpret("앞으로 3개월 자금 전망",context,day).reason()).isNull();assertThat(parser.interpret("2026-12~2027-05 앞으로 4개월 매출 전망",context,day).reason()).isNotNull();
    }
    @Test void currentAssignmentsAndSupplyDoNotInheritAnOldScreenMonth(){for(String q:java.util.List.of("현재 투입 현황","현재 인력 수급")){var parsed=parser.interpret(q,context,day);assertThat(parsed.from()).isEqualTo(YearMonth.of(2026,11));assertThat(parsed.to()).isEqualTo(parsed.from());assertThat(parsed.reason()).isNull();}}
    @Test void explicitRelativeForecastPeriodCannotBeSilentlyReplacedByDefaultSixMonths(){
        var past=parser.interpret("지난달 매출 전망",context,day);assertThat(past.reason()).contains("예측은");assertThat(past.from()).isEqualTo(YearMonth.of(2026,10));
        var next=parser.interpret("다음 달 매출 전망",context,day);assertThat(next.reason()).isNull();assertThat(next.from()).isEqualTo(YearMonth.of(2026,12));assertThat(next.to()).isEqualTo(next.from());
        assertThat(parser.interpret("다음 달 매출",context,day).reason()).contains("미래");
        var cumulative=parser.interpret("매출 누계",context,day);assertThat(cumulative.from()).isEqualTo(YearMonth.of(2026,1));assertThat(cumulative.to()).isEqualTo(YearMonth.of(2026,11));
    }
}
