package com.sicost.states;

import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.context.CurrentUserProvider;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class DeveloperStateRepository {
    private final DeveloperStateMapper mapper;
    private final CurrentUserProvider users;
    private final CommitInvalidation invalidation;
    private final TransactionTemplate writes;
    private final TransactionTemplate reads;
    public DeveloperStateRepository(DeveloperStateMapper mapper, CurrentUserProvider users,
                                    CommitInvalidation invalidation, PlatformTransactionManager manager) {
        this.mapper=mapper;this.users=users;this.invalidation=invalidation;
        writes=new TransactionTemplate(manager);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        reads=new TransactionTemplate(manager);reads.setReadOnly(true);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    public DeveloperStateMapper mapper(){return mapper;}
    public String actor(){return users.userId();}
    public <T> T write(Supplier<T> action){return writes.execute(status->action.get());}
    public <T> T read(Supplier<T> action){return reads.execute(status->action.get());}
    public void committed(){invalidation.afterCommit("developers");}
}
