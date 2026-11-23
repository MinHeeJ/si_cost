package com.sicost.exportsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.excel.ExportTestLookups;
import com.sicost.common.history.AccessLogMapper;
import com.sicost.common.history.AccessLogWriter;
import com.sicost.developers.DeveloperQueryService;
import com.sicost.candidates.CandidateQueryService;
import com.sicost.shared.SharedQueriesMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PeopleExportServiceTest {
    @Test void selfCompanyAndOutsourcingCompanyAreExportedFromTheListProjection() throws Exception {
        var json=new ObjectMapper();
        var developers=mock(DeveloperQueryService.class);
        var candidates=mock(CandidateQueryService.class);
        var codes=mock(SharedQueriesMapper.class);
        when(codes.codes("JOB",true)).thenReturn(List.of(new com.sicost.shared.SharedQueryDtos.CodeLookup("BE","백엔드",null,null,null,null,null)));
        var users=mock(CurrentUserProvider.class);
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("actor","처리자",List.of("ADMIN"),true));
        when(users.userId()).thenReturn("actor");
        var audit=mock(AccessLogMapper.class); when(audit.insert(any())).thenReturn(1);
        var tx=mock(PlatformTransactionManager.class); when(tx.getTransaction(any())).thenAnswer(c -> new SimpleTransactionStatus());
        var clock=Clock.fixed(Instant.parse("2026-11-20T00:00:00Z"),ZoneId.of("Asia/Seoul"));
        var excel=new ExcelExportService(users,new AccessLogWriter(audit,users,clock),clock,tx);
        ExportTestLookups.configure(excel);
        var service=new PeopleExportService(developers,candidates,codes,excel);
        when(developers.list(any())).thenReturn(json.readTree("{\"items\":[{\"name\":\"개발자\",\"companyName\":null}],\"total\":1}"));
        when(candidates.list(any())).thenReturn(json.readTree("{\"items\":[{\"name\":\"후보\",\"companyName\":\"협력 업체\",\"employeeType\":\"OUT\"}],\"total\":1}"));
        for(String view:List.of("DEVELOPERS","CANDIDATES")) {
            String columns=view.equals("DEVELOPERS")?"[\"name\",\"companyName\"]":"[\"name\",\"employeeType\"]";
            var body=json.readTree("{\"view\":\""+view+"\",\"filters\":{\"jobCode\":\"BE\"},\"columns\":"+columns+",\"amountUnit\":\"원\"}");
            try(var download=service.create(body,new MockHttpServletRequest())) {
                var output=new ByteArrayOutputStream();download.transferTo(output);
                try(var book=new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
                    var sheet=book.getSheetAt(0);
                    int company=view.equals("DEVELOPERS")?1:2;
                    assertThat(sheet.getRow(0).getCell(company).getStringCellValue()).isEqualTo("소속");
                    assertThat(sheet.getRow(1).getCell(company).getStringCellValue()).isEqualTo(view.equals("DEVELOPERS")?"자사":"협력 업체");
                    assertThat(book.getSheet("다운로드정보").getRow(3).getCell(1).getStringCellValue()).contains("직무=백엔드").doesNotContain("jobCode", "BE");
                }
            }
        }
        verify(audit,times(2)).insert(any());
    }
}
