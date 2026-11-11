package com.sicost.contacts;

import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.context.CurrentUserProvider;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class CandidateContactRepository {
    private final CandidateContactMapper mapper;
    private final CurrentUserProvider users;
    private final CommitInvalidation invalidation;
    private final TransactionTemplate transactions;
    public CandidateContactRepository(CandidateContactMapper mapper,CurrentUserProvider users,
            CommitInvalidation invalidation,PlatformTransactionManager manager) {
        this.mapper=mapper;this.users=users;this.invalidation=invalidation;this.transactions=new TransactionTemplate(manager);
    }
    public CandidateContactMapper mapper(){return mapper;}
    public String actor(){return users.userId();}
    public <T> T write(Supplier<T> action){return transactions.execute(status->action.get());}
    public void committed(){invalidation.afterCommit("candidates");}
}
