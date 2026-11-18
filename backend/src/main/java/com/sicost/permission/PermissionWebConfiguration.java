package com.sicost.permission;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class PermissionWebConfiguration implements WebMvcConfigurer {
    private final PermissionOperationRegistry registry;
    public PermissionWebConfiguration(PermissionOperationRegistry registry) {this.registry=registry;}
    @Override public void addInterceptors(InterceptorRegistry interceptors) {interceptors.addInterceptor(registry).addPathPatterns("/api/**");}
}
