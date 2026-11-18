package com.sicost.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.CostManagementApplication;
import com.sicost.common.installation.PostgreSqlDatabase;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
@SpringBootTest(classes=CostManagementApplication.class)
@AutoConfigureMockMvc
class SharedQueriesDatabaseTest {
    static PostgreSqlDatabase database;
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) throws Exception {
        database=new PostgreSqlDatabase();
        properties.add("spring.datasource.url",database.source::getUrl);
        properties.add("spring.datasource.username",database.source::getUsername);
        properties.add("spring.datasource.password",database.source::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate sql;
    @org.springframework.boot.test.mock.mockito.MockBean com.sicost.authentication.AuthenticationMailService mail;
    org.springframework.mock.web.MockHttpSession session;
    String actor;
    String group;
    @BeforeEach void fixture() throws Exception {
        // This full-filter test logs in for itself: discard the legacy extension's token,
        // including Spring Security Test's request context cache, before starting login.
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        actor="sq"+UUID.randomUUID().toString().replace("-","").substring(0,16);
        String password="Aa1!"+UUID.randomUUID();
        sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_chg_dttm,temp_pwd_yn,pwd_err_cnt,user_stat_cd,mfa_use_yn,mfa_err_cnt,reg_user_id) VALUES (?,?,'queries@example.test',?,CURRENT_TIMESTAMP,'N',0,'USE','Y',0,'SYSTEM')",actor,"조회 시험 사용자",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10).encode(password));
        sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,'ADMIN','SYSTEM')",actor);
        // Exercise the real password/MFA/session path; only the external mail transport is replaced in this query regression.
        var mfaCode=new java.util.concurrent.atomic.AtomicReference<String>();
        org.mockito.Mockito.when(mail.send(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(call->{var match=java.util.regex.Pattern.compile("[0-9]{6}").matcher((String)call.getArgument(2));assertThat(match.find()).isTrue();mfaCode.set(match.group());return "ACCEPTED";});
        var logged=mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("userId",actor,"password",password))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nextStep").value("MFA")).andReturn();
        session=(org.springframework.mock.web.MockHttpSession)logged.getRequest().getSession(false);
        mvc.perform(post("/api/auth/mfa/verify").session(session).with(csrf()).contentType("application/json").content(json.writeValueAsBytes(Map.of("code",mfaCode.getAndSet(null)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nextStep").value("AUTHENTICATED"));
        group="T"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase(Locale.ROOT);
        sql.update("INSERT INTO TB_SY_CODE_GRP(CODE_GRP_ID,CODE_GRP_NM,SORT_ORD,REG_USER_ID) VALUES (?,'조회 시험',1,'admin')",group);
        for(String code:List.of("A","STOP","DELETED")) sql.update("INSERT INTO TB_SY_CODE(CODE_GRP_ID,CODE,CODE_NM,SORT_ORD,USE_YN,DEL_YN,REG_USER_ID) VALUES (?,?,?,1,?,?,'admin')",group,code,code,code.equals("STOP")?"N":"Y",code.equals("DELETED")?"Y":"N");
    }
    @AfterEach void cleanup() {
        sql.update("DELETE FROM TB_SY_CHG_HIST WHERE TRGT_KEY=? OR split_part(TRGT_KEY,':',1)=?",group,group);
        sql.update("DELETE FROM TB_SY_CODE WHERE CODE_GRP_ID=?",group);
        sql.update("DELETE FROM TB_SY_CODE_GRP WHERE CODE_GRP_ID=?",group);
        if(session!=null)session.invalidate();
        sql.update("DELETE FROM tb_sy_accs_log WHERE user_id=?",actor);
        sql.update("DELETE FROM tb_sy_user_role WHERE user_id=?",actor);
        sql.update("DELETE FROM tb_sy_user WHERE user_id=?",actor);
    }
    com.fasterxml.jackson.databind.JsonNode data(String path) throws Exception {
        return json.readTree(mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("data");
    }
    @Test void initialMenusHaveTenGroupsThirtyScreensAndNoDiagnostics() throws Exception {
        var before=sql.queryForList("SELECT * FROM TB_SY_MENU ORDER BY MENU_ID");
        var current=data("/api/current-user");
        assertThat(current.get("userId").asText()).isEqualTo(actor);
        assertThat(current.get("developmentMode").asBoolean()).isFalse();
        assertThat(current.get("accessEnforcement").asBoolean()).isTrue();
        var menus=data("/api/menus"); assertThat(menus.get("diagnostics").size()).isZero(); assertThat(menus.get("items").size()).isEqualTo(10);
        int screens=0; for(var root:menus.get("items")) screens+=root.get("children").size(); assertThat(screens).isEqualTo(30);
        assertThat(sql.queryForList("SELECT * FROM TB_SY_MENU ORDER BY MENU_ID")).isEqualTo(before);
    }
    @Test void codeLookupSeparatesInactiveAndDeletedWithoutModification() throws Exception {
        var before=sql.queryForList("SELECT * FROM TB_SY_CODE WHERE CODE_GRP_ID=? ORDER BY CODE",group);
        assertThat(data("/api/codes/"+group).get("items").size()).isEqualTo(1);
        assertThat(data("/api/codes/"+group+"?includeInactive=true").get("items").size()).isEqualTo(2);
        assertThat(sql.queryForList("SELECT * FROM TB_SY_CODE WHERE CODE_GRP_ID=? ORDER BY CODE",group)).isEqualTo(before);
        var allowance=data("/api/codes/ALWNC");
        var rate=java.util.stream.StreamSupport.stream(allowance.get("items").spliterator(),false).filter(n -> n.get("code").asText().equals("INS_RATE")).findFirst().orElseThrow();
        assertThat(rate.get("codeVal").asText()).isEqualTo("9.4"); assertThat(rate.get("unitNm").asText()).isEqualTo("%"); assertThat(rate.size()).isEqualTo(7);
    }
    @Test void parameterAndContextUseInstalledValuesWithoutCloseWrites() throws Exception {
        long before=database.count("TB_CL_CLOSE");
        assertThat(data("/api/params/CASH_BASE_AMT?month=2026-01").get("value").asText()).isEqualTo("1150000000");
        assertThat(data("/api/period-context").get("cashBaseMonth").asText()).isEqualTo("2026-01");
        data("/api/month-close?from=2025-12&to=2026-11"); assertThat(database.count("TB_CL_CLOSE")).isEqualTo(before);
    }
    @Test
    void earlyCashBaseMonthHasTheSameSixDigitIdentityAcrossQueryPaths() throws Exception {
        var original=sql.queryForList("SELECT aply_bgn_ym,std_val FROM tb_sy_oper_std WHERE std_cd='CASH_BASE_YM'");
        try {
        sql.update("UPDATE TB_SY_OPER_STD SET STD_VAL=101 WHERE STD_CD='CASH_BASE_YM'");
        assertThat(data("/api/params/CASH_BASE_YM?month=2026-01").get("value").asText()).isEqualTo("000101");
        assertThat(data("/api/period-context").get("cashBaseMonth").asText()).isEqualTo("0001-01");
        var standards=data("/api/operating-standards?month=2026-01");
        var base=java.util.stream.StreamSupport.stream(standards.get("items").spliterator(),false)
            .filter(row -> row.get("key").asText().equals("CASH_BASE_YM")).findFirst().orElseThrow();
        assertThat(base.get("value").asText()).isEqualTo("000101");
        } finally {
            for(var row:original)sql.update("UPDATE tb_sy_oper_std SET std_val=? WHERE std_cd='CASH_BASE_YM' AND aply_bgn_ym=?",row.get("std_val"),row.get("aply_bgn_ym"));
        }
    }
    @Test void historyUsesDelimiterExactScopeStableTiesAndPreservesDeletedMappingJson() throws Exception {
        for(String table:List.of("TB_SY_CODE_GRP","TB_SY_CODE","TB_SY_CODE_MAP","TB_SY_OPER_STD")) {
            String key=table.equals("TB_SY_CODE_GRP")?group:group+":9";
            sql.update("INSERT INTO TB_SY_CHG_HIST(TRGT_TBL_NM,TRGT_KEY,CHG_TYPE_CD,CHG_ITEM_NM,BFR_VAL,REG_USER_ID,REG_DTTM) VALUES (?,?,'DEL','CODE_NM',?,'SYSTEM','2000-01-01')",table,key,table.equals("TB_SY_CODE_MAP")?"{\"groupId\":\""+group+"\",\"codeName\":\"과거 이름\"}":"긴".repeat(500));
        }
        var result=data("/api/common-code-history?groupId="+group);
        assertThat(result.get("total").asInt()).isEqualTo(3);
        var ids=new ArrayList<Long>(); for(var row:result.get("items")) { ids.add(row.get("histId").asLong()); assertThat(row.get("target").asText()).isNotEqualTo("OPER_STD"); }
        assertThat(ids).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(result.get("items").get(0).get("before").asText()).contains("과거 이름");
        assertThat(data("/api/common-code-history?groupId="+group+"X").get("total").asInt()).isZero();
        assertThat(data("/api/common-code-history?groupId="+group).get("total").asInt()).isEqualTo(3);
    }
}
