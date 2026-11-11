package com.sicost.candidates;

import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.context.CurrentUserProvider;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class CandidateRepository {
    private final CandidateMapper mapper;
    private final CurrentUserProvider users;
    private final CommitInvalidation invalidation;
    private final TransactionTemplate transaction;
    public CandidateRepository(CandidateMapper mapper,CurrentUserProvider users,CommitInvalidation invalidation,PlatformTransactionManager manager){this.mapper=mapper;this.users=users;this.invalidation=invalidation;transaction=new TransactionTemplate(manager);}
    public CandidateMapper mapper(){return mapper;}
    public String actor(){return users.userId();}
    public <T> T write(Supplier<T> action){return transaction.execute(status->action.get());}
    public void committed(){invalidation.afterCommit("candidates");}
}
