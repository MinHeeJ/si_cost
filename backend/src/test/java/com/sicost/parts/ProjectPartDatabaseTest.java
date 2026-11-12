package com.sicost.parts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.budget.*;
import com.sicost.businessusers.*;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static com.sicost.parts.ProjectPartDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class ProjectPartDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    ProjectPartService service;
    MockMvc mvc;
    ObjectMapper json;
    String id,a,b,c,d,e;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(ProjectPartMapper.class,()->session.getMapper(ProjectPartMapper.class));
        context.registerBean(ProjectBudgetMapper.class,()->session.getMapper(ProjectBudgetMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(MasterLockMapper.class,()->session.getMapper(MasterLockMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.registerBean(BusinessUserMapper.class,()->session.getMapper(BusinessUserMapper.class));
        context.registerBean(AccessLogMapper.class,()->session.getMapper(AccessLogMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,WriteCoordinator.class,
            CommitInvalidation.class,ProjectBudgetRepository.class,ProjectPartRepository.class,ProjectPartService.class,
            AccessLogWriter.class,BusinessUserRepository.class,BusinessUserService.class);
        context.refresh();service=context.getBean(ProjectPartService.class);
        mvc=MockMvcBuilders.standaloneSetup(new ProjectPartController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        id="P-"+UUID.randomUUID().toString().replace("-","").substring(0,16);
        a=part("A");b=part("B");c=part("C");d=part("D");e=part("E");
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'TK','ING','2080-01-01','2081-12-31',100,0,0,'admin')",id,id);
        source("208001",a);source("208003",b);source("208008",c);source("208009",d);
        sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('208003','CLOSE','admin')");
        sql.update("INSERT INTO tb_pj_proj_chg(proj_id,hist_sn,chg_item_cd,chg_item_nm,bfr_val,aft_val,chg_dt,reg_user_id) VALUES (?,1,'PART','영업 파트','이전 예약','과거 감사','2080-01-01','admin')",id);
    }
    @AfterEach void close() {if(context!=null)context.close();}
    String part(String suffix) {
        String key=suffix+UUID.randomUUID().toString().replace("-","").substring(0,15);
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES (?,?,'N','Y','admin')",key,key);return key;
    }
    void source(String month,String part) {sql.update("INSERT INTO tb_pj_proj_part(proj_id,aply_bgn_ym,part_id,reg_user_id) VALUES (?,?,?,'admin')",id,month,part);}
    List<Map<String,Object>> parts() {return sql.queryForList("SELECT * FROM tb_pj_proj_part WHERE proj_id=? ORDER BY aply_bgn_ym",id);}
    List<Map<String,Object>> events() {return sql.queryForList("SELECT * FROM tb_pj_proj_chg WHERE proj_id=? ORDER BY hist_sn",id);}
    SaveProjectPartRequest request(String part,String month) {return new SaveProjectPartRequest(2080,service.getProjectParts(id).revision(),part,month,"  보존 사유  ");}
    @Test void getRealSourcesReturnsPublicFieldsWithoutBusinessHistoryOrAccountingChanges() throws Exception {
        var before=parts();var history=events();var project=sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?",id);
        var accounting=sql.queryForList("SELECT * FROM tb_co_mon_cost");var audit=sql.queryForList("SELECT * FROM tb_sy_chg_hist");
        mvc.perform(get("/api/projects/"+id+"/parts")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(4))
            .andExpect(jsonPath("$.data.items[0].actor").value("admin")).andExpect(jsonPath("$.data.retainedBasePart.partId").value(b))
            .andExpect(jsonPath("$.data.minimumMonth").value("2080-04")).andExpect(jsonPath("$.data.maximumMonth").value("2081-12"));
        assertThat(parts()).isEqualTo(before);assertThat(events()).isEqualTo(history);assertThat(sql.queryForList("SELECT * FROM tb_sy_chg_hist")).isEqualTo(audit);
        assertThat(sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?",id)).isEqualTo(project);assertThat(sql.queryForList("SELECT * FROM tb_co_mon_cost")).isEqualTo(accounting);
    }
    @Test void replaceRetainsProtectedRowsAndAllUnrelatedSourcesWithExactReasonAndFinalRevision() {
        var protectedRows=parts().subList(0,2);var standards=sql.queryForList("SELECT * FROM tb_bs_part_std");var dev=sql.queryForList("SELECT * FROM tb_hr_dev");
        var result=service.saveProjectPart(id,request(e,"2080-07"));
        assertThat(result.changed()).isTrue();assertThat(parts().subList(0,2)).isEqualTo(protectedRows);assertThat(parts()).hasSize(3);
        assertThat(parts().get(2).get("chg_rsn")).isEqualTo("  보존 사유  ");assertThat(parts().get(2).get("reg_user_id")).isEqualTo("admin");
        assertThat(service.getProjectParts(id).revision()).isEqualTo(result.revision());assertThat(events()).hasSize(2);
        assertThat(events().get(1).get("bfr_val")).isEqualTo(b);assertThat(events().get(1).get("aft_val")).isEqualTo(e+" (2080-07부터)");
        assertThat(sql.queryForList("SELECT * FROM tb_bs_part_std")).isEqualTo(standards);assertThat(sql.queryForList("SELECT * FROM tb_hr_dev")).isEqualTo(dev);
    }
    @Test void cancellationPreservesPastAuditAndCreatesExactlyOnePartEventThenSameBaseIsNoOp() {
        var original=events().get(0);var result=service.saveProjectPart(id,request(b,"2080-07"));
        assertThat(result.changed()).isTrue();assertThat(parts()).hasSize(2);assertThat(events()).hasSize(2);assertThat(events().get(0)).isEqualTo(original);
        assertThat(events().get(1).get("aft_val")).isEqualTo("변경 취소");
        assertThat(sql.queryForObject("SELECT chg_rsn FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_PJ_PROJ' AND trgt_key=?",String.class,id)).isEqualTo("  보존 사유  ");
        var before=parts();var history=events();assertThat(service.saveProjectPart(id,request(b,"2080-07")).changed()).isFalse();
        assertThat(parts()).isEqualTo(before);assertThat(events()).isEqualTo(history);
    }
    @Test void forbiddenMonthsLeaveReservationsAndHistoryUntouched() {
        var before=parts();var history=events();
        for (String month:List.of("2080-01","2080-03","2082-01")) {
            var input=request(e,month);assertThatThrownBy(()->service.saveProjectPart(id,input)).isInstanceOf(ApiException.class);
            assertThat(parts()).isEqualTo(before);assertThat(events()).isEqualTo(history);
        }
    }
    @Test void invalidReasonThroughHttpHasNoEffects() throws Exception {
        var before=parts();var history=events();
        mvc.perform(put("/api/projects/"+id+"/parts").contentType("application/json").content(json.writeValueAsString(new SaveProjectPartRequest(2080,service.getProjectParts(id).revision(),e,"2080-07","가".repeat(501)))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("reason"));assertThat(parts()).isEqualTo(before);assertThat(events()).isEqualTo(history);
    }
    @Test void requiredAuditFailureRollsBackDeletesInsertAndContractHistory() {
        var before=parts();var history=events();var input=request(e,"2080-07");
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_parts_history CHECK(trgt_tbl_nm <> 'TB_PJ_PROJ')");
        assertThatThrownBy(()->service.saveProjectPart(id,input)).isInstanceOf(RuntimeException.class);
        assertThat(parts()).isEqualTo(before);assertThat(events()).isEqualTo(history);
    }
    @Test void completeAggregateDetectsChildChangesAndKeepsTimestampPrecision() {
        var input=request(e,"2080-07");
        sql.update("INSERT INTO tb_pj_mnpw_plan(proj_id,job_cd,plan_mm,src_cd,reg_user_id,reg_dttm) VALUES (?,'TEST',1.23,'MANUAL','admin',timestamp '2080-07-15 01:02:03.123456')",id);
        var before=parts();assertThatThrownBy(()->service.saveProjectPart(id,input)).isInstanceOfSatisfying(ApiException.class,x->assertThat(x.code()).isEqualTo("STALE_WRITE"));assertThat(parts()).isEqualTo(before);
        assertThat(context.getBean(ProjectPartRepository.class).snapshot(id)).contains("123456","1.23");
    }
    @Test void monthMembershipIsReadOnlyAndFinalIntervalHasNoContractEndCap() {
        var before=parts();assertThat(service.partAt(id,"2080-02")).get().extracting(PartPeriod::partId).isEqualTo(a);
        assertThat(service.partAt(id,"2080-07")).get().extracting(PartPeriod::partId).isEqualTo(b);
        assertThat(service.partAt(id,"2085-01")).get().extracting(PartPeriod::partId).isEqualTo(d);assertThat(parts()).isEqualTo(before);
    }
    @Test void emptyAndUnknownSourcesNeverGenerateBusinessRows() throws Exception {
        sql.update("DELETE FROM tb_pj_proj_part WHERE proj_id=?",id);
        mvc.perform(get("/api/projects/"+id+"/parts")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());assertThat(parts()).isEmpty();
        mvc.perform(get("/api/projects/missing/parts")).andExpect(status().isNotFound());
    }
    @Test void linkedPmRoleRemovalIsBlockedAndUnrelatedRolesRemainThenDetachAllowsRemoval() {
        BusinessUserService business=context.getBean(BusinessUserService.class);
        String user="pm"+UUID.randomUUID().toString().replace("-","").substring(0,14);
        var actor=business.create(new BusinessUserDtos.Create(user,"PM 시험",user+"@example.org",List.of("PM","SALES_LDR")),new org.springframework.mock.web.MockHttpServletRequest()).user();
        sql.update("INSERT INTO tb_sy_user_scope(user_id,scope_sn,scope_type_cd,proj_id,reg_user_id) VALUES (?,1,'PROJ',?,'admin')",user,id);
        var before=sql.queryForList("SELECT * FROM tb_sy_user_role WHERE user_id=? ORDER BY role_id",user);
        var input=new BusinessUserDtos.Edit(actor.name(),actor.email(),List.of("SALES_LDR"),actor.revision());
        assertThatThrownBy(()->business.update(user,input)).isInstanceOfSatisfying(ApiException.class,x->assertThat(x.code()).isEqualTo("USER_IS_PROJECT_PM"));
        assertThat(sql.queryForList("SELECT * FROM tb_sy_user_role WHERE user_id=? ORDER BY role_id",user)).isEqualTo(before);
        sql.update("DELETE FROM tb_sy_user_scope WHERE user_id=? AND scope_sn=1",user);
        assertThat(business.update(user,input).user().businessRoleIds()).containsExactly("SALES_LDR");
    }
}
