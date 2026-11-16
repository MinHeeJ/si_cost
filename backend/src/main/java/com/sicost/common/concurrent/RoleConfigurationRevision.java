package com.sicost.common.concurrent;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Shared stored-state revision for role reads and commands; timestamps remain full-precision text. */
@Service
public class RoleConfigurationRevision {
    private final RoleConfigurationSnapshotMapper mapper;
    private final RevisionService revisions;
    public RoleConfigurationRevision(RoleConfigurationSnapshotMapper mapper, RevisionService revisions) {
        this.mapper=mapper;this.revisions=revisions;
    }
    public record Snapshot(Map<String,Object> role, List<Map<String,Object>> permissions) {}
    public Snapshot snapshot(String roleId) {return new Snapshot(mapper.role(roleId),mapper.permissions(roleId));}
    public String storedSnapshot(String roleId) {return mapper.storedSnapshot(roleId);}
    public String revision(String roleId) {return revisions.revision(storedSnapshot(roleId));}
    public String settingsRevision() {return revisions.revision(mapper.settings());}
}
