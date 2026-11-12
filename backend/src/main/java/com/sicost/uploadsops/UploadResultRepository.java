package com.sicost.uploadsops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.b9.B9Dtos.Context;
import com.sicost.b9.B9Dtos.Upload;
import org.springframework.stereotype.Repository;

@Repository
public class UploadResultRepository {
    private final UploadResultMapper mapper;
    private final ObjectMapper json;
    public UploadResultRepository(UploadResultMapper mapper, ObjectMapper json) {
        this.mapper=mapper;this.json=json;
    }
    public record WarningCounts(int fileWarningCount, int tableWarningCount) {}
    public Upload find(long id) { return mapper.find(id); }
    public WarningCounts warnings(long id) { return mapper.warnings(id); }
    public Context context(Upload upload) {
        String value=mapper.context(upload.uploadId(),upload.formType());
        if(value==null)throw new IllegalStateException("업로드 검증 이력을 확인하지 못했습니다.");
        try { return json.readValue(value,Context.class); }
        catch(JsonProcessingException e) { throw new IllegalStateException("업로드 검증 이력을 확인하지 못했습니다."); }
    }
}
