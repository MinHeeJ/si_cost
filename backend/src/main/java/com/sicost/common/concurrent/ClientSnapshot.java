package com.sicost.common.concurrent;

import java.time.LocalDateTime;

public record ClientSnapshot(String id, String type, String name, String businessNo, String ceo,
        String industryCode, String contactName, String phone, String taxEmail, String bankCode,
        String encryptedAccount, String depositor, String useYn, String regUserId, LocalDateTime regDttm,
        String modUserId, LocalDateTime modDttm) {
    @Override public String toString() { return "ClientSnapshot[id=" + id + "]"; }
}
