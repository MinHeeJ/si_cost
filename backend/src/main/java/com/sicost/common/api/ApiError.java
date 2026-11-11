package com.sicost.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(boolean success, Map<String, Object> meta, String code,
                       String message, String field, Object details, String requestId) {
}
