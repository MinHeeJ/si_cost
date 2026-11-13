package com.sicost.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.beans.factory.support.BeanDefinitionDefaults;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 영구 OpenAPI 계약의 모든 연산이 컨트롤러에 정확히 하나씩 등록돼 있고, 반대로 컨트롤러의 모든 /api 경로가 계약에 있는지 본다.
 *
 * 컨트롤러는 com.sicost 아래에서 @RestController를 자동으로 찾는다. 서비스나 저장소는 만들지도 목으로 대체하지도 않는다:
 * 컨트롤러 빈을 게으르게 등록만 하고 스프링의 매핑 해석기가 어노테이션만 읽으므로 의존성은 한 번도 필요하지 않다.
 * 그래서 새 컨트롤러를 추가해도 이 시험을 고칠 필요가 없고, 컨텍스트를 띄우지 않아 빠르다.
 */
class OpenApiControllerRegistrationContractTest {
    private static RequestMappingHandlerMapping handlerMapping() {
        try (var context = new GenericApplicationContext()) {
            var scanner = new ClassPathBeanDefinitionScanner(context, false);
            var defaults = new BeanDefinitionDefaults();
            defaults.setLazyInit(true);
            scanner.setBeanDefinitionDefaults(defaults);
            scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
            scanner.scan("com.sicost");
            context.refresh();
            var mapping = new RequestMappingHandlerMapping();
            mapping.setApplicationContext(context);
            mapping.afterPropertiesSet();
            return mapping;
        }
    }

    @Test void controllersAreDiscoveredWithoutAnyManualList() {
        assertThat(handlerMapping().getHandlerMethods()).isNotEmpty();
    }

    @Test void everyDurableOpenApiOperationHasExactlyOneRegisteredLiteralHandler() {
        var handlerMapping = handlerMapping();
        // 계약 파일은 앵커·별칭을 많이 쓴다: SnakeYAML 기본 한도(별칭 50개)를 넘으면 시험이 계약을 읽지도 못한다
        var yaml = new YamlMapFactoryBean() {
            @Override protected org.yaml.snakeyaml.Yaml createYaml() {
                var options = new org.yaml.snakeyaml.LoaderOptions();
                options.setMaxAliasesForCollections(10000);
                options.setAllowDuplicateKeys(false);
                return new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(options));
            }
        };
        yaml.setResources(new ClassPathResource("contracts/openapi.yaml"));
        Map<String, Object> contract = yaml.getObject();
        assertThat(contract).isNotNull();
        assertThat(contract.get("paths")).isInstanceOf(Map.class);
        Map<?, ?> paths = (Map<?, ?>) contract.get("paths");
        Set<String> expected = new HashSet<>();
        Set<String> verbs = Set.of("get", "post", "put", "delete", "patch", "head", "options");
        paths.forEach((path, item) -> ((Map<?, ?>) item).forEach((method, definition) -> {
            if (verbs.contains(method.toString())) {
                Map<?, ?> operation = (Map<?, ?>) definition;
                expected.add(method.toString().toUpperCase(java.util.Locale.ROOT) + " " + normalize(path.toString()));
                assertThat(operation.get("operationId")).isInstanceOf(String.class);
                var matches = handlerMapping.getHandlerMethods().entrySet().stream().filter(entry ->
                    entry.getKey().getPatternValues().stream().anyMatch(p -> normalize(p).equals(normalize(path.toString())))
                    && entry.getKey().getMethodsCondition().getMethods().stream().anyMatch(m -> m.name().equalsIgnoreCase(method.toString())))
                    .toList();
                assertThat(matches).as("%s %s", method, path).hasSize(1);
                assertThat(matches.get(0).getValue().getMethod().getName()).isEqualTo(operation.get("operationId"));
                assertThat(matches.get(0).getKey().getPatternValues()).hasSize(1);
            }
        }));
        Set<String> actual = new HashSet<>();
        handlerMapping.getHandlerMethods().forEach((mapping, handler) -> mapping.getPatternValues().stream()
            .filter(path -> path.startsWith("/api/")).forEach(path -> mapping.getMethodsCondition().getMethods()
                .forEach(method -> actual.add(method.name() + " " + normalize(path)))));
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(expected).isNotEmpty();
    }

    private static String normalize(String path) { return path.replaceAll("\\{[^}]+}", "{}"); }
}
