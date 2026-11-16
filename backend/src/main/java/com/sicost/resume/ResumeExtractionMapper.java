package com.sicost.resume;

import java.util.List;
import org.apache.ibatis.annotations.*;
import static com.sicost.resume.ResumeExtractionDtos.*;

@Mapper
public interface ResumeExtractionMapper {
    @Select("""
        SELECT C.CODE_GRP_ID AS group_id,C.CODE,C.CODE_NM AS name,C.ADD_VAL1,C.SORT_ORD AS sort_order
        FROM TB_SY_CODE C JOIN TB_SY_CODE_GRP G ON G.CODE_GRP_ID=C.CODE_GRP_ID
        WHERE G.USE_YN='Y' AND C.USE_YN='Y' AND C.DEL_YN='N'
        AND C.CODE_GRP_ID IN ('RSM_LABEL','RSM_JOB_KW','JOB','SKILL','EMP_TYPE') ORDER BY C.SORT_ORD,C.CODE
        """) List<Code> codes();
    @Select("""
        SELECT AREA_CD AS code,AREA_LVL_CD AS level,UPPER_AREA_CD AS parent_code,SIDO_NM AS sido,SIDO_FULL_NM AS full_sido,SGG_NM AS sgg,EMD_NM AS emd
        FROM TB_SY_ADM_AREA WHERE USE_YN='Y' ORDER BY AREA_CD
        """) List<Area> areas();
    @Select("SELECT CLNT_ID AS id,CLNT_NM AS name FROM TB_BS_CLNT WHERE USE_YN='Y' AND CLNT_TYPE_CD IN ('VNDR','BOTH') ORDER BY CLNT_ID") List<Vendor> vendors();
}
