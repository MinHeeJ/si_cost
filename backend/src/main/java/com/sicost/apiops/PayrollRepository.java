package com.sicost.apiops;

import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.apiops.PayrollDtos.*;

@Repository
public class PayrollRepository {
    private final PayrollMapper mapper;
    public PayrollRepository(PayrollMapper mapper) { this.mapper = mapper; }
    public List<Row> rows(Query query) { return mapper.rows(query); }
    public long count(Query query) { return mapper.count(query); }
    public String snapshot(String databaseMonth) { return mapper.snapshot(databaseMonth); }
}
