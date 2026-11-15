package com.sicost.workforcerecommendations;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class RecommendationCompatibilityTest {
    @Test void recommendationContractPreservesExistingCandidateAndCalendarOptions() {
        var yaml=new YamlMapFactoryBean(){
            @Override protected org.yaml.snakeyaml.Yaml createYaml(){
                var options=new org.yaml.snakeyaml.LoaderOptions();options.setMaxAliasesForCollections(10000);
                return new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(options));
            }
        };
        yaml.setResources(new ClassPathResource("contracts/openapi.yaml"));
        Map<?,?> root=yaml.getObject();Map<?,?> paths=(Map<?,?>)root.get("paths");
        assertThat(parameters(paths,"/api/candidates")).contains("availableUntil","activeOnly");
        assertThat(parameters(paths,"/api/workforce-operations/export")).contains("sort","direction");
        Map<?,?> schemas=(Map<?,?>)((Map<?,?>)root.get("components")).get("schemas");
        Map<?,?> properties=(Map<?,?>)((Map<?,?>)schemas.get("CandidateFilters")).get("properties");
        assertThat(properties.containsKey("availableUntil")).isTrue();assertThat(properties.containsKey("activeOnly")).isTrue();
        Map<?,?> details=(Map<?,?>)schemas.get("ErrorDetails");
        assertThat((List<?>)details.get("required")).isEmpty();
    }
    private Set<String> parameters(Map<?,?> paths,String path){
        Map<?,?> get=(Map<?,?>)((Map<?,?>)paths.get(path)).get("get");Set<String> result=new HashSet<>();
        for(Object parameter:(List<?>)get.get("parameters"))result.add((String)((Map<?,?>)parameter).get("name"));
        return result;
    }
}
