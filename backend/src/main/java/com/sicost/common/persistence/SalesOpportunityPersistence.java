package com.sicost.common.persistence;

import java.util.*;
import java.util.regex.Pattern;

final class SalesOpportunityPersistence {
    private SalesOpportunityPersistence() {}
    static boolean allowed(String sql) {
        String s=sql.replaceAll("\\s+","").toUpperCase(Locale.ROOT);
        if(s.contains(";")||s.contains("--")||s.contains("/*"))return false;
        String child="WHEREOPP_ID=?ANDNEED_SN=?";
        if(s.equals("DELETEFROMTB_SL_OPP_NEED"+child)||s.equals("DELETEFROMTB_SL_TENT_ASGN"+child+"ANDTENT_STAT_CDIN('REL','DROP')"))return true;
        if(s.equals("UPDATETB_SL_TENT_ASGNSETBGN_YM=REPLACE(?,'-',''),TENT_STAT_CD=?,PROC_DTTM=?,MOD_USER_ID=?,MOD_DTTM=?WHERETENT_ID=CAST(?ASNUMERIC)ANDOPP_ID=?ANDTENT_STAT_CD='ACT'"))return true;
        String fields="OPP_NM|CUST_CLNT_ID|PART_ID|SALES_STEP_CD|WIN_PRBL_RT|PRBL_MANUAL_YN|EXPCT_AMT|BGN_PLAN_YM|CTRT_TYPE_CD|EXPCT_PRD_MCNT|NEXT_TODO_CN|NEXT_TODO_DDLN_DT|WORK_PLC_ADDR|RMRK|EXPCT_PRFT_RT|APRV_STAT_CD|APRV_PRFT_RT|MOD_USER_ID|MOD_DTTM";
        String need="JOB_CD|GRADE_CD|NEED_CNT|BGN_YM|PRD_MCNT|MON_MM|EXPCT_UPRC|MOD_USER_ID|MOD_DTTM";
        s=s.replace("CAST(?ASNUMERIC)","?").replace("REPLACE(?,'-','')","?").replace("CASEWHEN?THEN'Y'ELSE'N'END","?");
        String item="(?:"+fields+")=\\?",ni="(?:"+need+")=\\?";
        if(s.matches("UPDATETB_SL_OPPSET"+item+"(?:,"+item+")*WHEREOPP_ID=\\?ANDOPP_STAT_CD='ING'"))return true;
        if(s.matches("UPDATETB_SL_OPP_NEEDSET"+ni+"(?:,"+ni+")*WHEREOPP_ID=\\?ANDNEED_SN=\\?"))return true;
        var m=Pattern.compile("INSERTINTOTB_SL_(OPP|OPP_NEED)\\(([^)]+)\\)VALUES\\(([^)]+)\\)").matcher(s);
        if(!m.matches())return false;
        String allowed=m.group(1).equals("OPP")?fields+"|OPP_ID|OPP_STAT_CD|PROJ_ID|RSLT_DT|LOSE_RSN_CD|LOSE_MEMO|REG_USER_ID|REG_DTTM":need+"|OPP_ID|NEED_SN|REG_USER_ID|REG_DTTM";
        String[] names=m.group(2).split(","),values=m.group(3).split(",");
        return names.length==values.length&&Arrays.stream(names).distinct().count()==names.length
            &&Arrays.stream(names).allMatch(n->n.matches(allowed))&&Arrays.stream(values).allMatch(v->v.matches("\\?|NULL|'ING'"));
    }
}
