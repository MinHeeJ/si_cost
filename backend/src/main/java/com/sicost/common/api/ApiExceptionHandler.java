package com.sicost.common.api;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> mediaType(org.springframework.web.HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        return error(415, "UNSUPPORTED_MEDIA_TYPE", "요청 콘텐츠 형식을 확인해 주세요.", null, null, request);
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> uploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException e, HttpServletRequest request) {
        if (java.util.Set.of("/api/excel/B1/validate", "/api/excel/B1/apply", "/api/excel/B5/validate", "/api/excel/B5/apply",
                "/api/business-plans/imports/validate", "/api/business-plans/imports/apply",
                "/api/profit-loss/migration/imports/validate", "/api/profit-loss/migration/imports/apply").contains(request.getRequestURI()))
            return error(413, "PAYLOAD_TOO_LARGE", "파일은 10MiB 이하로 선택해 주세요.", "file", null, request);
        if (java.util.Set.of("/api/excel/B7/validate", "/api/excel/B7/apply", "/api/excel/B8/validate", "/api/excel/B8/apply",
                "/api/excel/B9/validate", "/api/excel/B9/apply", "/api/excel/B6/validate", "/api/excel/B6/apply").contains(request.getRequestURI()))
            return error(413, "FILE_TOO_LARGE", "파일은 10MiB 이하로 선택해 주세요.", "file", null, request);
        return unexpected(e, request);
    }
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> application(ApiException e, HttpServletRequest request) {
        return error(e.status(), e.code(), e.getMessage(), e.field(), e.details(), request);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> json(HttpMessageNotReadableException e, HttpServletRequest request) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof UnrecognizedPropertyException) {
                return error(400, "UNKNOWN_FIELD", "허용되지 않은 입력 항목입니다.", null, null, request);
            }
            cause = cause.getCause();
        }
        return error(400, "VALIDATION_ERROR", "입력 형식과 필수 항목을 확인해 주세요.", null, null, request);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest request) {
        var field = e.getBindingResult().getFieldError();
        return error(400, "VALIDATION_ERROR", "입력 항목을 확인해 주세요.",
                field == null ? null : field.getField(), null, request);
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> input(Exception e, HttpServletRequest request) {
        return error(400, "VALIDATION_ERROR", "입력 형식을 확인해 주세요.", null, null, request);
    }
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> missing(Exception e, HttpServletRequest request) {
        return error(404, "NOT_FOUND", "요청한 자료를 찾을 수 없습니다.", null, null, request);
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> database(DataAccessException e, HttpServletRequest request) {
        return error(500, "INTERNAL_ERROR", "자료 처리 중 오류가 발생했습니다.", null, null, request);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest request) {
        return error(500, "INTERNAL_ERROR", "서버 처리 중 오류가 발생했습니다.", null, null, request);
    }
    private ResponseEntity<ApiError> error(int status, String code, String message,
                                            String field, Object details, HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        String id = attribute instanceof String value ? value : UUID.randomUUID().toString();
        return ResponseEntity.status(status).header("X-Request-Id", id)
                .body(new ApiError(false, Map.of(), code, message, field, details, id));
    }
}
