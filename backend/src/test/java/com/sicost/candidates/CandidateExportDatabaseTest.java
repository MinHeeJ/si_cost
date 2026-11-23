package com.sicost.candidates;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.AccessLogMapper;
import com.sicost.common.history.AccessLogWriter;
import com.sicost.developers.DeveloperQueryService;
import com.sicost.exportsops.PeopleExportService;
import com.sicost.shared.SharedQueriesMapper;
import java.io.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Real SQL filters, server pagination and the export's 500-row chunk boundary. */
@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL", matches=".+")
class CandidateExportDatabaseTest {
    private final CandidateDatabaseTest fixture = new CandidateDatabaseTest();
    private PeopleExportService exports;
    private String job;
    @BeforeEach void setup() throws Exception {
        fixture.install();
        // Reuse the real query transaction and mapper configuration from candidate CRUD tests.
        var factory = new org.mybatis.spring.SqlSessionFactoryBean();
        var source = fixture.context.getBean(javax.sql.DataSource.class);
        factory.setDataSource(source);
        factory.setMapperLocations(new org.springframework.core.io.support.PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var config = new org.apache.ibatis.session.Configuration();
        config.setArgNameBasedConstructorAutoMapping(true); config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        var users = fixture.context.getBean(com.sicost.common.context.CurrentUserProvider.class);
        var clock = fixture.context.getBean(java.time.Clock.class);
        var excel = new ExcelExportService(users, new AccessLogWriter(session.getMapper(AccessLogMapper.class), users, clock), clock,
            fixture.context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        excel.setMenuQueries(session.getMapper(SharedQueriesMapper.class));
        exports = new PeopleExportService(mock(DeveloperQueryService.class), fixture.queries, session.getMapper(SharedQueriesMapper.class), excel);
        job = fixture.body().get("jobCode").toString();
    }
    @AfterEach void close() { fixture.close(); }
    private void seed(int count) {
        fixture.sql.update("""
            INSERT INTO tb_hr_cand(cand_id,cand_nm,emp_type_cd,job_cd,grade_cd,hope_uprc,input_psbl_dt,cntc_stat_cd,last_cntc_dt,cand_src_cd,reg_user_id)
            SELECT 'CAND-'||lpad(i::text,6,'0'), '후보'||lpad(i::text,6,'0'), 'FL', ?, 'MD',
              CASE i%3 WHEN 0 THEN 2 WHEN 1 THEN 10 ELSE 100 END,
              CASE i%3 WHEN 0 THEN DATE '2026-07-31' WHEN 1 THEN DATE '2026-06-01' ELSE DATE '2026-08-01' END,
              CASE i%5 WHEN 0 THEN 'HOLD' WHEN 1 THEN 'CTRT' WHEN 2 THEN 'DONE' ELSE 'BEFORE' END,
              CASE WHEN i%4=0 THEN NULL ELSE DATE '2026-06-15' END, 'SITE', 'admin'
            FROM generate_series(1,?) i
            """, job, count);
    }
    private List<String> screen(Map<String,String> conditions) {
        var rows = new ArrayList<String>();
        for (int page=1;;page++) {
            var q = new HashMap<>(conditions); q.put("page",Integer.toString(page)); q.put("size","20");
            JsonNode result = fixture.queries.list(CandidateInput.query(q));
            result.get("items").forEach(r -> rows.add(r.get("name").asText()));
            if (rows.size() >= result.get("total").asInt()) return rows;
        }
    }
    private List<String> download(Map<String,String> conditions) throws Exception {
        var filters = new HashMap<>(conditions); String sort=filters.remove("sort"), direction=filters.remove("direction");
        var body = new HashMap<String,Object>(); body.put("view","CANDIDATES"); body.put("filters",filters); body.put("columns",List.of("name","desiredRate","lastContactDate"));
        if(sort!=null)body.put("sort",sort);if(direction!=null)body.put("direction",direction);
        try(var file=exports.create(fixture.json.valueToTree(body),new MockHttpServletRequest())) {
            var bytes=new ByteArrayOutputStream(); file.transferTo(bytes);
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
                var sheet=book.getSheetAt(0);var names=new ArrayList<String>();
                for(int i=1;i<=sheet.getLastRowNum();i++)names.add(sheet.getRow(i).getCell(0).getStringCellValue());
                return names;
            }
        }
    }
    @Test void optionalFiltersKeepTheWholeScreenAndWorkbookPopulationIdentical() throws Exception {
        seed(55);
        var before=fixture.sql.queryForList("SELECT * FROM tb_hr_cand ORDER BY cand_id");
        var cases=List.of(Map.<String,String>of(), Map.of("jobCode",job),
            Map.of("jobCode",job,"availableUntil","2026-07-31"),
            Map.of("jobCode",job,"availableUntil","2026-07-31","activeOnly","true"),
            Map.of("activeOnly","false"), Map.of("activeOnly","true","contactStatus","HOLD"),
            Map.of("activeOnly","true","contactStatus","CTRT"), Map.of("activeOnly","true","contactStatus","DONE"),
            Map.of("jobCode",job,"availableUntil","2025-01-01"));
        for(var filters:cases)assertThat(download(filters)).as("filters %s",filters).containsExactlyElementsOf(screen(filters));
        assertThat(screen(Map.of())).hasSize(55);
        assertThat(screen(Map.of("jobCode",job,"availableUntil","2026-07-31"))).hasSize(37);
        assertThat(screen(Map.of("jobCode",job,"availableUntil","2026-07-31","activeOnly","true"))).hasSize(22);
        assertThat(screen(Map.of("activeOnly","false"))).containsExactlyElementsOf(screen(Map.of()));
        assertThat(screen(Map.of("activeOnly","true","contactStatus","HOLD"))).isEmpty();
        assertThat(screen(Map.of("activeOnly","true","contactStatus","CTRT"))).isEmpty();
        assertThat(screen(Map.of("activeOnly","true","contactStatus","DONE"))).hasSize(11);
        assertThat(fixture.sql.queryForList("SELECT * FROM tb_hr_cand ORDER BY cand_id")).isEqualTo(before);
        assertThat(fixture.sql.queryForObject("SELECT count(*) FROM tb_sy_chg_hist",Long.class)).isZero();
    }
    @Test void everyCandidateSortDirectionUsesNumericOrderNullsLastAndPkTiesAcrossPagesAndExport() throws Exception {
        seed(55);
        for(String sort:CandidateInput.SORTS)for(String direction:List.of("ASC","DESC")) {
            var filters=Map.of("sort",sort,"direction",direction,"activeOnly","true","availableUntil","2026-07-31");
            var names=screen(filters);
            assertThat(download(filters)).as("%s %s",sort,direction).containsExactlyElementsOf(names);
            String column=switch(sort) {
                case "name" -> "cand_nm";
                case "desiredRate" -> "hope_uprc";
                case "availableDate" -> "input_psbl_dt";
                case "lastContactDate" -> "last_cntc_dt";
                case "contactStatus" -> "(SELECT code_nm FROM tb_sy_code WHERE code_grp_id='CNTC_STAT' AND code=cntc_stat_cd)";
                default -> null; // Fixture employee/job/grade/source labels are equal: PK alone determines order.
            };
            String order=column==null?"cand_id":column+" "+direction+" NULLS LAST,cand_id";
            var expected=fixture.sql.queryForList("SELECT cand_nm FROM tb_hr_cand WHERE cntc_stat_cd NOT IN ('HOLD','CTRT') AND input_psbl_dt<=DATE '2026-07-31' ORDER BY "+order,String.class);
            assertThat(names).as("specified %s %s order, not just list/export agreement",sort,direction).containsExactlyElementsOf(expected);
        }
    }
    @Test void exportReadsPastItsFiveHundredRowChunkUsingTheSameGlobalOrdering() throws Exception {
        seed(857);
        var filters=Map.of("jobCode",job,"sort","desiredRate","direction","DESC");
        assertThat(download(filters)).hasSize(857).containsExactlyElementsOf(screen(filters));
        var active=Map.of("jobCode",job,"sort","desiredRate","direction","ASC","activeOnly","true");
        assertThat(download(active)).containsExactlyElementsOf(screen(active));
    }
}
