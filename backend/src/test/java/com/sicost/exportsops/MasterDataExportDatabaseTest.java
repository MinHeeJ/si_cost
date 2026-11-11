package com.sicost.exportsops;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.*;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.common.security.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class MasterDataExportDatabaseTest {
    PostgreSqlDatabase db;
    MasterDataExportService service;
    MasterDataExportRepository repository;
    AccountCipher accounts;
    final Clock clock=Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul"));
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase(); db.install();
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean(); factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();
        configuration.setArgNameBasedConstructorAutoMapping(true); configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration); factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        var users=new CurrentUserProvider(session.getMapper(CurrentUserMapper.class));
        var logs=new AccessLogWriter(session.getMapper(AccessLogMapper.class),users,clock);
        var transactions=new DataSourceTransactionManager(db.source);
        accounts=new AccountCipher(session.getMapper(AccountKeyMapper.class));
        repository=new MasterDataExportRepository(session.getMapper(MasterDataExportMapper.class));
        service=new MasterDataExportService(repository,new ExcelExportService(users,logs,clock,transactions),accounts,clock);
    }
    MasterDataExportInput.Export request(String view,Map<String,Object> filters,List<String> columns) {
        return MasterDataExportInput.parse(JsonMapper.builder().findAndAddModules().build().valueToTree(Map.of("view",view,"filters",filters,"columns",columns)));
    }
    XSSFWorkbook workbook(MasterDataExportInput.Export input) throws Exception {
        try(var file=service.create(input,new MockHttpServletRequest())) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(); file.transferTo(bytes);
            return new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()));
        }
    }
    void client(int index,String type,String status,String cipher) {
        db.sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,use_yn,bank_cd,acct_no_enc,reg_user_id) VALUES(?,?,?,?,?,'004',?,'admin')",
            "C-"+String.format("%03d",index),type,"내보내기"+String.format("%03d",index),String.format("700-00-%05d",index),status,cipher);
    }
    @Test void actualCursorExports51FilteredRowsMaskedAndOneCommittedAuditWithoutChangingBusinessOrHistory() throws Exception {
        String encrypted=accounts.encrypt("0012-3456-7890");
        for(int i=1;i<=51;i++) client(i,i%2==0?"BOTH":"VNDR","Y",encrypted);
        client(52,"CUST","Y",null); client(53,"VNDR","N",null);
        var before=db.sql.queryForList("SELECT * FROM tb_bs_clnt ORDER BY clnt_id");
        try(var book=workbook(request("CLIENTS",Map.of("type","VNDR","useYn","Y","q","내보내기"),List.of("name","account")))) {
            assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(51);
            assertThat(book.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()).endsWith("****7890").doesNotContain("0012-3456");
            assertThat(book.getSheetAt(1).getRow(5).getCell(1).getNumericCellValue()).isEqualTo(51);
        }
        assertThat(db.sql.queryForList("SELECT * FROM tb_bs_clnt ORDER BY clnt_id")).isEqualTo(before);
        assertThat(db.count("tb_sy_chg_hist")).isZero(); assertThat(db.count("tb_sy_accs_log")).isEqualTo(1);
        assertThat(db.sql.queryForMap("SELECT user_id,act_type_cd,scrn_id FROM tb_sy_accs_log"))
            .isEqualTo(Map.of("user_id","admin","act_type_cd","DNLD","scrn_id","UI-15"));
    }
    @Test void realPartProjectionExcludesHqPreservesStoppedAndUsesCurrentNotFuture() throws Exception {
        db.sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES('PART-901','내보내기파트','N','N','admin')");
        db.sql.update("INSERT INTO tb_bs_part_std(part_id,aply_bgn_ym,aply_end_ym,part_ldr_user_id,prft_dstrb_rt,sales_exps_amt,reg_user_id) VALUES('PART-901','202601','202606','admin',40.25,1001,'admin'),('PART-901','202607',NULL,'admin',50,2002,'admin')");
        try(var book=workbook(request("PARTS",Map.of("q","내보내기"),MasterDataExportInput.PART_COLUMNS))) {
            var row=book.getSheetAt(0).getRow(1);
            assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(1.001);
            assertThat(row.getCell(4).getStringCellValue()).isEqualTo("2026-01");
            assertThat(row.getCell(5).getStringCellValue()).contains("2026-07","2.002");
            assertThat(row.getCell(8).getStringCellValue()).isEqualTo("중지");
        }
        try(var book=workbook(request("PARTS",Map.of("q","내보내기","useYn","Y"),MasterDataExportInput.PART_COLUMNS))) {assertThat(book.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(1);}
        assertThat(db.count("tb_sy_chg_hist")).isZero();
    }
    @Test void statusOmissionAndTypeUnionAndLiteralSearchMatchFullResult() throws Exception {
        client(1,"CUST","Y",null); client(2,"BOTH","N",null); client(3,"VNDR","Y",null);
        try(var book=workbook(request("CLIENTS",Map.of("type","CUST","q","700-00"),List.of("name","businessNo")))) {assertThat(book.getSheetAt(0).getLastRowNum()).isEqualTo(2);}
        try(var book=workbook(request("CLIENTS",Map.of("q","%"),List.of("name")))) {assertThat(book.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(1);}
    }
    Set<Path> spools() throws Exception {
        try(var paths=Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {return paths.filter(p -> p.getFileName().toString().startsWith("sicost-export-")).collect(java.util.stream.Collectors.toSet());}
    }
    @Test void realAccessLogConstraintFailureRollsBackLogAndDeletesFileBeforeDelivery() throws Exception {
        client(1,"CUST","Y",null); var before=db.sql.queryForList("SELECT * FROM tb_bs_clnt"); var files=spools();
        db.sql.execute("ALTER TABLE tb_sy_accs_log ADD CONSTRAINT reject_export CHECK(act_type_cd <> 'DNLD')");
        assertThatThrownBy(() -> service.create(request("CLIENTS",Map.of(),List.of("name")),new MockHttpServletRequest()))
            .isInstanceOfSatisfying(ApiException.class,e -> {assertThat(e.status()).isEqualTo(500);assertThat(e.code()).isEqualTo("INTERNAL_ERROR");});
        assertThat(spools()).isEqualTo(files); assertThat(db.count("tb_sy_accs_log")).isZero(); assertThat(db.count("tb_sy_chg_hist")).isZero();
        assertThat(db.sql.queryForList("SELECT * FROM tb_bs_clnt")).isEqualTo(before);
    }
}
