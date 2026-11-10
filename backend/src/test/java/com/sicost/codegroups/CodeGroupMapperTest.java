package com.sicost.codegroups;

import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class CodeGroupMapperTest {
    Configuration configuration() throws Exception {
        Configuration config=new Configuration();
        try(var input=new ClassPathResource("mappers/codegroups/CodeGroupMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input,config,"mappers/codegroups/CodeGroupMapper.xml",config.getSqlFragments()).parse();
        }
        return config;
    }
    String sql(String operation,Map<String,Object> parameters) throws Exception {
        return configuration().getMappedStatement("com.sicost.codegroups.CodeGroupMapper."+operation).getBoundSql(parameters).getSql().replaceAll("\\s+"," ");
    }
    @ParameterizedTest @CsvSource({"JOB,tb_hr_dev,job_cd","JOB,tb_hr_cand,sub_job_cd","JOB,tb_pj_mnpw_plan,job_cd","JOB,tb_pj_asgn,job_cd","JOB,tb_sl_opp_need,job_cd","JOB,tb_sl_rcmd_log,job_cd","GRADE,tb_hr_dev,grade_cd","GRADE,tb_hr_cand,grade_cd","GRADE,tb_sl_opp_need,grade_cd","GRADE,tb_sl_rcmd_log,req_grade_cd","JBPS,tb_hr_dev,jbps_cd","EXT_PSBL,tb_pj_asgn,ext_psbl_cd","SGA_ITEM,tb_fi_item,sga_item_cd","DRCT_EXPS_ITEM,tb_pj_drct_exps,drct_exps_item_cd","CHG_ITEM,tb_pj_proj_chg,chg_item_cd","IMPR,tb_pj_issue,impr_cd","PROJ_STEP,tb_pj_step,proj_step_cd","CNTC_STAT,tb_hr_cand,cntc_stat_cd","CNTC_STAT,tb_hr_cand_cntc,cntc_stat_cd","CAND_SRC,tb_hr_cand,cand_src_cd","ASGN_CHG_RSN,tb_pj_asgn_hist,asgn_chg_rsn_cd","LOSE_RSN,tb_pj_proj,lose_rsn_cd","LOSE_RSN,tb_sl_opp,lose_rsn_cd","RCMD_EXCL,tb_sl_rcmd_log,rcmd_excl_cd","BANK,tb_bs_clnt,bank_cd","INDUTY,tb_bs_clnt,induty_cd","SKILL,tb_hr_dev_skill,skill_nm","SKILL,tb_hr_cand_skill,skill_nm"})
    void eachFixedReferenceMappingIncludesItsBusinessColumn(String group,String table,String column) throws Exception {
        assertThat(sql("references",Map.of("group",group,"code","A"))).contains(table,column,"tb_sy_code_map");
    }
    @Test void skillChecksCurrentAndHistoricalNamesAndExtensionChecksExptHistory() throws Exception {
        assertThat(sql("references",Map.of("group","SKILL","code","A"))).contains("code_nm","bfr_val","aft_val","chg_item_nm='CODE_NM'");
        assertThat(sql("references",Map.of("group","EXT_PSBL","code","A"))).contains("tb_pj_asgn_hist","asgn_chg_type_cd='EXTP'","bfr_val","aft_val","code_nm");
    }
    @Test void ownNewHistoryIsNotAUsageAndCsvExcludesOwnRow() throws Exception {
        String sql=sql("references",Map.of("group","JOB","code","A"));
        assertThat(sql).contains("string_to_array","NOT(c.code_grp_id=? AND c.code=?)").doesNotContain("tb_sy_chg_hist","close_stat_cd","asgn_stat_cd");
    }
    @Test void newBusinessGroupOnlyChecksMappingsWithoutInterpolatingInputIntoSql() throws Exception {
        String sql=sql("references",Map.of("group","tb_pj_proj; DELETE FROM tb_sy_code","code","A"));
        assertThat(sql).contains("tb_sy_code_map").doesNotContain("tb_pj_proj","DELETE");
    }
    @Test void revisionQueryLocksCompleteAggregateIncludingDeletedRowsInKeyOrder() throws Exception {
        String sql=sql("codes",Map.of("group","EDUCATION","lock",true));
        assertThat(sql).contains("ORDER BY code FOR UPDATE","reg_dttm","mod_dttm","code_val","del_yn").doesNotContain("LIMIT","OFFSET","del_yn='N'");
    }
    @Test void listCountsOnlyNonDeletedAndHasStableOrdering() throws Exception {
        assertThat(sql("groups",Map.of())).contains("c.del_yn='N'","ORDER BY g.sort_ord,g.code_grp_id");
    }
}
