package com.sicost.businessusersops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.businessusersops.BusinessUserProjectsDtos.*;

@Mapper
public interface BusinessUserProjectsMapper {
    boolean userExists(@Param("userId") String userId);
    List<ProjectRow> projects(Query query);
}
