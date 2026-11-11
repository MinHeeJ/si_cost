package com.sicost.common.concurrent;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sicost.common.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.stereotype.Service;

@Service
public class RevisionService {
    private final ObjectMapper mapper;
    public RevisionService(ObjectMapper mapper) {
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    }
    public String revision(Object completeStoredSnapshot) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(completeStoredSnapshot);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.io.IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("변경 식별값을 생성하지 못했습니다.");
        }
    }
    public void requireExpected(String expected, String actual) {
        if (expected == null || actual == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(409, "STALE_WRITE", "다른 사용자가 변경했습니다. 다시 조회한 뒤 저장해 주세요.");
        }
    }
}
