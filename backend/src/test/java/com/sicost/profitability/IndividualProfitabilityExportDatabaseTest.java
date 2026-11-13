package com.sicost.profitability;

import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.me.*;
import com.sicost.shared.SharedQueriesService;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import javax.sql.DataSource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runner-provided independent existing-data schema. No migrations, business inserts or seed replay.
 * Only test-owned DNLD rows are written/deleted; explicit second opt-in is required for those writes. */
@EnabledIfEnvironmentVariable(named="SICOST_COST_TEST_DB_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="SICOST_COST_TEST_AUDIT_WRITE_CONFIRMED",matches="true")
class IndividualProfitabilityExportDatabaseTest {
    CostDatabaseSupport db;
    Map<String,List<String>> before;
    final List<BigDecimal> ownedLogIds=new ArrayList<>();
    PlatformTransactionManager exportTransactions;
    IndividualProfitabilityService service;
    IndividualProfitabilityExportController controller;
    @BeforeEach void connect() throws Exception {
        db=new CostDatabaseSupport();before=db.snapshot();
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.context.getBean(DataSource.class));
        var config=new org.apache.ibatis.session.Configuration();config.setArgNameBasedConstructorAutoMapping(true);
        config.setMapUnderscoreToCamelCase(true);config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        config.addInterceptor(new com.sicost.common.persistence.PersistenceBoundary());factory.setConfiguration(config);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        db.context.registerBean(PreferenceMapper.class,() -> session.getMapper(PreferenceMapper.class));
        db.context.registerBean(PreferenceValidator.class);db.context.registerBean(CommitInvalidation.class);db.context.registerBean(PreferenceService.class);
        db.context.registerBean(EstimatedPayrollAdapter.class);db.context.registerBean(MonthlyCostCalculation.class);
        db.context.registerBean(TurnkeyRevenueCalculation.class);db.context.registerBean(MonthlyCostService.class);
        db.context.registerBean(IndividualProfitabilityCalculation.class);db.context.registerBean(IndividualProfitabilityService.class);
        service=db.context.getBean(IndividualProfitabilityService.class);
        var delegate=db.context.getBean(PlatformTransactionManager.class);
        // The shared fixture uses default read-only connections. Enable writing only for the separate audit transaction,
        // before its first SELECT. Source callbacks retain their real read-only REPEATABLE_READ transaction.
        exportTransactions=new PlatformTransactionManager() {
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                var status=delegate.getTransaction(definition);
                if(!definition.isReadOnly())db.sql.execute("SET TRANSACTION READ WRITE");
                return status;
            }
            public void commit(TransactionStatus status) {delegate.commit(status);}
            public void rollback(TransactionStatus status) {delegate.rollback(status);}
        };
        AccessLogMapper persistent=session.getMapper(AccessLogMapper.class);
        AccessLogMapper capture=entry -> {
            int count=persistent.insert(entry);
            ownedLogIds.add(db.sql.queryForObject("SELECT currval('sq_tb_sy_accs_log')",BigDecimal.class));
            return count;
        };
        var users=db.context.getBean(CurrentUserProvider.class);var clock=db.context.getBean(Clock.class);
        var excel=new ExcelExportService(users,new AccessLogWriter(capture,users,clock),clock,exportTransactions);
        var exports=new IndividualProfitabilityExportService(excel,service,db.context.getBean(PreferenceService.class),
            db.context.getBean(PreferenceValidator.class),db.context.getBean(SharedQueriesService.class),db.json);
        controller=new IndividualProfitabilityExportController(exports);
    }
    @AfterEach void cleanupOnlyOwnedAuditRowsAndCompareEverySourceValue() {
        if(db==null)return;
        try {
            if(exportTransactions!=null) new TransactionTemplate(exportTransactions).executeWithoutResult(status -> {
                for(var id:ownedLogIds) db.sql.update("DELETE FROM tb_sy_accs_log WHERE log_id=? AND scrn_id='UI-03' AND act_type_cd='DNLD'",id);
            });
            if(before!=null)assertThat(db.snapshot()).isEqualTo(before);
        } finally {db.close();}
    }
    @Test void realPostgresqlWorkbookPopulationsAndAuditColumnsMatchContractWithoutSourceWrites() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
        var query=new LinkedMultiValueMap<String,String>();query.add("show","LOSS");
        var input=IndividualProfitabilityExportInput.parse(query);
        var expectedSnapshot=service.snapshot(input.query());
        var selected=service.project(expectedSnapshot,input.query(),true);
        var excluded=service.project(expectedSnapshot,input.query().withTurnkey(false),true);
        var response=mvc.perform(get("/api/individual-profitability/export?show=LOSS&unit=원"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(IndividualProfitabilityExportContractTest.names(book.getSheetAt(0))).containsExactlyElementsOf(
                selected.items().stream().map(p -> p.name()+" ("+p.managementNo()+")").toList());
            assertThat(IndividualProfitabilityExportContractTest.names(book.getSheetAt(1))).containsExactlyElementsOf(
                excluded.items().stream().map(p -> p.name()+" ("+p.managementNo()+")").toList());
            numericParentsAndTotals(book.getSheetAt(0), selected);
            numericParentsAndTotals(book.getSheetAt(1), excluded);
            int expected=selected.items().stream().mapToInt(p -> service.getIndividualProfitabilityPerson(p.personId(),input.query()).months().stream()
                .mapToInt(m -> 1+m.rows().size()).sum()).sum();
            assertThat(book.getSheet("월별배분상세").getLastRowNum()).isEqualTo(expected);
        }
        assertThat(ownedLogIds).hasSize(1);
        var log=db.sql.queryForMap("SELECT log_id,user_id,act_type_cd,scrn_id,trgt_desc,client_ip,act_dttm,reg_user_id,reg_dttm FROM tb_sy_accs_log WHERE log_id=?",ownedLogIds.get(0));
        assertThat(log.get("act_type_cd")).isEqualTo("DNLD");assertThat(log.get("scrn_id")).isEqualTo("UI-03");
        assertThat(log.get("user_id")).isEqualTo(db.context.getBean(CurrentUserProvider.class).userId());
        assertThat(log.get("reg_user_id")).isEqualTo(log.get("user_id"));assertThat(log.get("reg_dttm")).isEqualTo(log.get("act_dttm"));
        assertThat(log.get("trgt_desc").toString()).contains("개인원가수익",Long.toString(selected.total()));
        assertThat(log.get("client_ip")).isNotNull();
        var after=db.snapshot();
        for(var entry:before.entrySet())if(!entry.getKey().equals("tb_sy_accs_log"))assertThat(after.get(entry.getKey())).isEqualTo(entry.getValue());
        assertThat(after.get("tb_sy_accs_log")).hasSize(before.get("tb_sy_accs_log").size()+1);
        // Assert the real PK, not a guessed download/file identifier.
        assertThat(db.sql.queryForList("SELECT a.attname FROM pg_index i JOIN pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=ANY(i.indkey) WHERE i.indrelid='tb_sy_accs_log'::regclass AND i.indisprimary",String.class)).containsExactly("log_id");
    }
    private void numericParentsAndTotals(org.apache.poi.ss.usermodel.Sheet sheet, MonthlyCostDtos.IndividualProfitabilityData data) {
        var fields = Map.of("개인 단가", "revenue", "원가합계", "totalCost", "영업이익", "profit");
        for (var field : fields.entrySet()) {
            int column = -1;
            for (var cell : sheet.getRow(0)) if (cell.getStringCellValue().startsWith(field.getKey()+" (")) column = cell.getColumnIndex();
            assertThat(column).as("필수 금액열: " + field.getKey()).isGreaterThanOrEqualTo(0);
            for (int row = 0; row < data.items().size(); row++) {
                var expected = db.json.valueToTree(data.items().get(row).metrics()).get(field.getValue());
                numericOrBlank(sheet.getRow(row+1).getCell(column), expected);
            }
            if (!data.items().isEmpty()) numericOrBlank(sheet.getRow(data.items().size()+1).getCell(column),
                db.json.valueToTree(data.totals()).get(field.getValue()));
        }
    }
    private static void numericOrBlank(org.apache.poi.ss.usermodel.Cell cell, com.fasterxml.jackson.databind.JsonNode expected) {
        if (expected.isNull()) {
            assertThat(cell == null || cell.getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK).isTrue();
        } else {
            assertThat(cell).isNotNull();
            assertThat(cell.getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.NUMERIC);
            assertThat(cell.getNumericCellValue()).isEqualTo(new BigDecimal(expected.asText()).doubleValue());
        }
    }
}
