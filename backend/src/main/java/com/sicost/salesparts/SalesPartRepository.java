package com.sicost.salesparts;

import com.sicost.common.concurrent.AggregateLockMapper;
import com.sicost.common.concurrent.MasterLockMapper;
import com.sicost.common.persistence.MasterIdService;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.salesparts.SalesPartDtos.*;

@Repository
public class SalesPartRepository {
    private final SalesPartMapper mapper;
    private final AggregateLockMapper locks;
    private final MasterLockMapper referenceLocks;
    private final MasterIdService ids;
    public SalesPartRepository(SalesPartMapper mapper, AggregateLockMapper locks, MasterLockMapper referenceLocks, MasterIdService ids) {
        this.mapper=mapper; this.locks=locks; this.referenceLocks=referenceLocks; this.ids=ids;
    }
    public List<Stored> list(Query query) { return mapper.list(query); }
    public Stored part(String id,boolean lock) { return mapper.part(id,lock); }
    public List<StoredStandard> standards(String id,boolean lock) { return mapper.standards(id,lock); }
    public String leaderName(String id) { return mapper.leaderName(id); }
    public void lockUser(String id) { mapper.lockUser(id); }
    public boolean eligible(String id) { return mapper.eligible(id); }
    public void lockName(String name) { locks.lock("sales-parts:name:"+name); }
    public void lockClose() { locks.lock("operating-standards:CASH_BASE_YM"); referenceLocks.lockClose(); }
    public boolean nameExists(String name) { return mapper.nameExists(name); }
    public Counts counts(String id,String month,boolean future) { return mapper.counts(id,com.sicost.common.persistence.DatabaseMonthCodec.toDatabase(month),future); }
    public String cashMonth() { return mapper.cashMonth(); }
    public String lastClose() { return mapper.lastClose(); }
    public String nextId() { return ids.nextPartId(); }
    public int insertPart(Registration registration) { return mapper.insertPart(registration); }
    public int insertStandard(Registration registration) { return mapper.insertStandard(registration); }
    public int update(Mutation mutation) { return mapper.update(mutation); }
}
