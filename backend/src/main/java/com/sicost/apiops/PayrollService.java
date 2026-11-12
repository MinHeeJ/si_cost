package com.sicost.apiops;

import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.persistence.DatabaseMonthCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.apiops.PayrollDtos.*;

@Service
public class PayrollService {
    private final PayrollRepository repository;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    public PayrollService(PayrollRepository repository, RevisionService revisions, CurrentUserProvider users) {
        this.repository = repository; this.revisions = revisions; this.users = users;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page getPayroll(Query query) {
        users.currentUser();
        String revision = revisions.revision(repository.snapshot(query.month()));
        return new Page(repository.rows(query), repository.count(query), query.page(), query.size(),
            DatabaseMonthCodec.toApi(query.month()), revision);
    }
}
