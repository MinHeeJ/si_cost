package com.sicost.developers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.areasops.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.*;
import java.time.*;
import java.util.*;
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

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class DeveloperDatabaseTest {
    AnnotationConfigApplicationContext context;JdbcTemplate sql;ObjectMapper json;DeveloperService service;DeveloperQueryService queries;
    @Configuration @EnableTransactionManagement static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install()throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addMapper(AggregateLockMapper.class);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(DeveloperMapper.class,()->session.getMapper(DeveloperMapper.class));context.registerBean(AddressMapper.class,()->session.getMapper(AddressMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));context.registerBean(MasterIdMapper.class,()->session.getMapper(MasterIdMapper.class));
        context.registerBean(DeveloperRevisionMapper.class,()->session.getMapper(DeveloperRevisionMapper.class));
        context.registerBean(com.sicost.developersops.DeveloperActionsMapper.class,()->session.getMapper(com.sicost.developersops.DeveloperActionsMapper.class));
        context.register(com.sicost.developersops.DeveloperActionsRepository.class);
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,DeveloperAggregateRevision.class,MasterIdService.class,AddressRepository.class,AddressService.class,DeveloperRepository.class,DeveloperQueryService.class,DeveloperService.class);
        context.refresh();service=context.getBean(DeveloperService.class);queries=context.getBean(DeveloperQueryService.class);
    }
    @AfterEach void close(){if(context!=null)context.close();}
    Map<String,Object> body(){Map<String,Object> b=new LinkedHashMap<>();b.put("name","시험개발자");b.put("employeeType","FT");b.put("companyId",null);b.put("jobCode",sql.queryForObject("SELECT code FROM tb_sy_code WHERE code_grp_id='JOB' AND use_yn='Y' AND del_yn='N' ORDER BY code LIMIT 1",String.class));b.put("directClass","DRCT");b.put("partId",sql.queryForObject("SELECT part_id FROM tb_bs_part WHERE use_yn='Y' ORDER BY part_id LIMIT 1",String.class));return b;}
    JsonNode create(Map<String,Object> b){return service.create(DeveloperInput.parse(json.valueToTree(b),false));}
    Map<String,Object> edit(String id){var row=queries.get(id);var b=body();for(String k:List.of("name","employeeType","companyId","jobCode","directClass","partId"))b.put(k,row.get(k).isNull()?null:row.get(k).asText());b.put("expectedRevision",row.get("revision").asText());return b;}
    JsonNode update(String id,Map<String,Object> b){return service.update(id,DeveloperInput.parse(json.valueToTree(b),true));}
    long history(String id){return sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_key=? OR trgt_key LIKE ?",Long.class,id,id+":%");}
    @Test void requirement395396397457WritesParentPriceAndOrderedSkills() {
        var b=body();b.put("hireDate","2020-02-29");b.put("skills",List.of(" Java,SQL ","Java","java"));String id=create(b).get("id").asText();var detail=queries.get(id);
        assertThat(sql.queryForObject("SELECT mng_no FROM tb_hr_dev WHERE dev_id=?",String.class,id)).isEqualTo(detail.get("managementNo").asText());assertThat(detail.get("name").asText()).isEqualTo("시험개발자");
        var price=sql.queryForMap("SELECT * FROM tb_hr_dev_prc WHERE dev_id=?",id);assertThat(price.get("aply_bgn_ym")).isEqualTo("202002");assertThat(price.get("aply_end_ym")).isNull();assertThat(price.get("mon_uprc")).isNull();assertThat(price.get("mon_pay_amt")).isNull();
        assertThat(sql.queryForList("SELECT skill_nm FROM tb_hr_dev_skill WHERE dev_id=? ORDER BY sort_ord",String.class,id)).containsExactly("Java","SQL","java");assertThat(sql.queryForObject("SELECT telno FROM tb_hr_dev WHERE dev_id=?",String.class,id)).isNull();assertThat(history(id)).isEqualTo(2);
    }
    @Test void requirement457MissingHireUsesCurrentMonthAndOneSidedAmount(){var b=body();b.put("monthlyPay","1,000원");String id=create(b).get("id").asText();var p=sql.queryForMap("SELECT * FROM tb_hr_dev_prc WHERE dev_id=?",id);assertThat(p.get("aply_bgn_ym")).isEqualTo("202606");assertThat(p.get("mon_uprc")).isNull();assertThat(p.get("mon_pay_amt").toString()).isEqualTo("1000");}
    @Test void requirement459PreservesFutureAndStitchesMonthIntervals(){var b=body();b.put("hireDate","2026-01-01");b.put("monthlyRate","100");String id=create(b).get("id").asText();sql.update("UPDATE tb_hr_dev_prc SET aply_end_ym='202608' WHERE dev_id=?",id);sql.update("INSERT INTO tb_hr_dev_prc(dev_id,aply_bgn_ym,mon_uprc,reg_user_id) VALUES(?,'202609',900,'admin')",id);var e=edit(id);e.put("monthlyRate","200");assertThat(update(id,e).get("changed").asBoolean()).isTrue();var p=sql.queryForList("SELECT aply_bgn_ym,aply_end_ym,mon_uprc FROM tb_hr_dev_prc WHERE dev_id=? ORDER BY aply_bgn_ym",id);assertThat(p).hasSize(3);assertThat(p.get(0).get("aply_end_ym")).isEqualTo("202605");assertThat(p.get(1).get("aply_end_ym")).isEqualTo("202608");assertThat(p.get(2).get("mon_uprc").toString()).isEqualTo("900");}
    @Test void requirement430UnchangedPreservesEveryStoredRow(){String id=create(body()).get("id").asText();var before=sql.queryForMap("SELECT * FROM tb_hr_dev WHERE dev_id=?",id);long n=history(id);assertThat(update(id,edit(id)).get("changed").asBoolean()).isFalse();assertThat(sql.queryForMap("SELECT * FROM tb_hr_dev WHERE dev_id=?",id)).isEqualTo(before);assertThat(history(id)).isEqualTo(n);}
    @Test void requirement428AggregateChildrenMakeRevisionStale(){String id=create(body()).get("id").asText();var e=edit(id);sql.update("INSERT INTO tb_hr_dev_skill(dev_id,skill_nm,sort_ord,reg_user_id) VALUES(?,'SQL',1,'admin')",id);long n=history(id);assertThatThrownBy(()->update(id,e)).isInstanceOfSatisfying(com.sicost.common.api.ApiException.class,x->assertThat(x.code()).isEqualTo("STALE_WRITE"));assertThat(history(id)).isEqualTo(n);}
    @Test void listDetailAndStatusActionsShareTheStateRevision() {
        String id=create(body()).get("id").asText();
        var canonical=context.getBean(DeveloperAggregateRevision.class);
        var actions=context.getBean(com.sicost.developersops.DeveloperActionsRepository.class);
        String revision=queries.get(id).get("revision").asText();
        assertThat(revision).isEqualTo(canonical.revision(id,false));
        var page=queries.list(DeveloperInput.query(Map.of("q","시험개발자")));
        assertThat(page.get("items").get(0).get("revision").asText()).isEqualTo(revision);
        assertThat(actions.revision(actions.required(id,false),false)).isEqualTo(revision);
    }
    @Test void requirement465SystemAndReadRecalculationIsIdempotentAndPreservesManualStop(){String id=create(body()).get("id").asText();sql.update("UPDATE tb_hr_dev SET retire_dt=DATE '2026-06-14' WHERE dev_id=?",id);queries.refresh(true);assertThat(sql.queryForObject("SELECT work_stat_cd FROM tb_hr_dev WHERE dev_id=?",String.class,id)).isEqualTo("RETIRE");assertThat(sql.queryForObject("SELECT use_yn FROM tb_hr_dev WHERE dev_id=?",String.class,id)).isEqualTo("N");long n=history(id);queries.refresh(true);queries.get(id);assertThat(history(id)).isEqualTo(n);assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_key=? AND reg_user_id='SYSTEM'",Long.class,id)).isEqualTo(2);sql.update("UPDATE tb_hr_dev SET retire_dt=NULL WHERE dev_id=?",id);queries.get(id);assertThat(sql.queryForObject("SELECT use_yn FROM tb_hr_dev WHERE dev_id=?",String.class,id)).isEqualTo("N");}
    @Test void requirement468CountAndListApplySameFilters(){var b=body();b.put("managementNo","Case-1");create(b);b.put("managementNo","case-1");create(b);var page=queries.list(DeveloperInput.query(Map.of("q","Case-1")));assertThat(page.get("total").asInt()).isEqualTo(1);assertThat(page.get("items")).hasSize(1);assertThat(queries.list(DeveloperInput.query(Map.of("q","Case-1","page","999"))).get("items")).isEmpty();}
    @Test void historyFailureRollsBackAllChildren(){sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_developer_history CHECK(trgt_tbl_nm <> 'TB_HR_DEV') NOT VALID");long before=sql.queryForObject("SELECT count(*) FROM tb_hr_dev",Long.class);assertThatThrownBy(()->create(body())).isInstanceOf(RuntimeException.class);assertThat(sql.queryForObject("SELECT count(*) FROM tb_hr_dev",Long.class)).isEqualTo(before);}
}
