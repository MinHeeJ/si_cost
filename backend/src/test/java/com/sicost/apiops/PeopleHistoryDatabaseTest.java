package com.sicost.apiops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.context.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static com.sicost.apiops.PeopleHistoryDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class PeopleHistoryDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    PeopleHistoryService service;
    MockMvc mvc;
    final ObjectMapper json=new ObjectMapper();
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();
        configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        context.registerBean(PeopleHistoryMapper.class,()->session.getMapper(PeopleHistoryMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,PeopleHistoryRepository.class,PeopleHistoryService.class);
        context.refresh();service=context.getBean(PeopleHistoryService.class);
        new ResourceDatabasePopulator(new ClassPathResource("apiops/people-history.sql")).execute(db.source);
        mvc=MockMvcBuilders.standaloneSetup(new PeopleHistoryController(service))
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
    }
    @AfterEach void close() {if(context!=null)context.close();}
    Page read(String type,String id,int page,int size) {return service.list(new Query(type,id,page,size,(long)(page-1)*size));}
    void history(String table,String key,String item,String time) {
        sql.update("INSERT INTO tb_sy_chg_hist(trgt_tbl_nm,trgt_key,chg_type_cd,chg_item_nm,aft_val,reg_user_id,reg_dttm) VALUES (?,?,'UPD',?,'검증값','admin',?::timestamp)",table,key,item,time);
    }
    Map<String,Object> snapshot() {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String table:List.of("tb_hr_dev","tb_hr_dev_prc","tb_hr_dev_stat","tb_hr_dev_skill","tb_hr_cand","tb_hr_cand_skill","tb_hr_cand_cntc","tb_sy_chg_hist","tb_sy_accs_log","tb_pj_asgn","tb_cl_close"))
            result.put(table,sql.queryForList("SELECT * FROM "+table+" ORDER BY 1,2"));
        result.put("historySequence",sql.queryForList("SELECT last_value,is_called FROM sq_tb_sy_chg_hist"));
        return result;
    }
    @ParameterizedTest @CsvSource({"DEVELOPER,DEV-000901,4", "CANDIDATE,CAND-000901,5"})
    void requirement436ReturnsParentAndChildrenInDatabaseOrderWithoutWrites(String type,String id,int total) throws Exception {
        var before=snapshot();
        var result=mvc.perform(get("/api/people-history").param("targetType",type).param("targetId",id))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.total").value(total)).andReturn();
        var body=json.readTree(result.getResponse().getContentAsString());
        String base=type.equals("DEVELOPER")?"TB_HR_DEV":"TB_HR_CAND";
        var expected=sql.queryForList("SELECT hist_id FROM tb_sy_chg_hist WHERE trgt_tbl_nm IN (?,?,?,?) AND (trgt_key=? OR trgt_key LIKE ?) ORDER BY reg_dttm DESC,hist_id DESC",Long.class,
            base,base+"_SKILL",type.equals("DEVELOPER")?base+"_PRC":base+"_CNTC",base+"_STAT",id,id+":%");
        List<Long> actual=new ArrayList<>();body.path("data").path("items").forEach(row->actual.add(row.path("historyId").asLong()));
        assertThat(actual).containsExactlyElementsOf(expected);
        assertThat(body.path("data").path("items").get(0).path("actorId").asText()).isEqualTo("admin");
        assertThat(result.getResponse().getHeader("X-Request-Id")).isNotBlank();
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void onlyExactParentAndWellFormedChildKeysAreIncluded() {
        for(String key:List.of("DEV-0009010:202601","DEV-000901:202600","DEV-000901:000001","DEV-000901:202613","DEV-000901:202601:extra"))
            history("TB_HR_DEV_PRC",key,"가격","2026-01-04 00:00:00");
        for(String key:List.of("DEV-0009010:1","DEV-000901:0","DEV-000901:-1","DEV-000901:100000","DEV-000901:1:extra"))
            history("TB_HR_DEV_STAT",key,"상태","2026-01-04 00:00:00");
        history("TB_HR_CAND","DEV-000901","다른 테이블","2026-01-04 00:00:00");
        history("TB_HR_DEV_SKILL","DEV-000901:Java","잘못된 스킬 키","2026-01-04 00:00:00");
        assertThat(read("DEVELOPER","DEV-000901",1,20).total()).isEqualTo(4);
        assertThat(read("DEVELOPER","DEV-000901",1,20).items()).hasSize(4);
        for(String key:List.of("CAND-0009010:1","CAND-000901:0","CAND-000901:100000","CAND-000901:1:extra"))
            history("TB_HR_CAND_CNTC",key,"연락","2026-01-04 00:00:00");
        assertThat(read("CANDIDATE","CAND-000901",1,20).total()).isEqualTo(5);
    }
    @Test void cancelledChildHistorySurvivesDeletionAndSystemActorNeedsNoUserJoin() {
        sql.update("DELETE FROM tb_hr_dev_stat WHERE dev_id='DEV-000901' AND stat_sn=1");
        sql.update("UPDATE tb_sy_chg_hist SET reg_user_id='SYSTEM' WHERE trgt_tbl_nm='TB_HR_DEV_STAT'");
        var row=read("DEVELOPER","DEV-000901",1,20).items().stream().filter(r->r.targetTable().equals("TB_HR_DEV_STAT")).findFirst().orElseThrow();
        assertThat(row.actorName()).isEqualTo("SYSTEM");assertThat(row.targetKey()).isEqualTo("DEV-000901:1");
    }
    @Test void countAndPagesUseIdenticalPersonScope() {
        for(int i=0;i<21;i++)history("TB_HR_DEV","DEV-000902","이름","2026-01-01 00:00:00");
        var first=read("DEVELOPER","DEV-000902",1,20);var second=read("DEVELOPER","DEV-000902",2,20);
        assertThat(first.total()).isEqualTo(21);assertThat(first.items()).hasSize(20);
        assertThat(second.total()).isEqualTo(21);assertThat(second.items()).hasSize(1);
        assertThat(first.items()).doesNotContainAnyElementsOf(second.items());
        assertThat(read("DEVELOPER","DEV-000902",3,20).items()).isEmpty();
    }
    @Test void emptyExistingPersonAndMissingPersonHaveDistinctResults() throws Exception {
        mvc.perform(get("/api/people-history").param("targetType","DEVELOPER").param("targetId","DEV-000902"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        history("TB_HR_DEV","absent","등록","2026-01-01 00:00:00");
        var before=snapshot();
        mvc.perform(get("/api/people-history").param("targetType","DEVELOPER").param("targetId","absent"))
            .andExpect(status().isNotFound());
        assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @CsvSource({"actor,other,UNKNOWN_FIELD", "sort,name,UNKNOWN_FIELD", "page,0,VALIDATION_ERROR", "targetType,USER,VALIDATION_ERROR", "size,30,VALIDATION_ERROR"})
    void rejectedQueriesLeaveAllBusinessAuditAndSequenceStateUnchanged(String field,String value,String code) throws Exception {
        var before=snapshot();
        var query=new org.springframework.util.LinkedMultiValueMap<String,String>();
        query.add("targetType","DEVELOPER");query.add("targetId","DEV-000901");query.set(field,value);
        mvc.perform(get("/api/people-history").params(query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void legacyUnmaskedContactNeverLeavesPublicEndpointButStoredHistoryIsPreserved() throws Exception {
        sql.update("UPDATE tb_sy_chg_hist SET bfr_val='private-phone',aft_val='private-email',chg_rsn='private-reason' WHERE chg_item_nm IN ('TELNO','EMAIL_ADDR')");
        var before=snapshot();
        var result=mvc.perform(get("/api/people-history").param("targetType","CANDIDATE").param("targetId","CAND-000901"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].afterValue").value("[비공개]")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private-");
        assertThat(snapshot()).isEqualTo(before);
    }
}
