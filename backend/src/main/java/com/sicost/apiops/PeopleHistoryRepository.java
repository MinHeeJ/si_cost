package com.sicost.apiops;

import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.apiops.PeopleHistoryDtos.*;

@Repository
public class PeopleHistoryRepository {
    private final PeopleHistoryMapper mapper;
    public PeopleHistoryRepository(PeopleHistoryMapper mapper) { this.mapper = mapper; }
    public boolean exists(Query query) { return mapper.exists(query); }
    public long count(Query query) { return mapper.count(query); }
    public List<Entry> list(Query query) { return mapper.list(query); }
}
