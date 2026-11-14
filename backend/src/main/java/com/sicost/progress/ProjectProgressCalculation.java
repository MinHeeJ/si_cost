package com.sicost.progress;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.sicost.progress.ProjectProgressDtos.*;
import static com.sicost.common.validation.ProjectInputPolicy.invalid;

public final class ProjectProgressCalculation {
    private ProjectProgressCalculation() {}
    public static final String NOTICE="일정·원가 신호는 1차 경보입니다. 실제 판단은 프로젝트 원가 분석의 상세 내역을 확인하세요.";
    public static List<String> codes(String type) {
        return Set.of("TK","IN").contains(type)
            ? List.of("TK_KICK","TK_ANLY","TK_DSGN","TK_DEV","TK_TEST","TK_OPEN","TK_END")
            : List.of("OP_KICK","OP_HAND","OP_STAB","OP_RUN","OP_END");
    }
    public static List<Template> template(String type,List<Template> source) {
        List<Template> selected=new ArrayList<>();List<Map<String,String>> errors=new ArrayList<>();BigDecimal total=BigDecimal.ZERO;
        for(String code:codes(type)) {
            Template row=source.stream().filter(t->t.code().equals(code)).findFirst().orElse(null);
            String reason=null;
            if(row==null)reason="필수 코드가 없습니다.";
            else if(!"Y".equals(row.useYn()) || !"N".equals(row.delYn()))reason="활성 코드가 아닙니다.";
            else if(row.types()==null || Arrays.stream(row.types().split(",")).map(String::strip).noneMatch(type::equals))reason="계약유형이 일치하지 않습니다.";
            else if(row.weight()==null || !row.weight().matches("[0-9]+(?:\\.[0-9]{1,2})?"))reason="비중은 소수 두 자리 이내 숫자여야 합니다.";
            else {
                BigDecimal weight=new BigDecimal(row.weight());
                if(weight.signum()<=0 || weight.compareTo(new BigDecimal("100"))>0)reason="비중은 0 초과 100 이하여야 합니다.";
                else {total=total.add(weight);selected.add(row);}
            }
            if(reason!=null)errors.add(Map.of("field","steps","code",code,"message",reason));
        }
        if(total.compareTo(new BigDecimal("100"))!=0)errors.add(Map.of("field","steps","code","PROJ_STEP","message","비중 합계는 100이어야 합니다."));
        if(!errors.isEmpty())throw new ApiException(400,"INVALID_INPUT","기본 단계 코드와 비중을 확인해 주세요.","steps",Map.of("violations",errors));
        return selected.stream().sorted(Comparator.comparingInt(Template::sortOrder).thenComparing(Template::code)).toList();
    }
    public static List<StepRow> initialize(ProjectRow p,List<Template> source) {
        List<Template> templates=template(p.contractType(),source);long days=days(p.startDate(),p.endDate());
        if(days<templates.size())return Collections.emptyList();
        List<StepRow> result=new ArrayList<>();long previous=0;BigDecimal cumulative=BigDecimal.ZERO;
        for(int i=0;i<templates.size();i++) {
            Template t=templates.get(i);BigDecimal weight=new BigDecimal(t.weight());cumulative=cumulative.add(weight);
            long boundary=i==templates.size()-1?days:BigDecimal.valueOf(days).multiply(cumulative).divide(new BigDecimal("100"),0,RoundingMode.HALF_UP).longValueExact();
            boundary=Math.min(days-(templates.size()-i-1),Math.max(previous+1,boundary));
            result.add(new StepRow(p.projectId(),i+1,t.code(),t.name(),weight.setScale(2),p.startDate().plusDays(previous),
                p.startDate().plusDays(boundary-1),null,null,null,null,null,null,null));previous=boundary;
        }
        return List.copyOf(result);
    }
    public static boolean valid(ProjectRow p,List<StepRow> rows) {
        if(rows.size()!=codes(p.contractType()).size() || !new HashSet<>(rows.stream().map(StepRow::stepCode).toList()).equals(new HashSet<>(codes(p.contractType()))))return false;
        LocalDate next=p.startDate();BigDecimal total=BigDecimal.ZERO;int previous=0;
        for(StepRow r:rows) {
            if(!p.projectId().equals(r.projectId()) || r.stepSn()<=previous || r.stepSn()>99 || r.weight()==null || r.weight().signum()<=0
                || r.weight().compareTo(new BigDecimal("100"))>0 || r.startDate()==null || r.endDate()==null || !r.startDate().equals(next) || r.endDate().isBefore(next))return false;
            next=r.endDate().plusDays(1);total=total.add(r.weight());previous=r.stepSn();
        }
        return next.equals(p.endDate().plusDays(1)) && total.compareTo(new BigDecimal("100"))==0;
    }
    public static List<StepRow> dates(ProjectRow p,List<StepRow> rows,List<EndDate> input) {
        if(!valid(p,rows) || input.size()!=rows.size())throw invalid("steps");
        List<StepRow> result=new ArrayList<>();LocalDate start=p.startDate();
        for(int i=0;i<rows.size();i++) {
            StepRow old=rows.get(i);EndDate in=input.get(i);
            if(in.stepSn()!=old.stepSn() || in.endDate()==null || in.endDate().isBefore(start) || in.endDate().isAfter(p.endDate())
                || (i==rows.size()-1 && !in.endDate().equals(p.endDate()))
                || (old.completedDate()!=null && (!start.equals(old.startDate()) || !in.endDate().equals(old.endDate()))))throw invalid("steps");
            result.add(withDates(old,start,in.endDate(),old.completedDate()));start=in.endDate().plusDays(1);
        }
        return List.copyOf(result);
    }
    public static StepRow withDates(StepRow r,LocalDate start,LocalDate end,LocalDate completed) {
        return new StepRow(r.projectId(),r.stepSn(),r.stepCode(),r.stepName(),r.weight(),start,end,completed,
            r.registeredBy(),r.registeredName(),r.registeredAt(),r.modifiedBy(),r.modifiedName(),r.modifiedAt());
    }
    public static String plan(ProjectRow p,List<StepRow> rows,LocalDate today) {
        BigDecimal numerator=BigDecimal.ZERO,denominator=BigDecimal.ONE;
        if(!valid(p,rows))return fraction(new BigDecimal("100"),p.startDate(),p.endDate(),today)[0]
            .divide(fraction(new BigDecimal("100"),p.startDate(),p.endDate(),today)[1],1,RoundingMode.HALF_UP).toPlainString();
        for(StepRow r:rows) {
            BigDecimal[] part=fraction(r.weight(),r.startDate(),r.endDate(),today);
            numerator=numerator.multiply(part[1]).add(part[0].multiply(denominator));denominator=denominator.multiply(part[1]);
        }
        return numerator.divide(denominator,1,RoundingMode.HALF_UP).toPlainString();
    }
    private static BigDecimal[] fraction(BigDecimal weight,LocalDate start,LocalDate end,LocalDate today) {
        if(today.isBefore(start))return new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ONE};
        if(!today.isBefore(end))return new BigDecimal[]{weight,BigDecimal.ONE};
        return new BigDecimal[]{weight.multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(start,today))),BigDecimal.valueOf(ChronoUnit.DAYS.between(start,end))};
    }
    public static Step step(StepRow r,LocalDate today) {
        String status=r.completedDate()!=null?"완료":r.endDate().isBefore(today)?"지연":r.startDate().isAfter(today)?"예정":"진행중";
        return new Step(r.projectId(),r.stepSn(),r.stepCode(),r.stepName(),r.weight().setScale(2).toPlainString(),r.startDate(),r.endDate(),r.completedDate(),status,
            actor(r.registeredBy(),r.registeredName()),time(r.registeredAt()),actor(r.modifiedBy(),r.modifiedName()),time(r.modifiedAt()));
    }
    public static Memo memo(MemoRow r) {return new Memo(r.projectId(),r.memoSn(),r.writtenDate(),r.thisWeek(),r.nextWeek(),r.request(),r.actualProgress(),
        actor(r.registeredBy(),r.registeredName()),time(r.registeredAt()),actor(r.modifiedBy(),r.modifiedName()),time(r.modifiedAt()));}
    public static Issue issue(IssueRow r,LocalDate today) {return new Issue(r.issueId(),r.projectId(),r.projectName(),r.title(),r.importance(),r.importanceName(),r.dueDate(),r.status(),r.statusName(),r.action(),
        !r.status().equals("CLOSE")&&r.dueDate().isBefore(today),actor(r.registeredBy(),r.registeredName()),time(r.registeredAt()),actor(r.modifiedBy(),r.modifiedName()),time(r.modifiedAt()));}
    public static Actor actor(String id,String name){return id==null?null:new Actor(id,name==null?id:name);}
    public static OffsetDateTime time(LocalDateTime value){return value==null?null:value.atZone(TimeConfiguration.SEOUL).toOffsetDateTime();}
    public static long days(LocalDate from,LocalDate to){return ChronoUnit.DAYS.between(from,to)+1;}
}
