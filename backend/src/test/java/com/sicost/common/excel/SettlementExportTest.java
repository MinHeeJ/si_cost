package com.sicost.common.excel;

import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.AccessLogWriter;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sicost.common.excel.ExcelExportService.*;

class SettlementExportTest {
    final CurrentUserProvider users = mock(CurrentUserProvider.class);
    final AccessLogWriter logs = mock(AccessLogWriter.class);
    final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    final ExcelExportService service = new ExcelExportService(users, logs,
        Clock.fixed(Instant.parse("2026-06-30T15:00:00Z"), ZoneId.of("Asia/Seoul")), transactions);
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final List<Column> columns = List.of(new Column("developerId", "인력ID", CellType.TEXT),
        new Column("amount", "금액", CellType.NUMBER));
    @BeforeEach void setup() {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin", "관리자", List.of("ADMIN"), true));
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
    }
    Specification specification(String screen) {
        return new Specification(screen.equals("UI-19") ? "영업파트이익금" : "단가재협상", null, columns, "전체", "천원", screen);
    }
    WorkbookData data(String filename, String qualifier, Rows rows) {
        return new WorkbookData(List.of(new Sheet("정산", columns, rows)), List.of(new Information("단위", "천원")), 6, qualifier, filename);
    }
    @Test void monthAndTrendNamesHaveNoTimestampAndCommitAuditBeforeBytes() throws Exception {
        for (String mode : List.of("이번달", "추이")) {
            String filename = "영업파트이익금_PART-001_202607_" + mode + ".xlsx";
            try (var download = service.generateWorkbook(specification("UI-19"),
                    () -> data(filename, "202607", sink -> sink.accept(List.of("000001", BigDecimal.ZERO))), request)) {
                assertThat(download.filename()).isEqualTo(filename);
                verify(logs).append(argThat(e -> e.screenId().equals("UI-19") && filename.equals(e.filename()) && e.count() == 6), same(request));
                var output = new ByteArrayOutputStream(); download.transferTo(output);
                assertThat(output.size()).isPositive();
            }
        }
        verify(transactions, times(4)).commit(any());
    }
    @Test void renegotiationNameUsesSeoulCalendarDate() throws Exception {
        try (var download = service.generateWorkbook(specification("UI-32"),
                () -> data("단가재협상_20260701.xlsx", "20260701", sink -> {}), request)) {
            assertThat(download.filename()).isEqualTo("단가재협상_20260701.xlsx");
        }
        verify(logs).append(argThat(e -> e.screenId().equals("UI-32") && e.filename().equals("단가재협상_20260701.xlsx")), same(request));
    }
    @Test void sourceAndRowsRunInsideOneReadOnlyRepeatableReadBeforeSeparateAudit() throws Exception {
        WorkbookSource source = mock(WorkbookSource.class); Rows rows = mock(Rows.class);
        when(source.read()).thenReturn(data("영업파트이익금_PART-001_202607_이번달.xlsx", "202607", rows));
        try (var download = service.generateWorkbook(specification("UI-19"), source, request)) {
            var order = inOrder(transactions, source, rows, logs);
            order.verify(transactions).getTransaction(argThat(d -> d.isReadOnly()
                && d.getIsolationLevel() == TransactionDefinition.ISOLATION_REPEATABLE_READ));
            order.verify(source).read(); order.verify(rows).read(any()); order.verify(transactions).commit(any());
            order.verify(transactions).getTransaction(argThat(d -> !d.isReadOnly()
                && d.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
            order.verify(logs).append(any(), same(request)); order.verify(transactions).commit(any());
        }
    }
    @Test void workbookKeepsLeadingZeroFormulaLikeTextNullZeroAndNegativeNumericTypes() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var download = service.generateWorkbook(specification("UI-19"), () -> data(
                "영업파트이익금_PART-001_202607_이번달.xlsx", "202607", sink -> {
                    for (String text : List.of("000001", "=1+1", "+1", "-1", "@user")) sink.accept(List.of(text, new BigDecimal("-0.001")));
                    sink.accept(Arrays.asList("미확인", null)); sink.accept(List.of("명시0", BigDecimal.ZERO));
                }), request)) { download.transferTo(output); }
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            for (int i = 1; i <= 5; i++) {
                assertThat(book.getSheetAt(0).getRow(i).getCell(0).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
                assertThat(book.getSheetAt(0).getRow(i).getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.NUMERIC);
                assertThat(book.getSheetAt(0).getRow(i).getCell(1).getNumericCellValue()).isEqualTo(-0.001);
            }
            assertThat(book.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("000001");
            assertThat(book.getSheetAt(0).getRow(6).getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
            assertThat(book.getSheetAt(0).getRow(7).getCell(1).getNumericCellValue()).isZero();
        }
    }
    @Test void failedAuditReturnsNoDownloadAndLeaksNoInternalFailure() {
        doThrow(new IllegalStateException("private SQL")).when(logs).append(any(), any());
        assertThatThrownBy(() -> service.generateWorkbook(specification("UI-19"), () -> data(
            "영업파트이익금_PART-001_202607_이번달.xlsx", "202607", sink -> sink.accept(List.of("0001", BigDecimal.ZERO))), request))
            .isInstanceOfSatisfying(ApiException.class, e -> {
                assertThat(e.status()).isEqualTo(500); assertThat(e.getMessage()).doesNotContain("private SQL");
            });
        verify(transactions).rollback(any());
    }
    @Test void invalidNamesAndMismatchedPeriodsNeverAudit() {
        for (String filename : List.of("../영업파트이익금_PART-001_202607_이번달.xlsx", "영업파트이익금_PART-001_202608_이번달.xlsx",
                "영업파트이익금_PART-001_202607_이번달.xlsx\r\nunsafe", "영업파트이익금_PART-001_202607_이번달_120000.xlsx")) {
            assertThatThrownBy(() -> service.generateWorkbook(specification("UI-19"), () -> data(filename, "202607", sink -> {}), request))
                .isInstanceOf(ApiException.class);
        }
        verifyNoInteractions(logs);
    }
    @Test void sourceFailureDoesNotAuditAndLegacyUnsnapshottedEntryCannotExportTheseScreens() {
        assertThatThrownBy(() -> service.generateWorkbook(specification("UI-32"), () -> { throw new IllegalStateException(); }, request))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.generate(specification("UI-19"), sink -> {}, request)).isInstanceOf(ApiException.class);
        verifyNoInteractions(logs);
    }
    @Test void existingWorkbookConstructorAndFilenameRemainCompatible() throws Exception {
        var spec = new Specification("영업이익표", null, columns, "전체", "천원", "UI-08");
        try (var download = service.generateWorkbook(spec, () -> new WorkbookData(
                List.of(new Sheet("기존", columns, sink -> {})), List.of(), 0, "202607"), request)) {
            assertThat(download.filename()).isEqualTo("영업이익표_202607.xlsx");
        }
    }
}
