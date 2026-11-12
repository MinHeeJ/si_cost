package com.sicost.apiops;

import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.apiops.BillingReviewDtos.*;

@Service
public class ReceivableService {
    private final BillingReviewRepository repository;
    private final CurrentUserProvider users;
    private final Clock clock;
    public ReceivableService(BillingReviewRepository repository,CurrentUserProvider users,Clock clock) {this.repository=repository;this.users=users;this.clock=clock;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ReceivablesPage getReceivables(PageQuery query) {
        var result=all();return new ReceivablesPage(result.asOfDate(),BillingCheckService.page(result.items(),query),result.total(),result.tabCount(),query.page(),query.size(),result.cards());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ReceivablesPage allReceivables() {return all();}
    private ReceivablesPage all() {
        users.currentUser();LocalDate today=ProjectInputPolicy.today(clock);
        String month=DatabaseMonthCodec.toDatabase(YearMonth.from(today).toString());
        List<BigDecimal> thresholds=thresholds(month);
        List<RecipientRole> roles=repository.roles();List<RecipientScope> scopes=repository.scopes();
        List<Receivable> items=new ArrayList<>();Map<String,List<RecipientGroup>> groups=new HashMap<>();
        for(OverdueBill bill:repository.overdue(today)) {
            if(!bill.dueDate().isBefore(today)||bill.unpaidAmount().signum()<=0)continue;
            Dunning dunning=dunning(bill.dueDate(),today,thresholds);
            var recipients=groups.computeIfAbsent(bill.projectId(),id->recipients(id,repository.part(id,month),roles,scopes));
            items.add(new Receivable(bill.billId(),bill.projectId(),bill.projectName(),BillingCheckService.decimal(bill.unpaidAmount()),bill.dueDate(),dunning,recipients,repository.history(bill.billId()),"발송 기능 연결 예정"));
        }
        items.sort(Comparator.comparingInt((Receivable r)->r.dunning().elapsedDays()).reversed().thenComparing(Receivable::dueDate).thenComparing(Receivable::billId));
        List<StepCard> cards=new ArrayList<>();
        for(int step=1;step<=3;step++) {
            final int current=step;
            cards.add(new StepCard(step,BillingCheckService.decimal(thresholds.get(step-1)),(int)items.stream().filter(r->r.dunning().step()==current).count(),
                step==1?List.of("PM"):step==2?List.of("PM","SALES_LDR"):List.of("PM","SALES_LDR","EXEC","FIN")));
        }
        return new ReceivablesPage(today,List.copyOf(items),items.size(),items.size(),1,50,List.copyOf(cards));
    }
    private List<BigDecimal> thresholds(String month) {
        BigDecimal d1=repository.standard("DUN_D1",month),d2=repository.standard("DUN_D2",month),d3=repository.standard("DUN_D3",month);
        if(d1==null||d2==null||d3==null||d1.compareTo(BigDecimal.ONE)<0||d1.compareTo(d2)>=0||d2.compareTo(d3)>=0)
            throw new ApiException(500,"INTERNAL_ERROR","미수 알림 기준일 설정(DUN_D1/DUN_D2/DUN_D3)을 확인해 주세요.");
        return List.of(d1,d2,d3);
    }
    public static Dunning dunning(LocalDate due,LocalDate today,List<BigDecimal> thresholds) {
        int elapsed=Math.toIntExact(ChronoUnit.DAYS.between(due,today)),step=0;
        for(int i=0;i<3;i++)if(BigDecimal.valueOf(elapsed).compareTo(thresholds.get(i))>=0)step=i+1;
        return new Dunning(elapsed,step,step==3?null:step+1,step==3?null:due.plusDays(thresholds.get(step).setScale(0,RoundingMode.CEILING).longValueExact()),step==3);
    }
    static List<RecipientGroup> recipients(String projectId,ProjectPart part,List<RecipientRole> roles,List<RecipientScope> scopes) {
        Map<String,Recipient> selected=new TreeMap<>();List<String> reasons=new ArrayList<>();List<RecipientGroup> result=new ArrayList<>();
        add(selected,reasons,roles,r->r.role().equals("PM")&&scopes.stream().anyMatch(s->s.userId().equals(r.userId())&&"PROJ".equals(s.type())&&projectId.equals(s.projectId())),"프로젝트 PM 미지정");
        result.add(group(1,selected,reasons));
        if(part==null||part.partId()==null) {
            add(selected,reasons,roles,r->r.role().equals("EXEC"),"영업파트 미지정 · EXEC 대체 수신자 미지정");
        } else if(part.headquarters()) {
            Predicate<RecipientRole> hq=r->r.role().equals("SALES_LDR")&&scopes.stream().anyMatch(s->s.userId().equals(r.userId())&&"PART".equals(s.type())&&part.partId().equals(s.partId()));
            if(roles.stream().anyMatch(hq))add(selected,reasons,roles,hq,"HQ SALES_LDR 미지정");
            else add(selected,reasons,roles,r->r.role().equals("EXEC"),"HQ SALES_LDR 없음 · EXEC 대체 수신자 미지정");
        } else add(selected,reasons,roles,r->r.role().equals("SALES_LDR")&&Objects.equals(part.leaderId(),r.userId()),"영업파트 SALES_LDR 미지정");
        result.add(group(2,selected,reasons));
        add(selected,reasons,roles,r->r.role().equals("EXEC"),"EXEC 미지정");
        add(selected,reasons,roles,r->r.role().equals("FIN"),"FIN 미지정");
        result.add(group(3,selected,reasons));return List.copyOf(result);
    }
    private static void add(Map<String,Recipient> selected,List<String> reasons,List<RecipientRole> roles,Predicate<RecipientRole> eligible,String missing) {
        List<RecipientRole> found=roles.stream().filter(eligible).toList();if(found.isEmpty())reasons.add(missing);
        for(RecipientRole row:found) {
            var old=selected.get(row.userId());Set<String> merged=new TreeSet<>();if(old!=null)merged.addAll(old.roles());merged.add(row.role());
            selected.put(row.userId(),new Recipient(row.userId(),row.name(),List.copyOf(merged)));
        }
    }
    private static RecipientGroup group(int step,Map<String,Recipient> selected,List<String> reasons) {return new RecipientGroup(step,List.copyOf(selected.values()),List.copyOf(reasons));}
}
