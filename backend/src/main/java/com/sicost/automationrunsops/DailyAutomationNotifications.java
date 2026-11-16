package com.sicost.automationrunsops;

import com.sicost.cashflowops.CashFlowService;
import com.sicost.common.notifications.*;
import com.sicost.shared.SharedQueriesMapper;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.common.notifications.NotificationPublicationMapper.*;
import static com.sicost.automationrunsops.DailyAutomationDtos.*;

@Service
public class DailyAutomationNotifications {
    private final DailyAutomationMapper mapper;
    private final DailyAutomationRepository repository;
    private final NotificationPublisher publisher;
    private final NotificationPublicationMapper recipients;
    private final CashFlowService cash;
    private final SharedQueriesMapper shared;
    public DailyAutomationNotifications(DailyAutomationMapper mapper,DailyAutomationRepository repository,NotificationPublisher publisher,
                                         NotificationPublicationMapper recipients,CashFlowService cash,SharedQueriesMapper shared) {
        this.mapper=mapper;this.repository=repository;this.publisher=publisher;this.recipients=recipients;this.cash=cash;this.shared=shared;
    }
    public List<String> withdrawalKeys(LocalDate date) { return mapper.assignmentIds(date).stream().map(Item::id).toList(); }
    public void withdrawal(String id,LocalDate date) {
        var found=mapper.assignmentIdentity(id);if(found==null)return;
        repository.item(List.of("ASGN:"+id,"DEV:"+found.developerId(),"PROJECT:"+found.projectId()),()->{
            var row=mapper.assignment(id);if(row==null)return null;
            if(!row.projectId().equals(found.projectId())||!row.developerId().equals(found.developerId()))throw new IllegalStateException("투입 소속이 변경되었습니다.");
            if(!DailyAutomationActions.eligible(row,date))return null;
            var configured=shared.parameter("WTHDR_NOTI_D",DailyAutomationActions.db(date));
            BigDecimal days=configured==null||configured.value()==null?null:new BigDecimal(configured.value());
            if(days==null||days.signum()<=0||days.stripTrailingZeros().scale()>0)throw new IllegalStateException("철수 알림 기준을 확인해 주세요.");
            if(!days.equals(BigDecimal.valueOf(ChronoUnit.DAYS.between(date,row.endDate())))) {
                if(days.compareTo(BigDecimal.valueOf(ChronoUnit.DAYS.between(date,row.endDate())))!=0)return null;
            }
            var selected=NotificationRecipients.withdrawal(row.projectId(),mapper.projectPart(row.projectId(),DailyAutomationActions.db(date)),recipients.eligibleRoles(),recipients.recipientScopes());
            if(selected.isEmpty())throw new com.sicost.common.api.ApiException(409,"RECIPIENT_UNASSIGNED","알림 수신자 미지정");
            publisher.publish(new Event(NotificationEventKeys.withdrawal(id,row.endDate(),days),"WTHDR","철수 예정: "+id+" · "+row.endDate(),new Link("UI-05",id),null),selected);
            return null;
        });
    }
    public void cash(LocalDate date) {
        repository.item(List.of("cash:automation","operating-standards:MIN_CASH"),()->{
            YearMonth from=YearMonth.from(date);String closed=mapper.lastClosedMonth();
            if(closed!=null) {
                YearMonth next=YearMonth.parse(closed.substring(0,4)+"-"+closed.substring(4)).plusMonths(1);
                if(next.isAfter(from))from=next;
            }
            var shortage=cash.calculateContiguousRange(from,3).stream().filter(m->m.shortage()&&m.closingBalance()!=null&&!m.status().equals("ACTUAL")).toList();
            if(shortage.isEmpty())return null;
            var selected=NotificationRecipients.cash(recipients.eligibleRoles());
            if(selected.isEmpty())throw new com.sicost.common.api.ApiException(409,"RECIPIENT_UNASSIGNED","알림 수신자 미지정");
            publisher.publish(new Event(NotificationEventKeys.cash(date),"CASH_SHORT","자금 부족 예상: "+String.join(", ",shortage.stream().map(m->m.month()).toList()),new Link("UI-21",shortage.get(0).month()),null),selected);
            return null;
        });
    }
    public void cleanup(LocalDate date) { repository.cleanup(date); }
}
