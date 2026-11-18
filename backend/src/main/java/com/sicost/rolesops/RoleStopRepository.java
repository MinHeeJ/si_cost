package com.sicost.rolesops;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RoleConfigurationRevision;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import static com.sicost.rolesops.RoleStopDtos.*;

@Repository
public class RoleStopRepository {
    private final RoleStopMapper mapper;
    private final RoleConfigurationRevision revisions;
    public RoleStopRepository(RoleStopMapper mapper,RoleConfigurationRevision revisions) {this.mapper=mapper;this.revisions=revisions;}
    public RoleConfigurationRevision.Snapshot snapshot(String id) {
        var snapshot=revisions.snapshot(id);
        if(snapshot.role()==null)throw new ApiException(404,"NOT_FOUND","역할을 찾을 수 없습니다.");
        return snapshot;
    }
    public long connectedUsers(String id) {return mapper.connectedUsers(id);}
    public String storedSnapshot(String id) {snapshot(id);return revisions.storedSnapshot(id);}
    public void stop(Mutation mutation) {if(mapper.stop(mutation)!=1)throw new IllegalStateException("역할을 저장하지 못했습니다.");}
    public Detail detail(String id,String revision) {
        Map<String,Object> row=snapshot(id).role();
        return new Detail(id,(String)row.get("name"),(String)row.get("dataScope"),"Y".equals(row.get("sensitiveView")),
            "Y".equals(row.get("systemRole")),"Y".equals(row.get("use")),((Number)row.get("sortOrder")).intValue(),
            revision,mapper.menuPermissions(id),false,true,revisions.settingsRevision());
    }
}
