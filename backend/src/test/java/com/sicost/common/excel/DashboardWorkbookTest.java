package com.sicost.common.excel;

import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.AccessLogMapper;
import com.sicost.common.history.AccessLogWriter;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.common.excel.ExcelExportService.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardWorkbookTest {
    final CurrentUserProvider users=mock(CurrentUserProvider.class);
    final AccessLogMapper mapper=mock(AccessLogMapper.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-07-14T15:00:00Z"),ZoneOffset.UTC);
    final AccessLogWriter logs=new AccessLogWriter(mapper,users,clock);
    final PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
    final ExcelExportService service=new ExcelExportService(users,logs,clock,transactions);
    final MockHttpServletRequest request=new MockHttpServletRequest();
    final List<Column> columns=List.of(new Column("amount","금액",CellType.NUMBER));
    final Specification spec=new Specification("대시보드",null,columns,"2026-07","천원","UI-16");
    @BeforeEach void setup() {
        when(users.userId()).thenReturn("admin");
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        when(transactions.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(mapper.insert(any())).thenReturn(1);
    }
    WorkbookData data() {return new WorkbookData(List.of(new Sheet("요약",columns,sink->{})),List.of(new Information("단위","천원")),0,"202607");}
    @Test void dashboardWorkbookUsesOneSnapshotAndMandatoryDownloadAuditBeforeBytes() throws Exception {
        WorkbookSource source=mock(WorkbookSource.class);when(source.read()).thenReturn(data());
        try(var download=service.generateWorkbook(spec,source,request)) {
            var order=inOrder(transactions,source,mapper);
            order.verify(transactions).getTransaction(argThat(d->d.isReadOnly()&&d.getIsolationLevel()==TransactionDefinition.ISOLATION_REPEATABLE_READ));
            order.verify(source).read();order.verify(transactions).commit(any());
            order.verify(transactions).getTransaction(argThat(d->d.getPropagationBehavior()==TransactionDefinition.PROPAGATION_REQUIRES_NEW&&!d.isReadOnly()));
            order.verify(mapper).insert(argThat(e->e.screenId().equals("UI-16")&&e.action().equals("DNLD")&&e.description().equals("대시보드 0")));
            order.verify(transactions).commit(any());
            assertThat(download.filename()).startsWith("대시보드_202607_");assertThat(download.size()).isPositive();
        }
    }
    @Test void failedMandatoryAuditExposesNoDownload() {
        when(mapper.insert(any())).thenReturn(0);
        assertThatThrownBy(()->service.generateWorkbook(spec,this::data,request)).isInstanceOf(ApiException.class);
        verify(transactions).rollback(any());
    }
    @Test void dashboardAccessDoesNotAcquireOtherActionsOrArbitraryTargets() {
        assertThatThrownBy(()->logs.append(new AccessLogWriter.Event("ACCT_VIEW","UI-16",null,"대시보드",0L),request)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->logs.append(new AccessLogWriter.Event("DNLD","UI-16","123","대시보드",0L),request)).isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper);
    }
}
