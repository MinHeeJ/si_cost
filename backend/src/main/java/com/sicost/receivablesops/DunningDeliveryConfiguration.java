package com.sicost.receivablesops;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class DunningDeliveryConfiguration {
    @Bean(name="dunningDeliveryExecutor")
    public ThreadPoolTaskExecutor dunningDeliveryExecutor() {
        var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(1);executor.setMaxPoolSize(2);executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("dunning-delivery-");executor.setWaitForTasksToCompleteOnShutdown(true);executor.setAwaitTerminationSeconds(20);
        return executor;
    }
}
