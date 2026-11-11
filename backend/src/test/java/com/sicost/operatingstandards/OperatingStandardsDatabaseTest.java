package com.sicost.operatingstandards;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.operatingstandards.OperatingStandardsDtos.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class OperatingStandardsDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    OperatingStandardsService service;
    ObjectMapper json;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        // Every method owns an isolated installed schema; no shared actor or application seed is modified.
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AggregateLockMapper.class);
        factory.setConfiguration(configuration);factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(OperatingStandardsMapper.class,()->session.getMapper(OperatingStandardsMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,CommitInvalidation.class,OperatingStandardsRepository.class,OperatingStandardsService.class);
        context.refresh();service=context.getBean(OperatingStandardsService.class);
    }
    @AfterEach void close(){if(context!=null)context.close();}
    Map<String,Object> change(String key,String value){return Map.of("key",key,"value",value,"expectedRevision",service.history(key).keyRevision());}
    Save request(String month,List<?> changes) throws Exception {Map<String,Object> body=new HashMap<>();body.put("changes",changes);if(month!=null)body.put("effectiveMonth",month);return OperatingStandardsInput.parse(json.valueToTree(body));}
    Result save(String month,String key,String value) throws Exception {return service.save(request(month,List.of(change(key,value))));}
    List<Map<String,Object>> standards(){return sql.queryForList("SELECT * FROM tb_sy_oper_std ORDER BY std_cd,aply_bgn_ym");}
    List<Map<String,Object>> history(){return sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id");}
    void version(String key,String month,String value){sql.update("INSERT INTO tb_sy_oper_std(std_cd,aply_bgn_ym,std_nm,std_val,unit_nm,std_desc,reg_user_id) SELECT std_cd,?,std_nm,?::numeric,unit_nm,std_desc,'admin' FROM tb_sy_oper_std WHERE std_cd=? ORDER BY aply_bgn_ym LIMIT 1",month,value,key);}
    void closeMonth(String status){sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('202601',?,'admin')",status);}
    @Test void seededActorCanFirstCreateAndRead43CurrentKeysWithoutFutureLeakage() throws Exception {
        version("VAT_RT","202607","99");
        var before=standards();var items=service.list(null).items();assertThat(items).hasSize(43).extracting(Standard::key).isSorted();
        assertThat(items).filteredOn(row->row.key().equals("VAT_RT")).first().extracting(Standard::value).isEqualTo("10");
        assertThat(service.list("2025-12").items()).allMatch(row->row.value()==null&&row.effectiveMonth()==null);
        assertThat(standards()).isEqualTo(before);assertThat(history()).isEmpty();
        assertThat(save("2026-06","VAT_RT","11").changed()).isTrue();
        assertThat(sql.queryForObject("SELECT reg_user_id FROM tb_sy_oper_std WHERE std_cd='VAT_RT' AND aply_bgn_ym='202606'",String.class)).isEqualTo("admin");
    }
    @Test void historyReadIsDescendingAndReadOnlyWithDifferentKeyAndRowRevisions() {
        version("VAT_RT","202603","11");version("VAT_RT","202607","12");var before=standards();
        History result=service.history("VAT_RT");assertThat(result.items()).extracting(Version::effectiveMonth).containsExactly("2026-07","2026-03","2026-01");
        assertThat(result.items()).extracting(Version::status).containsExactly("예정","현재","지난");
        assertThat(result.items()).allMatch(row->!row.revision().equals(result.keyRevision()));assertThat(standards()).isEqualTo(before);assertThat(history()).isEmpty();
    }
    @Test void insertOverwritePreserveOtherMonthMetadataAndAudit() throws Exception {
        version("VAT_RT","202603","11");var preserved=sql.queryForMap("SELECT * FROM tb_sy_oper_std WHERE std_cd='VAT_RT' AND aply_bgn_ym='202603'");
        assertThat(save("2026-01","VAT_RT","12").changed()).isTrue();assertThat(save("2026-07","VAT_RT","13").changed()).isTrue();
        assertThat(service.history("VAT_RT").items()).extracting(Version::value).containsExactly("13","11","12");
        assertThat(sql.queryForMap("SELECT * FROM tb_sy_oper_std WHERE std_cd='VAT_RT' AND aply_bgn_ym='202603'")).isEqualTo(preserved);
        assertThat(sql.queryForList("SELECT chg_type_cd,bfr_val,aft_val,reg_user_id FROM tb_sy_chg_hist ORDER BY hist_id"))
            .containsExactly(Map.of("chg_type_cd","UPD","bfr_val","10","aft_val","12","reg_user_id","admin"),
                new HashMap<String,Object>(){{put("chg_type_cd","NEW");put("bfr_val",null);put("aft_val","13");put("reg_user_id","admin");}});
    }
    @Test void noChangeHasNoAuditTimestampOrHistory() throws Exception {var before=standards();assertThat(save("2026-01","VAT_RT","10.0000").changed()).isFalse();assertThat(standards()).isEqualTo(before);assertThat(history()).isEmpty();}
    @Test void lastCloseMinimumRefusesWithNoWrites() throws Exception {
        closeMonth("CLOSE");var before=standards();Save input=request("2026-01",List.of(change("VAT_RT","11")));
        assertThatThrownBy(()->service.save(input)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("CLOSED_MONTH"));assertThat(standards()).isEqualTo(before);assertThat(history()).isEmpty();
    }
    @Test void futureBoundaryFailureRollsBackWholeRequest() throws Exception {
        version("AI_SCORE_LOC","202608","9");var before=standards();
        Save input=request("2026-06",List.of(change("VAT_RT","11"),change("AI_SCORE_JOB","24"),change("AI_SCORE_SKILL","21")));
        assertThatThrownBy(()->service.save(input)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(400));
        assertThat(standards()).isEqualTo(before);assertThat(history()).isEmpty();
    }
    @Test void auditInsertFailureRollsBackPreviouslyWrittenStandard() throws Exception {
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_operating_standard_history CHECK(trgt_tbl_nm <> 'TB_SY_OPER_STD')");
        var before=standards();Save input=request("2026-01",List.of(change("VAT_RT","11")));
        assertThatThrownBy(()->service.save(input)).isInstanceOf(RuntimeException.class);assertThat(standards()).isEqualTo(before);assertThat(history()).isEmpty();
    }
    @Test void cashClosedMonthBlocksBothKeysAndCannotEscapeByNewStart() throws Exception {
        closeMonth("CLOSE");var before=standards();
        for(var change:List.of(change("CASH_BASE_YM","202607"),change("CASH_BASE_AMT","1234"))) {
            Save input=request(null,List.of(change));assertThatThrownBy(()->service.save(input)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("CLOSED_MONTH"));
        }
        assertThat(standards()).isEqualTo(before);assertThat(history()).isEmpty();
    }
    @Test void cashWorkUpdatesBothSingleRowsWithoutTouchingCloseOrBusinessData() throws Exception {
        closeMonth("WORK");var closes=sql.queryForList("SELECT * FROM tb_cl_close");var projects=sql.queryForList("SELECT * FROM tb_pj_proj");var finance=sql.queryForList("SELECT * FROM tb_fi_item");
        save(null,"CASH_BASE_YM","202602");save(null,"CASH_BASE_AMT","1234.5678");
        for(String key:OperatingStandardsInput.CASH)assertThat(service.history(key).items()).hasSize(1).first().extracting(Version::effectiveMonth).isEqualTo("2026-01");
        assertThat(sql.queryForObject("SELECT std_val::bigint::text FROM tb_sy_oper_std WHERE std_cd='CASH_BASE_YM'",String.class)).isEqualTo("202602");
        assertThat(history()).hasSize(2).allMatch(row->row.get("chg_type_cd").equals("UPD"));
        assertThat(sql.queryForList("SELECT * FROM tb_cl_close")).isEqualTo(closes);assertThat(sql.queryForList("SELECT * FROM tb_pj_proj")).isEqualTo(projects);assertThat(sql.queryForList("SELECT * FROM tb_fi_item")).isEqualTo(finance);
    }
    @Test void cashNoCloseRowIsEditable() throws Exception {assertThat(save(null,"CASH_BASE_AMT","1").changed()).isTrue();assertThat(sql.queryForObject("SELECT count(*) FROM tb_cl_close",Integer.class)).isZero();}
    @Test void concurrentSameRevisionCommitsExactlyOnce() throws Exception {
        Save input=request("2026-06",List.of(change("VAT_RT","11")));CountDownLatch start=new CountDownLatch(1);
        var a=CompletableFuture.supplyAsync(()->writeAfter(start,input));var b=CompletableFuture.supplyAsync(()->writeAfter(start,input));start.countDown();
        assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","STALE_WRITE");assertThat(history()).hasSize(1);
    }
    String writeAfter(CountDownLatch start,Save input){try{if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");service.save(input);return "OK";}catch(ApiException e){return e.code();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
