package com.sicost.developersops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.concurrent.DeveloperAggregateRevision;
import com.sicost.common.concurrent.DeveloperRevisionMapper;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static com.sicost.developersops.DeveloperActionsDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class DeveloperActionsDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql; MockMvc mvc; ObjectMapper json;
    DeveloperActionsMapper mapper; DeveloperActionsRepository repository; DeveloperStatusRecalculationService states;
    @Configuration @EnableTransactionManagement static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        new ResourceDatabasePopulator(new ClassPathResource("developers/developer-assignments.sql"),new ClassPathResource("developers/developer-use-status.sql")).execute(db.source);
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());
        factory.setConfiguration(configuration);factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T16:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(DeveloperActionsMapper.class,()->session.getMapper(DeveloperActionsMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.registerBean(DeveloperRevisionMapper.class,()->session.getMapper(DeveloperRevisionMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,DeveloperAggregateRevision.class,DeveloperActionsRepository.class,
            DeveloperActionsQueryService.class,DeveloperUseStatusService.class,DeveloperBulkStatusService.class,DeveloperStatusRecalculationService.class,DeveloperActionsController.class);
        context.refresh();mapper=context.getBean(DeveloperActionsMapper.class);repository=context.getBean(DeveloperActionsRepository.class);states=context.getBean(DeveloperStatusRecalculationService.class);
        mvc=MockMvcBuilders.standaloneSetup(context.getBean(DeveloperActionsController.class)).setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).addFilters(new RequestIdFilter()).build();
    }
    @AfterEach void close() { if(context!=null) context.close(); }
    SelectionItem target(String id) { return new SelectionItem(id,repository.revision(repository.required(id,false),false)); }
    BulkResult save(String use,SelectionItem... targets) throws Exception {
        var response=mvc.perform(post("/api/developers/bulk-status").contentType("application/json").content(json.writeValueAsString(new BulkRequest(use,List.of(targets)))))
            .andExpect(status().isOk()).andReturn();
        return json.treeToValue(json.readTree(response.getResponse().getContentAsString()).get("data"),BulkResult.class);
    }
    List<Map<String,Object>> rows(String table) { return sql.queryForList("SELECT * FROM "+table+" ORDER BY 1,2"); }
    Map<String,List<Map<String,Object>>> snapshot() {
        Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();
        for(String table:List.of("tb_hr_dev","tb_hr_dev_skill","tb_hr_dev_prc","tb_hr_dev_stat","tb_pj_asgn","tb_pj_proj","tb_pj_asgn_mon","tb_cl_close","tb_sy_chg_hist")) result.put(table,rows(table));
        return result;
    }
    @Test void requirement467ActualHistoryPagesAreOrderedIncludeCancellationAndPreserveDatabase() throws Exception {
        var before=snapshot(); List<String> actual=new ArrayList<>();
        for(int page=1;page<=3;page++) {
            var response=mvc.perform(get("/api/developers/DEV-000901/assignments").param("page",Integer.toString(page)).param("size","20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(55)).andReturn();
            var items=json.readTree(response.getResponse().getContentAsString()).get("data").get("items");
            items.forEach(item->actual.add(item.get("id").asText()));
        }
        assertThat(actual).containsExactlyElementsOf(sql.queryForList("SELECT asgn_id FROM tb_pj_asgn WHERE dev_id='DEV-000901' ORDER BY input_bgn_dt DESC,asgn_id ASC",String.class));
        assertThat(mapper.assignments(new AssignmentQuery("DEV-000901",1,100,0))).extracting(Assignment::assignmentStatus).contains("CNCL","ACT");
        assertThat(mapper.assignments(new AssignmentQuery("DEV-000901",1,100,0))).extracting(Assignment::projectId).contains("P-901","P-902");
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void invalidReadQueriesPreserveAllBusinessAndHistoryRows() throws Exception {
        var before=snapshot();
        mvc.perform(get("/api/developers/selection").param("actor","other")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/developers/DEV-000901/assignments").param("size","30")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/developers/DEV-999999/assignments")).andExpect(status().isNotFound());
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void requirement470SelectionMatchesDatabaseFilterAndIncludesAllRowsWithoutPageLimit() throws Exception {
        states.refresh();
        var response=mvc.perform(get("/api/developers/selection").param("q","中不存在").param("includeStopped","true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andReturn();
        assertThat(response.getResponse().getHeader("X-Request-Id")).isNotBlank();
        var selected=context.getBean(DeveloperActionsQueryService.class).selection(new Filters("FT",null,null,null,"中不存在",true,null,"ASC",LocalDate.of(2026,6,16)));
        assertThat(selected.items()).isEmpty();
        var all=context.getBean(DeveloperActionsQueryService.class).selection(new Filters("FT",null,"DRCT",null,"中",true,null,"ASC",LocalDate.of(2026,6,16)));
        assertThat(all.items()).hasSize((int)sql.queryForObject("SELECT count(*) FROM tb_hr_dev WHERE emp_type_cd='FT' AND drct_cls_cd='DRCT' AND strpos(dev_nm,'中')>0",Long.class).longValue());
        var full=context.getBean(DeveloperActionsQueryService.class).selection(new Filters("FT",null,"DRCT",null,"중지검증",true,null,"ASC",LocalDate.of(2026,6,16)));
        assertThat(full.items()).hasSize(7);
        for(var item:full.items()) assertThat(item.revision()).isEqualTo(target(item.id()).revision());
    }
    @Test void requirement472FutureAndTodayEndBlockWhileCancelledAndPastAssignmentsDoNot() throws Exception {
        var future=sql.queryForMap("SELECT * FROM tb_hr_dev WHERE dev_id='DEV-000904'");
        var today=sql.queryForMap("SELECT * FROM tb_hr_dev WHERE dev_id='DEV-000905'");
        var result=save("N",target("DEV-000904"),target("DEV-000905"),target("DEV-000907"),target("DEV-000908"));
        assertThat(result.appliedCount()).isEqualTo(2);assertThat(result.rejected()).extracting(Rejected::code).containsExactly("DEVELOPER_IN_USE","DEVELOPER_IN_USE");
        assertThat(sql.queryForMap("SELECT * FROM tb_hr_dev WHERE dev_id='DEV-000904'")).isEqualTo(future);
        assertThat(sql.queryForMap("SELECT * FROM tb_hr_dev WHERE dev_id='DEV-000905'")).isEqualTo(today);
    }
    @Test void requirement473ManualStopResumesButActualRetireDoesNot() throws Exception {
        var before=sql.queryForMap("SELECT * FROM tb_hr_dev WHERE dev_id='DEV-000906'");
        var result=save("Y",target("DEV-000903"),target("DEV-000906"));
        assertThat(result.appliedCount()).isEqualTo(1);assertThat(result.rejected().get(0).code()).isEqualTo("RETIRED_DEVELOPER");
        assertThat(sql.queryForMap("SELECT * FROM tb_hr_dev WHERE dev_id='DEV-000906'")).isEqualTo(before);
        assertThat(sql.queryForObject("SELECT chg_type_cd FROM tb_sy_chg_hist WHERE trgt_key='DEV-000903'",String.class)).isEqualTo("RESUME");
    }
    @Test void requirement474MixedItemsCommitIndependentlyOnlyUseAndModificationAuditChange() throws Exception {
        var unchanged=sql.queryForMap("SELECT * FROM tb_hr_dev WHERE dev_id='DEV-000903'");
        var source=rows("tb_pj_asgn");var before=sql.queryForMap("SELECT * FROM tb_hr_dev WHERE dev_id='DEV-000902'");
        var result=save("N",target("DEV-000902"),target("DEV-000903"),new SelectionItem("DEV-000908","stale"),target("DEV-000904"));
        assertThat(result.appliedCount()).isEqualTo(1);assertThat(result.noChangeCount()).isEqualTo(1);assertThat(result.rejectedCount()).isEqualTo(2);
        assertThat(result.appliedCount()+result.noChangeCount()+result.rejectedCount()).isEqualTo(4);
        var after=sql.queryForMap("SELECT * FROM tb_hr_dev WHERE dev_id='DEV-000902'");
        assertThat(after.get("use_yn")).isEqualTo("N");assertThat(after.get("mod_user_id")).isEqualTo("admin");
        for(String key:List.of("use_yn","mod_user_id","mod_dttm")) { before.remove(key);after.remove(key); }
        assertThat(after).isEqualTo(before);assertThat(rows("tb_pj_asgn")).isEqualTo(source);
        assertThat(sql.queryForMap("SELECT * FROM tb_hr_dev WHERE dev_id='DEV-000903'")).isEqualTo(unchanged);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist",Long.class)).isEqualTo(1);
    }
    @Test void historyFailureRollsBackOnlyThatItemAndOtherItemsStillCommit() throws Exception {
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_one_developer CHECK(trgt_key <> 'DEV-000902') NOT VALID");
        var before=sql.queryForMap("SELECT * FROM tb_hr_dev WHERE dev_id='DEV-000902'");
        var result=save("N",target("DEV-000902"),target("DEV-000907"));
        assertThat(result.appliedCount()).isEqualTo(1);assertThat(result.rejectedCount()).isEqualTo(1);
        assertThat(sql.queryForMap("SELECT * FROM tb_hr_dev WHERE dev_id='DEV-000902'")).isEqualTo(before);
        assertThat(sql.queryForObject("SELECT use_yn FROM tb_hr_dev WHERE dev_id='DEV-000907'",String.class)).isEqualTo("N");
    }
    @Test void recalculationUsesInclusiveDatesRetiresNextDayAndDoesNotResumeManualStops() {
        states.refresh();var before=snapshot();states.refresh();assertThat(snapshot()).isEqualTo(before);
        assertThat(sql.queryForObject("SELECT work_stat_cd FROM tb_hr_dev WHERE dev_id='DEV-000905'",String.class)).isEqualTo("WTHDR");
        assertThat(sql.queryForObject("SELECT work_stat_cd FROM tb_hr_dev WHERE dev_id='DEV-000904'",String.class)).isEqualTo("IDLE");
        assertThat(sql.queryForObject("SELECT use_yn FROM tb_hr_dev WHERE dev_id='DEV-000903'",String.class)).isEqualTo("N");
        assertThat(sql.queryForObject("SELECT work_stat_cd FROM tb_hr_dev WHERE dev_id='DEV-000906'",String.class)).isEqualTo("RETIRE");
        assertThat(sql.queryForList("SELECT DISTINCT reg_user_id FROM tb_sy_chg_hist",String.class)).containsOnly("SYSTEM");
    }
}
