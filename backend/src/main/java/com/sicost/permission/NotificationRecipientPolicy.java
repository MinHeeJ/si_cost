package com.sicost.permission;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.notifications.NotificationPublicationMapper.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Evaluates a destination account without installing it as the caller or changing SecurityContext. */
@Service
public class NotificationRecipientPolicy {
    private final PermissionService permissions;
    private final PermissionMapper mapper;
    private final NotificationTargetMapper targets;
    private final Clock clock;
    private com.sicost.common.notifications.NotificationPublicationMapper publication;
    private org.springframework.transaction.support.TransactionTemplate fresh;
    @org.springframework.beans.factory.annotation.Autowired
    public void setPublication(com.sicost.common.notifications.NotificationPublicationMapper publication,org.springframework.transaction.PlatformTransactionManager manager) {
        this.publication=publication;fresh=new org.springframework.transaction.support.TransactionTemplate(manager);
        fresh.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }
    public NotificationRecipientPolicy(PermissionService permissions,PermissionMapper mapper,NotificationTargetMapper targets,Clock clock) {this.permissions=permissions;this.mapper=mapper;this.targets=targets;this.clock=clock;}
    public Event review(Event event,String user) {
        // Completion ownership may refer to the QNA/upload inserted in this very
        // transaction. Keep that read local; independently refresh account status.
        if(Set.of("AI_DONE","UPLD_DONE").contains(event.type()))return reviewCurrent(event,user);
        return fresh==null?reviewCurrent(event,user):fresh.execute(status->reviewCurrent(event,user));
    }
    private Event reviewCurrent(Event event,String user) {
        if(user==null || !permissions.activeUser(user))return null;
        String key=event.link()==null?null:event.link().key();
        if(event.type().equals("UPLD_DONE")) {
            var target=key!=null&&key.matches("[0-9]{1,12}")?targets.upload(key):null;
            return target!=null&&user.equals(target.userId())?new Event(event.key(),event.type(),"업로드 반영 완료",null,event.dunning()):null;
        }
        if(event.type().equals("AI_DONE"))return key!=null&&key.matches("[0-9]{1,12}")&&user.equals(mapper.questionOwner(key))?new Event(event.key(),event.type(),"AI 예측 완료",null,event.dunning()):null;
        var capabilities=permissions.capabilitiesFor(user);
        String screen=switch(event.type()) {case "DUN"->"UI-13";case "WTHDR"->"UI-05";case "CASH_SHORT"->"UI-21";case "QUOTE_REJ"->"UI-29";default->"";};
        if(!capabilities.screens().getOrDefault(screen,Set.of()).contains("INQ") || event.link()==null || !screen.equals(event.link().screenId()) || key==null)return null;
        boolean allowed=switch(event.type()) {
            case "DUN" -> project(targets.bill(key),capabilities);
            case "WTHDR" -> project(targets.assignment(key),capabilities);
            case "CASH_SHORT" -> capabilities.scope().all();
            case "QUOTE_REJ" -> {var target=targets.opportunity(key);yield target!=null&&(capabilities.scope().all() || target.partId()!=null&&capabilities.scope().partIds().contains(target.partId()) || target.projectId()!=null&&capabilities.scope().projectIds().contains(target.projectId()));}
            default -> false;
        };
        if(!allowed)return null;
        if(Set.of("DUN","WTHDR","CASH_SHORT").contains(event.type()) && !qualified(event,user,key))return null;
        return event.type().equals("QUOTE_REJ") && !capabilities.sensitive()?new Event(event.key(),event.type(),"견적 검토 결과가 도착했습니다.",event.link(),event.dunning()):event;
    }
    private boolean qualified(Event event,String user,String key) {
        var roles=publication.eligibleRoles();
        if(event.type().equals("CASH_SHORT"))return com.sicost.common.notifications.NotificationRecipients.cash(roles).stream().anyMatch(person->person.userId().equals(user));
        var target=event.type().equals("DUN")?targets.bill(key):targets.assignment(key);
        if(target==null || target.projectId()==null)return false;
        String month=target.month()==null?YearMonth.now(clock.withZone(TimeConfiguration.SEOUL)).toString().replace("-",""):target.month();
        String partId=mapper.projectPart(target.projectId(),month);
        var part=partId==null?null:targets.part(partId,month);
        var scopes=publication.recipientScopes();
        if(event.type().equals("WTHDR"))return com.sicost.common.notifications.NotificationRecipients.withdrawal(target.projectId(),part,roles,scopes).stream().anyMatch(person->person.userId().equals(user));
        int step=event.dunning()==null?0:event.dunning().step();
        return step>=1 && step<=3 && com.sicost.common.notifications.NotificationRecipients.dunning(target.projectId(),part,roles,scopes).get(step-1).recipients().stream().anyMatch(person->person.userId().equals(user));
    }
    public boolean dunning(String user,String bill) {return review(new Event("recipient-check","DUN","",new Link("UI-13",bill),new Dunning(bill,3,1,false)),user)!=null;}
    private boolean project(NotificationTargetMapper.Target target,PermissionService.Capabilities capabilities) {
        if(target==null || target.projectId()==null)return false;
        if(capabilities.scope().all() || capabilities.scope().projectIds().contains(target.projectId()))return true;
        String month=target.month()==null?YearMonth.now(clock.withZone(TimeConfiguration.SEOUL)).toString().replace("-",""):target.month();
        String part=mapper.projectPart(target.projectId(),month);return part!=null&&capabilities.scope().partIds().contains(part);
    }
}
