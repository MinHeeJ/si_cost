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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    String group;
    @BeforeEach void fixture() {
        group="T"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        sql.update("INSERT INTO TB_SY_CODE_GRP(CODE_GRP_ID,CODE_GRP_NM,SORT_ORD,REG_USER_ID) VALUES (?,'조회 시험',1,'admin')",group);
        for(String code:List.of("A","STOP","DELETED")) sql.update("INSERT INTO TB_SY_CODE(CODE_GRP_ID,CODE,CODE_NM,SORT_ORD,USE_YN,DEL_YN,REG_USER_ID) VALUES (?,?,?,1,?,?,'admin')",group,code,code,code.equals("STOP")?"N":"Y",code.equals("DELETED")?"Y":"N");
    }
    @AfterEach void cleanup() {
        sql.update("DELETE FROM TB_SY_CHG_HIST WHERE TRGT_KEY=? OR split_part(TRGT_KEY,':',1)=?",group,group);
        sql.update("DELETE FROM TB_SY_CODE WHERE CODE_GRP_ID=?",group);
        sql.update("DELETE FROM TB_SY_CODE_GRP WHERE CODE_GRP_ID=?",group);
    }
    com.fasterxml.jackson.databind.JsonNode data(String path) throws Exception {
        return json.readTree(mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }
    @Test void initialMenusHaveTenGroupsThirtyScreensAndNoDiagnostics() throws Exception {
        var before=sql.queryForList("SELECT * FROM TB_SY_MENU ORDER BY MENU_ID");
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
    @Test @org.springframework.transaction.annotation.Transactional
    void earlyCashBaseMonthHasTheSameSixDigitIdentityAcrossQueryPaths() throws Exception {
        sql.update("UPDATE TB_SY_OPER_STD SET STD_VAL=101 WHERE STD_CD='CASH_BASE_YM'");
        assertThat(data("/api/params/CASH_BASE_YM?month=2026-01").get("value").asText()).isEqualTo("000101");
        assertThat(data("/api/period-context").get("cashBaseMonth").asText()).isEqualTo("0001-01");
        var standards=data("/api/operating-standards?month=2026-01");
        var base=java.util.stream.StreamSupport.stream(standards.get("items").spliterator(),false)
            .filter(row -> row.get("key").asText().equals("CASH_BASE_YM")).findFirst().orElseThrow();
        assertThat(base.get("value").asText()).isEqualTo("000101");
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
