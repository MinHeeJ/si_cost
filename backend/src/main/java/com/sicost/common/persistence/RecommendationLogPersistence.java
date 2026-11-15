package com.sicost.common.persistence;

import java.util.*;
import java.util.regex.Pattern;

final class RecommendationLogPersistence {
    private RecommendationLogPersistence() {}
    static boolean allowed(String sql){
        String compact=sql.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if(compact.equals("UPDATETB_SL_RCMD_LOGSETUSER_ACT_CD=?,RCMD_EXCL_CD=?,ACT_USER_ID=?,ACT_DTTM=?WHERERCMD_ID=CAST(?ASNUMERIC)ANDRANK_ORD=?ANDUSER_ACT_CDISNULL"))return true;
        var insert=Pattern.compile("INSERTINTOTB_SL_RCMD_LOG\\(([^)]+)\\)VALUES\\((.*)\\)").matcher(compact);
        if(!insert.matches())return false;
        List<String> fields=Arrays.asList(insert.group(1).split(","));
        Set<String> required=Set.of("RCMD_ID","RANK_ORD","RCMD_TYPE_CD","OPP_ID","NEED_SN","PROJ_ID","JOB_CD","BGN_YM","REQ_GRADE_CD","DEV_ID","TOT_SCORE","SCORE_DTL","RCMD_RSN","REG_USER_ID","REG_DTTM");
        if(fields.size()!=required.size()||!new HashSet<>(fields).equals(required))return false;
        String values=insert.group(2).replace("CAST(?ASNUMERIC)","?");
        return values.matches("\\?(?:,\\?){14}");
    }
}
