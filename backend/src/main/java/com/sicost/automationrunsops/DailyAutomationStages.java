package com.sicost.automationrunsops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.notifications.ReceivableDunningCoordinator;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import static com.sicost.automationrunsops.DailyAutomationDtos.*;

@Service
public class DailyAutomationStages {
    private final DailyAutomationActions actions;
    private final DailyAutomationNotifications notifications;
    private final ObjectProvider<ReceivableDunningCoordinator> dunning;
    private final Clock clock;
    public DailyAutomationStages(DailyAutomationActions actions,DailyAutomationNotifications notifications,ObjectProvider<ReceivableDunningCoordinator> dunning,Clock clock) {
        this.actions=actions;this.notifications=notifications;this.dunning=dunning;this.clock=clock;
    }
    public void execute(Key key,int stage) {
        if(!key.runDate().equals(LocalDate.now(clock.withZone(TimeConfiguration.SEOUL))))throw new ItemFailure(stage,key.jobCode());
        if(key.jobCode().equals("RECEIVABLE_DUNNING")) {
            ReceivableDunningCoordinator coordinator=dunning.getIfAvailable();
            if(coordinator==null)throw new ItemFailure(stage,"RECEIVABLE_DUNNING");
            items(stage,coordinator.businessKeys(stage,key.runDate()),id->coordinator.process(stage,id,key.runDate()));return;
        }
        if(stage<=4)items(stage,actions.keys(stage,key.runDate()),id->actions.process(stage,id,key.runDate()));
        else if(stage==5)items(stage,notifications.withdrawalKeys(key.runDate()),id->notifications.withdrawal(id,key.runDate()));
        else if(stage==6)items(stage,List.of(key.runDate().toString()),id->notifications.cash(key.runDate()));
        else if(stage==7)items(stage,List.of(key.runDate().toString()),id->notifications.cleanup(key.runDate()));
        else throw new IllegalArgumentException("자동 처리 단계를 확인해 주세요.");
    }
    private void items(int stage,List<String> keys,Consumer<String> action) {
        for(String key:keys.stream().distinct().sorted().toList()) {
            try { action.accept(key); }
            catch(RuntimeException error) { throw new ItemFailure(stage,key); }
        }
    }
    public void recoverAbandonedSending() {
        var coordinator=dunning.getIfAvailable();if(coordinator!=null)coordinator.recoverAbandonedSending();
    }
    public static class ItemFailure extends RuntimeException {
        private final String publicMessage;
        public ItemFailure(int stage,String key) {
            String safe=key!=null&&key.matches("[A-Za-z0-9_:.-]{1,160}")?key:"JOB";
            publicMessage="단계 "+stage+" / "+safe+" / 처리 또는 필수 감사 저장 실패";
        }
        public String publicMessage() { return publicMessage; }
    }
}
