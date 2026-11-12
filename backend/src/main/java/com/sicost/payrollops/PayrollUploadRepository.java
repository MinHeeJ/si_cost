package com.sicost.payrollops;

import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.payrollops.PayrollUploadDtos.*;

@Repository
public class PayrollUploadRepository {
    private final PayrollUploadMapper mapper;
    public PayrollUploadRepository(PayrollUploadMapper mapper) {this.mapper=mapper;}
    public List<Stored> list(Query query) {return mapper.list(query);}
    public long count(Query query) {return mapper.count(query);}
}
