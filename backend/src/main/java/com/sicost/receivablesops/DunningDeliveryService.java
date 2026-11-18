package com.sicost.receivablesops;

import com.sicost.apiops.*;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.notifications.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.receivablesops.DunningDeliveryDtos.*;

@Service
public class DunningDeliveryService {
    private final DunningDeliveryRepository repository;
    private final BillingReviewRepository billing;
    private final NotificationPublicationMapper recipients;
    private final RevisionService revisions;
    private final DunningDeliveryMail mail;
    private final Clock clock;
    private com.sicost.permission.NotificationRecipientPolicy permission;
    @org.springframework.beans.factory.annotation.Autowired
    public void setRecipientPolicy(com.sicost.permission.NotificationRecipientPolicy value) {permission=value;}
    public DunningDeliveryService(DunningDeliveryRepository repository, BillingReviewRepository billing, NotificationPublicationMapper recipients,
        RevisionService revisions,DunningDeliveryMail mail,Clock clock) {
        this.repository=repository;this.billing=billing;this.recipients=recipients;this.revisions=revisions;this.mail=mail;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public DeliveryPreview preview(String billId) {
        var bill=repository.required(billId);LocalDate today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        String month=YearMonth.from(today).toString().replace("-","");
        var thresholds=new ArrayList<BigDecimal>();
        for(String code:List.of("DUN_D1","DUN_D2","DUN_D3")) {
            var value=billing.standard(code,month);
            if(value==null||value.compareTo(BigDecimal.ONE)<0||!thresholds.isEmpty()&&thresholds.get(thresholds.size()-1).compareTo(value)>=0)
                throw new ApiException(500,"INTERNAL_ERROR","미수 알림 기준일 설정을 확인해 주세요.");
            thresholds.add(value);
        }
        int step=ReceivableService.dunning(bill.dueDate(),today,thresholds).step();
        var part=billing.part(bill.projectId(),month);
        var groups=NotificationRecipients.dunning(bill.projectId(),part==null?null:new NotificationRecipients.Part(part.partId(),part.headquarters(),part.leaderId()),recipients.eligibleRoles(),recipients.recipientScopes());
        var selected=step==0?null:groups.get(step-1);
        if(permission==null)throw new IllegalStateException("수신 권한 정책이 연결되지 않았습니다.");
        List<DeliveryRecipient> people=selected==null?Collections.emptyList():selected.recipients().stream().filter(r->permission.dunning(r.userId(),billId)).map(r->new DeliveryRecipient(r.userId(),r.name(),r.emailAddress(),r.emailAddress()==null||r.emailAddress().isBlank(),r.qualifications())).toList();
        boolean inProgress=repository.tasks(billId).stream().anyMatch(t->!DunningDeliveryDtos.terminal(t.state()));
        var reasons=new ArrayList<String>();
        if(bill.cancelled())reasons.add("취소된 청구입니다.");
        if(bill.unpaidAmount().signum()<=0)reasons.add("현재 미수가 없습니다.");
        if(step==0)reasons.add("알림 단계에 도달하지 않았습니다.");
        if(inProgress)reasons.add("전달 처리 중입니다.");
        if(people.isEmpty())reasons.add("수신자가 지정되지 않았습니다.");
        return new DeliveryPreview(billId,bill.projectId(),bill.projectName(),bill.unpaidAmount().toPlainString(),bill.dueDate(),step,people,
            selected==null?Collections.emptyList():selected.unassignedReasons(),"BOTH",mail.configured(),inProgress,reasons.isEmpty(),List.copyOf(reasons),
            revisions.revision(repository.snapshot(billId)),OffsetDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Seoul")));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public DeliveryPage deliveries(String billId,DeliveryQuery query) {
        repository.required(billId);var all=repository.rounds(billId);
        long start=(long)(query.page()-1)*query.size();int from=(int)Math.min(start,all.size()),to=(int)Math.min(start+query.size(),all.size());
        return new DeliveryPage(billId,List.copyOf(all.subList(from,to)),query.page(),query.size(),all.size(),to<all.size(),OffsetDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Seoul")));
    }
    public void requireExpected(String expected,String actual) {revisions.requireExpected(expected,actual);}
}
