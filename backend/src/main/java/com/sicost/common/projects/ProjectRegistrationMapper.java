package com.sicost.common.projects;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import com.sicost.common.projects.ProjectRegistrationService.Write;

@Mapper
public interface ProjectRegistrationMapper {
    List<String> existingIds();
    int insertProject(Write write);
    int insertInitialPart(Write write);
    int insertContract(Write write);
}
