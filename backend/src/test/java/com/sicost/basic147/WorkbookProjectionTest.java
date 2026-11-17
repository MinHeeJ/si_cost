package com.sicost.basic147;

import com.sicost.common.history.AccessLogWriter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Actual authenticated HTTP downloads, real database audit, and byte-level workbook inspection. */
class WorkbookProjectionTest extends AuthenticationIntegrationTest {
    @Autowired java.time.Clock clock;
    @SpyBean AccessLogWriter downloadAudit;
    String developer,name,role;
    @AfterEach void removeOnlyOwnedBusinessFixture() {
        if(developer!=null) {sql.update("DELETE FROM tb_hr_dev_prc WHERE dev_id=? AND reg_user_id=?",developer,id);sql.update("DELETE FROM tb_hr_dev WHERE dev_id=? AND reg_user_id=?",developer,id);}
    }
    @Test void realDownloadContainsOnlySyntheticAllowedRowsAndCommittedActualActorMetadata() throws Exception {
        prepare();var session=authenticated();var result=download(session);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String xml=inspect(result.getResponse().getContentAsByteArray());
        assertThat(xml).contains(id,"합성 인증 사용자",name,"2026-11-17 12:00:00");
        assertThat(xml).doesNotContain("87654321","월 급여","mon_pay_amt","pwd_hash","mfa_code_hash");
        assertThat(downloadCount()).isEqualTo(1);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            var sheet=workbook.getSheetAt(0);assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).contains(name);
        }
    }
    @Test void salaryTemplateOmitsForbiddenHeadersEvenFromAuxiliarySheetsAndStrings() throws Exception {
        prepare();var response=mvc.perform(get("/api/excel/B1/template").session(authenticated())).andReturn();
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        String xml=inspect(response.getResponse().getContentAsByteArray());
        assertThat(xml).contains(id,"관리번호").doesNotContain("급여","기본급","보험","퇴직","실지급","실수령","소득세","원천세");
        assertThat(downloadCount()).isEqualTo(1);
    }
    @Test void anonymousOrRevokedDownloadIsJsonNotAnAttachmentAndWritesNoDownloadAudit() throws Exception {
        prepare();var anonymous=mvc.perform(get("/api/excel/B1/template")).andReturn();
        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);assertJsonNotWorkbook(anonymous);
        var session=authenticated();sql.update("UPDATE tb_sy_role_menu_auth SET dnld_yn='N' WHERE role_id=?",role);
        var denied=download(session);assertThat(denied.getResponse().getStatus()).isEqualTo(403);assertJsonNotWorkbook(denied);
        assertThat(downloadCount()).isZero();
    }
    @Test void requiredAuditFailureReleasesZeroWorkbookBytes() throws Exception {
        prepare();var session=authenticated();
        doThrow(new IllegalStateException("합성 다운로드 기록 장애")).when(downloadAudit).append(any(AccessLogWriter.Event.class),any());
        var result=download(session);assertThat(result.getResponse().getStatus()).isEqualTo(500);assertJsonNotWorkbook(result);
        assertThat(downloadCount()).isZero();
    }
    private void prepare() {
        var fixtures=new AuthenticationFixtures(sql,clock);role=fixtures.role("ALL",false,true);
        var account=fixtures.account(List.of("PM",role),"USE",1,false);id=account.id();password=account.password();
        sql.update("INSERT INTO tb_sy_role_menu_auth(role_id,menu_id,inq_yn,reg_yn,upd_yn,del_yn,upld_yn,dnld_yn,cnfm_yn,reg_user_id,reg_dttm) SELECT ?,menu_id,'Y','N','N','N','N','Y','N',?,CURRENT_TIMESTAMP FROM tb_sy_menu WHERE scrn_id='UI-02'",role,id);
        String digits=Long.toString(new java.security.SecureRandom().nextLong(100000000000L,999999999999L));developer="DEV-"+digits;name="합성파일"+digits;
        String part=sql.queryForObject("SELECT part_id FROM tb_bs_part WHERE use_yn='Y' AND hq_yn='N' ORDER BY part_id LIMIT 1",String.class);
        String job=sql.queryForObject("SELECT code FROM tb_sy_code WHERE code_grp_id='JOB' AND use_yn='Y' AND del_yn='N' ORDER BY sort_ord,code LIMIT 1",String.class);
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,jbps_cd,drct_cls_cd,part_id,jncmp_dt,work_stat_cd,use_yn,reg_user_id,reg_dttm) VALUES (?,?,?,'FT',?,'MEMBER','DRCT',?,'2026-01-01','IDLE','Y',?,CURRENT_TIMESTAMP)",developer,"ZIP"+digits,name,job,part,id);
        sql.update("INSERT INTO tb_hr_dev_prc(dev_id,aply_bgn_ym,mon_uprc,mon_pay_amt,reg_user_id,reg_dttm) VALUES (?,'202601',76543210,87654321,?,CURRENT_TIMESTAMP)",developer,id);
    }
    private MockHttpSession authenticated() throws Exception {
        var result=login(csrf(null),password);assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return (MockHttpSession)result.getRequest().getSession(false);
    }
    private MvcResult download(MockHttpSession session) throws Exception {
        var token=csrf(session);return mvc.perform(post("/api/exports/master-data").session(session).header(token.header(),token.token()).header("X-User-Id","SYSTEM").contentType("application/json").content(payload())).andReturn();
    }
    private byte[] payload() throws Exception {return json.writeValueAsBytes(Map.of("view","DEVELOPERS","filters",Map.of("q",name),"sort","name","direction","ASC","columns",List.of("name","monthlyRate"),"amountUnit","원"));}
    private long downloadCount() {return sql.queryForObject("SELECT count(*) FROM tb_sy_accs_log WHERE user_id=? AND act_type_cd='DNLD'",Long.class,id);}
    private void assertJsonNotWorkbook(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isBetween(400,599);
        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        assertThat(result.getResponse().getHeader("Content-Disposition")).isNull();
        byte[] bytes=result.getResponse().getContentAsByteArray();assertThat(bytes.length<2 || bytes[0]!='P' || bytes[1]!='K').isTrue();
        assertThat(json.readTree(bytes).path("success").asBoolean()).isFalse();
    }
    private String inspect(byte[] bytes) throws Exception {
        StringBuilder xml=new StringBuilder();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for(ZipEntry entry;(entry=zip.getNextEntry())!=null;) {
                assertThat(entry.getName()).doesNotContain("comments","vbaProject","externalLinks");
                if(entry.getName().endsWith(".xml") || entry.getName().endsWith(".rels"))xml.append(new String(zip.readAllBytes(),StandardCharsets.UTF_8));
            }
        }
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            for(int i=0;i<workbook.getNumberOfSheets();i++) {
                assertThat(workbook.getSheetVisibility(i)).isEqualTo(SheetVisibility.VISIBLE);
                for(Row row:workbook.getSheetAt(i))for(Cell cell:row) {assertThat(cell.getCellComment()).isNull();assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);}
            }
        }
        return xml.toString();
    }
}
