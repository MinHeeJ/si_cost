package com.sicost.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultMatcher;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.assertThat;

/** Shared fixture lookup; HTTP requests and business assertions stay in each operation test. */
public final class DurableOpenApi {
    private static final Map<?, ?> CONTRACT = load();
    private static final ObjectMapper JSON = new ObjectMapper();

    private DurableOpenApi() {}

    /** Add uploaded files to an explicit POST request while preserving its parameters and headers. */
    public static org.springframework.test.web.servlet.request.RequestPostProcessor multipart(
        org.springframework.mock.web.MockMultipartFile... files) {
        return original -> {
            var request = new org.springframework.mock.web.MockMultipartHttpServletRequest(original.getServletContext());
            request.setMethod(original.getMethod());
            request.setRequestURI(original.getRequestURI());
            request.setServletPath(original.getServletPath());
            request.setQueryString(original.getQueryString());
            request.setCharacterEncoding(original.getCharacterEncoding());
            request.setParameters(original.getParameterMap());
            java.util.Collections.list(original.getHeaderNames()).forEach(name ->
                java.util.Collections.list(original.getHeaders(name)).forEach(value -> request.addHeader(name,value)));
            java.util.Collections.list(original.getAttributeNames()).forEach(name -> request.setAttribute(name,original.getAttribute(name)));
            for (var file : files) request.addFile(file);
            request.setContentType(MediaType.MULTIPART_FORM_DATA_VALUE);
            return request;
        };
    }

    private static Map<?, ?> load() {
        var options = new LoaderOptions();
        options.setMaxAliasesForCollections(10000);
        options.setAllowDuplicateKeys(false);
        try (var input = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            return new Yaml(new SafeConstructor(options)).load(input);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("영구 OpenAPI 계약을 읽을 수 없습니다.", error);
        }
    }

    private static Map<?, ?> resolve(Map<?, ?> definition) {
        Object reference = definition.get("$ref");
        if (reference == null) return definition;
        String ref = reference.toString();
        assertThat(ref).startsWith("#/");
        Object value = CONTRACT;
        for (String segment : ref.substring(2).split("/")) {
            value = ((Map<?, ?>) value).get(segment.replace("~1", "/").replace("~0", "~"));
        }
        assertThat(value).as(ref).isInstanceOf(Map.class);
        return resolve((Map<?, ?>) value);
    }

    public static ResultMatcher response(String method, String path, int status) {
        var paths = (Map<?, ?>) CONTRACT.get("paths");
        assertThat(paths.get(path)).as("OpenAPI path %s", path).isInstanceOf(Map.class);
        var operation = (Map<?, ?>) ((Map<?, ?>) paths.get(path)).get(method.toLowerCase(Locale.ROOT));
        assertThat(operation).as("%s %s", method, path).isNotNull();
        assertThat(operation.get("operationId")).isInstanceOf(String.class);
        var responses = (Map<?, ?>) operation.get("responses");
        assertThat(responses.get(Integer.toString(status))).as("declared status %s", status).isInstanceOf(Map.class);
        var declared = resolve((Map<?, ?>) responses.get(Integer.toString(status)));
        var content = (Map<?, ?>) declared.get("content");
        assertThat(content).isNotNull();
        assertThat(content.get("application/json")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) content.get("application/json")).get("schema")).isInstanceOf(Map.class);
        return result -> {
            var actual = result.getResponse();
            assertThat(actual.getStatus()).isEqualTo(status);
            assertThat(actual.getContentType()).isNotNull();
            assertThat(MediaType.parseMediaType(actual.getContentType()).isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
            JsonNode body = JSON.readTree(actual.getContentAsByteArray());
            assertThat(body.isObject()).isTrue();
            assertThat(body.path("success").isBoolean()).isTrue();
            assertThat(body.path("success").booleanValue()).isEqualTo(status < 400);
            assertThat(body.path("meta").isObject()).isTrue();
            if (status < 400) {
                assertThat(body.hasNonNull("data")).isTrue();
            } else {
                assertThat(body.path("code").isTextual()).isTrue();
                assertThat(body.path("message").asText()).isNotBlank();
                assertThat(body.path("requestId").asText()).isNotBlank();
                assertThat(actual.getHeader("X-Request-Id")).isEqualTo(body.path("requestId").asText());
            }
        };
    }
}
