package com.sicost.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock serverClock() {
        return Clock.system(SEOUL);
    }
}
