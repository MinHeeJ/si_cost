package com.sicost.states;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class DeveloperStateSchedule {
    private final DeveloperStateService service;
    public DeveloperStateSchedule(DeveloperStateService service){this.service=service;}
    @EventListener(ApplicationReadyEvent.class)
    public void startup(){service.recalculateAll();}
    @Scheduled(cron="0 0 0 * * *",zone="Asia/Seoul")
    public void midnight(){service.recalculateAll();}
}
