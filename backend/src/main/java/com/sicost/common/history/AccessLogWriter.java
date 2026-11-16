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
    public record Event(String action, String screenId, String targetId, String fileType, Long count, String filename) {
        public Event(String action, String screenId, String targetId, String fileType, Long count) {
            this(action, screenId, targetId, fileType, count, null);
        }
    }
    public record Entry(String userId, String action, String screenId, String description, String clientIp, LocalDateTime now) {}

    public record MonthCloseEvent(String action, String month, int historySequence) {}
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendMonthClose(MonthCloseEvent event, String actor, LocalDateTime at, HttpServletRequest request) {
        if (event == null || !Set.of("CLOSE","UNCLOSE").contains(event.action())
                || event.month() == null || !event.month().matches("(?!0000)[0-9]{4}(0[1-9]|1[0-2])")
                || event.historySequence()<1 || event.historySequence()>99999 || at == null) throw InputValidator.invalid("accessLog");
        InputValidator.text(actor,30,"actor");
        String ip=request.getRemoteAddr();
        if(ip==null || ip.length()>45 || !ip.matches("[0-9A-Fa-f:.%]+"))ip=null;
        if(mapper.insert(new Entry(actor,event.action(),"UI-14",event.month()+":"+event.historySequence(),ip,at))!=1)
            throw new IllegalStateException("마감 접근기록을 저장하지 못했습니다.");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Event event, HttpServletRequest request) {
        if ("AUTH_CHG".equals(event.action())) {
            if (!Set.of("UI-23", "UI-24").contains(event.screenId()) || event.targetId()==null
                    || !event.targetId().matches("[A-Za-z0-9_-]{1,30}") || event.fileType()!=null
                    || event.count()!=null || event.filename()!=null) throw InputValidator.invalid("accessLog");
            String ip=request.getRemoteAddr();
            if(ip==null || ip.length()>45 || !ip.matches("[0-9A-Fa-f:.%]+"))ip=null;
            if(mapper.insert(new Entry(users.userId(),event.action(),event.screenId(),event.targetId(),ip,
                    LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL)))!=1)
                throw new IllegalStateException("접근기록을 저장하지 못했습니다.");
            return;
        }
        if ("UI-16".equals(event.screenId())) {
            if (!"DNLD".equals(event.action()) || event.targetId()!=null || !"대시보드".equals(event.fileType())
                    || event.count()==null || event.count()<0 || event.filename()!=null) throw InputValidator.invalid("accessLog");
            String ip=request.getRemoteAddr();
            if(ip==null || ip.length()>45 || !ip.matches("[0-9A-Fa-f:.%]+"))ip=null;
            if(mapper.insert(new Entry(users.userId(),"DNLD","UI-16","대시보드 "+event.count(),ip,
                    LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL)))!=1)
                throw new IllegalStateException("접근기록을 저장하지 못했습니다.");
            return;
        }
        if (Set.of("UI-21", "UI-26").contains(event.screenId())) {
            String kind = "UI-26".equals(event.screenId()) ? "AI분석" : "자금수지표";
            boolean validTarget = "UI-26".equals(event.screenId())
                ? event.targetId()!=null && event.targetId().matches("[1-9][0-9]{0,11}") : event.targetId()==null;
            if (!"DNLD".equals(event.action()) || !validTarget || !kind.equals(event.fileType())
                    || event.count()==null || event.count()<0 || event.filename()!=null) throw InputValidator.invalid("accessLog");
            String ip=request.getRemoteAddr();
            if(ip==null || ip.length()>45 || !ip.matches("[0-9A-Fa-f:.%]+"))ip=null;
            if(mapper.insert(new Entry(users.userId(),"DNLD",event.screenId(),(event.targetId()==null?"":event.targetId()+" ")+kind+" "+event.count(),ip,
                    LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL)))!=1)
                throw new IllegalStateException("접근기록을 저장하지 못했습니다.");
            return;
        }
        if (!Set.of("DNLD", "ACCT_VIEW", "USER_REG").contains(event.action())
                || !Set.of("UI-15", "UI-23", "UI-02", "UI-03", "UI-04", "UI-33", "UI-01", "UI-12", "UI-13", "UI-10", "UI-31", "UI-18", "UI-07", "UI-08", "UI-09", "UI-19", "UI-32", "UI-29", "UI-11", "UI-17", "UI-28", "UI-05").contains(event.screenId())) throw InputValidator.invalid("accessLog");
        if (event.screenId().equals("UI-05") && (!"DNLD".equals(event.action()) || event.targetId()!=null
                || !"철수리스트".equals(event.fileType()) || event.count()==null)) throw InputValidator.invalid("accessLog");
        if (event.screenId().equals("UI-28") && (!"DNLD".equals(event.action()) || event.targetId()!=null
                || event.count()==null || !Set.of("인력운영달력", "인력운영수요공급").contains(event.fileType()==null?"":event.fileType()))) throw InputValidator.invalid("accessLog");
        if (event.screenId().equals("UI-17") && (!"DNLD".equals(event.action()) || event.targetId()!=null
                || !"인력수요예측".equals(event.fileType()) || event.count()==null)) throw InputValidator.invalid("accessLog");
        if (event.screenId().equals("UI-11") && (!"DNLD".equals(event.action()) || event.targetId()!=null
                || !"계획대비투입".equals(event.fileType()) || event.count()==null)) throw InputValidator.invalid("accessLog");
        if (event.screenId().equals("UI-29") && (!"DNLD".equals(event.action()) || event.targetId()!=null
                || !"영업예측".equals(event.fileType()) || event.count()==null)) throw InputValidator.invalid("accessLog");
        boolean settlement = Set.of("UI-19", "UI-32").contains(event.screenId());
        if (settlement) {
            String pattern = event.screenId().equals("UI-19")
                ? "영업파트이익금_[A-Za-z0-9-]{1,20}_(?!0000)[0-9]{4}(0[1-9]|1[0-2])_(이번달|추이)\\.xlsx"
                : "단가재협상_" + java.time.LocalDate.now(clock.withZone(TimeConfiguration.SEOUL)).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "\\.xlsx";
            if (!"DNLD".equals(event.action()) || event.targetId() != null || event.count() == null
                    || event.filename() == null || !event.filename().matches(pattern)) throw InputValidator.invalid("accessLog");
        } else if (event.filename() != null) throw InputValidator.invalid("accessLog");
        if (event.screenId().equals("UI-09") && (!"DNLD".equals(event.action()) || event.targetId() != null
                || !"순이익표".equals(event.fileType()) || event.count() == null)) throw InputValidator.invalid("accessLog");
        if (event.screenId().equals("UI-08") && (!"DNLD".equals(event.action()) || event.count() == null
                || !(Set.of("영업이익표", "이관손익_등록양식").contains(event.fileType()) && event.targetId() == null
                    || "이관손익_업로드검사".equals(event.fileType()) && event.targetId() != null
                        && event.targetId().matches("[1-9][0-9]{0,11}")))) throw InputValidator.invalid("accessLog");
        if (event.screenId().equals("UI-07") && (!"DNLD".equals(event.action()) || event.targetId() != null
                || !"전체수익구조".equals(event.fileType()) || event.count() == null)) throw InputValidator.invalid("accessLog");
        if (event.screenId().equals("UI-18") && (!"DNLD".equals(event.action()) || event.count() == null
                || !("사업계획_등록양식".equals(event.fileType()) && event.targetId() == null
                    || "사업계획_업로드검사".equals(event.fileType()) && event.targetId() != null
                        && event.targetId().matches("[1-9][0-9]{0,11}")))) throw InputValidator.invalid("accessLog");
        if (event.screenId().equals("UI-04") && (!"DNLD".equals(event.action()) || event.targetId() != null
                || !"프로젝트 원가".equals(event.fileType()) || event.count() == null)) throw InputValidator.invalid("accessLog");
        if (event.screenId().equals("UI-31") && (!"DNLD".equals(event.action()) || event.targetId() != null
                || !"프로젝트현황".equals(event.fileType()) || event.count() == null)) throw InputValidator.invalid("accessLog");
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
            case "UI-05" -> Set.of("철수리스트");
            case "UI-28" -> Set.of("인력운영달력", "인력운영수요공급");
            case "UI-17" -> Set.of("인력수요예측");
            case "UI-11" -> Set.of("계획대비투입");
            case "UI-29" -> Set.of("영업예측");
            case "UI-19" -> Set.of("영업파트이익금");
            case "UI-32" -> Set.of("단가재협상");
            case "UI-07" -> Set.of("전체수익구조");
            case "UI-09" -> Set.of("순이익표");
            case "UI-08" -> Set.of("영업이익표", "이관손익_등록양식", "이관손익_업로드검사");
            case "UI-03" -> Set.of("개인원가수익");
            case "UI-04" -> Set.of("프로젝트 원가");
            case "UI-31" -> Set.of("프로젝트현황");
            case "UI-02" -> Set.of("개발자", "개발자_등록양식", "개발자_업로드검사", "급여_등록양식", "급여_업로드검사");
            case "UI-12" -> Set.of("투입배정", "투입_신규양식", "투입_업로드검사");
            case "UI-13" -> Set.of("청구", "청구누락점검", "미수알림");
            case "UI-10" -> Set.of("재무_등록양식", "재무_업로드검사");
            case "UI-18" -> Set.of("사업계획_등록양식", "사업계획_업로드검사");
            case "UI-33" -> Set.of("외부인력후보");
            case "UI-01" -> Set.of("프로젝트", "턴키배분표", "프로젝트_등록양식", "턴키배분표_등록양식", "프로젝트_업로드검사", "턴키배분표_업로드검사");
            default -> Set.of("거래처", "영업파트", "거래처_등록양식", "거래처_업로드검사");
        };
        if (((settlement || people || event.screenId().equals("UI-01") || event.screenId().equals("UI-12")) && kind == null) || (kind != null && !kinds.contains(kind))) {
            throw InputValidator.invalid("fileType");
        }
        if (event.count() != null && event.count() < 0) throw InputValidator.invalid("count");
        String description = (target == null ? "" : target) + (kind == null ? "" : " " + kind)
                + (event.count() == null ? "" : " " + event.count()) + (settlement ? " " + event.filename() : "");
        InputValidator.text(description, 300, "targetDescription");
        String ip = request.getRemoteAddr();
        if (ip == null || ip.length() > 45 || !ip.matches("[0-9A-Fa-f:.%]+")) ip = null;
        if (mapper.insert(new Entry(users.userId(), event.action(), event.screenId(), description, ip, LocalDateTime.ofInstant(clock.instant(), TimeConfiguration.SEOUL))) != 1) {
            throw new IllegalStateException("접근기록을 저장하지 못했습니다.");
        }
    }
}
