package com.sicost.withdrawalsops;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sicost.apiops.WithdrawalDtos.Source;
import com.sicost.apiops.WithdrawalPositionCalculator.Ranked;
import java.math.BigDecimal;
import java.util.*;
import static com.sicost.workforcerecommendations.WorkforceRecommendationDtos.ScoreItem;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record NextPositionScoreDetail(int v,List<BigDecimal> b,List<BigDecimal> w,List<BigDecimal> s,Evidence e) {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Evidence(String aid,String end,String st,int gap,@com.fasterxml.jackson.annotation.JsonProperty("wait") int waitDays,int left,Integer g,int cu,BigDecimal pr) {}
    private static final List<BigDecimal> WEIGHTS=List.of(new BigDecimal("40"),new BigDecimal("20"),new BigDecimal("25"),new BigDecimal("10"),new BigDecimal("5"));
    public static NextPositionScoreDetail from(Source source,Ranked rank){
        List<BigDecimal> scores=List.of(BigDecimal.valueOf(rank.jobScore()),BigDecimal.valueOf(rank.gradeScore()),BigDecimal.valueOf(rank.gapScore()),BigDecimal.valueOf(rank.customerScore()),BigDecimal.valueOf(rank.probabilityScore()));
        List<String> grades=List.of("JR","MD","SR","EX");String grade=rank.position().gradeCode()==null||rank.position().gradeCode().isBlank()?"MD":rank.position().gradeCode();
        Integer distance=source.gradeCode()==null||!grades.contains(source.gradeCode())||!grades.contains(grade)?null:Math.abs(grades.indexOf(source.gradeCode())-grades.indexOf(grade));
        return new NextPositionScoreDetail(1,scores,WEIGHTS,scores,new Evidence(source.assignmentId(),source.withdrawalDate().toString(),rank.position().startMonth(),rank.gap(),rank.waitDays(),rank.position().remainingCount(),distance,rank.customerScore()==10?1:0,rank.position().probability()));
    }
    public List<ScoreItem> items(){
        List<String> codes=List.of("JOB","GRADE","GAP","CUST","PROB");
        List<String> basis=List.of(s.get(0).intValue()==40?"주직무 일치":"자리 직무의 관련 주직무",e.g()==null?"인력 등급 미확인":"등급 거리 "+e.g(),"공백 "+e.gap()+"개월",e.cu()==1?"원철수 프로젝트 고객 일치":"원철수 프로젝트 고객 다름","저장 수주확률 "+e.pr().toPlainString()+"%");
        List<ScoreItem> result=new ArrayList<>();for(int i=0;i<5;i++)result.add(new ScoreItem(codes.get(i),b.get(i).toPlainString(),w.get(i).toPlainString(),s.get(i).toPlainString(),basis.get(i)));return List.copyOf(result);
    }
    public String reason(){return String.join(" · ",items().stream().map(ScoreItem::basis).toList());}
}
