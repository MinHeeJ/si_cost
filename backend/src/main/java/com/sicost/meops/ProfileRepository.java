package com.sicost.meops;

import com.sicost.common.api.ApiException;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.meops.ProfileDtos.*;

@Repository
public class ProfileRepository {
    private final ProfileMapper mapper;
    public ProfileRepository(ProfileMapper mapper) { this.mapper=mapper; }
    public User requiredUser(String userId) {
        User user=mapper.user(userId);
        if(user==null)throw new ApiException(404,"NOT_FOUND","현재 사용자 정보를 찾을 수 없습니다.");
        return user;
    }
    public List<Role> activeRoles(String userId) { return mapper.activeRoles(userId); }
    public List<Part> parts(String userId,String month) { return mapper.parts(userId,month); }
}
