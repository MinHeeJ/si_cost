package com.sicost.meops;

import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.meops.ProfileDtos.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class ProfileDatabaseTest {
    PostgreSqlDatabase db;
    ProfileMapper mapper;
    TransactionTemplate transaction;
    String actor;
    String other;
    String prefix;
    int scope;

    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();
        configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new ClassPathResource("mappers/meops/ProfileMapper.xml"));
        mapper=new SqlSessionTemplate(factory.getObject()).getMapper(ProfileMapper.class);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(db.source));
        transaction.setReadOnly(true);transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        prefix=UUID.randomUUID().toString().replace("-","").substring(0,10);
        actor="u"+prefix;other="v"+prefix;
        user(actor);user(other);
    }
    @AfterEach void removeOnlyOwnedSchema() {
        if(db!=null) {
            String schema=db.sql.queryForObject("SELECT current_schema()",String.class);
            if(schema==null||!schema.matches("sicost_test_[a-f0-9]{32}"))throw new IllegalStateException("시험 스키마만 정리합니다.");
            db.sql.execute("DROP SCHEMA "+schema+" CASCADE");
        }
    }
    void user(String id) {
        db.sql.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,reg_user_id) VALUES (?,?,'profile-test@example.invalid','unusable-test-hash',0,'USE',0,?)",id,"실제 사용자",id);
    }
    String part(String suffix,String name,boolean hq) {
        String id=prefix+suffix;
        db.sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES (?,?,?,'Y',?)",id,name,hq?"Y":"N",actor);
        return id;
    }
    void scope(String user,String type,String part,String project) {
        db.sql.update("INSERT INTO tb_sy_user_scope(user_id,scope_sn,scope_type_cd,part_id,proj_id,reg_user_id) VALUES (?,?,?,?,?,?)",user,++scope,type,part,project,actor);
    }
    void leader(String part,String from,String to,String user) {
        db.sql.update("INSERT INTO tb_bs_part_std(part_id,aply_bgn_ym,aply_end_ym,part_ldr_user_id,prft_dstrb_rt,sales_exps_amt,reg_user_id) VALUES (?,?,?,?,0,0,?)",part,from,to,user,actor);
    }
    String project(String suffix,String state,String part) {
        String id=prefix+suffix;
        db.sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO',?,'2026-01-01','2026-12-31',0,0,0,?)",id,id,state,actor);
        projectPart(id,"202601",part);
        scope(actor,"PROJ",null,id);
        return id;
    }
    void projectPart(String project,String month,String part) {
        db.sql.update("INSERT INTO tb_pj_proj_part(proj_id,aply_bgn_ym,part_id,reg_user_id) VALUES (?,?,?,?)",project,month,part,actor);
    }
    @Test void unionIncludesCurrentLeaderDirectPartHeadquartersAndLatestProjectPartOnceInNameAndIdOrder() {
        String a=part("a","가나다",false),b=part("b","가나다",false),hq=part("h","본사",true),c=part("c","영업",false);
        leader(a,"202601","202607",actor);scope(actor,"PART",a,null);scope(actor,"PART",a,null);
        scope(actor,"PART",hq,null);scope(actor,"PART",b,null);
        String p=project("p","ING",a);projectPart(p,"202607",c);projectPart(p,"202608",b);
        scope(actor,"PROJ",null,p);
        assertThat(mapper.parts(actor,"202607")).extracting(Part::partId).containsExactly(a,b,hq,c);
    }
    @Test void endedProjectContributesNoPart() {
        String a=part("a","종료",false);project("p","END",a);
        assertThat(mapper.parts(actor,"202607")).isEmpty();
    }
    @Test void lostProjectContributesNoPart() {
        String a=part("a","실주",false);project("p","LOSE",a);
        assertThat(mapper.parts(actor,"202607")).isEmpty();
    }
    @Test void futureAndExpiredLeadershipAndOtherUserScopesDoNotLeak() {
        String a=part("a","미래",false),b=part("b","과거",false),c=part("c","다른 주체",false);
        leader(a,"202608",null,actor);leader(b,"202601","202606",actor);scope(other,"PART",c,null);
        assertThat(mapper.parts(actor,"202607")).isEmpty();
    }
    @Test void newestEffectiveLeaderReplacesOverlappingOlderLeader() {
        String a=part("a","파트",false);leader(a,"202601",null,actor);leader(a,"202607",null,other);
        assertThat(mapper.parts(actor,"202607")).isEmpty();
        assertThat(mapper.parts(other,"202607")).extracting(Part::partId).containsExactly(a);
    }
    @Test void latestNullProjectPartDoesNotFallBackToHistoricalAffiliation() {
        String a=part("a","과거",false);String p=project("p","ING",a);projectPart(p,"202607",null);
        assertThat(mapper.parts(actor,"202607")).isEmpty();
    }
    @Test void rolesExcludeDisabledAndOtherUsersAndSortByNameThenId() {
        String a=prefix+"a",b=prefix+"b",off=prefix+"c",foreign=prefix+"d";
        role(a,"가나다","Y",actor);role(b,"가나다","Y",actor);role(off,"비활성","N",actor);role(foreign,"타 사용자","Y",other);
        assertThat(mapper.activeRoles(actor)).extracting(Role::roleId).containsExactly(a,b);
    }
    void role(String id,String name,String use,String user) {
        db.sql.update("INSERT INTO tb_sy_role(role_id,role_nm,data_scope_cd,sort_ord,use_yn,reg_user_id) VALUES (?,?,'ALL',1,?,?)",id,name,use,actor);
        db.sql.update("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES (?,?,?)",user,id,actor);
    }
    @Test void mapperMaterializesSafeIdentityAndDoesNotCreateAnyMissingAffiliation() {
        assertThat(mapper.user(actor)).isEqualTo(new User(actor,"실제 사용자"));
        assertThat(mapper.user(prefix+"missing")).isNull();
        assertThat(mapper.activeRoles(actor)).isEmpty();assertThat(mapper.parts(actor,"202607")).isEmpty();
    }
    @Test void readOnlyRepeatableReadLeavesUserPreferencesDevelopersLedgersAndAuditCountsUnchanged() {
        List<String> tables=List.of("tb_sy_user","tb_sy_user_pref","tb_hr_dev","tb_hr_mon_pay","tb_sy_noti","tb_sy_delivery_task","tb_sy_automation_run","tb_sy_chg_hist","tb_sy_accs_log");
        var before=new LinkedHashMap<String,Long>();for(String table:tables)before.put(table,db.count(table));
        var userBefore=db.sql.queryForMap("SELECT user_nm,email_addr,user_stat_cd,mod_user_id,mod_dttm FROM tb_sy_user WHERE user_id=?",actor);
        transaction.executeWithoutResult(status->{
            assertThat(db.sql.queryForObject("SHOW transaction_isolation",String.class)).isEqualTo("repeatable read");
            assertThat(db.sql.queryForObject("SHOW transaction_read_only",String.class)).isEqualTo("on");
            mapper.user(actor);mapper.activeRoles(actor);mapper.parts(actor,"202607");
        });
        for(String table:tables)assertThat(db.count(table)).isEqualTo(before.get(table));
        assertThat(db.sql.queryForMap("SELECT user_nm,email_addr,user_stat_cd,mod_user_id,mod_dttm FROM tb_sy_user WHERE user_id=?",actor)).isEqualTo(userBefore);
    }
}
