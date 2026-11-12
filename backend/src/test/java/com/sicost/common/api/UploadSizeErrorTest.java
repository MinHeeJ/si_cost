package com.sicost.common.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import static org.assertj.core.api.Assertions.assertThat;

class UploadSizeErrorTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @ParameterizedTest
    @ValueSource(strings={"/api/excel/B1/validate","/api/excel/B1/apply","/api/excel/B5/validate","/api/excel/B5/apply"})
    void assignmentAndPayrollMultipartLimitsUseTheirContractError(String path) {
        var request = new MockHttpServletRequest("POST", path);
        request.setAttribute(RequestIdFilter.ATTRIBUTE, "upload-size-request");
        var response = handler.uploadSize(new MaxUploadSizeExceededException(10_485_760), request);
        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat(response.getBody().field()).isEqualTo("file");
        assertThat(response.getBody().requestId()).isEqualTo("upload-size-request");
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("upload-size-request");
    }

    @ParameterizedTest
    @ValueSource(strings={"B7","B8","B9","B6"})
    void existingImportLimitsKeepTheirOriginalError(String form) {
        for (String action : new String[]{"validate","apply"}) {
            var request = new MockHttpServletRequest("POST", "/api/excel/" + form + "/" + action);
            var response = handler.uploadSize(new MaxUploadSizeExceededException(10_485_760), request);
            assertThat(response.getStatusCode().value()).isEqualTo(413);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("FILE_TOO_LARGE");
        }
    }
}
