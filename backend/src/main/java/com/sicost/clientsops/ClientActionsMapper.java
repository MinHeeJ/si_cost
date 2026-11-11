package com.sicost.clientsops;

import com.sicost.common.concurrent.ClientSnapshot;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.clientsops.ClientActionsDtos.*;

@Mapper
public interface ClientActionsMapper {
    List<Option> options(OptionQuery query);
    List<ClientSnapshot> selection(Filters filters);
    ClientSnapshot client(@Param("id") String id, @Param("lock") boolean lock);
    String bankName(@Param("code") String code);
    List<Assignment> assignments(AssignmentQuery query);
    long assignmentCount(AssignmentQuery query);
    List<Reference> references(@Param("id") String id, @Param("type") String type);
    int status(StatusChange change);
}
