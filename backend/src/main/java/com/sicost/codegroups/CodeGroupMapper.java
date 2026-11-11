package com.sicost.codegroups;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.codegroups.CodeGroupDtos.*;

@Mapper
public interface CodeGroupMapper {
    List<Group> groups();
    StoredGroup group(@Param("group") String group, @Param("lock") boolean lock);
    List<StoredCode> codes(@Param("group") String group, @Param("lock") boolean lock);
    void lockGroups();
    int maxGroupOrder();
    long duplicateName(@Param("name") String name);
    void insertGroup(GroupInsert insert);
    void insertCode(Mutation mutation);
    void updateCode(Mutation mutation);
    List<Reference> references(@Param("group") String group, @Param("code") String code);
}
