package com.sicost.expenses;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.budget.*;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.shared.SharedQueriesMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static com.sicost.expenses.ProjectExpenseDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class ProjectExpenseDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    ProjectExpenseService service;
    MockMvc mvc;
    ObjectMapper json;
    String id;
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
        json=JsonMapper.builder().findAndAddModules().build();context.registerBean(ObjectMapper.class,()->json);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(ProjectExpenseMapper.class,()->session.getMapper(ProjectExpenseMapper.class));
        context.registerBean(ProjectBudgetMapper.class,()->session.getMapper(ProjectBudgetMapper.class));
        context.registerBean(SharedQueriesMapper.class,()->session.getMapper(SharedQueriesMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,
            WriteCoordinator.class,CommitInvalidation.class,ProjectBudgetRepository.class,ProjectExpenseRepository.class,ProjectExpenseService.class);
        context.refresh();service=context.getBean(ProjectExpenseService.class);
        mvc=MockMvcBuilders.standaloneSetup(new ProjectExpenseController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        id="E:"+UUID.randomUUID().toString().replace("-","").substring(0,16);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'TK','END','2079-01-01','2079-12-31',1,0,0,0,'admin')",id,"경비 시험 "+id);
    }
    @AfterEach void close() {if(context!=null)context.close();}
    CreateProjectExpenseRequest input(String month) {return new CreateProjectExpenseRequest(2080,service.getProjectExpenses(id).revision(),month,"TRIP","1","가".repeat(500));}
    ExpenseResult create(String month) {return service.createProjectExpense(id,input(month));}
    ExpenseResult delete(ProjectExpense row) {return service.deleteProjectExpense(new ExpenseKey(id,row.expenseMonth().replace("-",""),row.expenseSn()),new DeleteProjectExpenseRequest(2080,service.getProjectExpenses(id).revision()));}
    List<Map<String,Object>> rows() {return sql.queryForList("SELECT * FROM tb_pj_drct_exps WHERE proj_id=? ORDER BY exps_ym,exps_sn",id);}
    List<Map<String,Object>> audits() {return sql.queryForList("SELECT * FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_PJ_DRCT_EXPS' AND left(trgt_key,length(?)+1)=?||':' ORDER BY hist_id",id,id);}
    Map<String,List<Map<String,Object>>> accounting() {
        Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();
        for(String table:List.of("tb_bl_bill","tb_bl_rcpt","tb_hr_mon_pay","tb_fi_item","tb_fi_xfer","tb_co_mon_cost"))result.put(table,sql.queryForList("SELECT * FROM "+table));
        return result;
    }
    void closed() {sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('208007','CLOSE','admin')");}
    @Test void oneWonAfterContractEndWithoutAssignmentsCommitsOnlyExpenseAndFourAuditFields() throws Exception {
        var before=accounting();var parent=sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?",id);
        var result=create("2080-07");assertThat(rows()).hasSize(1);assertThat(result.value().items().get(0).amount()).isEqualTo("1");
        mvc.perform(get("/api/projects/"+id+"/expenses")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalAmount").value("1"))
            .andExpect(jsonPath("$.data.items[0].itemName").value("출장비")).andExpect(jsonPath("$.data.revision").value(result.revision()));
        assertThat(accounting()).isEqualTo(before);assertThat(sql.queryForMap("SELECT * FROM tb_pj_proj WHERE proj_id=?",id)).isEqualTo(parent);
        assertThat(audits()).hasSize(4).allSatisfy(a->{assertThat(a.get("trgt_key")).isEqualTo(id+":208007:1");assertThat(a.get("chg_type_cd")).isEqualTo("NEW");assertThat(a.get("reg_user_id")).isEqualTo("admin");assertThat(a.get("bfr_val")).isNull();assertThat(((String)a.get("aft_val")).codePointCount(0,((String)a.get("aft_val")).length())).isLessThanOrEqualTo(500);});
        assertThat(audits()).extracting(a->a.get("aft_val")).containsExactly("2080-07","TRIP","1","가".repeat(500));
    }
    @Test void deleteRemovesOnlyTargetAndSerialIncludesRetainedDelAuditEvenWithColonInId() {
        var first=create("2080-07").value().items().get(0);create("2080-08");
        delete(first);assertThat(rows()).hasSize(1);assertThat(rows().get(0).get("exps_ym")).isEqualTo("208008");
        assertThat(audits().stream().filter(a->a.get("chg_type_cd").equals("DEL")).toList()).extracting(a->a.get("bfr_val")).containsExactly("2080-07","TRIP","1","가".repeat(500));
        var next=create("2080-07");assertThat(next.value().items().get(0).expenseSn()).isEqualTo(2);
        assertThat(service.getProjectExpenses(id).items()).hasSize(2);
    }
    @Test void readNeverChangesRowsAuditTimesHistoryOrAccounting() throws Exception {
        create("2080-07");var beforeRows=rows();var beforeAudit=audits();var money=accounting();
        mvc.perform(get("/api/projects/"+id+"/expenses")).andExpect(status().isOk());
        assertThat(rows()).isEqualTo(beforeRows);assertThat(audits()).isEqualTo(beforeAudit);assertThat(accounting()).isEqualTo(money);
    }
    @Test void listOrderingUsesRegistrationThenMonthThenSerialDescending() {
        create("2080-07");create("2080-08");create("2080-08");
        var list=service.getProjectExpenses(id).items();assertThat(list).extracting(ProjectExpense::expenseMonth).containsExactly("2080-08","2080-08","2080-07");
        assertThat(list).extracting(ProjectExpense::expenseSn).containsExactly(2,1,1);
        sql.update("UPDATE tb_pj_drct_exps SET reg_dttm='2080-08-01 00:00:00.123456' WHERE proj_id=? AND exps_ym='208007'",id);
        assertThat(service.getProjectExpenses(id).items().get(0).expenseMonth()).isEqualTo("2080-07");
    }
    @Test void closedAfterReadRejectsDeleteAndPreservesOriginalAndAudit() {
        var row=create("2080-07").value().items().get(0);var before=rows();var audit=audits();closed();
        assertThatThrownBy(()->delete(row)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("CLOSED_MONTH"));
        assertThat(rows()).isEqualTo(before);assertThat(audits()).isEqualTo(audit);
    }
    @Test void closedCreateChangesNoBusinessOrAuditRows() {
        closed();var before=rows();var audit=audits();assertThatThrownBy(()->create("2080-07")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("CLOSED_MONTH"));assertThat(rows()).isEqualTo(before);assertThat(audits()).isEqualTo(audit);
    }
    @Test void auditFailureRollsBackCreate() {
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_expense_audit CHECK(trgt_tbl_nm <> 'TB_PJ_DRCT_EXPS')");
        assertThatThrownBy(()->create("2080-07")).isInstanceOf(RuntimeException.class);assertThat(rows()).isEmpty();assertThat(audits()).isEmpty();
    }
    @Test void auditFailureRollsBackPhysicalDelete() {
        var row=create("2080-07").value().items().get(0);var before=rows();var audit=audits();
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_expense_delete CHECK(chg_type_cd <> 'DEL')");
        assertThatThrownBy(()->delete(row)).isInstanceOf(RuntimeException.class);assertThat(rows()).isEqualTo(before);assertThat(audits()).isEqualTo(audit);
    }
    @Test void deletedHistoricalMaximumPreventsReuseAndCapsAt99999() {
        sql.update("INSERT INTO tb_sy_chg_hist(trgt_tbl_nm,trgt_key,chg_type_cd,chg_item_nm,bfr_val,reg_user_id) VALUES ('TB_PJ_DRCT_EXPS',?,'DEL','월','2080-07','admin')",id+":208007:99999");
        assertThatThrownBy(()->create("2080-07")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("SERIAL_LIMIT"));assertThat(rows()).isEmpty();assertThat(audits()).hasSize(1);
    }
    @Test void concurrentSameRevisionCommitsOneExpenseAndOneSetOfAuditFields() throws Exception {
        var command=input("2080-07");CountDownLatch start=new CountDownLatch(1);
        var a=CompletableFuture.supplyAsync(()->concurrent(start,command));var b=CompletableFuture.supplyAsync(()->concurrent(start,command));start.countDown();
        assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","STALE_WRITE");assertThat(rows()).hasSize(1);assertThat(audits()).hasSize(4);
    }
    String concurrent(CountDownLatch start,CreateProjectExpenseRequest input) {
        try {if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");service.createProjectExpense(id,input);return "OK";}
        catch(ApiException e){return e.code();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    }
}
