package com.sicost.monthclosesops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.CostManagementApplication;
import com.sicost.common.history.AccessLogWriter;
import com.sicost.common.installation.PostgreSqlDatabase;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Full HTTP -> calculation -> guarded MyBatis -> PostgreSQL path, in a fresh schema per test. */
@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
@SpringBootTest(classes=CostManagementApplication.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MonthCloseDatabaseTest {
    static PostgreSqlDatabase database;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry)throws Exception {
        database=new PostgreSqlDatabase();
        registry.add("spring.datasource.url",database.source::getUrl);
        registry.add("spring.datasource.username",()->System.getenv("SICOST_TEST_DB_USER"));
        registry.add("spring.datasource.password",()->System.getenv("SICOST_TEST_DB_PASSWORD"));
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate sql;
    @SpyBean AccessLogWriter access;
    @SpyBean MonthCloseRepository repository;
    String month,dbMonth,part;

    @BeforeEach void ownSources()throws Exception {
        var context=json.readTree(mvc.perform(get("/api/period-context")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        month=context.path("cashBaseMonth").asText();dbMonth=month.replace("-","");
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_cl_close",Integer.class)).isZero();
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_fi_corp_tax WHERE tax_yr=?",Integer.class,month.substring(0,4))).isZero();
        sql.update("INSERT INTO tb_fi_corp_tax(tax_yr,est_tax_amt,reg_user_id) VALUES (?,0,'admin')",month.substring(0,4));
        part="T"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        var at=java.sql.Timestamp.valueOf(YearMonth.parse(month).atDay(1).atStartOfDay());
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id,reg_dttm) VALUES (?,?,'N','Y','admin',?)",part,part,at);
        sql.update("INSERT INTO tb_bs_part_std(part_id,aply_bgn_ym,part_ldr_user_id,prft_dstrb_rt,sales_exps_amt,reg_user_id,reg_dttm) VALUES (?,?,'admin',0,0,'admin',?)",part,dbMonth,at);
    }
    @Test void allFiveHandlersPersistAndReplayWithoutRewritingSettlements()throws Exception {
        String initial=revision();Map<String,String> before=state();
        var preview=postCommand("preview",Map.of("expectedRevision",initial),200);
        assertThat(preview.path("checks").size()).isEqualTo(6);
        assertThat(preview.path("canClose").asBoolean()).isTrue();assertThat(state()).isEqualTo(before);
        var checked=postCommand("checks",Map.of("expectedRevision",initial),200);
        assertThat(checked.path("checks").size()).isEqualTo(6);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_cl_close_chk WHERE close_ym=?",Integer.class,dbMonth)).isEqualTo(6);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_cl_close_hist",Integer.class)).isZero();
        String ready=revision();
        var closePreview=postCommand("preview",Map.of("expectedRevision",ready),200);
        var close=Map.<String,Object>of("expectedRevision",closePreview.path("revision").asText(),"operationId",UUID.randomUUID().toString(),"acceptWarnings",true);
        var result=postCommand("close",close,200);
        assertThat(result.path("status").asText()).isEqualTo("CLOSE");
        assertThat(sql.queryForObject("SELECT fix_yn||':'||migr_yn FROM tb_co_mon_pl WHERE pl_ym=?",String.class,dbMonth)).isEqualTo("Y:N");
        assertThat(sql.queryForObject("SELECT setl_stat_cd FROM tb_co_part_setl WHERE part_id=? AND setl_ym=?",String.class,part,dbMonth)).isEqualTo("AUTO");
        Map<String,String> closed=state();
        assertThat(postCommand("close",close,200).path("replayed").asBoolean()).isTrue();assertThat(state()).isEqualTo(closed);
        String confirmed=sql.queryForObject("SELECT row_to_json(t)::text FROM tb_co_part_setl t WHERE part_id=? AND setl_ym=?",String.class,part,dbMonth);
        var reopenPreview=postCommand("reopen-preview",Map.of("expectedRevision",revision()),200);
        assertThat(reopenPreview.path("canReopen").asBoolean()).isTrue();assertThat(state()).isEqualTo(closed);
        var reopen=Map.<String,Object>of("expectedRevision",reopenPreview.path("revision").asText(),"operationId",UUID.randomUUID().toString(),"reason","시험 소유 자료 정정","acceptAdjustments",false);
        assertThat(postCommand("reopen",reopen,200).path("status").asText()).isEqualTo("WORK");
        assertThat(sql.queryForObject("SELECT fix_yn FROM tb_co_mon_pl WHERE pl_ym=?",String.class,dbMonth)).isEqualTo("N");
        assertThat(sql.queryForObject("SELECT close_user_id IS NULL AND close_dttm IS NULL FROM tb_cl_close WHERE close_ym=?",Boolean.class,dbMonth)).isTrue();
        assertThat(sql.queryForObject("SELECT row_to_json(t)::text FROM tb_co_part_setl t WHERE part_id=? AND setl_ym=?",String.class,part,dbMonth)).isEqualTo(confirmed);
        Map<String,String> opened=state();
        assertThat(postCommand("reopen",reopen,200).path("replayed").asBoolean()).isTrue();assertThat(state()).isEqualTo(opened);
        postCommand("close",close,409);assertThat(state()).isEqualTo(opened);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_cl_close_hist WHERE close_ym=?",Integer.class,dbMonth)).isEqualTo(2);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE scrn_id='UI-14'",Integer.class)).isEqualTo(2);
    }
    @Test void requiredAccessFailureRollsBackCopiesAutoChecksAndHistory()throws Exception {
        String revision=revision();Map<String,String> before=state();
        AccessLogWriter target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(access);
        doThrow(new IllegalStateException("private SQL must not leak")).when(target).appendMonthClose(any(),anyString(),any(),any());
        var response=mvc.perform(post("/api/month-closes/"+month+"/close").contentType("application/json").content(json.writeValueAsString(Map.of("expectedRevision",revision,"operationId",UUID.randomUUID().toString(),"acceptWarnings",true))))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("private SQL");assertThat(state()).isEqualTo(before);
    }
    @Test void thirdCheckFailureRollsBackTheNewParentAndEarlierCheckRows()throws Exception {
        String revision=revision();Map<String,String> before=state();
        doThrow(new IllegalStateException("check save failed")).when(repository).save(eq("TB_CL_CLOSE_CHK"),argThat(row->"BILL".equals(row.get("chk_item_cd"))),any(),anyBoolean());
        mvc.perform(post("/api/month-closes/"+month+"/checks").contentType("application/json").content(json.writeValueAsString(Map.of("expectedRevision",revision))))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertThat(state()).isEqualTo(before);
    }
    private String revision()throws Exception {
        return json.readTree(mvc.perform(get("/api/month-closes").param("month",month)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("revision").asText();
    }
    private JsonNode postCommand(String action,Map<String,Object> body,int expected)throws Exception {
        var response=mvc.perform(post("/api/month-closes/"+month+"/"+action).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().is(expected)).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("data");
    }
    private Map<String,String> state() {
        Map<String,String> result=new TreeMap<>();
        for(String table:List.of("tb_cl_close","tb_cl_close_chk","tb_cl_close_hist","tb_pj_asgn_mon","tb_co_mon_cost","tb_co_mon_pl","tb_co_part_setl","tb_co_part_setl_dtl","tb_hr_mon_pay","tb_bl_bill","tb_bl_rcpt","tb_fi_item","tb_fi_corp_tax","tb_fi_tax_pay","tb_sy_chg_hist","tb_sy_accs_log"))
            result.put(table,sql.queryForObject("SELECT COALESCE(jsonb_agg(row_to_json(t) ORDER BY row_to_json(t)::text),'[]'::jsonb)::text FROM "+table+" t",String.class));
        return result;
    }
}
