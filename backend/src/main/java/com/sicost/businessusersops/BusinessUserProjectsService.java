package com.sicost.businessusersops;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.ProjectInputPolicy;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.businessusersops.BusinessUserProjectsDtos.*;

@Service
public class BusinessUserProjectsService {
    private final BusinessUserProjectsRepository repository;
    private final CurrentUserProvider users;
    private final RevisionService revisions;
    private final Clock clock;
    public BusinessUserProjectsService(BusinessUserProjectsRepository repository, CurrentUserProvider users,
                                       RevisionService revisions, Clock clock) {
        this.repository=repository;this.users=users;this.revisions=revisions;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public BusinessUserProjectsData getBusinessUserProjects(String userId) {
        String id=ProjectInputPolicy.text(userId,30,"userId");
        users.currentUser();
        if (!repository.userExists(id)) throw new ApiException(404,"NOT_FOUND","사용자를 찾을 수 없습니다.");
        var today=ProjectInputPolicy.today(clock);
        var rows=repository.projects(new Query(id,today,YearMonth.from(today).toString().replace("-","")));
        var items=rows.stream().map(this::view).toList();
        return new BusinessUserProjectsData(id,items,revisions.revision(new ListingSnapshot(id,rows)));
    }
    private ProjectItem view(ProjectRow row) {
        DistributionBadge badge=null;
        if ("TK".equals(row.contractType())) {
            badge=new DistributionBadge(row.selectedStep(),row.selectedStep()!=null,
                ProjectInputPolicy.amountString(row.distributionTotal()),
                row.contractAmount().subtract(row.distributionTotal()).toPlainString(),row.mismatchCount());
        }
        return new ProjectItem(row.id(),row.name(),row.contractType(),row.startDate().toString(),row.endDate().toString(),
            "IN".equals(row.contractType())?null:ProjectInputPolicy.amountString(row.contractAmount()),row.status(),
            "IN".equals(row.contractType())?"(자체)":row.customerName(),row.personCount(),row.partName(),badge);
    }
    private record ListingSnapshot(String userId, java.util.List<ProjectRow> projects) {}
}
