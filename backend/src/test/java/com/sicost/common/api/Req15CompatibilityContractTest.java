package com.sicost.common.api;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.*;

class Req15CompatibilityContractTest {
    @Test void existingOperationsParametersOptionalFiltersAndFinancialNullabilityRemainCompatible() throws Exception {
        var mapper=new ObjectMapper();var options=new LoaderOptions();options.setCodePointLimit(8*1024*1024);options.setMaxAliasesForCollections(10000);options.setAllowDuplicateKeys(false);
        JsonNode current;
        try(var in=new ClassPathResource("contracts/openapi.yaml").getInputStream()) {current=mapper.valueToTree(new Yaml(new SafeConstructor(options)).load(in));}
        JsonNode baseline;
        try(var in=new ClassPathResource("contracts/req15-main-compatibility.json").getInputStream()) {baseline=mapper.readTree(in);}
        for(var paths=baseline.path("operations").fields();paths.hasNext();) {
            var path=paths.next();
            for(var methods=path.getValue().fields();methods.hasNext();) {
                var method=methods.next();var operation=current.path("paths").path(path.getKey()).path(method.getKey());
                assertThat(operation.isMissingNode()).as(path.getKey()+" "+method.getKey()).isFalse();
                for(var fields=method.getValue().fields();fields.hasNext();) {var field=fields.next();assertThat(operation.path(field.getKey())).as(path.getKey()+" "+field.getKey()).isEqualTo(field.getValue());}
            }
        }
        var currentSchemas=current.path("components").path("schemas");
        for(var schemas=baseline.path("schemas").fields();schemas.hasNext();) {
            var schema=schemas.next();var actual=currentSchemas.path(schema.getKey());assertThat(actual.isMissingNode()).as(schema.getKey()).isFalse();
            for(var field:schema.getValue().path("properties"))assertThat(actual.path("properties").has(field.asText())).as(schema.getKey()+"."+field.asText()).isTrue();
            JsonNode required=actual.has("required")?actual.path("required"):mapper.createArrayNode();
            assertThat(required).as(schema.getKey()+" required unchanged").isEqualTo(schema.getValue().path("required"));
        }
        for(var schemas=baseline.path("exactSchemas").fields();schemas.hasNext();) {var schema=schemas.next();assertThat(currentSchemas.path(schema.getKey())).as(schema.getKey()).isEqualTo(schema.getValue());}
    }
}
