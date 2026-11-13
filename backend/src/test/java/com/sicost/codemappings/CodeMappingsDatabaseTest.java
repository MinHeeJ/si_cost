package com.sicost.codemappings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.CostManagementApplication;
import com.sicost.common.history.ChangeHistoryEntry;
import com.sicost.common.history.ChangeHistoryMapper;
import com.sicost.common.installation.PostgreSqlDatabase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
@SpringBootTest(classes=CostManagementApplication.class)
@AutoConfigureMockMvc
class CodeMappingsDatabaseTest {
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
    @SpyBean ChangeHistoryMapper history;
    String group;
    @BeforeEach void fixture() {
        group="T"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase(java.util.Locale.ROOT);
        sql.update("INSERT INTO TB_SY_CODE_GRP (CODE_GRP_ID,CODE_GRP_NM,SORT_ORD,REG_USER_ID) VALUES (?, '독립 시험',1,'admin')",group);
        for(String code:List.of("A","B","STOP","DELETED"))
            sql.update("INSERT INTO TB_SY_CODE (CODE_GRP_ID,CODE,CODE_NM,SORT_ORD,USE_YN,DEL_YN,REG_USER_ID) VALUES (?,?,?,1,?,?,'admin')",
                    group,code,"이름 "+code,code.equals("STOP")?"N":"Y",code.equals("DELETED")?"Y":"N");
    }
    @AfterEach void cleanup() {
        sql.update("DELETE FROM TB_SY_CHG_HIST WHERE TRGT_TBL_NM='TB_SY_CODE_MAP' AND TRGT_KEY LIKE ?",group+"%:%");
        sql.update("DELETE FROM TB_SY_CODE_MAP WHERE CODE_GRP_ID LIKE ?",group+"%");
        sql.update("DELETE FROM TB_SY_CODE WHERE CODE_GRP_ID LIKE ?",group+"%");
        sql.update("DELETE FROM TB_SY_CODE_GRP WHERE CODE_GRP_ID LIKE ?",group+"%");
    }
    private void create(String source,String groupId,String code,int expected) throws Exception {
        mvc.perform(post("/api/code-mappings").contentType("application/json").content(json.writeValueAsString(java.util.Map.of("sourceText",source,"groupId",groupId,"code",code))))
                .andExpect(status().is(expected));
    }
    private com.fasterxml.jackson.databind.JsonNode page(String source,int page,int size) throws Exception {
        var response=mvc.perform(get("/api/code-mappings").param("groupId",group).param("sourceText",source)
                .param("page",String.valueOf(page)).param("size",String.valueOf(size)))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("data");
    }
    private long historyCount(String type) {
        return sql.queryForObject("SELECT count(*) FROM TB_SY_CHG_HIST WHERE TRGT_TBL_NM='TB_SY_CODE_MAP' AND TRGT_KEY LIKE ? AND CHG_TYPE_CD=?",Long.class,group+":%",type);
    }
    private Object businessSnapshot() {
        return sql.queryForList("SELECT PROJ_ID,PROJ_NM,REG_DTTM,MOD_DTTM FROM TB_PJ_PROJ ORDER BY PROJ_ID");
    }
    @Test void immediateCreateThenRealApiReadKeepsBusinessRowsAndJson() throws Exception {
        var business=businessSnapshot();
        create("  신규 표기  ",group,"A",201);
        var result=page("신규",1,50);
        assertThat(result.get("total").asLong()).isEqualTo(1);
        var item=result.get("items").get(0);
        assertThat(item.get("sourceText").asText()).isEqualTo("신규 표기");
        assertThat(item.get("revision").asText()).isNotEmpty();
        var audit=sql.queryForMap("SELECT BFR_VAL,AFT_VAL,REG_USER_ID,TRGT_KEY FROM TB_SY_CHG_HIST WHERE TRGT_KEY=? AND CHG_TYPE_CD='NEW'",group+":"+item.get("mapId").asLong());
        assertThat(audit.get("bfr_val")).isNull(); assertThat(audit.get("reg_user_id")).isEqualTo("admin");
        var after=json.readTree((String)audit.get("aft_val"));
        assertThat(after.size()).isEqualTo(4); assertThat(after.get("codeName").asText()).isEqualTo("이름 A");
        assertThat(businessSnapshot()).isEqualTo(business);
    }
    @Test void filteredPageAndCountAgreeAndLiteralWildcardIsNotExpanded() throws Exception {
        for(int i=24;i>=0;i--) create(String.format("표기%02d",i),group,"A",201);
        var filtered=page("표기",2,20);
        assertThat(filtered.get("total").asInt()).isEqualTo(25);
        assertThat(filtered.get("items").size()).isEqualTo(5);
        assertThat(filtered.get("items").get(0).get("sourceText").asText()).isEqualTo("표기20");
        assertThat(page("%",1,50).get("total").asInt()).isZero();
    }
    @Test void emptyFilterDoesNotWrite() throws Exception {
        long before=historyCount("NEW");
        var empty=page("없는 표기",1,50);
        assertThat(empty.get("items").size()).isZero(); assertThat(empty.get("total").asLong()).isZero();
        assertThat(historyCount("NEW")).isEqualTo(before);
    }
    @Test void duplicatePairRejectsAnotherCodeButAnotherGroupSucceeds() throws Exception {
        create("표기",group,"A",201); create("표기",group,"B",409);
        String other=group+"X";
        sql.update("INSERT INTO TB_SY_CODE_GRP (CODE_GRP_ID,CODE_GRP_NM,SORT_ORD,REG_USER_ID) VALUES (?,'다른 그룹',1,'admin')",other);
        sql.update("INSERT INTO TB_SY_CODE (CODE_GRP_ID,CODE,CODE_NM,SORT_ORD,REG_USER_ID) VALUES (?,'A','다른 이름',1,'admin')",other);
        create("표기",other,"A",201);
        assertThat(page("표기",1,50).get("total").asInt()).isEqualTo(1);
        assertThat(historyCount("NEW")).isEqualTo(1);
    }
    @Test void stoppedCodeRejectedWithoutHistory() throws Exception { create("표기",group,"STOP",400); assertThat(historyCount("NEW")).isZero(); }
    @Test void deletedCodeRejectedWithoutHistory() throws Exception { create("표기",group,"DELETED",400); assertThat(historyCount("NEW")).isZero(); }
    @Test void nonexistentCodeRejectedWithoutHistory() throws Exception { create("표기",group,"MISSING",400); assertThat(historyCount("NEW")).isZero(); }
    @Test void nonexistentGroupRejectedWithoutHistory() throws Exception { create("표기",group+"X","A",400); assertThat(historyCount("NEW")).isZero(); }
    private String selection(com.fasterxml.jackson.databind.JsonNode rows) {
        var array=json.createArrayNode();
        rows.forEach(r -> array.addObject().put("mapId",r.get("mapId").asLong()).put("expectedRevision",r.get("revision").asText()));
        return json.createObjectNode().set("items",array).toString();
    }
    @Test void physicalDeleteKeepsLosslessBeforeJsonAndGroupPrefix() throws Exception {
        var business=businessSnapshot();
        create("가",group,"A",201); create("나",group,"B",201);
        var rows=page("",1,50).get("items");
        mvc.perform(post("/api/code-mappings/delete").contentType("application/json").content(selection(rows))).andExpect(status().isNoContent());
        assertThat(page("",1,50).get("total").asLong()).isZero(); assertThat(historyCount("DEL")).isEqualTo(2);
        var audits=sql.queryForList("SELECT TRGT_KEY,BFR_VAL,AFT_VAL,REG_USER_ID FROM TB_SY_CHG_HIST WHERE TRGT_KEY LIKE ? AND CHG_TYPE_CD='DEL'",group+":%");
        for(var audit:audits) {
            assertThat(audit.get("aft_val")).isNull(); assertThat(audit.get("reg_user_id")).isEqualTo("admin");
            assertThat(json.readTree((String)audit.get("bfr_val")).get("groupId").asText()).isEqualTo(group);
        }
        assertThat(businessSnapshot()).isEqualTo(business);
    }
    @Test void revisionConflictRollsBackWholeSelectionAndHistory() throws Exception {
        create("가",group,"A",201); create("나",group,"B",201);
        var rows=page("",1,50).get("items");
        sql.update("UPDATE TB_SY_CODE_MAP SET SRC_TEXT='변경' WHERE MAP_ID=?",rows.get(1).get("mapId").asLong());
        mvc.perform(post("/api/code-mappings/delete").contentType("application/json").content(selection(rows)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        assertThat(page("",1,50).get("total").asInt()).isEqualTo(2); assertThat(historyCount("DEL")).isZero();
    }
    @Test void missingSelectionDoesNotDeleteRemainingRow() throws Exception {
        create("가",group,"A",201); create("나",group,"B",201);
        var rows=page("",1,50).get("items");
        sql.update("DELETE FROM TB_SY_CODE_MAP WHERE MAP_ID=?",rows.get(1).get("mapId").asLong());
        mvc.perform(post("/api/code-mappings/delete").contentType("application/json").content(selection(rows))).andExpect(status().isConflict());
        assertThat(page("",1,50).get("total").asInt()).isEqualTo(1); assertThat(historyCount("DEL")).isZero();
    }
    @Test void historyFailureRollsBackInsertedMapping() throws Exception {
        doThrow(new IllegalStateException("intentional history failure")).when(history).insert(any(ChangeHistoryEntry.class));
        try {
            create("롤백",group,"A",500);
            assertThat(sql.queryForObject("SELECT count(*) FROM TB_SY_CODE_MAP WHERE CODE_GRP_ID=?",Long.class,group)).isZero();
            assertThat(historyCount("NEW")).isZero();
        } finally { reset(history); }
    }
}
