package com.sicost.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;

class RuntimeCheckContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode resource(String name, int limit) throws IOException {
        try (var stream = new ClassPathResource("contracts/" + name).getInputStream()) {
            byte[] bytes = stream.readAllBytes();
            assertThat(bytes.length).isLessThanOrEqualTo(limit);
            return mapper.readTree(bytes);
        }
    }

    /** 앱 사용 확인기가 계약을 읽는 상한(services/agent_sandboxes/quality_agent/graph/nodes/_runtime_smoke.py 의 _OPENAPI_LIMIT, 8MiB)과 같게 둔다. */
    @Test void durableOpenApiFitsRuntimeReaderBudget() throws IOException {
        try (var stream = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            assertThat(stream.readAllBytes().length).isLessThanOrEqualTo(8 * 1024 * 1024);
        }
    }

    @Test void unauthenticatedRuntimeContractMatchesDurableOpenApi() throws IOException {
        var yaml = new YamlMapFactoryBean() {
            @Override protected org.yaml.snakeyaml.Yaml createYaml() {
                var options = new org.yaml.snakeyaml.LoaderOptions();
                options.setMaxAliasesForCollections(10000);
                options.setAllowDuplicateKeys(false);
                return new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(options));
            }
        };
        yaml.setResources(new ClassPathResource("contracts/openapi.yaml"));
        Map<String, Object> openApi = yaml.getObject();
        assertThat(openApi).isNotNull();
        Object schemes = ((Map<?, ?>) openApi.get("components")).get("securitySchemes");
        assertThat(schemes == null || schemes instanceof Map<?, ?> map && map.isEmpty()).isTrue();
        assertThat((Iterable<?>) openApi.get("security")).isEmpty();

        JsonNode runtime = resource("aiops.runtime-check.json", 65536);
        assertThat(fields(runtime)).containsExactlyInAnyOrder("schema_version", "backend", "auth", "first_menu");
        assertThat(runtime.path("schema_version").asInt()).isEqualTo(1);
        assertThat(fields(runtime.path("auth"))).containsExactly("required");
        assertThat(runtime.path("auth").path("required").isBoolean()).isTrue();
        assertThat(runtime.path("auth").path("required").asBoolean()).isFalse();
        JsonNode backend = runtime.path("backend");
        assertThat(fields(backend)).containsExactlyInAnyOrder("service", "container_port", "health");
        assertThat(backend.path("service").asText()).isEqualTo("backend");
        assertThat(backend.path("container_port").asInt()).isEqualTo(8080);
        checkOperation(backend.path("health"), openApi, false);
        checkOperation(runtime.path("first_menu"), openApi, true);
        assertThat(runtime.path("first_menu").path("route").asText()).isEqualTo("/UI-27");
    }

    @Test void firstUseGuideDoesNotInventAuthenticationOrCredentials() throws IOException {
        JsonNode guide = resource("aiops.initial-login.json", 16384);
        assertThat(fields(guide)).containsExactlyInAnyOrder("schema_version", "auth_required", "guide_path");
        assertThat(guide.path("schema_version").asInt()).isEqualTo(1);
        assertThat(guide.path("auth_required").isBoolean()).isTrue();
        assertThat(guide.path("auth_required").asBoolean()).isFalse();
        assertThat(guide.path("guide_path").asText()).isEqualTo("README.md");
    }

    private void checkOperation(JsonNode operation, Map<String, Object> openApi, boolean menu) {
        assertThat(fields(operation)).containsExactlyInAnyOrderElementsOf(menu
            ? Set.of("route", "operation_id", "method", "path", "expect")
            : Set.of("operation_id", "method", "path", "expect"));
        assertThat(operation.path("method").asText()).isEqualTo("GET");
        assertThat(operation.path("path").asText()).matches("/[A-Za-z0-9/_-]{0,199}");
        Map<?, ?> paths = (Map<?, ?>) openApi.get("paths");
        Map<?, ?> item = (Map<?, ?>) paths.get(operation.path("path").asText());
        assertThat(item).isNotNull();
        Map<?, ?> definition = (Map<?, ?>) item.get("get");
        assertThat(definition).isNotNull();
        assertThat(definition.get("operationId")).isEqualTo(operation.path("operation_id").asText());
        assertThat(definition.get("x-roles")).isNull();
        assertThat((Iterable<?>) definition.get("security")).isEmpty();
        assertThat((Iterable<?>) definition.get("parameters")).isEmpty();
        JsonNode expectations = operation.path("expect");
        assertThat(expectations.isArray()).isTrue();
        assertThat(expectations.size()).isBetween(1, 6);
        boolean typeCheck = false;
        for (JsonNode expectation : expectations) {
            assertThat(fields(expectation)).containsExactlyInAnyOrder("path", "op", "value");
            assertThat(expectation.path("path").asText()).matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*){0,7}");
            assertThat(expectation.path("op").asText()).isIn("type", "equals");
            if (expectation.path("op").asText().equals("type")) {
                typeCheck = true;
                assertThat(expectation.path("value").asText()).isIn("object", "array", "string", "number", "boolean", "array_of_string");
            }
        }
        assertThat(typeCheck).isTrue();
    }

    private Set<String> fields(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
