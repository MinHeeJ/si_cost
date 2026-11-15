package com.sicost.developersops;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
public class DeveloperStatusSchedule {
    private final DeveloperStatusRecalculationService states;
    public DeveloperStatusSchedule(DeveloperStatusRecalculationService states) { this.states=states; }
    public void started() { states.refresh(); }
    public void midnight() { states.refresh(); }
}
