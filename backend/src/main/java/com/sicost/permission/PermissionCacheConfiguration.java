package com.sicost.permission;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;

/**
 * SQL scoping happens at statement preparation. An earlier executor cache key
 * therefore cannot distinguish calculation source reads from scoped reads, nor
 * authorization changes within a transaction. Never cache their results or
 * reuse a differently scoped prepared statement under that earlier SQL key.
 */
@Configuration
public class PermissionCacheConfiguration {
    @Bean ConfigurationCustomizer permissionCacheBoundaries() {
        return configuration->{
            configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
            configuration.setCacheEnabled(false);
            configuration.setDefaultExecutorType(org.apache.ibatis.session.ExecutorType.SIMPLE);
        };
    }
}
