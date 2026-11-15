package com.sicost.monthclosesops;

import java.util.*;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MonthCloseMapper {
    record Table(String name,List<String> keys,List<String> fields) {
        public Table {
            if(!name.matches("TB_[A-Z_]+")||keys.isEmpty()||java.util.stream.Stream.concat(keys.stream(),fields.stream()).anyMatch(s->!s.matches("[A-Z_]+")))throw new IllegalArgumentException("저장 식별자 오류");
        }
    }
    record Read(String table,boolean lock) {}
    record Write(String table,String month,String base,Map<String,Object> keys,Map<String,Object> values,String owner,String targetKey) {}
    List<String> rows(Read read);
    int insert(Write write);
    int update(Write write);
    int delete(Write write);
    int release(Write write);
}
