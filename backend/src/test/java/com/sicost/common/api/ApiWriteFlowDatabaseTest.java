package com.sicost.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.CostManagementApplication;
import com.sicost.common.installation.PostgreSqlDatabase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
@SpringBootTest(classes=CostManagementApplication.class)
@AutoConfigureMockMvc
@Transactional
class ApiWriteFlowDatabaseTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) throws Exception {
        var database=new PostgreSqlDatabase();
        properties.add("spring.datasource.url",database.source::getUrl);
        properties.add("spring.datasource.username",database.source::getUsername);
        properties.add("spring.datasource.password",database.source::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate sql;

    @BeforeEach void durableContractAndGroup() throws Exception {
        assertThat(new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8))
            .contains("operationId: saveGroupCodes", "operationId: saveOperatingStandards", "operationId: createCodeMapping");
        mvc.perform(post("/api/code-groups").contentType("application/json")
            .content("{\"groupId\":\"CONTRACT_EDU\",\"name\":\"계약 검증 교육\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true));
    }
    JsonNode data(org.springframework.test.web.servlet.ResultActions request) throws Exception {
        return json.readTree(request.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }
    String revision() throws Exception {
        return data(mvc.perform(get("/api/code-groups/CONTRACT_EDU/codes"))).get("groupRevision").asText();
    }
    String changes(String revision,String changes) throws Exception {
        var body=json.createObjectNode().put("expectedRevision",revision);
        body.set("changes",json.readTree(changes));return body.toString();
    }
    void addCode() throws Exception {
        mvc.perform(put("/api/code-groups/CONTRACT_EDU/codes").contentType("application/json")
            .content(changes(revision(),"[{\"code\":\"A\",\"isNew\":true,\"name\":\"시험 교육\"}]")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
    }
    @Test void authHappyGroupAndCodeSideEffectsPersistUntilTestRollbackWithNoOpAndLogicalDelete() throws Exception {
        assertThat(sql.queryForMap("SELECT sys_code_yn,val_code_yn,use_yn,reg_user_id FROM tb_sy_code_grp WHERE code_grp_id='CONTRACT_EDU'"))
            .containsEntry("sys_code_yn","N").containsEntry("val_code_yn","N").containsEntry("use_yn","Y").containsEntry("reg_user_id","admin");
        mvc.perform(get("/api/code-groups")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isArray());
        addCode();String revision=revision();
        var before=sql.queryForMap("SELECT * FROM tb_sy_code WHERE code_grp_id='CONTRACT_EDU' AND code='A'");
        long histories=sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist",Long.class);
        mvc.perform(put("/api/code-groups/CONTRACT_EDU/codes").contentType("application/json")
            .content(changes(revision,"[{\"code\":\"A\",\"isNew\":false,\"name\":\"시험 교육\"}]")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));
        assertThat(sql.queryForMap("SELECT * FROM tb_sy_code WHERE code_grp_id='CONTRACT_EDU' AND code='A'")).isEqualTo(before);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist",Long.class)).isEqualTo(histories);
        mvc.perform(delete("/api/code-groups/CONTRACT_EDU/codes/A").param("expectedRevision",revision))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(sql.queryForMap("SELECT del_yn,mod_user_id FROM tb_sy_code WHERE code_grp_id='CONTRACT_EDU' AND code='A'"))
            .containsEntry("del_yn","Y").containsEntry("mod_user_id","admin");
        assertThat(sql.queryForMap("SELECT chg_item_nm,bfr_val,aft_val FROM tb_sy_chg_hist WHERE trgt_key='CONTRACT_EDU:A' AND chg_type_cd='DEL'"))
            .containsEntry("chg_item_nm","DEL_YN").containsEntry("bfr_val","N").containsEntry("aft_val","Y");
        mvc.perform(get("/api/codes/CONTRACT_EDU")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }
    @Test void authHappyMappingsAreReadBackByGetCodeMappingsAndGetCommonCodeHistoryThenDeletedAtomically() throws Exception {
        addCode();
        mvc.perform(post("/api/code-mappings").contentType("application/json")
            .content("{\"sourceText\":\"  계약 교육  \",\"groupId\":\"CONTRACT_EDU\",\"code\":\"A\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true));
        JsonNode mapping=data(mvc.perform(get("/api/code-mappings").param("groupId","CONTRACT_EDU"))).get("items").get(0);
        assertThat(mapping.get("sourceText").asText()).isEqualTo("계약 교육");
        assertThat(sql.queryForObject("SELECT reg_user_id FROM tb_sy_code_map WHERE map_id=?",String.class,mapping.get("mapId").asLong())).isEqualTo("admin");
        JsonNode history=data(mvc.perform(get("/api/common-code-history").param("groupId","CONTRACT_EDU").param("targetType","CODE_MAP")));
        assertThat(json.readTree(history.get("items").get(0).get("after").asText()).get("codeName").asText()).isEqualTo("시험 교육");
        String body=json.writeValueAsString(java.util.Map.of("items",List.of(java.util.Map.of(
            "mapId",mapping.get("mapId").asLong(),"expectedRevision",mapping.get("revision").asText()))));
        mvc.perform(post("/api/code-mappings/delete").contentType("application/json").content(body))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/code-mappings").param("groupId","CONTRACT_EDU"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_code_map WHERE code_grp_id='CONTRACT_EDU'",Long.class)).isZero();
        JsonNode deleted=data(mvc.perform(get("/api/common-code-history").param("groupId","CONTRACT_EDU").param("targetType","CODE_MAP"))).get("items").get(0);
        assertThat(deleted.get("type").asText()).isEqualTo("DEL");
        assertThat(deleted.get("entityKey").asText()).isEqualTo("CONTRACT_EDU:"+mapping.get("mapId").asLong());
        assertThat(json.readTree(deleted.get("before").asText()).get("sourceText").asText()).isEqualTo("계약 교육");
    }
    @Test void authHappyCashPreservesAplyBgnYmAndRefreshesGetOperatingStandardsGetParameterWithoutBusinessWrites() throws Exception {
        var before=sql.queryForMap("SELECT * FROM tb_sy_oper_std WHERE std_cd='CASH_BASE_AMT'");
        var closes=sql.queryForList("SELECT * FROM tb_cl_close ORDER BY close_ym");
        var projects=sql.queryForList("SELECT * FROM tb_pj_proj ORDER BY proj_id");
        JsonNode versions=data(mvc.perform(get("/api/operating-standards/CASH_BASE_AMT/history")));
        var body=json.createObjectNode();
        body.putArray("changes").addObject().put("key","CASH_BASE_AMT").put("value","1234")
            .put("expectedRevision",versions.get("keyRevision").asText());
        mvc.perform(put("/api/operating-standards").contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        var after=sql.queryForMap("SELECT * FROM tb_sy_oper_std WHERE std_cd='CASH_BASE_AMT'");
        assertThat(after.get("aply_bgn_ym")).isEqualTo(before.get("aply_bgn_ym"));
        assertThat(after.get("mod_user_id")).isEqualTo("admin");
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_oper_std WHERE std_cd='CASH_BASE_AMT'",Long.class)).isEqualTo(1);
        mvc.perform(get("/api/params/CASH_BASE_AMT").param("month","2026-01"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("1234"));
        JsonNode standards=data(mvc.perform(get("/api/operating-standards").param("month","2026-01")));
        assertThat(standards.get("items")).anyMatch(n->n.get("key").asText().equals("CASH_BASE_AMT") && n.get("value").asText().equals("1234"));
        assertThat(sql.queryForList("SELECT * FROM tb_cl_close ORDER BY close_ym")).isEqualTo(closes);
        assertThat(sql.queryForList("SELECT * FROM tb_pj_proj ORDER BY proj_id")).isEqualTo(projects);
        assertThat(sql.queryForMap("SELECT bfr_val,aft_val,chg_type_cd FROM tb_sy_chg_hist WHERE trgt_key='CASH_BASE_AMT:202601'"))
            .containsEntry("bfr_val","1150000000").containsEntry("aft_val","1234").containsEntry("chg_type_cd","UPD");
    }
}
