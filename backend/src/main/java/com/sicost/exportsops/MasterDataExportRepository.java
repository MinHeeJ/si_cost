package com.sicost.exportsops;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.ibatis.cursor.Cursor;
import org.springframework.stereotype.Repository;

@Repository
public class MasterDataExportRepository {
    private final MasterDataExportMapper mapper;
    public MasterDataExportRepository(MasterDataExportMapper mapper) { this.mapper = mapper; }
    public void read(String view, MasterDataExportMapper.Query query, Consumer<Map<String, Object>> sink) {
        try (Cursor<Map<String, Object>> cursor = view.equals("CLIENTS") ? mapper.clients(query) : mapper.parts(query)) {
            for (Map<String, Object> row : cursor) sink.accept(row);
        } catch (IOException e) { throw new IllegalStateException("다운로드 조회를 마치지 못했습니다."); }
    }
}
