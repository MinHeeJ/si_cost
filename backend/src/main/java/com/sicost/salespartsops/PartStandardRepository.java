package com.sicost.salespartsops;

import com.sicost.common.concurrent.AggregateLockMapper;
import com.sicost.common.concurrent.MasterLockMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PartStandardRepository {
    private final PartStandardMapper mapper;
    private final AggregateLockMapper locks;
    private final MasterLockMapper referenceLocks;
    public PartStandardRepository(PartStandardMapper mapper, AggregateLockMapper locks, MasterLockMapper referenceLocks) {
        this.mapper=mapper; this.locks=locks; this.referenceLocks=referenceLocks;
    }
    public PartStandardMapper rows() { return mapper; }
    public void lockCashAndClose() { locks.lock("operating-standards:CASH_BASE_YM"); referenceLocks.lockClose(); }
    public void lockSettlement() { referenceLocks.lockSettlement(); }
}
