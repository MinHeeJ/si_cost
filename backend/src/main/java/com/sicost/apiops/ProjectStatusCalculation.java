package com.sicost.apiops;

import com.sicost.profitability.MonthlyCostDtos.Project;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.sicost.apiops.ProjectAnalysisDtos.*;
import static com.sicost.apiops.ProjectCostCalculation.*;

@Component
public class ProjectStatusCalculation {
    public Map<String,Object> row(Snapshot snapshot,Project project,Map<String,Object> cost,LocalDate today) {
        String id=project.projectId();String month=YearMonth.from(today).toString();
        var stages=snapshot.stages().stream().filter(s->id.equals(s.projectId())).sorted(Comparator.comparingInt(Stage::stepSn)).toList();
        var expected=snapshot.templates().stream().filter(t->t.contractTypes()!=null&&Arrays.asList(t.contractTypes().split(",")).contains(project.contractType())).map(StageTemplate::code).toList();
        boolean valid=validStages(project,stages)&&!expected.isEmpty()&&stages.size()==expected.size();
        Fraction plan=new Fraction(BigDecimal.ZERO,BigDecimal.ONE);
        if(valid)for(var stage:stages)plan=plan.add(portion(stage.startDate(),stage.endDate(),today,stage.weight()));
        else plan=portion(project.fromDate(),project.toDate(),today,BigDecimal.valueOf(100));
        BigDecimal p=plan.display(1,RoundingMode.HALF_UP);
        var memos=snapshot.memos().stream().filter(m->id.equals(m.projectId())).toList();
        BigDecimal a=Set.of("MO","DP").contains(project.contractType())?p:memos.stream().filter(m->!m.writtenDate().isAfter(today)&&m.actualProgress()!=null)
            .max(Comparator.comparing(Memo::writtenDate).thenComparingInt(Memo::memoSn)).map(Memo::actualProgress).orElse(BigDecimal.ZERO).setScale(1,RoundingMode.HALF_UP);
        boolean tk="TK".equals(project.contractType());
        BigDecimal usage=tk?decimal(rate(percent(decimal(cost.get("cumulativeCost")),project.budget()))):null;
        boolean unknown=tk&&usage==null;
        BigDecimal schedule=p.subtract(a),gap=usage==null?null:usage.subtract(a);
        LocalDate updated=memos.stream().map(Memo::writtenDate).max(LocalDate::compareTo).orElse(null);
        var evidence=snapshot.evidence().stream().filter(e->id.equals(e.projectId())).findFirst().orElse(null);
        LocalDate baseline=updated==null?(evidence!=null&&evidence.registeredDate().isAfter(project.fromDate())?evidence.registeredDate():project.fromDate()):updated;
        int days=(int)Math.max(0,ChronoUnit.DAYS.between(baseline,today));
        var open=snapshot.issues().stream().filter(i->id.equals(i.projectId())&&!"CLOSE".equals(i.status())).toList();
        var overdue=open.stream().filter(i->i.dueDate().isBefore(today)).toList();
        long high=overdue.stream().filter(i->"HIGH".equals(i.importance())).count();
        var reasons=new ArrayList<Map<String,Object>>();
        signal(reasons,"일정 늦음",schedule,BigDecimal.TEN,new BigDecimal("5.0"),"계획 진척률 − 실적 진척률 (표시 1자리)");
        if(gap!=null)signal(reasons,"진척대비원가",gap,new BigDecimal("15.0"),new BigDecimal("5.0"),"TK 원가 사용률 − 실적 진척률 (표시 1자리)");
        if(high>0)reasons.add(reason("기한지난HIGH","위험",Long.toString(high),"1","상태 ≠ CLOSE · 중요도 HIGH · 기한 < 기준일"));
        if(!overdue.isEmpty())reasons.add(reason("기한지난이슈","주의",Integer.toString(overdue.size()),"1","상태 ≠ CLOSE · 기한 < 기준일"));
        if(days>14)reasons.add(reason("메모경과","주의",Integer.toString(days),">14","기준일 − 마지막 작성일 (메모 없음: max(등록일, 계약시작일))"));
        String light=reasons.stream().anyMatch(r->"위험".equals(r.get("light")))?"위험":reasons.isEmpty()?"정상":"주의";
        var part=snapshot.sources().projectParts().stream().filter(s->id.equals(s.projectId())&&s.fromMonth().compareTo(month.replace("-",""))<=0)
            .max(Comparator.comparing(com.sicost.profitability.MonthlyCostDtos.ProjectPart::fromMonth));
        String partId=part.map(com.sicost.profitability.MonthlyCostDtos.ProjectPart::partId).orElse(null);
        var partInfo=snapshot.sources().parts().stream().filter(s->s.partId().equals(partId)).findFirst();
        var pm=snapshot.managers().stream().filter(m->id.equals(m.projectId())).map(m->new Actor(m.userId(),m.userName())).distinct().toList();
        boolean all=!stages.isEmpty()&&stages.stream().allMatch(s->s.completedDate()!=null);
        Stage current=stages.stream().filter(s->s.completedDate()==null&&s.endDate().isBefore(today)).findFirst()
            .orElseGet(()->stages.stream().filter(s->s.completedDate()==null&&!s.startDate().isAfter(today)&&!s.endDate().isBefore(today)).findFirst()
                .orElseGet(()->stages.stream().filter(s->s.completedDate()==null).findFirst().orElse(null)));
        return fields("projectId",id,"projectName",project.name(),"contractType",project.contractType(),"sourceStatus",project.status(),"partId",partId,
            "partName",partInfo.map(com.sicost.profitability.MonthlyCostDtos.Part::name).orElse(null),"hq",partInfo.map(s->"Y".equals(s.headquarters())).orElse(false),
            "pm",pm,"light",light,"reasons",List.copyOf(reasons),"costUnknown",unknown,"planProgress",p.toPlainString(),"actualProgress",a.toPlainString(),
            "costUsage",usage==null?null:usage.toPlainString(),"scheduleGap",schedule.toPlainString(),"costProgressGap",gap==null?null:gap.toPlainString(),
            "currentStep",current==null?null:stage(current,today),"allStepsCompleted",all,"stepsInitialized",!stages.isEmpty(),
            "planBasis",valid?"단계 기준":"기간 기준 · "+(stages.isEmpty()?"단계 미입력":"단계 집합 불완전 · 원본 유지"),
            "openIssueCount",open.size(),"overdueIssueCount",overdue.size(),"lastUpdatedDate",updated,"elapsedDays",days,"updateWarning",days>14,
            "baseDate",today,"costMonth",month,"completeness",cost.get("completeness"));
    }
    private static Map<String,Object> stage(Stage s,LocalDate today) {
        return fields("projectId",s.projectId(),"stepSn",s.stepSn(),"stepCode",s.stepCode(),"stepName",s.stepName(),"weight",s.weight().setScale(2).toPlainString(),
            "startDate",s.startDate(),"endDate",s.endDate(),"completedDate",s.completedDate(),"status",s.completedDate()!=null?"완료":s.endDate().isBefore(today)?"지연":s.startDate().isAfter(today)?"예정":"진행중",
            "registeredBy",new Actor(s.registeredBy(),s.registeredName()),"registeredAt",s.registeredAt(),"modifiedBy",s.modifiedBy()==null?null:new Actor(s.modifiedBy(),s.modifiedName()),"modifiedAt",s.modifiedAt());
    }
    public static boolean validStages(Project project,List<Stage> stages) {
        if(stages.isEmpty()||!stages.get(0).startDate().equals(project.fromDate())||!stages.get(stages.size()-1).endDate().equals(project.toDate()))return false;
        BigDecimal total=BigDecimal.ZERO;Set<String> codes=new HashSet<>();
        for(int i=0;i<stages.size();i++) {
            Stage s=stages.get(i);if(s.weight().signum()<=0||s.endDate().isBefore(s.startDate())||!codes.add(s.stepCode()))return false;
            if(i>0&&!stages.get(i-1).endDate().plusDays(1).equals(s.startDate()))return false;
            total=total.add(s.weight());
        }
        return total.compareTo(BigDecimal.valueOf(100))==0;
    }
    private static Fraction portion(LocalDate start,LocalDate end,LocalDate today,BigDecimal weight) {
        if(today.isBefore(start))return new Fraction(BigDecimal.ZERO,BigDecimal.ONE);
        if(!today.isBefore(end))return new Fraction(weight,BigDecimal.ONE);
        return new Fraction(weight.multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(start,today))),BigDecimal.valueOf(ChronoUnit.DAYS.between(start,end)));
    }
    private static void signal(List<Map<String,Object>> reasons,String kind,BigDecimal value,BigDecimal danger,BigDecimal warning,String formula) {
        if(value.compareTo(warning)>=0)reasons.add(reason(kind,value.compareTo(danger)>=0?"위험":"주의",value.setScale(1).toPlainString(),value.compareTo(danger)>=0?danger.toPlainString():warning.toPlainString(),formula));
    }
    private static Map<String,Object> reason(String kind,String light,String value,String threshold,String formula) {return fields("kind",kind,"light",light,"value",value,"threshold",threshold,"formula",formula);}
}
