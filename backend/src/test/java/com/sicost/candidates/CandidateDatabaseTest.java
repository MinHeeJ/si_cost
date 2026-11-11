package com.sicost.candidates;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.areasops.*;
import com.sicost.common.api.ApiException;
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
class CandidateDatabaseTest {
    AnnotationConfigApplicationContext context;JdbcTemplate sql;ObjectMapper json;CandidateService service;CandidateQueryService queries;
    @Configuration @EnableTransactionManagement static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());
        factory.setConfiguration(configuration);factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T16:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(CandidateMapper.class,()->session.getMapper(CandidateMapper.class));context.registerBean(CandidateRevisionMapper.class,()->session.getMapper(CandidateRevisionMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));context.registerBean(MasterIdMapper.class,()->session.getMapper(MasterIdMapper.class));context.registerBean(AddressMapper.class,()->session.getMapper(AddressMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,CommitInvalidation.class,MasterIdService.class,AddressRepository.class,AddressService.class,CandidateRevisionService.class,CandidateRepository.class,CandidateService.class,CandidateQueryService.class);
        context.refresh();service=context.getBean(CandidateService.class);queries=context.getBean(CandidateQueryService.class);
    }
    @AfterEach void close(){if(context!=null)context.close();}
    Map<String,Object> body(){Map<String,Object> b=new HashMap<>();b.put("name","후보 시험");b.put("employeeType","FL");b.put("jobCode",sql.queryForObject("SELECT code FROM tb_sy_code WHERE code_grp_id='JOB' AND use_yn='Y' AND del_yn='N' ORDER BY code LIMIT 1",String.class));b.put("gradeCode","MD");b.put("sourceCode","SITE");b.put("desiredRate","1");b.put("availableDate","2026-07-16");b.put("skills",List.of("Java,SQL","Java"));b.put("consent",false);return b;}
    String create(Map<String,Object> b){return service.create(CandidateInput.parse(json.valueToTree(b),false)).get("id").asText();}
    Map<String,Object> edit(String id){Map<String,Object> b=body();b.put("expectedRevision",queries.get(id).get("revision").asText());return b;}
    JsonNode update(String id,Map<String,Object> b){return service.update(id,CandidateInput.parse(json.valueToTree(b),true));}
    long events(String id){return sql.queryForObject("SELECT count(*) FROM tb_hr_cand_cntc WHERE cand_id=?",Long.class,id);}
    @Test void registrationAndOrderedSkillsCommitAsAdminWithoutChangingAccounts() {
        var users=sql.queryForList("SELECT * FROM tb_sy_user ORDER BY user_id");String id=create(body());JsonNode d=queries.get(id);
        assertThat(d.get("desiredRate").asText()).isEqualTo("1");assertThat(d.get("contactStatus").asText()).isEqualTo("BEFORE");assertThat(d.get("lastContactDate").asText()).isEqualTo("2026-06-16");assertThat(d.get("consentDate").isNull()).isTrue();
        assertThat(sql.queryForList("SELECT skill_nm FROM tb_hr_cand_skill WHERE cand_id=? ORDER BY sort_ord",String.class,id)).containsExactly("Java","SQL");assertThat(events(id)).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_key=? AND trgt_tbl_nm='TB_HR_CAND' AND chg_type_cd='NEW'",Long.class,id)).isEqualTo(1);assertThat(sql.queryForList("SELECT * FROM tb_sy_user ORDER BY user_id")).isEqualTo(users);
    }
    @Test void actualChangeThenNoOpKeepLastContactAndConsentMetadataAndMaskContactHistory() {
        var b=body();b.put("consent",true);b.put("phone","010-1234-5678");String id=create(b);var before=sql.queryForMap("SELECT * FROM tb_hr_cand WHERE cand_id=?",id);
        var change=edit(id);change.put("consent",true);change.put("phone","010-9999-8888");change.put("memo","수정 메모");assertThat(update(id,change).get("changed").asBoolean()).isTrue();
        var after=sql.queryForMap("SELECT * FROM tb_hr_cand WHERE cand_id=?",id);for(String k:List.of("last_cntc_dt","prvc_agre_dt","prvc_agre_user_id","reg_dttm"))assertThat(after.get(k)).isEqualTo(before.get(k));assertThat(events(id)).isEqualTo(2);
        assertThat(sql.queryForMap("SELECT bfr_val,aft_val FROM tb_sy_chg_hist WHERE trgt_key=? AND chg_item_nm='TELNO'",id)).containsEntry("bfr_val","[비공개]").containsEntry("aft_val","[비공개]");
        change.put("expectedRevision",queries.get(id).get("revision").asText());var histories=sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id");assertThat(update(id,change).get("changed").asBoolean()).isFalse();assertThat(events(id)).isEqualTo(2);assertThat(sql.queryForMap("SELECT * FROM tb_hr_cand WHERE cand_id=?",id)).isEqualTo(after);assertThat(sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id")).isEqualTo(histories);
    }
    @Test void contactAddedAfterReadMakesUpdateStaleAndWritesNothing() {
        String id=create(body());var b=edit(id);b.put("memo","충돌");sql.update("INSERT INTO tb_hr_cand_cntc(cand_id,cntc_sn,cntc_stat_cd,cntc_memo,cntc_dt,reg_user_id) VALUES(?,2,'BEFORE','별도 연락',DATE '2026-06-16','admin')",id);
        var before=sql.queryForMap("SELECT * FROM tb_hr_cand WHERE cand_id=?",id);assertThatThrownBy(()->update(id,b)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));assertThat(events(id)).isEqualTo(2);assertThat(sql.queryForMap("SELECT * FROM tb_hr_cand WHERE cand_id=?",id)).isEqualTo(before);
    }
    @Test void auditFailureRollsBackParentChildrenAndContact() {
        long count=sql.queryForObject("SELECT count(*) FROM tb_hr_cand",Long.class);var events=sql.queryForList("SELECT * FROM tb_hr_cand_cntc ORDER BY cand_id,cntc_sn");var skills=sql.queryForList("SELECT * FROM tb_hr_cand_skill ORDER BY cand_id,skill_nm");
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_candidate_history CHECK(trgt_tbl_nm <> 'TB_HR_CAND') NOT VALID");assertThatThrownBy(()->create(body())).isInstanceOf(RuntimeException.class);assertThat(sql.queryForObject("SELECT count(*) FROM tb_hr_cand",Long.class)).isEqualTo(count);assertThat(sql.queryForList("SELECT * FROM tb_hr_cand_cntc ORDER BY cand_id,cntc_sn")).isEqualTo(events);assertThat(sql.queryForList("SELECT * FROM tb_hr_cand_skill ORDER BY cand_id,skill_nm")).isEqualTo(skills);
    }
    @Test void monthEndFiltersAndDefaultHoldOrderMatchCountAndEveryUserSort() {
        List<String> ids=new ArrayList<>();for(String date:List.of("2026-07-01","2026-07-31","2026-08-01","2026-09-30","2026-10-01")){var b=body();b.put("availableDate",date);ids.add(create(b));}
        sql.update("UPDATE tb_hr_cand SET cntc_stat_cd='HOLD' WHERE cand_id=?",ids.get(0));
        var next=queries.list(CandidateInput.query(Map.of("availability","NEXT_MONTH")));assertThat(next.get("total").asInt()).isEqualTo(2);assertThat(next.get("items").get(0).get("id").asText()).isEqualTo(ids.get(1));
        assertThat(queries.list(CandidateInput.query(Map.of("availability","THREE_MONTHS"))).get("total").asInt()).isEqualTo(4);
        for(String sort:CandidateInput.SORTS)for(String direction:List.of("ASC","DESC")){var result=queries.list(CandidateInput.query(Map.of("sort",sort,"direction",direction)));assertThat(result.get("items").size()).isEqualTo(result.get("total").asInt());}
        assertThat(queries.list(CandidateInput.query(Map.of("page","999"))).get("items").isEmpty()).isTrue();
    }
    @Test void maximumSkillSetIsStoredInFullAndAuditSummaryFits() {
        var b=body();b.put("skills",java.util.stream.IntStream.range(0,999).mapToObj(i->"스킬"+i).toList());String id=create(b);assertThat(queries.get(id).get("skills").size()).isEqualTo(999);
        var change=new HashMap<>(b);change.put("expectedRevision",queries.get(id).get("revision").asText());change.put("skills",java.util.stream.IntStream.range(0,999).mapToObj(i->"새스킬"+i).toList());update(id,change);
        assertThat(sql.queryForObject("SELECT length(aft_val) FROM tb_sy_chg_hist WHERE trgt_key=? AND chg_item_nm='보유 스킬'",Integer.class,id)).isLessThanOrEqualTo(500);assertThat(queries.get(id).get("skills").size()).isEqualTo(999);
    }
}
