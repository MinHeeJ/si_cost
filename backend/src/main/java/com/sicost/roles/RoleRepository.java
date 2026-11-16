package com.sicost.roles;

import com.sicost.common.api.ApiException;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.roles.RoleDtos.*;

@Repository
public class RoleRepository {
    private final RoleMapper mapper;
    public RoleRepository(RoleMapper mapper) {this.mapper=mapper;}
    public List<Stored> roles() {return mapper.roles();}
    public Stored required(String id) {
        Stored row=mapper.find(id);if(row==null)throw new ApiException(404,"NOT_FOUND","역할을 찾을 수 없습니다.");return row;
    }
    public List<Permission> permissions(String id) {return mapper.permissions(id);}
    public String snapshot(String id) {required(id);return mapper.snapshot(id);}
    public String settingsSnapshot() {return mapper.settingsSnapshot();}
    public void insert(RoleWrite row) {requireWrite(mapper.insert(row));}
    public void update(RoleWrite row) {requireWrite(mapper.update(row));}
    public boolean permissionExists(PermissionWrite row) {return mapper.permissionExists(row);}
    public void permission(PermissionWrite row) {
        requireWrite(mapper.permissionExists(row)?mapper.updatePermission(row):mapper.insertPermission(row));
    }
    private void requireWrite(int count) {if(count!=1)throw new IllegalStateException("역할 설정을 저장하지 못했습니다.");}
}
