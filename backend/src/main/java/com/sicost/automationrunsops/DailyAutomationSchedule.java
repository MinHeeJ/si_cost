package com.sicost.automationrunsops;

import com.sicost.common.TimeConfiguration;
import java.time.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import static com.sicost.automationrunsops.DailyAutomationDtos.*;

@Configuration
@EnableScheduling
public class DailyAutomationSchedule implements org.springframework.context.ApplicationContextAware {
    private org.springframework.context.ApplicationContext context;
    @Override public void setApplicationContext(org.springframework.context.ApplicationContext context) {this.context=context;}
    private boolean operatorProcess() {return context!=null && !(context instanceof org.springframework.web.context.WebApplicationContext);}
    private final DailyAutomationService service;
    private final DailyAutomationRepository repository;
    private final DailyAutomationStages stages;
    private final Clock clock;
    public DailyAutomationSchedule(DailyAutomationService service,DailyAutomationRepository repository,DailyAutomationStages stages,Clock clock) {
        this.service=service;this.repository=repository;this.stages=stages;this.clock=clock;
    }
    @Bean(name="dailyAutomationExecutor")
    public static ThreadPoolTaskExecutor dailyAutomationExecutor() {
        var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(2);executor.setMaxPoolSize(2);executor.setQueueCapacity(2);
        executor.setThreadNamePrefix("daily-automation-");executor.setWaitForTasksToCompleteOnShutdown(true);executor.setAwaitTerminationSeconds(30);return executor;
    }
    @Scheduled(cron="0 0 2 * * *",zone="Asia/Seoul")
    public void daily() { if(!operatorProcess())service.scheduled("DAILY_OPERATIONS"); }
    @Scheduled(cron="0 0 9 * * *",zone="Asia/Seoul")
    public void receivables() { if(!operatorProcess())service.scheduled("RECEIVABLE_DUNNING"); }
    @EventListener(ApplicationReadyEvent.class)
    public void startup() {
        if(operatorProcess())return;
        stages.recoverAbandonedSending();
        ZonedDateTime now=ZonedDateTime.now(clock.withZone(TimeConfiguration.SEOUL));
        if(now.toLocalTime().isBefore(LocalTime.of(2,0)))return;
        Stored run=repository.find(new Key("DAILY_OPERATIONS",now.toLocalDate()));
        if(run!=null&&!run.state().equals("SUCCEEDED"))service.scheduled("DAILY_OPERATIONS");
    }
}
