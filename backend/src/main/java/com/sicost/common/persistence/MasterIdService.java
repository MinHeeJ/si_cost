package com.sicost.common.persistence;

import com.sicost.common.api.ApiException;
import java.math.BigInteger;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterIdService {
    private final MasterIdMapper mapper;
    public MasterIdService(MasterIdMapper mapper) { this.mapper = mapper; }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextClientId() { return allocate("C-", 18, mapper::nextClient); }
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextPartId() { return allocate("PART-", 15, mapper::nextPart); }

    private String allocate(String prefix, int digits, Supplier<BigInteger> next) {
        BigInteger number;
        try { number = next.get(); }
        catch (RuntimeException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && "2200H".equals(sql.getSQLState())) throw exhausted();
            }
            throw e;
        }
        if (number == null || number.signum() <= 0 || number.toString().length() > digits) throw exhausted();
        String value = number.toString();
        return prefix + "0".repeat(Math.max(0, 3 - value.length())) + value;
    }
    private ApiException exhausted() { return new ApiException(409, "ID_EXHAUSTED", "사용 가능한 식별자를 모두 사용했습니다."); }
}
