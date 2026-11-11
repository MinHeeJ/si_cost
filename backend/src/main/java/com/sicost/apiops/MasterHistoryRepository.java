package com.sicost.apiops;

import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.apiops.MasterHistoryDtos.*;

@Repository
public class MasterHistoryRepository {
    private final MasterHistoryMapper mapper;
    public MasterHistoryRepository(MasterHistoryMapper mapper) { this.mapper = mapper; }
    public boolean exists(Query query) { return mapper.exists(query); }
    public long count(Query query) { return mapper.count(query); }
    public List<Entry> list(Query query) { return mapper.list(query); }
}
