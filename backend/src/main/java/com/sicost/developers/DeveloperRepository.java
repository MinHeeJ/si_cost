package com.sicost.developers;

import com.sicost.common.concurrent.AggregateLockMapper;
import com.sicost.common.concurrent.DeveloperAggregateRevision;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.api.ApiException;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class DeveloperRepository {
    private final DeveloperMapper mapper;
    private final CurrentUserProvider users;
    private final AggregateLockMapper locks;
    private final DeveloperAggregateRevision revisions;
    private final TransactionTemplate writes,reads,recalculation;
    public DeveloperRepository(DeveloperMapper mapper,CurrentUserProvider users,AggregateLockMapper locks,PlatformTransactionManager manager,DeveloperAggregateRevision revisions) {
        this.mapper=mapper;this.users=users;this.locks=locks;this.revisions=revisions;
        writes=new TransactionTemplate(manager);reads=new TransactionTemplate(manager);reads.setReadOnly(true);reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        recalculation=new TransactionTemplate(manager);recalculation.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public DeveloperMapper mapper(){return mapper;}
    public String actor(){return users.userId();}
    public String revision(String id,boolean lock){return revisions.revision(id,lock);}
    public void lockNumber(String number){locks.lock("developers:management-number:"+number);}
    public <T>T write(Supplier<T> action){return writes.execute(s->action.get());}
    public <T>T read(Supplier<T> action){return reads.execute(s->action.get());}
    public void recalculate(Runnable action){recalculation.executeWithoutResult(s->action.run());}
    public Map<String,Object> required(String id,boolean lock){Map<String,Object> r=mapper.find(id,lock);if(r==null)throw new ApiException(404,"NOT_FOUND","개발자를 찾을 수 없습니다.");return new LinkedHashMap<>(r);}
    public record Snapshot(Map<String,Object> developer,List<Map<String,Object>> prices,List<Map<String,Object>> skills,List<Map<String,Object>> states) {}
    public Snapshot snapshot(String id,boolean lock){Map<String,Object> r=required(id,lock);return new Snapshot(r,mapper.prices(id,lock),mapper.skills(id,lock),mapper.states(id,lock));}
}
