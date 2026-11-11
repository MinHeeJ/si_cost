package com.sicost.common.history;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChangeHistoryWriterTest {
    final ChangeHistoryMapper mapper = mock(ChangeHistoryMapper.class);
    final CurrentUserProvider users = mock(CurrentUserProvider.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    final ChangeHistoryWriter writer = new ChangeHistoryWriter(mapper, users, clock, JsonMapper.builder().findAndAddModules().build());
    @Test void writesOriginalTableCompositeKeyAndServerAuditFields() {
        when(users.userId()).thenReturn("admin");
        writer.append(new ChangeHistoryWriter.Change("TB_SY_CODE","A:B","UPD","CODE_NM","이전","이후",null));
        var captured = ArgumentCaptor.forClass(ChangeHistoryEntry.class); verify(mapper).insert(captured.capture());
        var entry = captured.getValue();
        assertThat(entry.tableName()).isEqualTo("TB_SY_CODE"); assertThat(entry.targetKey()).isEqualTo("A:B");
        assertThat(entry.item()).isEqualTo("CODE_NM"); assertThat(entry.userId()).isEqualTo("admin");
        assertThat(entry.occurredAt().toString()).isEqualTo("2026-01-01T09:00");
    }
    @Test void mappingNewAndDeleteJsonPreserveAllFields() {
        var snapshot = new ChangeHistoryWriter.MappingSnapshot("원문","A","B","이름");
        assertThat(writer.mappingJson(snapshot)).isEqualTo("{\"sourceText\":\"원문\",\"groupId\":\"A\",\"code\":\"B\",\"codeName\":\"이름\"}");
    }
    @Test void historyOver500CharactersIsRejectedWithoutTruncation() {
        assertThatThrownBy(() -> writer.append(new ChangeHistoryWriter.Change("TB_SY_CODE","A:B","UPD","CODE_NM","이전","가".repeat(501),null))).isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper);
    }
    @Test void newBusinessTargetsUseExistingHistoryMappingAndServerActor() {
        when(users.userId()).thenReturn("admin");
        for (String table : new String[]{"TB_SY_USER", "TB_BS_CLNT", "TB_BS_PART", "TB_BS_PART_STD"}) {
            writer.append(new ChangeHistoryWriter.Change(table,"PART-001:202601","NEW","등록",null,"ID 이름",null));
        }
        verify(mapper,times(4)).insert(argThat(entry -> entry.userId().equals("admin") && entry.before()==null && entry.type().equals("NEW")));
    }
    @Test void businessHistoryCannotDeleteOrRecordSecrets() {
        for (String item : new String[]{"PWD_HASH", "accountNumber", "ACCT_NO_ENC", "password"}) {
            assertThatThrownBy(() -> writer.append(new ChangeHistoryWriter.Change("TB_BS_CLNT","C-001","UPD",item,null,"not-masked",null)))
                .isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> writer.append(new ChangeHistoryWriter.Change("TB_BS_PART_STD","PART-001:202601","DEL","등록","기존",null,null)))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper);
    }
    @Test void accountHistoryAcceptsOnlyMaskedValues() {
        when(users.userId()).thenReturn("admin");
        writer.append(new ChangeHistoryWriter.Change("TB_BS_CLNT","C-001","UPD","account","****1234","****5678",null));
        verify(mapper).insert(argThat(entry -> entry.before().equals("****1234") && entry.after().equals("****5678")));
    }
    @Test void sameValueDoesNotAppendHistory() {
        writer.append(new ChangeHistoryWriter.Change("TB_SY_CODE","A:B","UPD","CODE_NM","같음","같음",null));
        verifyNoInteractions(mapper,users);
    }
}
