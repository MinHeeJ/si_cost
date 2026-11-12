package com.sicost.assignments;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssignmentMapper {
    record CodeLabel(String group,String code,String name) {}
    List<CodeLabel> labels();
    List<String> lockDevelopers(@Param("ids") List<String> ids);
    String lockProject(String id);
}
