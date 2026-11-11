package com.sicost.clientsops;

import com.sicost.common.concurrent.ClientSnapshot;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.clientsops.ClientActionsDtos.*;

@Repository
public class ClientActionsRepository {
    private final ClientActionsMapper mapper;
    public ClientActionsRepository(ClientActionsMapper mapper) { this.mapper=mapper; }
    public List<Option> options(OptionQuery query) { return mapper.options(query); }
    public List<ClientSnapshot> selection(Filters filters) { return mapper.selection(filters); }
    public ClientSnapshot client(String id, boolean lock) { return mapper.client(id,lock); }
    public String bankName(String code) { return mapper.bankName(code); }
    public List<Assignment> assignments(AssignmentQuery query) { return mapper.assignments(query); }
    public long assignmentCount(AssignmentQuery query) { return mapper.assignmentCount(query); }
    public List<Reference> references(String id,String type) { return mapper.references(id,type); }
    public int status(StatusChange change) { return mapper.status(change); }
}
