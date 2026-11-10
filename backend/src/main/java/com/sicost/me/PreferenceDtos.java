package com.sicost.me;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

public final class PreferenceDtos {
    private PreferenceDtos() {}
    public record PreferenceId(String userId, String type, String key) {}
    public record PreferenceWrite(PreferenceId id, String value) {}
    public record StoredPreference(String userId, String type, String key, String value,
                                   String regUserId, LocalDateTime regDttm, String modUserId, LocalDateTime modDttm) {}
    public record PreferenceIssue(String code, String message) {}
    public record Preference(String type, String key, @JsonInclude(JsonInclude.Include.ALWAYS) JsonNode value,
                             @JsonInclude(JsonInclude.Include.ALWAYS) String revision, boolean stored,
                             @JsonInclude(JsonInclude.Include.NON_NULL) PreferenceIssue error) {}
    public record PreferencesData(List<Preference> items) {}
    public record PreferenceSaveResult(boolean changed, Preference preference,
                                       @JsonInclude(JsonInclude.Include.NON_NULL) String message) {}
    public record PutPreferenceRequest(JsonNode value, String expectedRevision) {}
}
