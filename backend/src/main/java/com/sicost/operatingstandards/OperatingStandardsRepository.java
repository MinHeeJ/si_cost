package com.sicost.operatingstandards;

import com.sicost.common.concurrent.AggregateLockMapper;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.operatingstandards.OperatingStandardsDtos.*;

@Repository
public class OperatingStandardsRepository {
    private final OperatingStandardsMapper mapper;
    private final AggregateLockMapper locks;
    public OperatingStandardsRepository(OperatingStandardsMapper mapper,AggregateLockMapper locks) {this.mapper=mapper;this.locks=locks;}
    public List<Stored> all(){return mapper.all();}
    public List<Stored> rows(String key,boolean lock){return mapper.rows(key,lock);}
    public void lockKey(String key){locks.lock("operating-standards:"+key);}
    public List<String> closedMonths(){return mapper.closedMonths();}
    public int update(Mutation mutation){return mapper.update(mutation);}
    public int insert(Mutation mutation){return mapper.insert(mutation);}
}
