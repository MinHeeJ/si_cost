package com.sicost.businessusersops;

import com.sicost.businessusers.*;
import com.sicost.common.api.ApiException;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.businessusersops.UserAdministrationDtos.*;

@Repository
public class UserAdministrationRepository {
    private final UserAdministrationMapper mapper;
    private final BusinessUserRepository users;
    private final BusinessUserService details;
    public UserAdministrationRepository(UserAdministrationMapper mapper,BusinessUserRepository users,BusinessUserService details) {
        this.mapper=mapper;this.users=users;this.details=details;
    }
    public BusinessUserDtos.Stored required(String id) {
        var row=users.find(id,false);
        if(row==null)throw new ApiException(404,"NOT_FOUND","사용자를 찾을 수 없습니다.");
        return row;
    }
    public String snapshot(String id) {required(id);return users.snapshot(id);}
    public BusinessUserDtos.User detail(String id) {return details.get(id);}
    public List<BusinessUserDtos.AssignedPart> parts(String id,String month) {return users.assignedParts(id,month);}
    public List<AssignedProject> projects(String id) {return mapper.projects(id);}
    public List<String> leaders(String id,String month) {return users.leaderParts(id,month);}
    public boolean lastAdministrator(String id) {return mapper.activeAdministrator(id) && mapper.activeAdministratorCount()<=1;}
    public Activities activities(ActivityQuery query) {
        return new Activities(mapper.activities(query),mapper.activityCount(query.userId()),query.page(),query.size());
    }
    public void change(StateWrite write) {
        if(mapper.changeState(write)!=1)throw new IllegalStateException("사용자 상태를 저장하지 못했습니다.");
    }
}
