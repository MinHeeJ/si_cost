package com.sicost.common.notifications;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/** Inputs must be selected with USER_STAT_CD=USE and role USE_YN=Y. */
public final class NotificationRecipients {
    private NotificationRecipients() {}
    public record Role(String userId, String name, String emailAddress, String role) {}
    public record Scope(String userId, String type, String partId, String projectId) {}
    public record Part(String partId, boolean headquarters, String leaderId) {}
    public record Recipient(String userId, String name, String emailAddress, List<String> qualifications) {}
    public record Group(int step, List<Recipient> recipients, List<String> unassignedReasons) {}

    public static List<Group> dunning(String projectId, Part part, List<Role> roles, List<Scope> scopes) {
        Map<String, Recipient> selected = new TreeMap<>();
        List<String> reasons = new ArrayList<>();
        List<Group> result = new ArrayList<>();
        add(selected, reasons, roles, r -> r.role().equals("PM") && scopes.stream().anyMatch(s -> s.userId().equals(r.userId())
            && "PROJ".equals(s.type()) && projectId.equals(s.projectId())), "프로젝트 PM 미지정");
        result.add(group(1, selected, reasons));
        if (part == null || part.partId() == null) {
            add(selected, reasons, roles, r -> r.role().equals("EXEC"), "영업파트 미지정 · EXEC 대체 수신자 미지정");
        } else if (part.headquarters()) {
            Predicate<Role> hq = r -> r.role().equals("SALES_LDR") && scopes.stream().anyMatch(s -> s.userId().equals(r.userId())
                && "PART".equals(s.type()) && part.partId().equals(s.partId()));
            if (roles.stream().anyMatch(hq)) add(selected, reasons, roles, hq, "HQ SALES_LDR 미지정");
            else add(selected, reasons, roles, r -> r.role().equals("EXEC"), "HQ SALES_LDR 없음 · EXEC 대체 수신자 미지정");
        } else {
            add(selected, reasons, roles, r -> r.role().equals("SALES_LDR") && Objects.equals(part.leaderId(), r.userId()), "영업파트 SALES_LDR 미지정");
        }
        result.add(group(2, selected, reasons));
        add(selected, reasons, roles, r -> r.role().equals("EXEC"), "EXEC 미지정");
        add(selected, reasons, roles, r -> r.role().equals("FIN"), "FIN 미지정");
        result.add(group(3, selected, reasons));
        return List.copyOf(result);
    }

    public static List<Recipient> withdrawal(String projectId, Part part, List<Role> roles, List<Scope> scopes) {
        Map<String, Recipient> selected = new TreeMap<>();
        Group responsible = dunning(projectId, part, roles, scopes).get(1);
        for (Recipient recipient : responsible.recipients()) selected.put(recipient.userId(), recipient);
        add(selected, new ArrayList<>(), roles, r -> List.of("ADMIN", "EXEC", "FIN").contains(r.role()), "");
        return List.copyOf(selected.values());
    }

    public static List<Recipient> cash(List<Role> roles) {
        Map<String, Recipient> selected = new TreeMap<>();
        add(selected, new ArrayList<>(), roles, r -> List.of("ADMIN", "EXEC", "FIN").contains(r.role()), "");
        return List.copyOf(selected.values());
    }

    private static void add(Map<String, Recipient> selected, List<String> reasons, List<Role> roles, Predicate<Role> eligible, String missing) {
        List<Role> found = roles.stream().filter(eligible).toList();
        if (found.isEmpty()) reasons.add(missing);
        for (Role row : found) {
            Recipient old = selected.get(row.userId());
            var qualifications = new TreeSet<String>();
            if (old != null) qualifications.addAll(old.qualifications());
            qualifications.add(row.role());
            selected.put(row.userId(), new Recipient(row.userId(), row.name(), row.emailAddress(), List.copyOf(qualifications)));
        }
    }
    private static Group group(int step, Map<String, Recipient> selected, List<String> reasons) {
        return new Group(step, List.copyOf(selected.values()), List.copyOf(reasons));
    }
}
