package com.sicost.developersops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DeveloperBulkInputTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void acceptsRevisionAndTheDocumentedExpectedRevisionName() throws Exception {
        var request = DeveloperActionsInput.bulk(JSON.readTree("{\"targetUseYn\":\"N\",\"items\":["
            + "{\"id\":\"DEV-000001\",\"revision\":\"r1\"},{\"id\":\"DEV-000002\",\"expectedRevision\":\"r2\"}]}"));
        assertThat(request.items()).extracting("id", "revision").containsExactly(
            tuple("DEV-000001", "r1"), tuple("DEV-000002", "r2"));
    }
    @Test void rejectsBothNamesOnOneItemAndUnknownItemFields() throws Exception {
        assertThatThrownBy(() -> DeveloperActionsInput.bulk(JSON.readTree("{\"targetUseYn\":\"N\",\"items\":["
            + "{\"id\":\"DEV-000001\",\"revision\":\"a\",\"expectedRevision\":\"b\"}]}"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> DeveloperActionsInput.bulk(JSON.readTree("{\"targetUseYn\":\"N\",\"items\":["
            + "{\"id\":\"DEV-000001\",\"revision\":\"a\",\"extra\":1}]}"))).isInstanceOf(ApiException.class);
    }
    @Test void stillRejectsDuplicateIdsAndMissingRevision() throws Exception {
        assertThatThrownBy(() -> DeveloperActionsInput.bulk(JSON.readTree("{\"targetUseYn\":\"N\",\"items\":["
            + "{\"id\":\"DEV-000001\",\"revision\":\"a\"},{\"id\":\"DEV-000001\",\"expectedRevision\":\"b\"}]}"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> DeveloperActionsInput.bulk(JSON.readTree("{\"targetUseYn\":\"N\",\"items\":["
            + "{\"id\":\"DEV-000001\"}]}"))).isInstanceOf(ApiException.class);
    }
}
