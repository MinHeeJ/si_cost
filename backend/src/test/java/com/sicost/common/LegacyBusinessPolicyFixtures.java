package com.sicost.common;

import com.sicost.permission.*;
import com.sicost.authentication.*;
import com.sicost.common.context.CurrentUserProvider;
import java.util.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import static org.mockito.Mockito.*;

/**
 * Explicit privileged baseline for the old, isolated business/controller tests.
 * These tests already mock the caller and are NOT session/permission integration
 * tests. Never import this into the full application or basic147 security tests.
 * Real authentication, scope and revocation coverage remains in those full-stack tests.
 */
@TestConfiguration(proxyBeanMethods=false)
public class LegacyBusinessPolicyFixtures {
    private static PermissionService.Capabilities grants() {
        Map<String,Set<String>> screens=new LinkedHashMap<>();
        for(String id:com.sicost.shared.MenuService.SCREEN_IDS)screens.put(id,Set.of("INQ","REG","UPD","DEL","UPLD","DNLD","CNFM"));
        return new PermissionService.Capabilities(screens,new PermissionService.Scope(true,Set.of(),Set.of()),true,
            Map.of("UI-33",Set.of("desiredRate"),"UI-32",Set.of("name","projectName","renewalDate","contractMm","unitPrice","monthlyLaborCost","employeeType","marginRate","targetUnitPrice","requiredIncreaseRate","annualGain")),
            Map.of("managePartners",true,"automationLogs",true,"analysisPanel",true,"userAccessLogs",true,"roleStop",true),Set.of());
    }
    /** The mocks below are inert stand-ins: skip property population so their @Autowired setters never demand real collaborators. */
    @Bean static org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor legacyBusinessInertMocks() {
        return new org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor() {
            @Override public boolean postProcessAfterInstantiation(Object bean,String name) {
                return !(name.startsWith("legacyBusiness") && org.mockito.Mockito.mockingDetails(bean).isMock());
            }
        };
    }
    @Bean PermissionRowPolicy legacyBusinessRows() {return mock(PermissionRowPolicy.class);}
    @Bean com.sicost.common.history.AccessLogWriter legacyBusinessAccessLog() {return mock(com.sicost.common.history.AccessLogWriter.class);}
    /** A slice that imports a real collaborator must win over the stand-in of the same type (@ConditionalOnMissingBean cannot see later imports). */
    @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor legacyBusinessYieldToRealBeans() {
        class Yield implements org.springframework.beans.factory.config.BeanFactoryPostProcessor,org.springframework.core.PriorityOrdered {
            @Override public int getOrder() {return org.springframework.core.Ordered.LOWEST_PRECEDENCE;}
            @Override public void postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory factory) {
                var registry=(org.springframework.beans.factory.support.BeanDefinitionRegistry)factory;
                for(String name:factory.getBeanDefinitionNames()) {
                    if(!name.startsWith("legacyBusiness") || name.equals("legacyBusinessYieldToRealBeans") || name.equals("legacyBusinessInertMocks") || name.equals("legacyBusinessCaller"))continue;
                    Class<?> type=factory.getType(name,false);if(type==null)continue;
                    for(String other:factory.getBeanNamesForType(type,true,false))
                        if(!other.startsWith("legacyBusiness") && !other.equals(name)) {registry.removeBeanDefinition(name);break;}
                }
            }
        }
        return new Yield();
    }
    @Bean @ConditionalOnMissingBean PermissionService legacyBusinessPermissions() {
        var policy=mock(PermissionService.class);
        when(policy.capabilities()).thenReturn(grants());when(policy.userId()).thenReturn("admin");
        when(policy.allows(anyString(),anyString())).thenReturn(true);
        when(policy.hasRole(anyString())).thenAnswer(call->"ADMIN".equals(call.getArgument(0)));
        when(policy.activeUser(anyString())).thenReturn(true);when(policy.candidateRateAllowed()).thenReturn(true);
        when(policy.projectAllowed(anyString(),anyString())).thenReturn(true);when(policy.currentProjectAllowed(anyString())).thenReturn(true);
        return policy;
    }
    @Bean @Primary CurrentUserProvider legacyBusinessCaller() {
        var caller=mock(CurrentUserProvider.class);
        when(caller.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),false,"ALL"));
        when(caller.userId()).thenReturn("admin");when(caller.allows(anyString())).thenReturn(true);return caller;
    }
    @Bean @ConditionalOnMissingBean PermissionProjectionService legacyBusinessProjection(PermissionService permissions) {return new PermissionProjectionService(permissions);}
    @Bean @ConditionalOnMissingBean AuthenticationTaskService legacyBusinessTasks() {
        var tasks=mock(AuthenticationTaskService.class);when(tasks.capture(any())).thenReturn(mock(AuthenticationTaskService.Proof.class));
        when(tasks.run(any(),any())).thenAnswer(call->{
            var previous=org.springframework.security.core.context.SecurityContextHolder.getContext();
            try {
                var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("admin",null,List.of()));
                org.springframework.security.core.context.SecurityContextHolder.setContext(context);
                return ((java.util.function.Supplier<?>)call.getArgument(1)).get();
            } finally {org.springframework.security.core.context.SecurityContextHolder.setContext(previous);}
        });return tasks;
    }
    @Bean @ConditionalOnMissingBean PermissionExportProjection legacyBusinessExports(PermissionProjectionService fields,PermissionService permissions,AuthenticationTaskService tasks) {return new PermissionExportProjection(fields,permissions,tasks);}
    @Bean @ConditionalOnMissingBean PermissionBusinessPolicy legacyBusinessBodies() {return mock(PermissionBusinessPolicy.class);}
    @Bean @ConditionalOnMissingBean PermissionOperationRegistry legacyBusinessOperations() {var value=mock(PermissionOperationRegistry.class);when(value.preHandle(any(),any(),any())).thenReturn(true);return value;}
    @Bean @ConditionalOnMissingBean AuthenticationService legacyBusinessSessions() {return mock(AuthenticationService.class);}
    @Bean @ConditionalOnMissingBean AuthenticationPasswordService legacyBusinessPasswords() {return mock(AuthenticationPasswordService.class);}
    @Bean @ConditionalOnMissingBean AuthenticationMapper legacyBusinessAccounts(org.springframework.beans.factory.ObjectProvider<javax.sql.DataSource> sources) throws Exception {
        var source=sources.getIfAvailable();if(source==null)return mock(AuthenticationMapper.class);
        var factory=new org.mybatis.spring.SqlSessionFactoryBean();factory.setDataSource(source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setMapUnderscoreToCamelCase(true);configuration.addMapper(AuthenticationMapper.class);factory.setConfiguration(configuration);
        return new org.mybatis.spring.SqlSessionTemplate(factory.getObject()).getMapper(AuthenticationMapper.class);
    }
    @Bean @ConditionalOnMissingBean NotificationRecipientPolicy legacyBusinessRecipients() {
        var value=mock(NotificationRecipientPolicy.class);when(value.review(any(),any())).thenAnswer(call->call.getArgument(0));when(value.dunning(anyString(),anyString())).thenReturn(true);return value;
    }
    @Bean @ConditionalOnMissingBean NotificationProjectionService legacyBusinessNotifications() {
        var value=mock(NotificationProjectionService.class);
        when(value.project(org.mockito.ArgumentMatchers.<com.sicost.notificationsops.NotificationDtos.Item>any())).thenAnswer(call->call.getArgument(0));
        when(value.project(org.mockito.ArgumentMatchers.<List<com.sicost.notificationsops.NotificationDtos.Item>>any())).thenAnswer(call->call.getArgument(0));return value;
    }
    @Bean com.sicost.common.concurrent.RevisionService legacyBusinessRevisions(com.fasterxml.jackson.databind.ObjectMapper json) {return new com.sicost.common.concurrent.RevisionService(json);}
    @Bean @ConditionalOnMissingBean com.sicost.currentuserops.PermissionSettingsService legacyBusinessSettings() {
        var value=mock(com.sicost.currentuserops.PermissionSettingsService.class);
        when(value.getCurrentUserPermissionSettings()).thenReturn(new com.sicost.currentuserops.PermissionSettingsDtos.Settings("admin",List.of("ADMIN"),List.of(),true,new com.sicost.currentuserops.PermissionSettingsDtos.ScopeUnion(true,List.of(),List.of()),List.of(),List.of(),false,true,"business-unit-fixture"));return value;
    }
}
