package com.sicost.parts;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.parts.ProjectPartDtos.*;

@Mapper
public interface ProjectPartMapper {
    ProjectSource find(String id);
    List<PartSource> parts(String id);
    List<String> protectedMonths();
    String lastClose();
    PartReference reference(String id);
    int deleteReservation(@Param("id") String id,@Param("month") String month);
    int insert(PartInsert input);
    int insertHistory(PartHistory input);
}
