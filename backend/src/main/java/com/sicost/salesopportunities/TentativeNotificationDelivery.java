package com.sicost.salesopportunities;

import com.sicost.authentication.AuthenticationMailService;
import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TentativeNotificationDelivery {
    private final JdbcTemplate jdbc;
    private final AuthenticationMailService mail;

    public TentativeNotificationDelivery(JdbcTemplate jdbc, AuthenticationMailService mail) {
        this.jdbc = jdbc;
        this.mail = mail;
    }

    @Scheduled(fixedDelayString = "${sicost.tentative-mail.delay-ms:10000}")
    public void deliverPending() {
        try {
            mail.requireConfigured();
        } catch (ApiException unavailable) {
            return;
        }
        jdbc.update("""
                UPDATE tb_sl_tent_mail_outbox SET delivery = 'UNKNOWN', updated_at = clock_timestamp()
                WHERE delivery = 'SENDING' AND updated_at < clock_timestamp() - interval '5 minutes'
                """);
        for (int i = 0; i < 10; i++) {
            List<Notification> pending = claim();
            if (pending.isEmpty()) return;
            Notification notification = pending.get(0);
            jdbc.update("""
                    UPDATE tb_sl_tent_mail_outbox SET delivery = ?, updated_at = clock_timestamp()
                    WHERE mail_id = ? AND delivery = 'SENDING'
                    """, deliver(notification), notification.id());
        }
    }

    private List<Notification> claim() {
        return jdbc.query("""
                WITH claimed AS (
                    UPDATE tb_sl_tent_mail_outbox
                    SET delivery = 'SENDING', updated_at = clock_timestamp()
                    WHERE mail_id = (
                        SELECT mail_id FROM tb_sl_tent_mail_outbox WHERE delivery = 'PENDING'
                        ORDER BY mail_id LIMIT 1 FOR UPDATE SKIP LOCKED
                    )
                    RETURNING mail_id, dev_id, opp_nm, event_type, previous_probability, probability
                )
                SELECT c.*, d.email_addr FROM claimed c LEFT JOIN tb_hr_dev d ON d.dev_id = c.dev_id
                """, (rs, row) -> new Notification(rs.getLong("mail_id"), rs.getString("email_addr"),
                rs.getString("opp_nm"), rs.getString("event_type"),
                rs.getBigDecimal("previous_probability"), rs.getBigDecimal("probability")));
    }

    private String deliver(Notification notification) {
        if (notification.address() == null) return "SKIPPED";
        String title = switch (notification.event()) {
            case "ASSIGNED" -> "영업 기회 가배정";
            case "RELEASED" -> "영업 기회 가배정 해제";
            case "PROBABILITY" -> "영업 기회 수주 확률 변경";
            default -> throw new IllegalArgumentException("Unknown tentative assignment notification");
        };
        String probability = percent(notification.probability());
        if (notification.before() != null) probability = percent(notification.before()) + " → " + probability;
        return mail.send(notification.address(), "[원가관리시스템] " + title, title + "\n\n"
                + "영업 기회 " + notification.opportunity() + "\n수주 확률 " + probability);
    }

    private String percent(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString() + "%";
    }

    private record Notification(long id, String address, String opportunity, String event,
                                BigDecimal before, BigDecimal probability) {}
}
