package com.sicost.common.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.InputValidator;
import com.sicost.common.TimeConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChangeHistoryWriter {
    private static final Set<String> TABLES = Set.of("TB_SY_CODE_GRP", "TB_SY_CODE", "TB_SY_CODE_MAP", "TB_SY_OPER_STD",
            "TB_SY_USER", "TB_BS_CLNT", "TB_BS_PART", "TB_BS_PART_STD",
            "TB_HR_DEV", "TB_HR_DEV_PRC", "TB_HR_DEV_SKILL", "TB_HR_DEV_STAT",
            "TB_HR_CAND", "TB_HR_CAND_SKILL", "TB_HR_CAND_CNTC");
    private static final Set<String> PEOPLE = Set.of("TB_HR_DEV", "TB_HR_DEV_PRC", "TB_HR_DEV_SKILL", "TB_HR_DEV_STAT",
            "TB_HR_CAND", "TB_HR_CAND_SKILL", "TB_HR_CAND_CNTC");
    private static final Set<String> TYPES = Set.of("NEW", "UPD", "STOP", "RESUME", "DEL");
    private final ChangeHistoryMapper mapper;
    private final CurrentUserProvider users;
    private final Clock clock;
    private final ObjectMapper json;
    public ChangeHistoryWriter(ChangeHistoryMapper mapper, CurrentUserProvider users, Clock clock, ObjectMapper json) {
        this.mapper = mapper; this.users = users; this.clock = clock; this.json = json;
    }
    public record Change(String tableName, String targetKey, String type, String item,
                         String before, String after, String reason) {}
    public record MappingSnapshot(String sourceText, String groupId, String code, String codeName) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Change change) {
        append(change, users::userId);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendAsSystem(Change change) {
        if (!PEOPLE.contains(change.tableName())) throw InputValidator.invalid("history");
        append(change, () -> "SYSTEM");
    }
    private void append(Change change, Supplier<String> actor) {
        if (!TABLES.contains(change.tableName()) || !TYPES.contains(change.type())) throw InputValidator.invalid("history");
        if ((Set.of("TB_SY_USER", "TB_BS_CLNT", "TB_BS_PART", "TB_BS_PART_STD").contains(change.tableName()) || PEOPLE.contains(change.tableName()))
                && change.type().equals("DEL")) throw InputValidator.invalid("history");
        if (change.item() != null && change.item().toUpperCase(java.util.Locale.ROOT)
                .matches(".*(PWD|PASSWORD|HASH|MFA|CIPHER|ACCT_NO_ENC).*")) throw InputValidator.invalid("history");
        if (change.item() != null && Set.of("ACCOUNT", "ACCOUNTNUMBER", "ACCT_NO", "계좌").contains(change.item().toUpperCase(java.util.Locale.ROOT))) {
            for (String value : new String[]{change.before(), change.after()}) {
                if (value != null && !value.matches("\\*{4}[0-9]{1,4}")) throw InputValidator.invalid("history");
            }
        }
        if (Objects.equals(change.before(), change.after())) return;
        InputValidator.text(change.targetKey(), 100, "targetKey");
        check(change.item(), 50); check(change.before(), 500); check(change.after(), 500); check(change.reason(), 500);
        boolean privateContact = PEOPLE.contains(change.tableName()) && change.item() != null
                && Set.of("TELNO", "PHONE", "EMAIL_ADDR", "EMAIL", "연락처", "이메일").contains(change.item().toUpperCase(java.util.Locale.ROOT));
        mapper.insert(new ChangeHistoryEntry(change.tableName(), change.targetKey(), change.type(), change.item(),
                privateContact ? maskedContact(change.before()) : change.before(),
                privateContact ? maskedContact(change.after()) : change.after(), change.reason(), actor.get(), LocalDateTime.ofInstant(clock.instant(), TimeConfiguration.SEOUL)));
    }
    private String maskedContact(String value) { return value == null ? null : "[비공개]"; }
    public record SkillsSummary(int count, boolean ordered, String sha256) {}
    public String skillsJson(List<String> skills) {
        try {
            String value = json.writeValueAsString(skills);
            if (value.codePointCount(0, value.length()) <= 500) return value;
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
            return json.writeValueAsString(new SkillsSummary(skills.size(), true, digest));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("변경 이력을 생성하지 못했습니다.");
        }
    }
    public String mappingJson(MappingSnapshot mapping) {
        try {
            String value = json.writeValueAsString(mapping);
            check(value, 500);
            return value;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("변경 이력을 생성하지 못했습니다.");
        }
    }
    private void check(String value, int max) {
        if (value != null && value.codePointCount(0, value.length()) > max) throw InputValidator.invalid("history");
    }
}
