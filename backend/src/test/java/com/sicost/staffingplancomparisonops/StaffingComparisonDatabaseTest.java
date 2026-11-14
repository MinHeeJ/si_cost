package com.sicost.staffingplancomparisonops;

import com.sicost.common.api.*;
import com.sicost.common.assignments.*;
import com.sicost.common.context.*;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class StaffingComparisonDatabaseTest {
    PostgreSqlDatabase db;
    MockMvc mvc;
    final Clock clock=Clock.fixed(Instant.parse("2026-06-30T15:00:00Z"),ZoneId.of("Asia/Seoul"));
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var config=new org.apache.ibatis.session.Configuration();config.setArgNameBasedConstructorAutoMapping(true);config.setMapUnderscoreToCamelCase(true);config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        factory.setConfiguration(config);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        var users=new CurrentUserProvider(session.getMapper(CurrentUserMapper.class));
        var actor=users.userId();
        var transactions=new DataSourceTransactionManager(db.source);
        var monthly=new AssignmentMonthlyService(session.getMapper(AssignmentMonthlyMapper.class),users,new ChangeHistoryWriter(session.getMapper(ChangeHistoryMapper.class),users,clock,new com.fasterxml.jackson.databind.ObjectMapper()),clock);
        var repository=new StaffingComparisonRepository(session.getMapper(StaffingComparisonMapper.class),monthly);
        var excel=new ExcelExportService(users,new AccessLogWriter(session.getMapper(AccessLogMapper.class),users,clock),clock,transactions);
        mvc=MockMvcBuilders.standaloneSetup(new StaffingComparisonExportController(new StaffingComparisonExportService(repository,excel,clock))).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
        String part=db.sql.queryForObject("SELECT part_id FROM tb_bs_part WHERE use_yn='Y' ORDER BY part_id LIMIT 1",String.class);
        db.sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES('D-export','M-export','다운로드 검증','FT','DEV','DRCT',?,'WORK',?)",part,actor);
        db.sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES('P-export','=SUM(A1)','MO','ING','2025-07-01','2026-06-30',100,0,0,?)",actor);
        db.sql.update("INSERT INTO tb_pj_mnpw_plan(proj_id,job_cd,plan_mm,src_cd,reg_user_id) VALUES('P-export','DEV',12,'MANUAL',?)",actor);
        db.sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES('A-export','P-export','D-export','DEV','2026-01-01','2026-07-31',1,'NEW',0,'ACT',?)",actor);
    }
    @AfterEach void cleanup() {
        if(db==null)return;
        String schema=db.sql.queryForObject("SELECT current_schema()",String.class);
        if(schema!=null && schema.matches("sicost_test_[0-9a-f]+"))db.sql.execute("DROP SCHEMA "+schema+" CASCADE");
    }
    Map<String,List<Map<String,Object>>> snapshot() {
        var result=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:List.of("tb_pj_proj","tb_pj_mnpw_plan","tb_pj_asgn","tb_pj_asgn_mon","tb_hr_mon_pay","tb_co_mon_cost","tb_cl_close","tb_sy_chg_hist","tb_sl_opp","tb_sl_opp_need","tb_sl_tent_asgn","tb_sl_aprv_hist"))
            result.put(table,db.sql.queryForList("SELECT * FROM "+table));
        return result;
    }
    @Test void realPostgresqlMaterializationAnnualCalculationAndCommittedDownloadOnly() throws Exception {
        var before=snapshot();long logs=db.count("tb_sy_accs_log");
        var response=mvc.perform(get("/api/staffing-plan-comparison/export?year=2026&projectId=P-export")).andExpect(status().isOk()).andExpect(header().exists("Content-Disposition")).andReturn().getResponse();
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var row=book.getSheetAt(0).getRow(1);assertThat(row.getCell(0).getStringCellValue()).isEqualTo("P-export");assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(6);assertThat(row.getCell(4).getNumericCellValue()).isEqualTo(7);assertThat(row.getCell(5).getNumericCellValue()).isEqualTo(1);
        }
        assertThat(snapshot()).isEqualTo(before);assertThat(db.count("tb_sy_accs_log")).isEqualTo(logs+1);
        var log=db.sql.queryForMap("SELECT * FROM tb_sy_accs_log WHERE scrn_id='UI-11'");assertThat(log.get("act_type_cd")).isEqualTo("DNLD");assertThat(log.get("reg_user_id")).isEqualTo(log.get("user_id"));
    }
    @Test void actualAuditConstraintFailureRollsBackAndDoesNotTransmit() throws Exception {
        db.sql.execute("ALTER TABLE tb_sy_accs_log ADD CONSTRAINT reject_comparison_download CHECK(scrn_id<>'UI-11')");
        var before=snapshot();long count=db.count("tb_sy_accs_log");
        mvc.perform(get("/api/staffing-plan-comparison/export?year=2026&projectId=P-export")).andExpect(status().isInternalServerError()).andExpect(header().doesNotExist("Content-Disposition")).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        assertThat(snapshot()).isEqualTo(before);assertThat(db.count("tb_sy_accs_log")).isEqualTo(count);
    }
}
