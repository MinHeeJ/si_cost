package com.sicost.codemappings;

import java.time.LocalDateTime;

public record CodeMappingRow(long mapId, String sourceText, String groupId, String code,
                             String codeName, String regUserId, LocalDateTime regDttm,
                             String modUserId, LocalDateTime modDttm) {}
