package com.sicost.payrollops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.payrollops.PayrollUploadDtos.*;

@Mapper
public interface PayrollUploadMapper {
    List<Stored> list(Query query);
    long count(Query query);
}
