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

class CashFlowWorkbookTest {
    final CurrentUserProvider users=mock(CurrentUserProvider.class);
    final AccessLogWriter logs=mock(AccessLogWriter.class);
    final PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
    final ExcelExportService service=new ExcelExportService(users,logs,Clock.fixed(Instant.parse("2026-06-30T15:00:00Z"),ZoneOffset.UTC),transactions);
    final MockHttpServletRequest request=new MockHttpServletRequest();
    final List<Column> columns=List.of(new Column("sourceId","원천",CellType.TEXT),new Column("amount","금액",CellType.NUMBER));
    final Specification spec=new Specification("자금수지표",null,columns,"2026","원","UI-21");
    @BeforeEach void setup() {
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        when(transactions.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
    }
    WorkbookData data(Rows rows) {return new WorkbookData(List.of(new Sheet("현금흐름",columns,rows)),List.of(new Information("단위","원")),3,"2026");}
    @Test void cashWorkbookUsesExistingSnapshotThenCommitsDownloadAuditBeforeExposingBytes() throws Exception {
        WorkbookSource source=mock(WorkbookSource.class);Rows rows=mock(Rows.class);
        when(source.read()).thenReturn(data(rows));
        try(var download=service.generateWorkbook(spec,source,request)) {
            var order=inOrder(transactions,source,rows,logs);
            order.verify(transactions).getTransaction(argThat(d->d.isReadOnly()&&d.getIsolationLevel()==TransactionDefinition.ISOLATION_REPEATABLE_READ));
            order.verify(source).read();order.verify(rows).read(any());order.verify(transactions).commit(any());
            order.verify(transactions).getTransaction(argThat(d->d.getPropagationBehavior()==TransactionDefinition.PROPAGATION_REQUIRES_NEW&&!d.isReadOnly()));
            order.verify(logs).append(argThat(e->e.action().equals("DNLD")&&e.screenId().equals("UI-21")&&e.fileType().equals("자금수지표")),same(request));
            order.verify(transactions).commit(any());assertThat(download.filename()).startsWith("자금수지표_2026_");
            var bytes=new ByteArrayOutputStream();download.transferTo(bytes);assertThat(bytes.size()).isPositive();
        }
    }
    @Test void exactAmountsNullAndFormulaLikeSourceTextAreNotConvertedToBusinessCalculations() throws Exception {
        var output=new ByteArrayOutputStream();
        try(var download=service.generateWorkbook(spec,()->data(sink->{
            sink.accept(List.of("00001",new BigDecimal("999999999999999")));
            sink.accept(Arrays.asList("=1+1",null));sink.accept(List.of("음수",new BigDecimal("-1")));
            sink.accept(List.of("표현범위초과",new BigDecimal("10000000000000001")));
        }),request)){download.transferTo(output);}
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            var sheet=workbook.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("00001");
            assertThat(sheet.getRow(1).getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.NUMERIC);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("=1+1");
            assertThat(sheet.getRow(2).getCell(1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
            assertThat(sheet.getRow(3).getCell(1).getNumericCellValue()).isEqualTo(-1);
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("10000000000000001");
        }
    }
    @Test void mandatoryAuditFailureReturnsNoDownloadAndRollsBackWithoutLeakingSql() {
        doThrow(new IllegalStateException("private SQL")).when(logs).append(any(),any());
        assertThatThrownBy(()->service.generateWorkbook(spec,()->data(sink->{}),request)).isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.status()).isEqualTo(500);assertThat(e.getMessage()).doesNotContain("private SQL");});
        verify(transactions).rollback(any());
    }
}
