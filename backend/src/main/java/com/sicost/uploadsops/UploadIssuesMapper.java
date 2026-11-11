package com.sicost.uploadsops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.uploadsops.UploadIssuesDtos.*;

@Mapper
public interface UploadIssuesMapper {
    boolean exists(@Param("uploadId") long uploadId);
    long count(Query query);
    List<Issue> issues(Query query);
}
