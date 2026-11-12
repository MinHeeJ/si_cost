package com.sicost.businessusers;

import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.businessusers.BusinessUserDtos.*;

@Repository
public class BusinessUserRepository {
    private final BusinessUserMapper mapper;
    public BusinessUserRepository(BusinessUserMapper mapper) {this.mapper=mapper;}
    public List<Stored> list(Filter filter) {return mapper.list(filter);}
    public long count(Filter filter) {return mapper.count(filter);}
    public Stored find(String id,boolean lock) {return mapper.find(id,lock);}
    public List<Role> roles(String id) {return mapper.roles(id);}
    public List<Role> availableRoles() {return mapper.availableRoles();}
    public void lockUser(String id) {mapper.lockUser(id);}
    public List<String> connectedProjects(String id) {return mapper.connectedProjects(id);}
    public int insert(NewUser user) {return mapper.insert(user);}
    public int update(Mutation mutation) {return mapper.update(mutation);}
    public int link(RoleLink link) {return mapper.link(link);}
    public int unlink(RoleLink link) {return mapper.unlink(link);}
    public List<String> leaderParts(String id,String month) {return mapper.leaderParts(id,month);}
}
