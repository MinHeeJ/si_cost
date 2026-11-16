package com.sicost.analysis;

import java.time.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;
import static com.sicost.analysis.AnalysisAssistantDtos.*;

@Component
public class AnalysisQuestionInterpreter {
    public enum Function { COMPANY, PERSONAL, PROJECT, SETTLEMENT, BILLING, RECEIVABLE, ASSIGNMENTS, OPPORTUNITIES, WORKFORCE, REVENUE_FORECAST, CASH, CASH_FORECAST, TURNKEY, UNKNOWN }
    public record Intent(Function function,YearMonth from,YearMonth to,boolean prediction,Integer minimumOverdue,String reason) {}
    private static final Pattern CHANGE=Pattern.compile("삭제|수정|등록해|등록하|저장해|저장하|배정해|배정하|마감해|마감하|정산해|정산하|청구해|입금처리|입금해|이체|발송|보내줘|보내 줘|실행|ignore|delete|update|insert|drop|truncate|execute",Pattern.CASE_INSENSITIVE);
    public Intent interpret(String question,Context context,LocalDate day) {
        String q=question.replaceAll("\\s+","");YearMonth m=YearMonth.from(day),from=m,to=m;
        if(CHANGE.matcher(q).find())return new Intent(Function.UNKNOWN,m,m,false,null,"업무 변경 명령은 실행하지 않습니다. 조회할 대상과 지표를 질문해 주세요.");
        Function f=Function.UNKNOWN;
        if(q.matches(".*(무엇|뭐야|정의|개념|용어뜻).*"))return new Intent(f,m,m,false,null,"용어 설명은 업무자료 조회와 구분합니다. 조회할 지표와 기간을 명확히 지정해 주세요.");
        if(q.contains("턴키")&&(q.contains("마진")||q.contains("이익률")))f=Function.TURNKEY;
        else if(q.contains("자금")||q.contains("현금")||q.contains("기말잔액"))f=q.matches(".*(앞으로|예측|전망|3개월).*" )?Function.CASH_FORECAST:Function.CASH;
        else if(q.contains("미수")||q.contains("연체"))f=Function.RECEIVABLE;
        else if(q.contains("청구")||q.contains("입금"))f=Function.BILLING;
        else if(q.contains("영업기회")||q.contains("파이프라인")||q.contains("진행기회")||q.contains("수주확률"))f=Function.OPPORTUNITIES;
        else if(q.contains("현재투입")||q.contains("배정현황")||q.contains("투입현황"))f=Function.ASSIGNMENTS;
        else if(q.contains("대기")||q.contains("수급")||q.contains("과부족")||q.contains("인력수요"))f=Function.WORKFORCE;
        else if((q.contains("적자인력")||q.contains("개인")||q.contains("개발자"))&&q.matches(".*(원가|수익|영업이익|매출|적자|비용|이익).*"))f=Function.PERSONAL;
        else if(q.contains("프로젝트")&&q.matches(".*(원가|비용|진척|진행률|예산|마진|수익|매출|이익).*"))f=Function.PROJECT;
        else if(q.contains("파트")&&q.contains("정산"))f=Function.SETTLEMENT;
        else if(q.matches(".*(매출|영업이익|매출원가|총이익|판관비|순이익|회사손익|회사실적).*"))f=q.matches(".*(앞으로|예측|전망|예상|6개월|다음분기).*" )?Function.REVENUE_FORECAST:Function.COMPANY;
        boolean prediction=Set.of(Function.TURNKEY,Function.CASH_FORECAST,Function.WORKFORCE,Function.REVENUE_FORECAST).contains(f);
        if(f==Function.WORKFORCE&&q.matches(".*(현재|이번달|지난달|선택월).*"))prediction=false;
        Integer requestedMonths=null;
        if(prediction&&Set.of(Function.REVENUE_FORECAST,Function.WORKFORCE,Function.CASH_FORECAST).contains(f)) {
            Matcher counts=Pattern.compile("(?<![0-9])([0-9]+)개월").matcher(q);
            while(counts.find()) {
                int count;try{count=Integer.parseInt(counts.group(1));}catch(NumberFormatException e){return new Intent(f,m,m,true,null,"지원 범위를 넘는 개월 수입니다. 매출·인력은 다음 6개월 이내, 자금은 3개월로 다시 지정해 주세요.");}
                if(count<1||count>(f==Function.CASH_FORECAST?3:6)||f==Function.CASH_FORECAST&&count!=3||requestedMonths!=null&&requestedMonths!=count||q.contains("다음분기")&&count!=3)return new Intent(f,m,m,true,null,"명시한 개월 수는 지원하지 않습니다. 매출·인력은 다음 6개월 이내 또는 다음 달력분기 3개월, 자금은 3개월로 다시 지정해 주세요.");
                requestedMonths=count;
            }
            if(q.contains("개월")&&requestedMonths==null)return new Intent(f,m,m,true,null,"예측 기간의 개월 수를 숫자로 명확히 지정해 주세요.");
        }
        if((!prediction||f==Function.TURNKEY)&&context.baseMonth()!=null)from=to=YearMonth.parse(context.baseMonth());
        if(prediction&&f!=Function.TURNKEY){from=m.plusMonths(f==Function.CASH_FORECAST?0:1);to=from.plusMonths(f==Function.CASH_FORECAST?2:5);}
        if(requestedMonths!=null)to=from.plusMonths(requestedMonths-1);
        if(q.contains("현재")&&Set.of(Function.ASSIGNMENTS,Function.WORKFORCE).contains(f))from=to=m;
        if(q.contains("이번달"))from=to=m;
        else if(q.contains("지난달"))from=to=m.minusMonths(1);
        else if(q.contains("다음달"))from=to=m.plusMonths(1);
        else if(q.contains("지난해")){from=YearMonth.of(m.getYear()-1,1);to=YearMonth.of(m.getYear()-1,12);}
        else if(q.contains("올해")||q.contains("누계")){from=YearMonth.of(m.getYear(),1);to=m;}
        if(q.contains("다음분기")){from=YearMonth.of(m.getYear(),((m.getMonthValue()-1)/3)*3+1).plusMonths(3);to=from.plusMonths(2);}
        Matcher dates=Pattern.compile("(?<![0-9])([1-9][0-9]{3})[-./년]\\s*(0?[1-9]|1[0-2])(?:월)?(?![0-9])").matcher(question);
        List<YearMonth> explicit=new ArrayList<>();while(dates.find())explicit.add(YearMonth.of(Integer.parseInt(dates.group(1)),Integer.parseInt(dates.group(2))));
        if(!explicit.isEmpty()){from=explicit.get(0);to=explicit.get(explicit.size()-1);}
        if(requestedMonths!=null&&java.time.temporal.ChronoUnit.MONTHS.between(from,to)+1!=requestedMonths)return new Intent(f,from,to,prediction,null,"명시한 날짜 범위와 개월 수가 서로 다릅니다. 기간을 다시 지정해 주세요.");
        if(Set.of(Function.REVENUE_FORECAST,Function.WORKFORCE).contains(f)&&prediction&&(from.isBefore(m.plusMonths(1))||to.isAfter(m.plusMonths(6))))return new Intent(f,from,to,true,null,"예측은 현재월 다음 달부터 6개월 또는 다음 달력 분기의 범위만 지원합니다. 지원 기간을 다시 지정해 주세요.");
        if(f==Function.WORKFORCE&&!prediction&&(!from.equals(m)||!to.equals(m)))return new Intent(f,from,to,false,null,"현재 수급은 현재월 기준이며 과거 수급 사본을 복원하지 않습니다. 현재월 또는 앞으로 6개월 전망을 질문해 주세요.");
        if(Set.of(Function.RECEIVABLE,Function.OPPORTUNITIES).contains(f)) {
            if((!explicit.isEmpty()||q.matches(".*(지난달|지난해|올해).*"))&&(!from.equals(m)||!to.equals(m)))return new Intent(f,from,to,false,null,"미수·진행 기회는 오늘 현재 자료만 지원합니다. 과거 당시 상태를 복원하지 않습니다.");
            from=to=m;
        }
        if(q.matches(".*[0-9]+(원|만원|억원|천원|백만원|%)?(이상|이하|초과|미만).*"))return new Intent(f,from,to,prediction,null,"금액·확률 조건은 아직 자동 적용하지 않습니다. 원래 화면에서 명시 단위와 범위를 선택해 주세요.");
        if(to.isBefore(from)||java.time.temporal.ChronoUnit.MONTHS.between(from,to)>23)return new Intent(f,from,to,prediction,null,"기간을 최대 24개월 이내의 순서로 다시 지정해 주세요.");
        Integer min=null;
        if(f==Function.RECEIVABLE&&q.contains("연체")) {
            Matcher overdue=Pattern.compile("(?<![0-9])([0-9]+)일이상").matcher(q);
            while(overdue.find()) {
                int days;try{days=Integer.parseInt(overdue.group(1));}catch(NumberFormatException e){return new Intent(f,from,to,prediction,null,"연체일은 0~99999일 이상의 조건으로 다시 지정해 주세요.");}
                if(days>99999||min!=null&&min!=days)return new Intent(f,from,to,prediction,null,"연체일 조건을 하나의 명확한 일수 이상으로 다시 지정해 주세요.");
                min=days;
            }
            if(q.matches(".*[0-9]+일.*")&&(min==null||q.matches(".*[0-9]+일(이하|초과|미만).*")))return new Intent(f,from,to,prediction,null,"지원하는 연체일 조건은 '연체 60일 이상' 또는 '60일 이상 연체'입니다. 조건을 다시 지정해 주세요.");
        }
        if(f==Function.UNKNOWN)return new Intent(f,from,to,false,min,"지원하는 조회 지표·대상·기간을 명확히 지정해 주세요. 원인이나 신용 확률은 근거 없이 판단하지 않습니다.");
        if(!prediction&&f!=Function.RECEIVABLE&&to.isAfter(m))return new Intent(f,from,to,false,min,"미래 월 실제 실적은 조회할 수 없습니다. 현재월 이전 실적 또는 예측을 질문해 주세요.");
        return new Intent(f,from,to,prediction,min,null);
    }
}
