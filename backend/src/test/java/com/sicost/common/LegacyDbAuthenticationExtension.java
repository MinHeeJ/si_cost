package com.sicost.common;

import java.util.List;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The old real-database business tests were written for the temporary administrator (no login).
 * Requirement 18 replaced that with real authentication, so these tests now run as an
 * authenticated "admin". It is auto-detected, applies ONLY to classes that run against the
 * real test database and are outside com.sicost.basic147 (the session/permission/CSRF tests
 * keep their own real authentication and must never receive this).
 */
public class LegacyDbAuthenticationExtension implements BeforeEachCallback, AfterEachCallback {
    private static boolean applies(ExtensionContext context) {
        Class<?> type = context.getRequiredTestClass();
        if (type.getName().startsWith("com.sicost.basic147.")) return false;
        EnabledIfEnvironmentVariable gate = type.getAnnotation(EnabledIfEnvironmentVariable.class);
        return gate != null && gate.named().equals("SICOST_TEST_DB_URL");
    }
    @Override public void beforeEach(ExtensionContext context) {
        if (!applies(context)) return;
        var empty = SecurityContextHolder.createEmptyContext();
        empty.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("admin", null, List.of()));
        SecurityContextHolder.setContext(empty);
    }
    @Override public void afterEach(ExtensionContext context) {
        if (applies(context)) SecurityContextHolder.clearContext();
    }
}
