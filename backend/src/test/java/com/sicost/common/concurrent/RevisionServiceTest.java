package com.sicost.common.concurrent;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RevisionServiceTest {
    final RevisionService revisions = new RevisionService(JsonMapper.builder().findAndAddModules().build());
    record Stored(String id, String value, LocalDateTime regDttm, LocalDateTime modDttm) {}
    @Test void bothNullTimestampsStillIncludeStoredValues() {
        assertThat(revisions.revision(new Stored("A", "1", null, null)))
                .isNotEqualTo(revisions.revision(new Stored("A", "2", null, null)));
    }
    @Test void oneNullTimestampIsNotEqualToNonNull() {
        var time = LocalDateTime.of(2026, 1, 1, 0, 0);
        assertThat(revisions.revision(new Stored("A", "1", time, null)))
                .isNotEqualTo(revisions.revision(new Stored("A", "1", null, time)));
    }
    @Test void fullAggregateIncludesOtherPagesAndEveryEffectiveMonth() {
        var first = new Stored("A:202601", "1", null, null);
        assertThat(revisions.revision(Arrays.asList(first, new Stored("A:202602", "2", null, null))))
                .isNotEqualTo(revisions.revision(Arrays.asList(first, new Stored("A:202602", "3", null, null))));
    }
    @Test void mapInsertionOrderDoesNotChangeRevision() {
        assertThat(revisions.revision(Map.of("id", "A", "value", "1"))).isEqualTo(revisions.revision(Map.of("value", "1", "id", "A")));
    }
    @Test void clientDetailAndSelectionSnapshotsUseTheSameRevisionFields() {
        var registered = LocalDateTime.of(2026, 6, 1, 10, 0, 0, 123456000);
        var detail = new com.sicost.clients.ClientDtos.Stored("C-901", "VNDR", "거래처", "123-45-67890",
                null, null, null, "01000112233", null, "004", null, "예금주", "Y", "admin", registered, null, null);
        var selected = new ClientSnapshot(detail.id(), detail.type(), detail.name(), detail.businessNo(), detail.ceo(),
                detail.industryCode(), detail.contactName(), detail.phone(), detail.taxEmail(), detail.bankCode(),
                detail.encryptedAccount(), detail.depositor(), detail.useYn(), detail.regUserId(), detail.regDttm(),
                detail.modUserId(), detail.modDttm());
        assertThat(revisions.revision(selected)).isEqualTo(revisions.revision(detail));
        revisions.requireExpected(revisions.revision(detail), revisions.revision(selected));
    }
    @Test void mismatchIsConflictAndTokenIsOpaque() {
        String token = revisions.revision(new Stored("A", "1", null, null));
        assertThat(token).matches("[A-Za-z0-9_-]+").doesNotContain("value");
        revisions.requireExpected(token, token);
        assertThatThrownBy(() -> revisions.requireExpected("stale", token)).isInstanceOfSatisfying(ApiException.class,
                e -> { assertThat(e.status()).isEqualTo(409); assertThat(e.code()).isEqualTo("STALE_WRITE"); });
    }
}
