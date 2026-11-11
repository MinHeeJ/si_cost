package com.sicost.uploadsops;

import com.sicost.common.api.ApiException;
import com.sicost.common.context.*;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class UploadIssuesDatabaseTest {
    PostgreSqlDatabase db;
    AnnotationConfigApplicationContext context;
    UploadIssuesService service;
    long uploadId;
    @Configuration @EnableTransactionManagement static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);factory.setPlugins(new PersistenceBoundary());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        context.registerBean(Clock.class,()->Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        context.registerBean(UploadIssuesMapper.class,()->session.getMapper(UploadIssuesMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.registerBean(AccessLogMapper.class,()->session.getMapper(AccessLogMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,AccessLogWriter.class,ExcelExportService.class,UploadIssuesRepository.class,UploadIssuesService.class);
        context.refresh();service=context.getBean(UploadIssuesService.class);
        uploadId=db.sql.queryForObject("INSERT INTO tb_sy_upld(form_type_cd,scrn_id,file_nm,file_size,tot_row_cnt,succ_cnt,warn_cnt,err_cnt,upld_stat_cd,reg_user_id) VALUES('B7','UI-15','검사.xlsx',100,120,0,119,1,'CHECKED','admin') RETURNING upld_id",Long.class);
        issue(1,"WARN",1,"추가 열","","알 수 없는 열");
        issue(2,"ERR",2,"계좌번호","[비공개]","계좌번호 형식 오류");
        for(int i=3;i<=122;i++)issue(i,"WARN",i,"상호",i==3?"저장된 발췌…":"=SUM(1,1)","저장된 사유");
    }
    void issue(int sequence,String severity,int row,String column,String value,String reason) {
        db.sql.update("INSERT INTO tb_sy_upld_err(upld_id,rslt_sn,rslt_type_cd,row_no,col_nm,input_val,rsn_cn,reg_user_id) VALUES(?,?,?,?,?,?,?,'admin')",uploadId,sequence,severity,row,column,value,reason);
    }
    @AfterEach void close() { if(context!=null)context.close(); }
    Map<String,List<Map<String,Object>>> state() {
        var result=new TreeMap<String,List<Map<String,Object>>>();
        for(String table:List.of("tb_sy_upld","tb_sy_upld_err","tb_bs_clnt","tb_sy_chg_hist"))result.put(table,db.sql.queryForList("SELECT * FROM "+table));
        return result;
    }
    @Test void warnPageTwoDownloadIncludesEveryWarnFileWarningAndNoBusinessMutation() throws Exception {
        var before=state();var page=service.list(uploadId,"WARN",2,50);
        assertThat(page.total()).isEqualTo(121);assertThat(page.items()).hasSize(50).allMatch(i->i.severity().equals("WARN"));
        var bytes=new ByteArrayOutputStream();
        try(var download=service.download(uploadId,"WARN",new MockHttpServletRequest())) {download.transferTo(bytes);}
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
            var sheet=book.getSheetAt(0);assertThat(sheet.getLastRowNum()).isEqualTo(121);
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(1);
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("저장된 발췌…");
            assertThat(sheet.getRow(3).getCell(3).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(book.getSheetAt(1).getRow(5).getCell(1).getNumericCellValue()).isEqualTo(121);
            for(var row:sheet)if(row.getRowNum()>0)assertThat(row.getCell(0).getStringCellValue()).isEqualTo("WARN");
        }
        assertThat(state()).isEqualTo(before);
        assertThat(db.sql.queryForMap("SELECT user_id,act_type_cd,scrn_id FROM tb_sy_accs_log")).isEqualTo(Map.of("user_id","admin","act_type_cd","DNLD","scrn_id","UI-15"));
    }
    @Test void storedSequencePreservesPhysicalColumnOrderAndAccountIsAlwaysPrivate() {
        issue(123,"ERR",123,"계좌번호","불완전한 과거 진단","계좌 형식");
        var page=service.list(uploadId,null,1,20);
        assertThat(page.total()).isEqualTo(123);assertThat(page.items()).extracting(UploadIssuesDtos.Issue::sequence).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1,20).boxed().toList());
        var last=service.list(uploadId,"ERR",1,20);assertThat(last.items()).hasSize(2).allMatch(i->i.inputValue().equals("[비공개]"));
        assertThat(db.count("tb_sy_accs_log")).isZero();
    }
    Set<Path> spools() throws IOException {
        try(var files=Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {return files.filter(p->p.getFileName().toString().startsWith("sicost-export-")).collect(java.util.stream.Collectors.toSet());}
    }
    @Test void accessInsertFailureRollsBackAuditAndDeletesFileWhileBusinessRowsRemainUnchanged() throws Exception {
        db.sql.execute("ALTER TABLE tb_sy_accs_log ADD CONSTRAINT reject_download CHECK(act_type_cd <> 'DNLD')");
        var before=state();var files=spools();
        assertThatThrownBy(()->service.download(uploadId,null,new MockHttpServletRequest())).isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.status()).isEqualTo(500);assertThat(e.code()).isEqualTo("INTERNAL_ERROR");});
        assertThat(state()).isEqualTo(before);assertThat(db.count("tb_sy_accs_log")).isZero();assertThat(spools()).isEqualTo(files);
    }
}
