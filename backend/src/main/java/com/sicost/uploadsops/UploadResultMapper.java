package com.sicost.uploadsops;

import com.sicost.b9.B9Dtos.Upload;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UploadResultMapper {
    Upload find(@Param("uploadId") long uploadId);
    UploadResultRepository.WarningCounts warnings(@Param("uploadId") long uploadId);
    String context(@Param("uploadId") long uploadId, @Param("formType") String formType);
}
