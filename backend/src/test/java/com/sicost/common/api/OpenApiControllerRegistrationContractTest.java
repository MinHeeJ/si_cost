package com.sicost.common.api;

import com.sicost.codegroups.CodeGroupController;
import com.sicost.codegroups.CodeGroupService;
import com.sicost.codemappings.CodeMappingsController;
import com.sicost.codemappings.CodeMappingsService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.health.HealthController;
import com.sicost.common.health.HealthService;
import com.sicost.me.PreferenceController;
import com.sicost.me.PreferenceService;
import com.sicost.operatingstandards.OperatingStandardsController;
import com.sicost.operatingstandards.OperatingStandardsService;
import com.sicost.shared.MenuService;
import com.sicost.shared.SharedQueriesController;
import com.sicost.shared.SharedQueriesService;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest({HealthController.class, SharedQueriesController.class, CodeGroupController.class,
        CodeMappingsController.class, PreferenceController.class, OperatingStandardsController.class})
@ContextConfiguration(classes={HealthController.class, SharedQueriesController.class, CodeGroupController.class,
        CodeMappingsController.class, PreferenceController.class, OperatingStandardsController.class})
class OpenApiControllerRegistrationContractTest {
    @Autowired RequestMappingHandlerMapping handlerMapping;
    @MockBean HealthService health;
    @MockBean SharedQueriesService shared;
    @MockBean MenuService menus;
    @MockBean CurrentUserProvider users;
    @MockBean CodeGroupService groups;
    @MockBean CodeMappingsService mappings;
    @MockBean PreferenceService preferences;
    @MockBean OperatingStandardsService standards;

    @Test void everyDurableOpenApiOperationHasExactlyOneRegisteredLiteralHandler() {
        var yaml=new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource("contracts/openapi.yaml"));
        Map<String,Object> contract=yaml.getObject();
        assertThat(contract).isNotNull();
        assertThat(contract.get("paths")).isInstanceOf(Map.class);
        Map<?,?> paths=(Map<?,?>)contract.get("paths");
        Set<String> expected=new HashSet<>();
        Set<String> verbs=Set.of("get","post","put","delete","patch","head","options");
        paths.forEach((path,item)->((Map<?,?>)item).forEach((method,definition)->{
            if(verbs.contains(method.toString())) {
                Map<?,?> operation=(Map<?,?>)definition;
                expected.add(method.toString().toUpperCase(java.util.Locale.ROOT)+" "+normalize(path.toString()));
                assertThat(operation.get("operationId")).isInstanceOf(String.class);
                var matches=handlerMapping.getHandlerMethods().entrySet().stream().filter(entry->
                    entry.getKey().getPatternValues().stream().anyMatch(p->normalize(p).equals(normalize(path.toString())))
                    && entry.getKey().getMethodsCondition().getMethods().stream().anyMatch(m->m.name().equalsIgnoreCase(method.toString())))
                    .toList();
                assertThat(matches).as("%s %s",method,path).hasSize(1);
                assertThat(matches.get(0).getValue().getMethod().getName()).isEqualTo(operation.get("operationId"));
                assertThat(matches.get(0).getKey().getPatternValues()).hasSize(1);
            }
        }));
        Set<String> actual=new HashSet<>();
        handlerMapping.getHandlerMethods().forEach((mapping,handler)->mapping.getPatternValues().stream()
            .filter(path->path.startsWith("/api/")).forEach(path->mapping.getMethodsCondition().getMethods()
                .forEach(method->actual.add(method.name()+" "+normalize(path)))));
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(expected).isNotEmpty();
    }
    private static String normalize(String path) { return path.replaceAll("\\{[^}]+}","{}"); }
}
