package com.sicost.workforcerecommendations;

import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.AssignmentMonthlyService.Source;
import java.math.*;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.sicost.workforcerecommendations.WorkforceRecommendationDtos.*;

@Component
public class AiWorkforceScoring {
    public static final List<String> ITEMS=List.of("JOB","SKILL","CUST","GRADE","PERF","COST","AVAIL","LOC");
    private static final List<BigDecimal> BASE=List.of(25,20,15,10,5,5,10,10).stream().map(BigDecimal::valueOf).toList();
    public record History(Assignment assignment,Source source) {}
    public record Context(Condition condition,Target target,LocalDate today,List<Code> codes,List<Skill> skills,List<Area> areas,List<History> histories) {}
    public record Labor(String month,String cost,boolean estimated) {}
    public List<BigDecimal> weights(List<Standard> standards) {
        List<BigDecimal> result=new ArrayList<>();
        for(String item:ITEMS){String key="AI_SCORE_"+item;BigDecimal v=standards.stream().filter(s->key.equals(s.code())).map(Standard::value).findFirst().orElse(null);
            if(v==null||v.signum()<0||v.compareTo(new BigDecimal("100"))>0||v.stripTrailingZeros().scale()>4)throw standard(key);result.add(v.stripTrailingZeros());}
        if(result.stream().reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(new BigDecimal("100"))!=0)throw standard("AI_SCORE_합계");return List.copyOf(result);
    }
    private ApiException standard(String field){return new ApiException(409,"INVALID_STANDARD","현재월 추천 배점이 누락되었거나 합계가 100이 아닙니다.",field,null);}
    public Calculation calculate(Person person,Context c,List<BigDecimal> weights,Labor labor,BigDecimal price) {
        String requested=c.condition().jobCode();
        String job=requested.equals(person.jobCode())?"P":requested.equals(person.subJobCode())?"S":c.codes().stream().filter(k->"JOB".equals(k.groupId())&&requested.equals(k.code())).anyMatch(k->tokens(k.related()).contains(person.jobCode())&&activeJob(c.codes(),person.jobCode()))?"R":"O";
        Set<String> owned=new HashSet<>();c.skills().stream().filter(s->person.developerId().equals(s.developerId())).forEach(s->owned.add(normalize(s.name())));
        Set<String> required=new HashSet<>();c.codes().stream().filter(k->"SKILL".equals(k.groupId())&&tokens(k.related()).contains(requested)).forEach(k->required.add(normalize(k.name())));
        int hits=(int)required.stream().filter(owned::contains).count(),n=required.size();
        int feature=owned.stream().filter(s->!s.isEmpty()).anyMatch(s->normalize(c.target().feature()).contains(s))?1:0;
        List<History> history=c.histories().stream().filter(h->person.developerId().equals(h.source().developerId())&&!"CNCL".equals(h.source().status())).toList();
        List<History> past=history.stream().filter(h->!h.source().startDate().isAfter(c.today())).toList();
        String customer=c.target().customerId()!=null&&past.stream().anyMatch(h->c.target().customerId().equals(h.assignment().customerId()))?"S":notBlank(c.target().industry())&&past.stream().anyMatch(h->c.target().industry().equals(h.assignment().industry()))?"I":"O";
        List<String> grades=c.codes().stream().filter(k->"GRADE".equals(k.groupId())&&Set.of("JR","MD","SR","EX").contains(k.code())).sorted(Comparator.comparingInt(Code::order)).map(Code::code).toList();
        int a=grades.indexOf(c.condition().requestedGradeCode()),b=grades.indexOf(person.gradeCode());Integer distance=a<0||b<0?null:Math.abs(a-b);
        List<History> completed=history.stream().filter(h->h.source().endDate().isBefore(c.today())).toList();
        int completedCount=completed.size(),extended=(int)completed.stream().filter(h->h.source().extensionCount()>0).count(),hr=(int)completed.stream().filter(h->h.assignment().hrEarly()).count();
        BigDecimal performance=completedCount==0?new BigDecimal("3"):clamp(new BigDecimal(5).multiply(BigDecimal.valueOf(extended)).divide(BigDecimal.valueOf(completedCount),0,RoundingMode.HALF_UP).subtract(BigDecimal.valueOf(2L*hr)),0,5);
        BigDecimal cost=labor.cost()==null||price==null||price.signum()<=0?BigDecimal.ZERO:clamp(new BigDecimal("11").subtract(new BigDecimal(labor.cost()).multiply(BigDecimal.TEN).divide(price,20,RoundingMode.HALF_UP)).setScale(0,RoundingMode.HALF_UP),1,5);
        YearMonth month=YearMonth.parse(c.condition().startMonth());
        List<History> current=history.stream().filter(h->!h.source().startDate().isAfter(c.today())&&!h.source().endDate().isBefore(c.today())).toList();
        LocalDate last=current.stream().map(h->h.source().endDate()).max(Comparator.naturalOrder()).orElse(null);
        String availability;
        if(current.isEmpty())availability="IDLE";
        else if(YearMonth.from(last).isBefore(month))availability="BEFORE";
        else{
            List<History> overlap=history.stream().filter(h->!h.source().startDate().isAfter(month.atEndOfMonth())&&!h.source().endDate().isBefore(month.atDay(1))).toList();
            LocalDate latest=overlap.stream().map(h->h.source().endDate()).max(Comparator.naturalOrder()).orElse(null);
            availability=latest!=null&&overlap.stream().filter(h->h.source().endDate().equals(latest)).allMatch(h->"L".equals(h.source().extensionPossibility()))?"LOW":"OTHER";
        }
        String targetArea=area(c.target().address(),null,c.areas()),home=area(person.address(),person.areaCode(),c.areas());
        String location=!notBlank(c.target().address())?"N":targetArea==null||home==null?"U":targetArea.equals(home)?"S":"D";
        List<BigDecimal> basic=List.of(BigDecimal.valueOf(Set.of("P","S").contains(job)?25:"R".equals(job)?12:3),
            BigDecimal.valueOf(n==0?0:16L*hits).divide(BigDecimal.valueOf(n==0?1:n),0,RoundingMode.HALF_UP).add(BigDecimal.valueOf(4L*feature)).min(new BigDecimal("20")),
            BigDecimal.valueOf("S".equals(customer)?15:"I".equals(customer)?9:6),BigDecimal.valueOf(distance==null?2:distance==0?10:distance==1?5:2),performance,cost,
            BigDecimal.valueOf(Set.of("IDLE","BEFORE").contains(availability)?10:"LOW".equals(availability)?6:2),BigDecimal.valueOf("N".equals(location)?5:"S".equals(location)?10:6));
        List<BigDecimal> scores=new ArrayList<>();for(int i=0;i<8;i++)scores.add(basic.get(i).multiply(weights.get(i)).divide(BASE.get(i),6,RoundingMode.UNNECESSARY).stripTrailingZeros());
        String total=decimal(scores.stream().reduce(BigDecimal.ZERO,BigDecimal::add).setScale(1,RoundingMode.HALF_UP));
        Evidence evidence=new Evidence(job,List.of(hits,n,feature),customer,distance,List.of(completedCount,extended,hr),availability,location);
        AiScoreDetail detail=new AiScoreDetail(1,basic,weights,List.copyOf(scores),evidence,labor.month(),labor.cost(),price==null?null:price.toPlainString(),labor.estimated()?1:0);
        String reason="직무 "+switch(job){case "P"->"주직무 일치";case "S"->"보조직무 일치";case "R"->"관련직무";default->"그 밖 직무";}+", 필요 스킬 "+hits+"/"+n+", "+switch(customer){case "S"->"같은 고객 경험";case "I"->"같은 업종 경험";default->"고객 경험 없음";}+", 가용 "+availability+", 비용 "+(labor.cost()==null?"미확인":"확인")+" 기준으로 추천했습니다.";
        return new Calculation(person,detail,total,reason);
    }
    public List<ScoreItem> items(AiScoreDetail d){List<ScoreItem> items=new ArrayList<>();for(int i=0;i<8;i++)items.add(new ScoreItem(ITEMS.get(i),decimal(d.b().get(i)),decimal(d.w().get(i)),decimal(d.s().get(i)),basis(i,d)));return List.copyOf(items);}
    private String basis(int i,AiScoreDetail d){return switch(i){case 0->"직무 분류 "+d.e().j();case 1->"필요 스킬 "+d.e().sk().get(0)+"/"+d.e().sk().get(1)+" · 특징 "+d.e().sk().get(2);case 2->"고객 경험 "+d.e().cu();case 3->d.e().g()==null?"등급 미확인":"등급 거리 "+d.e().g();case 4->"완료/연장/HR "+d.e().p();case 5->d.c()==null?"개인 인건비 미확인":d.u()==null||new BigDecimal(d.u()).signum()<=0?"유효 단가 미확인":"원가월 "+d.m()+" · 인건비 "+d.c()+" · 단가 "+d.u()+(d.f()==1?" · 예정":"");case 6->"가용 상태 "+d.e().a();default->"근무지 "+d.e().l();};}
    private static boolean activeJob(List<Code> codes,String job){return codes.stream().anyMatch(k->"JOB".equals(k.groupId())&&k.code().equals(job));}
    private static Set<String> tokens(String value){if(value==null)return Set.of();Set<String> result=new HashSet<>();for(String s:value.split(","))if(!s.trim().isEmpty())result.add(s.trim());return result;}
    public static String normalize(String text){return text==null?"":Normalizer.normalize(text,Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);}
    private static boolean notBlank(String value){return value!=null&&!value.isBlank();}
    private String area(String address,String code,List<Area> areas){if(code!=null){String found=areas.stream().filter(a->code.equals(a.code())).map(Area::name).findFirst().orElse(null);if(found!=null)return found;}
        String first=normalize(address).split("\\s+")[0];return areas.stream().filter(a->first.equals(normalize(a.name()))||first.equals(normalize(a.fullName()))).map(Area::name).findFirst().orElse(null);}
    private static BigDecimal clamp(BigDecimal v,int low,int high){return v.max(BigDecimal.valueOf(low)).min(BigDecimal.valueOf(high));}
    public static String decimal(BigDecimal v){return v.stripTrailingZeros().toPlainString();}
}
