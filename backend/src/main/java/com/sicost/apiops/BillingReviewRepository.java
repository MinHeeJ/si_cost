package com.sicost.apiops;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.shared.SharedQueriesMapper;
import com.sicost.shared.SharedQueryDtos.CloseRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.apiops.BillingReviewDtos.*;

@Repository
public class BillingReviewRepository {
    private final BillingReviewMapper mapper;
    private final SharedQueriesMapper shared;
    private final RevisionService revisions;
    public BillingReviewRepository(BillingReviewMapper mapper,SharedQueriesMapper shared,RevisionService revisions) {
        this.mapper=mapper;this.shared=shared;this.revisions=revisions;
    }
    public String cashBaseMonth() {return shared.cashBaseMonth();}
    public List<CloseRow> closes(String from,String to) {return shared.monthCloses(from,to);}
    public BigDecimal standard(String code,String month) {
        var value=shared.parameter(code,month);
        return value==null||value.value()==null?null:new BigDecimal(value.value());
    }
    public List<Project> projects() {return mapper.checkProjects();}
    public Project requiredProject(String id) {
        Project row=mapper.project(id);if(row==null)throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");return row;
    }
    public List<AssignmentReference> assignments(String id) {return mapper.assignments(id);}
    public List<CheckBill> bills(CheckKey key) {return mapper.checkBills(key);}
    public String checkRevision(CheckKey key) {return revisions.revision(mapper.checkSnapshot(key));}
    public String billRevision(String id) {return revisions.revision(mapper.billSnapshot(id));}
    public List<OverdueBill> overdue(LocalDate today) {return mapper.overdue(today);}
    public List<DunningHistory> history(String id) {return mapper.sentHistory(id);}
    public List<RecipientRole> roles() {return mapper.eligibleRoles();}
    public List<RecipientScope> scopes() {return mapper.recipientScopes();}
    public ProjectPart part(String id,String month) {return mapper.projectPart(id,month);}
}
