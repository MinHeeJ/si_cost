package com.sicost.common.persistence;

import java.util.Locale;

/** Narrow immutable recommendation result/one-time choice boundary. */
final class RecommendationPersistence {
    private RecommendationPersistence() {}
    static boolean allowed(String sql) {
        String s=sql.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        String insert="INSERTINTOTB_SL_RCMD_LOG(RCMD_ID,RANK_ORD,RCMD_TYPE_CD,OPP_ID,NEED_SN,PROJ_ID,JOB_CD,BGN_YM,REQ_GRADE_CD,DEV_ID,TOT_SCORE,SCORE_DTL,RCMD_RSN,USER_ACT_CD,RCMD_EXCL_CD,ACT_USER_ID,ACT_DTTM,REG_USER_ID,REG_DTTM)VALUES(CAST(?ASNUMERIC),?,%s,?,CAST(?ASNUMERIC),?,?,?,?,?,CAST(?ASNUMERIC),?,?,NULL,NULL,NULL,NULL,?,?)";
        if(s.equals(insert.formatted("'AI'"))||s.equals(insert.formatted("'NEXT'")))return true;
        return s.equals("UPDATETB_SL_RCMD_LOGSETUSER_ACT_CD=?,RCMD_EXCL_CD=?,ACT_USER_ID=?,ACT_DTTM=?WHERERCMD_ID=CAST(?ASNUMERIC)ANDRANK_ORD=?ANDUSER_ACT_CDISNULL");
    }
}
