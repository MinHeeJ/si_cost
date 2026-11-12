package com.sicost.pm;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.pm.ProjectPmDtos.*;

@Mapper
public interface ProjectPmMapper {
    ProjectSource find(String id);
    List<Scope> scopes(String id);
    boolean eligible(String userId);
    int greatestScopeSn(String userId);
    int insertScope(ScopeWrite input);
    int deleteScope(ScopeWrite input);
    int insertHistory(PmHistory input);
}
