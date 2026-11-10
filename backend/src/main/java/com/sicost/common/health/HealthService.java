package com.sicost.common.health;

import com.sicost.common.api.ApiException;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
    private final HealthMapper mapper;
    public HealthService(HealthMapper mapper) { this.mapper = mapper; }

    public void checkDatabase() {
        try {
            if (mapper.selectDatabaseHealth() != 1) {
                throw new IllegalStateException("Database probe failed");
            }
        } catch (RuntimeException e) {
            throw new ApiException(503, "DB_UNAVAILABLE", "데이터베이스에 연결할 수 없습니다. 잠시 후 다시 확인해 주세요.");
        }
    }
}
