package com.sicost.common.history;

import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.validation.InputValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccessLogWriter {
    private final AccessLogMapper mapper;
    private final CurrentUserProvider users;
    private final Clock clock;
    public AccessLogWriter(AccessLogMapper mapper, CurrentUserProvider users, Clock clock) {
        this.mapper = mapper; this.users = users; this.clock = clock;
    }
    public record Event(String action, String screenId, String targetId, String fileType, Long count) {}
    public record Entry(String userId, String action, String screenId, String description, String clientIp, LocalDateTime now) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Event event, HttpServletRequest request) {
        if (!Set.of("DNLD", "ACCT_VIEW", "USER_REG").contains(event.action())
                || !Set.of("UI-15", "UI-23", "UI-02", "UI-03", "UI-33", "UI-01", "UI-12", "UI-13", "UI-10").contains(event.screenId())) throw InputValidator.invalid("accessLog");
        if (event.screenId().equals("UI-03") && (!"DNLD".equals(event.action()) || event.targetId() != null
                || !"개인원가수익".equals(event.fileType()) || event.count() == null)) throw InputValidator.invalid("accessLog");
        if (event.screenId().equals("UI-10") && (!"DNLD".equals(event.action()) || event.count() == null
                || !("재무_등록양식".equals(event.fileType()) && event.targetId() == null
                    || "재무_업로드검사".equals(event.fileType()) && event.targetId() != null
                        && event.targetId().matches("[1-9][0-9]{0,11}")))) throw InputValidator.invalid("accessLog");
        boolean people = Set.of("UI-02", "UI-33").contains(event.screenId());
        if (event.screenId().equals("UI-01") && !"DNLD".equals(event.action())) throw InputValidator.invalid("accessLog");
        if (event.screenId().equals("UI-12") && !"DNLD".equals(event.action())) throw InputValidator.invalid("accessLog");
        if (event.screenId().equals("UI-13") && (!"DNLD".equals(event.action()) || event.targetId()!=null || event.fileType()==null)) throw InputValidator.invalid("accessLog");
        if (people && !"DNLD".equals(event.action())) throw InputValidator.invalid("accessLog");
        String target = event.targetId();
        if (target != null && !target.matches("(?:[a-z0-9]{4,20}|C-[0-9]{3,18}|PART-[0-9]{3,15}|[0-9]{1,12})")) {
            throw InputValidator.invalid("targetId");
        }
        String kind = event.fileType();
        Set<String> kinds = switch (event.screenId()) {
            case "UI-03" -> Set.of("개인원가수익");
            case "UI-02" -> Set.of("개발자", "개발자_등록양식", "개발자_업로드검사", "급여_등록양식", "급여_업로드검사");
            case "UI-12" -> Set.of("투입배정", "투입_신규양식", "투입_업로드검사");
            case "UI-13" -> Set.of("청구", "청구누락점검", "미수알림");
            case "UI-10" -> Set.of("재무_등록양식", "재무_업로드검사");
            case "UI-33" -> Set.of("외부인력후보");
            case "UI-01" -> Set.of("프로젝트", "턴키배분표", "프로젝트_등록양식", "턴키배분표_등록양식", "프로젝트_업로드검사", "턴키배분표_업로드검사");
            default -> Set.of("거래처", "영업파트", "거래처_등록양식", "거래처_업로드검사");
        };
        if (((people || event.screenId().equals("UI-01") || event.screenId().equals("UI-12")) && kind == null) || (kind != null && !kinds.contains(kind))) {
            throw InputValidator.invalid("fileType");
        }
        if (event.count() != null && event.count() < 0) throw InputValidator.invalid("count");
        String description = (target == null ? "" : target) + (kind == null ? "" : " " + kind)
                + (event.count() == null ? "" : " " + event.count());
        InputValidator.text(description, 300, "targetDescription");
        String ip = request.getRemoteAddr();
        if (ip == null || ip.length() > 45 || !ip.matches("[0-9A-Fa-f:.%]+")) ip = null;
        if (mapper.insert(new Entry(users.userId(), event.action(), event.screenId(), description, ip, LocalDateTime.ofInstant(clock.instant(), TimeConfiguration.SEOUL))) != 1) {
            throw new IllegalStateException("접근기록을 저장하지 못했습니다.");
        }
    }
}
