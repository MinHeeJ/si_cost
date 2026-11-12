package com.sicost.common.projects;

import com.sicost.common.api.ApiException;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectRegistrationService {
    public static final String CREATION_LOCK = "PROJECT:CREATE";
    public record Project(String name, String customerId, String contractType, String status,
                          LocalDate startDate, LocalDate endDate, String partId, String workplace,
                          BigDecimal estimateAmount, BigDecimal contractAmount, BigDecimal budgetAmount,
                          String budgetManual, BigDecimal teamExpenseAmount, BigDecimal projectExpenseAmount, String feature) {}
    public record Write(String id, Project project, String actor, LocalDateTime now, String startMonth) {}
    private final ProjectRegistrationMapper mapper;
    public ProjectRegistrationService(ProjectRegistrationMapper mapper) { this.mapper = mapper; }

    // The caller holds CREATION_LOCK and every known explicit PROJECT key before validation.
    @Transactional(propagation = Propagation.MANDATORY)
    public Set<String> reservedIds(Collection<String> explicitIds) {
        Set<String> reserved = new HashSet<>(mapper.existingIds());
        reserved.addAll(explicitIds);
        return reserved;
    }
    public static String allocate(Set<String> reserved) {
        BigInteger maximum = BigInteger.ZERO;
        for (String id : reserved) if (id.matches("P-[0-9]+")) maximum = maximum.max(new BigInteger(id.substring(2)));
        String suffix = maximum.add(BigInteger.ONE).toString();
        String id = "P-" + "0".repeat(Math.max(0, 4 - suffix.length())) + suffix;
        if (id.length() > 20) throw new ApiException(409, "ID_EXHAUSTED", "사용 가능한 프로젝트 ID를 모두 사용했습니다.");
        reserved.add(id);
        return id;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void register(String id, Project project, String actor, LocalDateTime now) {
        if (id == null || !id.matches("[A-Za-z0-9-]{1,20}")) throw ProjectInputPolicy.invalid("id");
        var write = new Write(id, project, actor, now, project.startDate().format(DateTimeFormatter.ofPattern("uuuuMM")));
        one(mapper.insertProject(write));
        one(mapper.insertInitialPart(write));
        one(mapper.insertContract(write));
    }
    private void one(int count) { if (count != 1) throw new IllegalStateException("프로젝트 등록 결과를 저장하지 못했습니다."); }
}
