package com.sicost.salespartsettlementsops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SettlementSnapshotCodec {
    private final ObjectMapper json;
    public SettlementSnapshotCodec(ObjectMapper json) { this.json = json; }
    public String projects(String developerId, List<String> names) {
        try {
            String value = json.writeValueAsString(names.stream().sorted().toList());
            if (value.codePointCount(0, value.length()) > 1000)
                throw new ApiException(409, "SNAPSHOT_TEXT_TOO_LONG", "프로젝트 목록이 1000자를 초과하여 전체 정산을 취소했습니다.", "PROJ_NM_LIST", Map.of("developerId", developerId, "field", "PROJ_NM_LIST"));
            return value;
        } catch (JsonProcessingException e) { throw new IllegalStateException("프로젝트 사본을 생성하지 못했습니다."); }
    }
}
