package com.sicost.meops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.meops.ProfileDtos.*;

@Mapper
public interface ProfileMapper {
    User user(@Param("userId") String userId);
    List<Role> activeRoles(@Param("userId") String userId);
    List<Part> parts(@Param("userId") String userId, @Param("month") String month);
}
