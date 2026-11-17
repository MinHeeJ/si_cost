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
                for(var fields=method.getValue().fields();fields.hasNext();) {var field=fields.next();JsonNode expected=field.getValue();
                    // Req17 adds two user filters and ROLE/MENU history targets; all old parameter definitions remain exact.
                    if(path.getKey().equals("/api/business-users") && method.getKey().equals("get")) {
                        if(field.getKey().equals("parameters")) {
                            var values=(com.fasterxml.jackson.databind.node.ArrayNode)expected.deepCopy();
                            for(String name:List.of("role","includeStopped")) {
                                var addition=java.util.stream.StreamSupport.stream(operation.path("parameters").spliterator(),false)
                                    .filter(p->p.path("name").asText().equals(name)).findFirst().orElseThrow();
                                assertThat(addition.path("required").asBoolean()).isFalse();values.add(addition);
                            }
                            expected=values;
                        } else if(field.getKey().equals("x-query-validation")) {
                            var value=(com.fasterxml.jackson.databind.node.ObjectNode)expected.deepCopy();
                            ((com.fasterxml.jackson.databind.node.ArrayNode)value.path("allowed")).add("role").add("includeStopped");expected=value;
                        }
                    }
                    if(path.getKey().equals("/api/master-history") && field.getKey().equals("parameters")) {
                        var values=expected.deepCopy();for(var parameter:values)if(parameter.path("name").asText().equals("targetType"))
                            ((com.fasterxml.jackson.databind.node.ArrayNode)parameter.path("schema").path("enum")).add("ROLE").add("MENU");expected=values;
                    }
                    assertThat(operation.path(field.getKey())).as(path.getKey()+" "+field.getKey()).isEqualTo(expected);}
            }
        }
        var currentSchemas=current.path("components").path("schemas");
        for(var schemas=baseline.path("schemas").fields();schemas.hasNext();) {
            var schema=schemas.next();var actual=currentSchemas.path(schema.getKey());assertThat(actual.isMissingNode()).as(schema.getKey()).isFalse();
            for(var field:schema.getValue().path("properties"))assertThat(actual.path("properties").has(field.asText())).as(schema.getKey()+"."+field.asText()).isTrue();
            JsonNode required=actual.has("required")?actual.path("required"):mapper.createArrayNode();
            var expectedRequired=(com.fasterxml.jackson.databind.node.ArrayNode)schema.getValue().path("required").deepCopy();
            if(schema.getKey().equals("BusinessUser"))for(String field:List.of("roleIds","headOffice","assignedParts","tempPassword"))expectedRequired.add(field);
            if(schema.getKey().equals("ProjectPm"))expectedRequired.add("status");
            if(Set.of("CreateBusinessUserRequest","PutBusinessUserRequest").contains(schema.getKey())) {
                var fields=mapper.createArrayNode();for(var field:expectedRequired)if(!field.asText().equals("businessRoleIds"))fields.add(field);expectedRequired=fields;
                assertThat(actual.path("oneOf")).as("req17 full/partial role alternatives").hasSize(2);
            }
            assertThat(required).as(schema.getKey()+" required changes limited to req17").isEqualTo(expectedRequired);
        }
        for(var schemas=baseline.path("exactSchemas").fields();schemas.hasNext();) {var schema=schemas.next();assertThat(currentSchemas.path(schema.getKey())).as(schema.getKey()).isEqualTo(schema.getValue());}
    }
}
