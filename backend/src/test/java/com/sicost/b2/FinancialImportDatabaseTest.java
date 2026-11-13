package com.sicost.b2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.b7.ExcelImportService;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
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
import static com.sicost.b2.FinancialImportDtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class FinancialImportDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    FinancialImportService service;
    org.springframework.mock.web.MockMultipartFile file;
    @Configuration @EnableTransactionManagement
    static class Transactions { @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); } }
    @BeforeEach void install()throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AggregateLockMapper.class);configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        context.registerBean(ObjectMapper.class,()->JsonMapper.builder().findAndAddModules().build());
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(FinancialImportMapper.class,()->session.getMapper(FinancialImportMapper.class));
        context.registerBean(AggregateLockMapper.class,()->session.getMapper(AggregateLockMapper.class));context.registerBean(MasterLockMapper.class,()->session.getMapper(MasterLockMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));context.registerBean(ChangeHistoryMapper.class,()->session.getMapper(ChangeHistoryMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,ChangeHistoryWriter.class,RevisionService.class,WriteCoordinator.class,CommitInvalidation.class,ExcelImportService.class,FinancialImportDefinition.class,FinancialImportRepository.class,FinancialImportService.class);
        context.refresh();service=context.getBean(FinancialImportService.class);file=FinancialWorkbook.valid();
    }
    @AfterEach void close() { if(context!=null)context.close(); }
    Result checked() { return service.validate(file,"2080-07"); }
    Result apply(Result result) { return service.apply(file,new Request(result.month(),result.uploadId(),result.expectedRevision())); }
    List<Map<String,Object>> rows() { return sql.queryForList("SELECT * FROM tb_fi_item ORDER BY fin_ym,item_sn"); }
    List<Map<String,Object>> audits() { return sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id"); }
    void manual(String month,int sn,String name) { sql.update("INSERT INTO tb_fi_item(fin_ym,item_sn,fin_cls_cd,item_nm,amt,vat_amt,reg_user_id) VALUES (?,?,'SGA',?,1,0,'admin')",month,sn,name); }
    @Test void validateHasNoBusinessWritesApplyPreservesManualOtherMonthAndOriginalReceiptAfterDelete() {
        manual("208007",1,"직접 입력");manual("208006",1,"다른 월");var before=rows();var result=checked();assertThat(result.canApply()).isTrue();assertThat(rows()).isEqualTo(before);
        var first=apply(result);assertThat(first.status()).isEqualTo("APPLIED");assertThat(rows()).hasSize(3);assertThat(first.items()).hasSize(1);assertThat(first.uploadId()).isEqualTo(result.uploadId());
        sql.update("DELETE FROM tb_fi_item WHERE fin_ym='208007' AND upld_id IS NOT NULL");var history=audits();assertThat(apply(result)).isEqualTo(first);assertThat(audits()).isEqualTo(history);
    }
    @Test void secondUploadReplacesOnlyUploadOriginAndRetainsFirstReceipt() {
        manual("208007",1,"수동");Result first=apply(checked());Result next=apply(checked());assertThat(rows()).hasSize(2);assertThat(next.summary().replaceCount()).isEqualTo(1);assertThat(next.summary().preserveCount()).isEqualTo(1);assertThat(service.result(Long.parseLong(first.uploadId()))).isEqualTo(first);
    }
    @Test void sameCountContentChangeRejectsStaleWithoutChangingHistory() {
        manual("208007",1,"전값");var result=checked();sql.update("UPDATE tb_fi_item SET item_nm='후값' WHERE fin_ym='208007' AND item_sn=1");var before=rows();var history=audits();assertThatThrownBy(()->apply(result)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("STALE_WRITE"));assertThat(rows()).isEqualTo(before);assertThat(audits()).isEqualTo(history);
    }
    @Test void exhaustedItemNumberUsesSharedFinancialErrorAndPreservesState() {
        manual("208007",99999,"번호 한계");var result=checked();var before=rows();var history=audits();
        assertThatThrownBy(()->apply(result)).isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.status()).isEqualTo(409);assertThat(e.code()).isEqualTo("ID_EXHAUSTED");});
        assertThat(rows()).isEqualTo(before);assertThat(audits()).isEqualTo(history);
        assertThat(((Result)service.result(Long.parseLong(result.uploadId()))).status()).isEqualTo("CHECKED");
    }
    @Test void businessAuditFailureRollsBackDeletionInsertionAndUploadStatus() {
        Result first=apply(checked());Result next=checked();var before=rows();var history=audits();
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_financial_changes CHECK(NOT(trgt_tbl_nm='TB_FI_ITEM' AND chg_type_cd='DEL')) NOT VALID");
        assertThatThrownBy(()->apply(next)).isInstanceOf(RuntimeException.class);assertThat(rows()).isEqualTo(before);assertThat(audits()).isEqualTo(history);assertThat(((Result)service.result(Long.parseLong(next.uploadId()))).status()).isEqualTo("CHECKED");
    }
    @Test void metadataFailureRollsBackUploadAndIssues() {
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_financial_metadata CHECK(chg_rsn <> 'B2 문맥') NOT VALID");
        assertThatThrownBy(this::checked).isInstanceOf(RuntimeException.class);assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_upld WHERE form_type_cd='B2'",Integer.class)).isZero();assertThat(rows()).isEmpty();
    }
    @Test void selectedMonthCloseRejectsAndPreservesState() {
        var result=checked();sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('208007','CLOSE','admin')");var history=audits();assertThatThrownBy(()->apply(result)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("MONTH_CLOSED"));assertThat(rows()).isEmpty();assertThat(audits()).isEqualTo(history);
    }
    @Test void sameUploadReplayPreservesCommittedRowsStatusHistoryAndReceipt() {
        Result checked=checked();Result first=apply(checked);var before=rows();var history=audits();
        assertThat(first.status()).isEqualTo("APPLIED");assertThat(before).hasSize(1);
        assertThat(service.result(Long.parseLong(checked.uploadId()))).isEqualTo(first);
        assertThat(apply(checked)).isEqualTo(first);assertThat(rows()).isEqualTo(before);assertThat(audits()).isEqualTo(history);
        assertThat(service.result(Long.parseLong(checked.uploadId()))).isEqualTo(first);
    }
    @Test void uploadWithValidationErrorsRejectsAllRowsAndKeepsCheckedDiagnostics()throws Exception {
        manual("208007",1,"수동");manual("208006",1,"다른 월");
        file=FinancialWorkbook.file(List.of(List.of("판관비","","정상","1","","","N",""),List.of("판관비","","오류","-1","","","N","")));
        var before=rows();Result checked=checked();assertThat(checked.canApply()).isFalse();assertThat(checked.summary().errorRows()).isEqualTo(1);
        var businessHistory=sql.queryForList("SELECT * FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_FI_ITEM' ORDER BY hist_id");
        assertThatThrownBy(()->apply(checked)).isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.status()).isEqualTo(422);assertThat(e.code()).isEqualTo("REVALIDATION_FAILED");});
        assertThat(rows()).isEqualTo(before);assertThat(sql.queryForList("SELECT * FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_FI_ITEM' ORDER BY hist_id")).isEqualTo(businessHistory);
        assertThat(service.result(Long.parseLong(checked.uploadId()))).isEqualTo(checked);
    }
    @Test void insertionAuditFailureRollsBackReplacedRowsMetadataAndUploadStatus() {
        Result first=apply(checked());Result next=checked();var before=rows();var history=audits();
        var uploads=sql.queryForList("SELECT * FROM tb_sy_upld ORDER BY upld_id");var issues=sql.queryForList("SELECT * FROM tb_sy_upld_err ORDER BY upld_id,rslt_sn");
        sql.execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT reject_financial_insert CHECK(NOT(trgt_tbl_nm='TB_FI_ITEM' AND chg_type_cd='NEW')) NOT VALID");
        assertThatThrownBy(()->apply(next)).isInstanceOf(RuntimeException.class);
        assertThat(rows()).isEqualTo(before);assertThat(audits()).isEqualTo(history);assertThat(service.result(Long.parseLong(next.uploadId()))).isEqualTo(next);
        assertThat(sql.queryForList("SELECT * FROM tb_sy_upld ORDER BY upld_id")).isEqualTo(uploads);assertThat(sql.queryForList("SELECT * FROM tb_sy_upld_err ORDER BY upld_id,rslt_sn")).isEqualTo(issues);
        assertThat(service.result(Long.parseLong(first.uploadId()))).isEqualTo(first);
    }
    @Test void newCashMonthCloseRejectsAndPreservesCheckedUpload()throws Exception {
        file=FinancialWorkbook.file(List.of(List.of("판관비","","임대료","1","","2080-06-10","N","")));
        var result=checked();sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('208006','CLOSE','admin')");var history=audits();
        assertThatThrownBy(()->apply(result)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("MONTH_CLOSED"));
        assertThat(rows()).isEmpty();assertThat(audits()).isEqualTo(history);assertThat(service.result(Long.parseLong(result.uploadId()))).isEqualTo(result);
    }
    @Test void replacedCashMonthCloseRejectsAndPreservesOriginalReceipt()throws Exception {
        file=FinancialWorkbook.file(List.of(List.of("판관비","","임대료","1","","2080-06-10","N","")));Result first=apply(checked());
        file=FinancialWorkbook.valid();Result next=checked();var before=rows();var history=audits();
        sql.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('208006','CLOSE','admin')");
        assertThatThrownBy(()->apply(next)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("MONTH_CLOSED"));
        assertThat(rows()).isEqualTo(before);assertThat(audits()).isEqualTo(history);assertThat(service.result(Long.parseLong(next.uploadId()))).isEqualTo(next);assertThat(service.result(Long.parseLong(first.uploadId()))).isEqualTo(first);
    }
    @Test void concurrentSameUploadCommitsOnlyOnceAndBothReturnOriginalResult()throws Exception {
        Result checked=checked();CountDownLatch start=new CountDownLatch(1);
        var repository=org.mockito.Mockito.spy(context.getBean(FinancialImportRepository.class));var reads=new java.util.concurrent.atomic.AtomicInteger();CountDownLatch initialReads=new CountDownLatch(2);
        org.mockito.Mockito.doAnswer(call->{
            Object upload=call.callRealMethod();
            if(reads.incrementAndGet()<=2){initialReads.countDown();if(!initialReads.await(10,TimeUnit.SECONDS))throw new IllegalStateException("initial read timeout");}
            return upload;
        }).when(repository).required(Long.parseLong(checked.uploadId()),false);
        service=new FinancialImportService(context.getBean(FinancialImportMapper.class),repository,context.getBean(FinancialImportDefinition.class),context.getBean(WriteCoordinator.class),context.getBean(MasterLockMapper.class),context.getBean(PlatformTransactionManager.class));
        var a=CompletableFuture.supplyAsync(()->concurrent(start,checked));var b=CompletableFuture.supplyAsync(()->concurrent(start,checked));start.countDown();
        assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo(b.get(20,TimeUnit.SECONDS));assertThat(rows()).hasSize(1);assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_FI_ITEM' AND chg_item_nm='ITEM_NM' AND chg_type_cd='NEW'",Integer.class)).isEqualTo(1);
        assertThat(((Result)service.result(Long.parseLong(checked.uploadId()))).status()).isEqualTo("APPLIED");
    }
    Result concurrent(CountDownLatch start,Result result) { try { if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");return apply(result); }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);} }
}
