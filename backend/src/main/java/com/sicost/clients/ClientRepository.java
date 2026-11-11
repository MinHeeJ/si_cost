package com.sicost.clients;

import com.sicost.common.concurrent.AggregateLockMapper;
import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.context.CurrentUserProvider;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class ClientRepository {
    private final ClientMapper mapper;
    private final AggregateLockMapper locks;
    private final CurrentUserProvider users;
    private final CommitInvalidation invalidation;
    private final TransactionTemplate transactions;
    public ClientRepository(ClientMapper mapper, AggregateLockMapper locks, CurrentUserProvider users,
                            CommitInvalidation invalidation, PlatformTransactionManager manager) {
        this.mapper=mapper;this.locks=locks;this.users=users;this.invalidation=invalidation;
        this.transactions=new TransactionTemplate(manager);
    }
    public ClientMapper mapper(){return mapper;}
    public String actor(){return users.userId();}
    public void lockBusinessNo(String number){locks.lock("clients:business-number:"+number);}
    public <T> T write(Supplier<T> action){return transactions.execute(status->action.get());}
    public void committed(){invalidation.afterCommit("clients");}
}
