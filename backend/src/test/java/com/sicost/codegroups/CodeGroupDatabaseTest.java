package com.sicost.codegroups;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.codegroups.CodeGroupDtos.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class CodeGroupDatabaseTest {
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate sql;
    static CodeGroupService service;
    static ObjectMapper json;
    static CodeGroupMapper mapper;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
    }
    @BeforeAll static void install() throws Exception {
        String schema="sicost_test_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv("SICOST_TEST_DB_URL"),user=System.getenv("SICOST_TEST_DB_USER"),password=System.getenv("SICOST_TEST_DB_PASSWORD");
        try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {statement.execute("CREATE SCHEMA "+schema);}
        DataSource source=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema+"&options=-c%20TimeZone=Asia%2FSeoul",user,password);
        sql=new JdbcTemplate(source);
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").group(true).baselineOnMigrate(false).cleanDisabled(true).load().migrate();
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class,()->source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.system(ZoneId.of("Asia/Seoul")));
        context.registerBean(CodeGroupMapper.class,()->session.getMapper(CodeGroupMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,CommitInvalidation.class,CodeReferenceGuard.class,CodeGroupValidator.class,CodeGroupService.class);
        context.refresh();service=context.getBean(CodeGroupService.class);mapper=context.getBean(CodeGroupMapper.class);
    }
    @AfterAll static void close() {if(context!=null)context.close();}
    String key() {return "T"+UUID.randomUUID().toString().replace("-","").substring(0,18).toUpperCase(Locale.ROOT);}
    String group() {String key=key();service.create(new Create(key,"독립 시험 "+key));return key;}
    Save request(String group,String changes) throws Exception {return CodeGroupInput.save(json.readTree("{\"expectedRevision\":\""+service.page(group,1,50).groupRevision()+"\",\"changes\":"+changes+"}"));}
    void add(String group,String code) throws Exception {service.save(group,request(group,"[{\"code\":\""+code+"\",\"isNew\":true,\"name\":\"시험 코드\"}]"));}
    long history(String group) {return sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_key=? OR trgt_key LIKE ?",Long.class,group,group+":%");}

    @Test void seededGroupsAndAllowanceValuesComeFromSql() {
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_code_grp WHERE reg_user_id='SYSTEM'",Long.class)).isEqualTo(64);
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_code_grp WHERE sys_code_yn='Y' AND reg_user_id='SYSTEM'",Long.class)).isEqualTo(44);
        assertThat(service.groups().items()).isSortedAccordingTo(Comparator.comparingInt(Group::sortOrder).thenComparing(Group::groupId));
        Map<String,String> expected=Map.of("ALLOW_TEAM","200000","ALLOW_GROUP","400000","INS_RATE","9.4","SEV_RATE","8.33","TEAM_FEE","50000","PEXP_FEE","50000");
        service.page("ALWNC",1,50).items().forEach(row->assertThat(new java.math.BigDecimal(row.codeVal())).isEqualByComparingTo(expected.get(row.code())));
    }
    @Test void createCommitContainsOneGroupAndOneNewHistoryFromAdmin() {
        String key=group();
        var row=sql.queryForMap("SELECT * FROM tb_sy_code_grp WHERE code_grp_id=?",key);
        assertThat(row).containsEntry("sys_code_yn","N").containsEntry("val_code_yn","N").containsEntry("use_yn","Y").containsEntry("reg_user_id","admin");
        assertThat(history(key)).isEqualTo(1);
    }
    @Test void concurrentSameNameSerializesAndOnlyOneGroupCommits() throws Exception {
        String first=key(),second=key(),name="동시 등록 "+key();CountDownLatch start=new CountDownLatch(1);
        var a=CompletableFuture.supplyAsync(()->createAfter(start,new Create(first,name)));
        var b=CompletableFuture.supplyAsync(()->createAfter(start,new Create(second,name)));
        start.countDown();assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("CREATED","DUPLICATE_GROUP_NAME");
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_code_grp WHERE code_grp_nm=?",Long.class,name)).isEqualTo(1);
    }
    String createAfter(CountDownLatch start,Create input) {
        try {if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");service.create(input);return "CREATED";}
        catch(ApiException e){return e.code();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    }
    @Test void sameRevisionTwoWritersOnlyOneCommitsAndStaleHasNoAudit() throws Exception {
        String group=group();add(group,"A");String revision=service.page(group,1,50).groupRevision();long before=history(group);
        Save first=CodeGroupInput.save(json.readTree("{\"expectedRevision\":\""+revision+"\",\"changes\":[{\"code\":\"A\",\"isNew\":false,\"name\":\"선행\"}]}"));
        Save second=CodeGroupInput.save(json.readTree("{\"expectedRevision\":\""+revision+"\",\"changes\":[{\"code\":\"A\",\"isNew\":false,\"name\":\"후행\"}]}"));
        CountDownLatch start=new CountDownLatch(1);
        var a=CompletableFuture.supplyAsync(()->saveAfter(start,group,first));var b=CompletableFuture.supplyAsync(()->saveAfter(start,group,second));start.countDown();
        assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("SAVED","STALE_WRITE");
        assertThat(history(group)).isEqualTo(before+1);
    }
    String saveAfter(CountDownLatch start,String group,Save input) {
        try{if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");service.save(group,input);return "SAVED";}
        catch(ApiException e){return e.code();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    }
    @Test void failedHistoryInsertRollsBackPreviouslyWrittenCode() throws Exception {
        String group=group();add(group,"A");var before=sql.queryForMap("SELECT * FROM tb_sy_code WHERE code_grp_id=? AND code='A'",group);long count=history(group);
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT "+group.toLowerCase(Locale.ROOT)+" CHECK (trgt_key <> '"+group+":A') NOT VALID");
        try {
            Save input=request(group,"[{\"code\":\"A\",\"isNew\":false,\"name\":\"롤백\"}]");
            assertThatThrownBy(()->service.save(group,input)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(sql.queryForMap("SELECT * FROM tb_sy_code WHERE code_grp_id=? AND code='A'",group)).isEqualTo(before);assertThat(history(group)).isEqualTo(count);
        } finally {sql.execute("ALTER TABLE tb_sy_chg_hist DROP CONSTRAINT "+group.toLowerCase(Locale.ROOT));}
    }
    @Test void referenceRefusalThenLogicalDeleteAndPrimaryKeyReuseRefusal() throws Exception {
        String group=group();add(group,"A");long count=history(group);
        long mapId=sql.queryForObject("INSERT INTO tb_sy_code_map(src_text,code_grp_id,code,reg_user_id) VALUES(?,?,'A','admin') RETURNING map_id",Long.class,"시험 "+group,group);
        String revision=service.page(group,1,50).groupRevision();
        assertThatThrownBy(()->service.delete(group,"A",revision)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("CODE_IN_USE"));
        assertThat(history(group)).isEqualTo(count);sql.update("DELETE FROM tb_sy_code_map WHERE map_id=?",mapId);
        service.delete(group,"A",revision);
        assertThat(service.page(group,1,50).items()).isEmpty();
        assertThat(sql.queryForObject("SELECT del_yn FROM tb_sy_code WHERE code_grp_id=? AND code='A'",String.class,group)).isEqualTo("Y");
        assertThat(sql.queryForMap("SELECT bfr_val,aft_val,reg_user_id FROM tb_sy_chg_hist WHERE trgt_key=? AND chg_type_cd='DEL'",group+":A")).containsEntry("bfr_val","N").containsEntry("aft_val","Y").containsEntry("reg_user_id","admin");
        Save input=request(group,"[{\"code\":\"A\",\"isNew\":true,\"name\":\"재등록\"}]");
        assertThatThrownBy(()->service.save(group,input)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("DUPLICATE_CODE"));
    }
    @Test void noOpLeavesDatabaseAuditAndHistoryUntouched() throws Exception {
        String group=group();add(group,"A");var before=sql.queryForMap("SELECT * FROM tb_sy_code WHERE code_grp_id=?",group);long count=history(group);
        assertThat(service.save(group,request(group,"[{\"code\":\"A\",\"isNew\":false,\"name\":\"시험 코드\"}]")).changed()).isFalse();
        assertThat(sql.queryForMap("SELECT * FROM tb_sy_code WHERE code_grp_id=?",group)).isEqualTo(before);assertThat(history(group)).isEqualTo(count);
    }
}
