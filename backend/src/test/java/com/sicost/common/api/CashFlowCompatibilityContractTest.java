package com.sicost.common.api;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.assertThat;

/** Existing consumers keep their optional filters and whole-contribution completeness contract. */
class CashFlowCompatibilityContractTest {
    @SuppressWarnings("unchecked")
    Map<String,Object> contract() throws Exception {
        var options=new LoaderOptions();options.setMaxAliasesForCollections(10000);options.setCodePointLimit(8*1024*1024);
        try(var stream=new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            return new Yaml(new SafeConstructor(options)).load(stream);
        }
    }
    @Test void existingCandidateAndWorkforceFiltersRemainOptional() throws Exception {
        var paths=(Map<?,?>)contract().get("paths");
        Map<String,List<String>> expected=Map.of("/api/candidates",List.of("activeOnly"),
            "/api/withdrawals",List.of("sort","direction"),"/api/withdrawals/export",List.of("sort","direction"),
            "/api/workforce-operations/export",List.of("sort","direction"),"/api/workforce-recommendations/history",List.of("sort","direction"));
        expected.forEach((path,names)->{
            var operation=(Map<?,?>)((Map<?,?>)paths.get(path)).get("get");
            var parameters=(List<?>)operation.get("parameters");
            names.forEach(name->{
                var parameter=parameters.stream().map(Map.class::cast).filter(p->name.equals(p.get("name"))).findFirst().orElseThrow();
                assertThat(parameter.get("required")).isNotEqualTo(true);
            });
            assertThat(operation.get("x-query-validation")).isNotNull();
        });
        assertThat(((Map<?,?>)((Map<?,?>)paths.get("/api/candidates")).get("get")).get("x-aiops-query-role")).isNotNull();
        var schemas=(Map<?,?>)((Map<?,?>)contract().get("components")).get("schemas");
        var candidate=(Map<?,?>)((Map<?,?>)schemas.get("CandidateFilters")).get("properties");
        assertThat(candidate.containsKey("activeOnly")).isTrue();assertThat(candidate.containsKey("availableUntil")).isTrue();
    }
    @Test void cashCompletenessFieldsAreAdditiveAndUnknownCardAmountIsAllowed() throws Exception {
        var schemas=(Map<?,?>)((Map<?,?>)contract().get("components")).get("schemas");
        var schema=(Map<?,?>)schemas.get("FinancialContributions");
        var required=(List<?>)schema.get("required");
        assertThat(required.contains("verified")).isTrue();assertThat(required.contains("cashVerified")).isFalse();assertThat(required.contains("cashIssues")).isFalse();
        var properties=(Map<?,?>)schema.get("properties");
        assertThat(properties.containsKey("cashVerified")).isTrue();assertThat(properties.containsKey("cashIssues")).isTrue();
        assertThat(((Map<?,?>)properties.get("cashCardExpense")).get("anyOf")).isInstanceOf(List.class);
    }
}
