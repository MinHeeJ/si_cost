package com.sicost.businessusers;

import java.time.LocalDateTime;
import java.util.List;

public final class BusinessUserDtos {
    private BusinessUserDtos() {}
    public record Stored(String userId, String name, String email, String status, LocalDateTime lastLoginAt,
                         String regUserId, LocalDateTime regDttm, String modUserId, LocalDateTime modDttm) {}
    public record Role(String roleId, String name, String useYn) {}
    public record Snapshot(String userId, String name, String email, LocalDateTime regDttm,
                           LocalDateTime modDttm, List<String> roleIds) {}
    public record User(String userId, String name, String email, List<String> roles, List<String> businessRoleIds,
                       String status, String lastLoginAt, String revision, List<String> roleIds,
                       boolean headOffice, List<AssignedPart> assignedParts, boolean tempPassword) {}
    public record AssignedPart(String partId, String name, boolean headOffice) {}
    public record Page(List<User> items, long total, int page, int size) {}
    public record Result(boolean changed, String message, User user) {}
    public record Filter(String q, String businessRole, String status, int page, int size,
                         String sort, String direction, long offset, String role, boolean includeStopped) {
        public Filter(String q,String businessRole,String status,int page,int size,String sort,String direction,long offset) {this(q,businessRole,status,page,size,sort,direction,offset,null,false);}
    }
    public record ProjectAssignment(String projectId,String expectedRevision,boolean replaceConfirmed) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Create(String userId, String name, String email, List<String> businessRoleIds,
                         List<String> roleIds, boolean headOffice, List<ProjectAssignment> projectAssignments) {
        public Create(String id,String name,String email,List<String> roles) {this(id,name,email,roles,null,false,List.of());}
        public List<String> requestedRoles() {return roleIds==null?businessRoleIds:roleIds;}
    }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Edit(String name, String email, List<String> businessRoleIds, String expectedRevision,List<String> roleIds,Boolean headOffice) {
        public Edit(String name,String email,List<String> roles,String revision) {this(name,email,roles,revision,null,null);}
    }
    public record HeadOfficeWrite(String userId,int scopeSn,String actor,LocalDateTime now) {}
    public record NewUser(Create input, String passwordHash, String actor, LocalDateTime now) {}
    public record Mutation(String userId, Edit input, String actor, LocalDateTime now) {}
    public record RoleLink(String userId, String roleId, String actor, LocalDateTime now) {}
}
