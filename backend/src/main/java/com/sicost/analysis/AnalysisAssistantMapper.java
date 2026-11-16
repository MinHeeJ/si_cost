package com.sicost.analysis;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
import static com.sicost.analysis.AnalysisAssistantDtos.*;

@Mapper
public interface AnalysisAssistantMapper {
    @Select("SELECT nextval('SQ_TB_SY_AI_QNA')::text") String nextId();
    @org.apache.ibatis.annotations.Insert("""
        INSERT INTO TB_SY_AI_QNA (QNA_ID,USER_ID,QSTN_CN,ANS_CN,USE_DATA_DESC,BASE_YM,PRVS_YN,ANS_STAT_CD,QSTN_DTTM,REG_USER_ID,REG_DTTM,MOD_USER_ID,MOD_DTTM)
        VALUES (CAST(#{qnaId} AS NUMERIC),#{userId},#{question},#{answer},#{description},#{baseMonth},#{provisional},#{status},#{questionAt},#{userId},#{registeredAt},NULL,NULL)
        """) void insert(AnalysisAssistantDtos.Insert row);
    String COLUMNS="QNA_ID::text AS qna_id,USER_ID,QSTN_CN AS question,ANS_CN AS answer,USE_DATA_DESC AS description,BASE_YM AS base_month,PRVS_YN AS provisional,ANS_STAT_CD AS status,QSTN_DTTM AS question_at";
    @Select("SELECT "+COLUMNS+" FROM TB_SY_AI_QNA WHERE USER_ID=#{userId} AND QNA_ID=CAST(#{id} AS NUMERIC)")
    Stored find(@Param("userId") String userId,@Param("id") String id);
    @Select("""
        <script>SELECT ${@com.sicost.analysis.AnalysisAssistantMapper@COLUMNS} FROM TB_SY_AI_QNA WHERE USER_ID=#{userId}
        <if test='at != null'>AND (QSTN_DTTM &lt; #{at} OR (QSTN_DTTM=#{at} AND QNA_ID &lt; CAST(#{id} AS NUMERIC)))</if>
        ORDER BY TB_SY_AI_QNA.QSTN_DTTM DESC,TB_SY_AI_QNA.QNA_ID DESC LIMIT #{limit}</script>
        """)
    List<Stored> page(@Param("userId") String userId,@Param("at") LocalDateTime at,@Param("id") String id,@Param("limit") int limit);
    public record Entity(String id,String name,String number) {}
    @Select("SELECT DEV_ID AS id,DEV_NM AS name,MNG_NO AS number FROM TB_HR_DEV ORDER BY DEV_ID") List<Entity> people();
    @Select("SELECT PROJ_ID AS id,PROJ_NM AS name,PROJ_ID AS number FROM TB_PJ_PROJ ORDER BY PROJ_ID") List<Entity> projects();
    @Select("SELECT max(CLOSE_YM) FROM TB_CL_CLOSE WHERE CLOSE_STAT_CD='CLOSE'") String latestClose();
    @Select("SELECT EXISTS (SELECT 1 FROM TB_CL_CLOSE WHERE CLOSE_YM=#{month} AND CLOSE_STAT_CD='CLOSE')") boolean closed(String month);
}
