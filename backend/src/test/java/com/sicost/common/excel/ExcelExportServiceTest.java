package com.sicost.common.excel;

import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.AccessLogWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sicost.common.excel.ExcelExportService.*;

class ExcelExportServiceTest {
    final CurrentUserProvider users=mock(CurrentUserProvider.class);
    final AccessLogWriter logs=mock(AccessLogWriter.class);
    final PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
    final ExcelExportService service=new ExcelExportService(users,logs,Clock.fixed(Instant.parse("2026-01-01T23:59:00Z"),ZoneId.of("Asia/Seoul")),transactions);
    final MockHttpServletRequest request=new MockHttpServletRequest();
    @BeforeEach void setup() {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        when(transactions.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
    }
    Specification specification(List<Column> columns) { return new Specification("거래처",null,columns,"전체","천원"); }
    @Test void textNeverBecomesFormulaAndNumbersKeepTheirCellTypesAndInfoIsLast() throws Exception {
        var specification=specification(List.of(new Column("name","상호",CellType.TEXT),new Column("amount","관리비(천원)",CellType.NUMBER),new Column("account","계좌",CellType.MASKED_ACCOUNT)));
        var download=service.generate(specification,sink->{for(String text:List.of("=SUM(1,1)","+1","-1","@x"))sink.accept(List.of(text,new BigDecimal("1234.567"),"은행 ****1234"));},request);
        verify(logs).append(argThat(event->event.action().equals("DNLD") && event.count()==4),same(request));
        assertThat(download.filename()).isEqualTo("거래처_20260102_085900.xlsx");
        ByteArrayOutputStream output=new ByteArrayOutputStream();download.transferTo(output);
        assertThatThrownBy(download::size).isInstanceOf(IOException.class);
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(workbook.getSheetName(workbook.getNumberOfSheets()-1)).isEqualTo("다운로드정보");
            for(int row=1;row<=4;row++) {
                assertThat(workbook.getSheetAt(0).getRow(row).getCell(0).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
                assertThat(workbook.getSheetAt(0).getRow(row).getCell(1).getNumericCellValue()).isEqualTo(1234.567);
                assertThat(workbook.getSheetAt(0).getRow(row).getCell(2).getStringCellValue()).isEqualTo("은행 ****1234");
            }
            assertThat(workbook.getSheetAt(1).getRow(0).getCell(1).getStringCellValue()).isEqualTo("admin");
            assertThat(workbook.getSheetAt(1).getRow(5).getCell(1).getNumericCellValue()).isEqualTo(4);
        }
    }
    @Test void peopleExportsUseTheirScreenKindFilenameAndInformationSheet() throws Exception {
        for (String screen : List.of("UI-02","UI-33")) {
            String kind = screen.equals("UI-02") ? "개발자" : "외부인력후보";
            var spec = new Specification(kind,null,List.of(new Column("name","이름",CellType.TEXT)),"전체","원",screen);
            var download = service.generate(spec,sink -> sink.accept(List.of("=untrusted")),request);
            verify(logs).append(argThat(event -> event.screenId().equals(screen) && event.fileType().equals(kind) && event.action().equals("DNLD") && event.count()==1),same(request));
            assertThat(download.filename()).isEqualTo((screen.equals("UI-02")?"개발자목록":kind)+"_20260102_085900.xlsx");
            var output = new ByteArrayOutputStream();download.transferTo(output);
            try (var book = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
                assertThat(book.getSheetAt(0).getRow(1).getCell(0).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
                assertThat(book.getSheet("다운로드정보").getRow(4).getCell(1).getStringCellValue()).isEqualTo("원");
            }
        }
    }
    @Test void b8TemplateUsesExplicitSpecificationAndLeavesLegacyTemplateOnUi15() throws Exception {
        var columns = List.of(new Column("name","이름",CellType.TEXT));
        try (var download = service.generateTemplate(new Specification("개발자_등록양식",null,columns,"B8","원","UI-02"),List.of("입력 안내"),request)) {
            assertThat(download.filename()).startsWith("B8_개발자양식_");
            var bytes = new ByteArrayOutputStream();download.transferTo(bytes);
            try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
                assertThat(book.getSheet("입력안내").getRow(0).getCell(0).getStringCellValue()).isEqualTo("입력 안내");
                assertThat(book.getSheetAt(0).getColumnStyle(0).getDataFormatString()).isEqualTo("@");
            }
        }
        try (var download = service.generateTemplate(columns,List.of("기존 안내"),request)) { assertThat(download.filename()).startsWith("거래처_등록양식_"); }
        verify(logs).append(argThat(event -> event.screenId().equals("UI-02") && event.fileType().equals("개발자_등록양식")),same(request));
        verify(logs).append(argThat(event -> event.screenId().equals("UI-15") && event.fileType().equals("거래처_등록양식")),same(request));
    }
    @Test void b8IssuesRequireUploadIdAndPreserveScreenAndFilename() throws Exception {
        var columns = List.of(new Column("reason","사유",CellType.TEXT));
        try (var download = service.generate(new Specification("개발자_업로드검사","12",columns,"검사","원","UI-02"),sink -> sink.accept(List.of("확인")),request)) {
            assertThat(download.filename()).isEqualTo("B8_검사_12_20260102_085900.xlsx");
        }
        verify(logs).append(argThat(event -> event.screenId().equals("UI-02") && event.targetId().equals("12")),same(request));
        assertThatThrownBy(() -> service.generate(new Specification("개발자_업로드검사",null,columns,"검사","원","UI-02"),sink -> {},request)).isInstanceOf(ApiException.class);
    }
    @Test void mismatchedScreenKindAndInjectedUploadIdAreRejectedBeforeWork() {
        var columns = List.of(new Column("name","이름",CellType.TEXT));
        for (Specification spec : List.of(
                new Specification("개발자",null,columns,"전체","원","UI-33"),
                new Specification("외부인력후보",null,columns,"전체","원","UI-15"),
                new Specification("개발자_업로드검사","1\r\nunsafe",columns,"전체","원","UI-02"),
                new Specification("개발자","1",columns,"전체","원","UI-02"))) {
            assertThatThrownBy(() -> service.generate(spec,sink -> {throw new AssertionError();},request)).isInstanceOf(ApiException.class);
        }
        verifyNoInteractions(users,logs,transactions);
    }
    @Test void peopleAuditFailureAndCancelledTransferNeverLeaveDownload() throws Exception {
        var spec = new Specification("개발자",null,List.of(new Column("name","이름",CellType.TEXT)),"전체","원","UI-02");
        var download = service.generate(spec,sink -> sink.accept(List.of("이름")),request);
        var broken = new java.io.OutputStream(){public void write(int value)throws IOException{throw new IOException("disconnected");}};
        assertThatThrownBy(() -> download.transferTo(broken)).isInstanceOf(IOException.class);
        assertThatThrownBy(download::size).isInstanceOf(IOException.class);
        doThrow(new IllegalStateException("private details")).when(logs).append(any(),any());
        assertThatThrownBy(() -> service.generate(spec,sink -> sink.accept(List.of("이름")),request)).isInstanceOfSatisfying(ApiException.class,e -> {assertThat(e.status()).isEqualTo(500);assertThat(e.getMessage()).doesNotContain("private details");});
    }
    @Test void fullAccountColumnIsRejectedBeforeGenerationOrAudit() {
        assertThatThrownBy(()->service.generate(specification(List.of(new Column("accountNumber","전체계좌",CellType.TEXT))),sink->{throw new AssertionError();},request))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(400));
        verifyNoInteractions(users,logs,transactions);
    }
    @Test void unmaskedAccountValueIsRejected() {
        assertThatThrownBy(()->service.generate(specification(List.of(new Column("account","계좌",CellType.MASKED_ACCOUNT))),sink->sink.accept(List.of("unmasked")),request)).isInstanceOf(ApiException.class);
        verifyNoInteractions(logs);
    }
    @Test void accessLogFailureNeverReturnsDownloadAndRollsBackAudit() {
        doThrow(new IllegalStateException("sensitive database detail")).when(logs).append(any(),any());
        assertThatThrownBy(()->service.generate(specification(List.of(new Column("name","상호",CellType.TEXT))),sink->sink.accept(List.of("이름")),request))
            .isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.status()).isEqualTo(500);assertThat(e.getMessage()).doesNotContain("sensitive database detail");});
        verify(transactions).rollback(any());
    }
    @Test void generationFailureNeverAudits() {
        assertThatThrownBy(()->service.generate(specification(List.of(new Column("name","상호",CellType.TEXT))),sink->{throw new IllegalStateException();},request)).isInstanceOf(ApiException.class);
        verifyNoInteractions(logs);
    }
    @Test void cancelledConnectionDeletesSpool() throws Exception {
        var download=service.generate(specification(List.of(new Column("name","상호",CellType.TEXT))),sink->sink.accept(List.of("이름")),request);
        var broken=new java.io.OutputStream(){public void write(int value)throws IOException{throw new IOException("disconnected");}};
        assertThatThrownBy(()->download.transferTo(broken)).isInstanceOf(IOException.class);
        assertThatThrownBy(download::size).isInstanceOf(IOException.class);
    }
    @Test void sheetRolloverRetainsHeaderAndOneRemainingRow() throws Exception {
        SXSSFWorkbook workbook=new SXSSFWorkbook(100);
        try(workbook) {
            SheetRows writer=new SheetRows(workbook,List.of(new Column("name","상호",CellType.TEXT)));
            for(int i=0;i<DATA_ROWS_PER_SHEET+1;i++)writer.append(List.of("이름"));
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(DATA_ROWS_PER_SHEET);
            assertThat(workbook.getSheetAt(1).getSheetName()).isEqualTo("데이터_2");
            assertThat(workbook.getSheetAt(1).getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheetAt(1).getRow(0).getCell(0).getStringCellValue()).isEqualTo("상호");
            assertThat(writer.total).isEqualTo(1_048_576);
        } finally {workbook.dispose();}
    }
}
