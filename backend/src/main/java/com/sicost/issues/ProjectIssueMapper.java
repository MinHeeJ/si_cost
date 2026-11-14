package com.sicost.issues;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.issues.ProjectIssueDtos.*;

@Mapper
public interface ProjectIssueMapper {
    Project project(String id);
    List<Row> issues(String id);
    boolean activeCode(@Param("group") String group, @Param("code") String code);
    int insert(Map<String,Object> values);
    int update(Mutation mutation);
}
