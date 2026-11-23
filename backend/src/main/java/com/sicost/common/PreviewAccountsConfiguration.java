package com.sicost.common;

import java.util.stream.Stream;

import org.flywaydb.core.api.Location;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PreviewAccountsConfiguration {

    @Bean
    FlywayConfigurationCustomizer previewAccountMigrations(
            @Value("${SICOST_COMPOSE_PROJECT:}") String composeProject) {
        return configuration -> {
            if (composeProject.startsWith("syncops-preview-")) {
                String[] locations = Stream.concat(
                        Stream.of(configuration.getLocations()).map(Location::getDescriptor),
                        Stream.of("classpath:db/preview")
                ).distinct().toArray(String[]::new);
                configuration.locations(locations);
            }
        };
    }
}
