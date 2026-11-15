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
import com.sicost.common.notifications.NotificationRecipients;
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
        Set<String> pendingDeliveries=new HashSet<>(repository.pendingDeliveryBillIds());
        List<Receivable> items=new ArrayList<>();Map<String,List<RecipientGroup>> groups=new HashMap<>();
        for(OverdueBill bill:repository.overdue(today)) {
            if(!bill.dueDate().isBefore(today)||bill.unpaidAmount().signum()<=0)continue;
            Dunning dunning=dunning(bill.dueDate(),today,thresholds);
            var recipients=groups.computeIfAbsent(bill.projectId(),id->recipients(id,repository.part(id,month),roles,scopes));
            String sendNotice=dunning.step()==0?"알림 대상 아님":pendingDeliveries.contains(bill.billId())?"전달 처리 중":recipients.get(dunning.step()-1).recipients().isEmpty()?"수신자 미지정":"다시알림 가능";
            items.add(new Receivable(bill.billId(),bill.projectId(),bill.projectName(),BillingCheckService.decimal(bill.unpaidAmount()),bill.dueDate(),dunning,recipients,repository.history(bill.billId()),sendNotice));
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
        return NotificationRecipients.dunning(projectId,
            part==null?null:new NotificationRecipients.Part(part.partId(),part.headquarters(),part.leaderId()),
            roles.stream().map(r->new NotificationRecipients.Role(r.userId(),r.name(),null,r.role())).toList(),
            scopes.stream().map(s->new NotificationRecipients.Scope(s.userId(),s.type(),s.partId(),s.projectId())).toList())
            .stream().map(g->new RecipientGroup(g.step(),g.recipients().stream()
                .map(r->new Recipient(r.userId(),r.name(),r.qualifications())).toList(),g.unassignedReasons())).toList();
    }
}
