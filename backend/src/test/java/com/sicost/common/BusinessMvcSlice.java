package com.sicost.common;

import java.lang.annotation.*;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.*;

/**
 * Isolates legacy controller/business-contract slices from Boot's unrelated default
 * HTTP Basic configuration. Real session/CSRF/permission tests use the full application
 * and must never use this annotation. Request-ID and business filters remain enabled.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@org.springframework.context.annotation.Import(LegacyBusinessPolicyFixtures.class)
@ImportAutoConfiguration(exclude={SecurityAutoConfiguration.class,SecurityFilterAutoConfiguration.class,UserDetailsServiceAutoConfiguration.class})
public @interface BusinessMvcSlice {}
