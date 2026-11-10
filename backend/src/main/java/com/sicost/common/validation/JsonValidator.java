package com.sicost.common.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonValidator {
    private final ObjectMapper mapper;
    public JsonValidator(ObjectMapper mapper) { this.mapper = mapper; }
    public JsonNode parse(String value) {
        String json = InputValidator.text(value, 2000, "value");
        try {
            JsonNode node = mapper.readTree(json);
            if (node == null) throw InputValidator.invalid("value");
            return node;
        } catch (JsonProcessingException e) {
            throw InputValidator.invalid("value");
        }
    }
    public boolean booleanValue(JsonNode value) {
        if (value == null || !value.isBoolean()) throw InputValidator.invalid("value");
        return value.booleanValue();
    }
}
