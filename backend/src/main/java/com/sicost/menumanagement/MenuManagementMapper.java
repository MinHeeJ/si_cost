package com.sicost.menumanagement;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.menumanagement.MenuManagementDtos.*;

@Mapper
public interface MenuManagementMapper {
    List<Stored> tree();
    int update(Mutation mutation);
}
