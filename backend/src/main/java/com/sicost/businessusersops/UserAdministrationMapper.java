package com.sicost.businessusersops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.businessusersops.UserAdministrationDtos.*;

@Mapper
public interface UserAdministrationMapper {
    List<AssignedProject> projects(String userId);
    List<Activity> activities(ActivityQuery query);
    long activityCount(String userId);
    boolean activeAdministrator(String userId);
    long activeAdministratorCount();
    int changeState(StateWrite write);
}
