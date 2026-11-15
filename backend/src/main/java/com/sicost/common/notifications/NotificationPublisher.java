package com.sicost.common.notifications;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.InputValidator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.common.notifications.NotificationPublicationMapper.*;

/** Joins the business mutation transaction; never performs SMTP or independent after-commit inserts. */
@Service
public class NotificationPublisher {
    private final NotificationPublicationMapper mapper;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    public NotificationPublisher(NotificationPublicationMapper mapper, ChangeHistoryWriter history, Clock clock) {
        this.mapper = mapper; this.history = history; this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> publish(Event event, List<NotificationRecipients.Recipient> recipients) {
        validate(event);
        if (event.link() != null && !mapper.registeredScreen(event.link().screenId())) throw InputValidator.invalid("screenId");
        var active = new TreeMap<String, NotificationRecipients.Role>();
        for (var role : mapper.eligibleRoles()) active.putIfAbsent(role.userId(), role);
        var selected = new TreeMap<String, NotificationRecipients.Recipient>();
        for (var recipient : recipients) {
            var current = active.get(recipient.userId());
            if (current != null) selected.put(recipient.userId(), new NotificationRecipients.Recipient(current.userId(), current.name(), current.emailAddress(), recipient.qualifications()));
        }
        List<UUID> created = new ArrayList<>();
        LocalDateTime at = LocalDateTime.ofInstant(clock.instant(), TimeConfiguration.SEOUL);
        for (var recipient : selected.values()) {
            InputValidator.text(recipient.name(), 50, "recipientName");
            UUID taskId = UUID.randomUUID();
            var publication = new Publication(taskId, event, recipient, at);
            int inserted = mapper.reserveSystem(publication);
            if (inserted == 0) continue;
            if (inserted != 1) throw new IllegalStateException("전달 작업을 저장하지 못했습니다.");
            BigDecimal notificationId = mapper.insertNotification(publication);
            if (notificationId == null || notificationId.signum() < 0 || notificationId.precision() > 12) throw new IllegalStateException("알림 식별자를 확인하지 못했습니다.");
            if (mapper.connectNotification(new NotificationConnection(taskId, notificationId)) != 1) throw new IllegalStateException("알림 연결을 저장하지 못했습니다.");
            var task = new LinkedHashMap<String, Object>();
            task.put("TASK_ID", taskId); task.put("EVENT_KEY", event.key()); task.put("RCVR_USER_ID", recipient.userId()); task.put("RCVR_NM", recipient.name());
            task.put("CHANNEL_CD", "SYS"); task.put("STATE_CD", "SENT"); task.put("NOTI_TYPE_CD", event.type()); task.put("NOTI_CN", event.content());
            task.put("LINK_SCRN_ID", event.link() == null ? null : event.link().screenId()); task.put("LINK_KEY", event.link() == null ? null : event.link().key());
            task.put("MAIL_TO", null); task.put("MAIL_SUBJECT", null); task.put("MAIL_BODY", null); task.put("MESSAGE_ID", null); task.put("NOTI_ID", notificationId.toPlainString());
            task.put("BILL_ID", event.dunning() == null ? null : event.dunning().billId()); task.put("DUN_STEP", event.dunning() == null ? null : event.dunning().step());
            task.put("DUN_SN", event.dunning() == null ? null : event.dunning().serial()); task.put("RESEND_YN", event.dunning() != null && event.dunning().resend() ? "Y" : "N");
            task.put("SENT_DTTM", at); task.put("ERROR_CD", null); task.put("ERROR_CN", null); task.put("REG_USER_ID", "SYSTEM"); task.put("REG_DTTM", at);
            audit("TB_SY_DELIVERY_TASK", taskId.toString(), task);
            var notification = new LinkedHashMap<String, Object>();
            notification.put("NOTI_ID", notificationId.toPlainString()); notification.put("RCVR_USER_ID", recipient.userId()); notification.put("NOTI_TYPE_CD", event.type());
            notification.put("NOTI_CN", event.content()); notification.put("LINK_SCRN_ID", event.link() == null ? null : event.link().screenId());
            notification.put("LINK_KEY", event.link() == null ? null : event.link().key()); notification.put("SEND_MTHD_CD", event.type().equals("DUN") ? "BOTH" : "SYS");
            notification.put("READ_YN", "N"); notification.put("READ_DTTM", null); notification.put("NOTI_DTTM", at); notification.put("REG_USER_ID", "SYSTEM"); notification.put("REG_DTTM", at);
            audit("TB_SY_NOTI", notificationId.toPlainString(), notification);
            created.add(taskId);
        }
        return List.copyOf(created);
    }

    private void audit(String table, String key, Map<String, Object> fields) {
        fields.forEach((field, value) -> history.appendAsSystem(new ChangeHistoryWriter.Change(table, key, "NEW", field, null,
            value == null ? null : value.toString(), null)));
    }
    private static void validate(Event event) {
        if (event == null || event.type() == null || !Set.of("DUN", "WTHDR", "CASH_SHORT", "UPLD_DONE", "AI_DONE", "QUOTE_REJ").contains(event.type())) throw InputValidator.invalid("notificationType");
        InputValidator.text(event.key(), 160, "eventKey");
        InputValidator.text(event.content(), 300, "content");
        if (event.key().contains("\n") || event.key().contains("\r")) throw InputValidator.invalid("eventKey");
        if (event.link() != null) {
            if (event.link().screenId() == null || !event.link().screenId().matches("UI-[0-9]{2}")) throw InputValidator.invalid("screenId");
            if (event.link().key() != null && (event.link().key().length() > 50 || !event.link().key().matches("[A-Za-z0-9_.:,=-]+"))) throw InputValidator.invalid("linkKey");
        }
        if (event.type().equals("DUN")) {
            var d = event.dunning();
            if (d == null || d.billId() == null || d.billId().length() > 20 || d.step() < 1 || d.step() > 3 || d.serial() < 1 || d.serial() > 99999) throw InputValidator.invalid("dunning");
        } else if (event.dunning() != null) throw InputValidator.invalid("dunning");
    }
}
