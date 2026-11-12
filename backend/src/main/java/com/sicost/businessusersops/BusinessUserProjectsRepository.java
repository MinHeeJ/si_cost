package com.sicost.businessusersops;

import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.businessusersops.BusinessUserProjectsDtos.*;

@Repository
public class BusinessUserProjectsRepository {
    private final BusinessUserProjectsMapper mapper;
    public BusinessUserProjectsRepository(BusinessUserProjectsMapper mapper) { this.mapper=mapper; }
    public boolean userExists(String userId) { return mapper.userExists(userId); }
    public List<ProjectRow> projects(Query query) { return mapper.projects(query); }
}
