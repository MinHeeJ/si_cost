package com.sicost.exportsops;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.apiops.BillingCheckService;
import com.sicost.apiops.ReceivableService;
import com.sicost.bills.BillDtos;
import com.sicost.bills.BillService;
import com.sicost.common.api.ApiException;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.excel.ExcelExportService.Rows;
import com.sicost.common.excel.ExcelExportService.Specification;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** UI-13 청구 목록 다운로드(view=BILLS): 목록 API와 같은 조건·정렬로 전체를 쓰고, 열·필터는 고정 규칙으로 검증한다. */
class BillingReviewBillsExportTest {
    private final ObjectMapper json = new ObjectMapper();
    private BillService bills;
    private ExcelExportService excel;
    private BillingReviewExportService service;

    @BeforeEach void setup() {
        bills = mock(BillService.class);
        excel = mock(ExcelExportService.class);
        service = new BillingReviewExportService(mock(BillingCheckService.class), mock(ReceivableService.class), excel, bills);
    }

    private static BillDtos.Bill bill(String id, String status, BillDtos.Dunning dunning) {
        return new BillDtos.Bill(id, "P-0001", "월단위프로젝트", "고객A", "MO", 1, "2026-10", "2026-10-31", "4800000", "10", "480000", "5280000", "1000000", "4280000",
            "2026-11-25", null, null, status, "CNCL".equals(status), null, null, null, false, dunning, null, "rev");
    }
    private JsonNode body(String filters, String extra) throws Exception {
        String columns = "[\"id\",\"projectName\",\"order\",\"billingDate\",\"supplyAmount\",\"vatAmount\",\"totalAmount\",\"receiptAmount\",\"unpaidAmount\",\"dueDate\",\"status\",\"dunning\"]";
        return json.readTree("{\"view\":\"BILLS\",\"filters\":" + filters + ",\"columns\":" + columns + (extra.isEmpty() ? "" : "," + extra) + "}");
    }
    private ApiException rejected(JsonNode body) {
        return catchThrowableOfType(() -> service.create(body, null), ApiException.class);
    }

    @Test void billsViewIsHandledAlongsideTheReviewViews() throws Exception {
        assertThat(BillingReviewExportService.handles(json.readTree("{\"view\":\"BILLS\"}"))).isTrue();
        assertThat(BillingReviewExportService.handles(json.readTree("{\"view\":\"RECEIVABLES\"}"))).isTrue();
        assertThat(BillingReviewExportService.handles(json.readTree("{\"view\":\"ASSIGNMENTS\"}"))).isFalse();
    }

    @Test void filtersSortAndDirectionBecomeTheListQueryAndEveryRowIsWritten() throws Exception {
        when(bills.listAll(any())).thenReturn(List.of(bill("B-202610-001", "UNPAID", new BillDtos.Dunning(-3, 0, 1, "2026-12-01", false)),
            bill("B-202606-001", "LATE", new BillDtos.Dunning(79, 3, null, null, true)), bill("B-202601-001", "PAID", null)));
        service.create(body("{\"year\":2026,\"status\":\"UNPAID\",\"month\":\"2026-10\",\"projectId\":\"P-0001\",\"includeCarryover\":\"false\",\"unused\":null}".replace(",\"unused\":null", ""),
            "\"sort\":\"totalAmount\",\"direction\":\"ASC\",\"amountUnit\":\"천원\""), null);
        ArgumentCaptor<BillDtos.Query> query = ArgumentCaptor.forClass(BillDtos.Query.class);
        verify(bills).listAll(query.capture());
        assertThat(query.getValue()).extracting(BillDtos.Query::year, BillDtos.Query::status, BillDtos.Query::month, BillDtos.Query::projectId, BillDtos.Query::includeCarryover,
            BillDtos.Query::sort, BillDtos.Query::direction).containsExactly(2026, "UNPAID", "2026-10", "P-0001", false, "totalAmount", "ASC");
        ArgumentCaptor<Specification> spec = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Rows> rows = ArgumentCaptor.forClass(Rows.class);
        verify(excel).generate(spec.capture(), rows.capture(), any());
        assertThat(spec.getValue().kind()).isEqualTo("청구");
        assertThat(spec.getValue().screenId()).isEqualTo("UI-13");
        assertThat(spec.getValue().columns()).hasSize(12);
        assertThat(spec.getValue().columns().get(4).title()).isEqualTo("공급가 (천원)");
        List<List<Object>> written = new ArrayList<>();
        rows.getValue().read(written::add);
        assertThat(written).hasSize(3);
        assertThat(written.get(0).stream().map(v -> v instanceof java.math.BigDecimal d ? d.stripTrailingZeros().toPlainString() : String.valueOf(v)).toList())
            .containsExactly("B-202610-001", "월단위프로젝트", "1", "2026-10-31", "4800", "480", "5280", "1000", "4280", "2026-11-25", "미수", "알림 전");
        assertThat(written.get(1).get(10)).isEqualTo("연체");
        assertThat(written.get(1).get(11)).isEqualTo("3단계 (79일)");
        assertThat(written.get(2).get(10)).isEqualTo("입금 완료");
        assertThat(written.get(2).get(11)).isEqualTo("");
    }

    @Test void theAmountUnitScalesAllAmountColumns() throws Exception {
        when(bills.listAll(any())).thenReturn(List.of(bill("B-202610-001", "UNPAID", null)));
        service.create(body("{\"year\":2026}", "\"amountUnit\":\"원\""), null);
        ArgumentCaptor<Rows> rows = ArgumentCaptor.forClass(Rows.class);
        verify(excel).generate(any(), rows.capture(), any());
        List<List<Object>> written = new ArrayList<>();
        rows.getValue().read(written::add);
        assertThat(((java.math.BigDecimal) written.get(0).get(4)).stripTrailingZeros().toPlainString()).isEqualTo("4800000");
    }

    @Test void invalidRequestsAreRejectedBeforeAnyRead() throws Exception {
        assertThat(rejected(json.readTree("{\"view\":\"BILLS\",\"filters\":{\"year\":2026},\"columns\":[\"id\"]}")).field()).isEqualTo("columns");
        assertThat(rejected(body("{\"year\":2026,\"vatAmount\":\"1\"}", "")).field()).isEqualTo("vatAmount");
        assertThat(rejected(body("{\"year\":2026,\"status\":\"PAYING\"}", "")).field()).isEqualTo("status");
        assertThat(rejected(body("{}", "")).field()).isEqualTo("year");
        assertThat(rejected(body("{\"year\":2026}", "\"sort\":\"quickActions\"")).field()).isEqualTo("sort");
        assertThat(rejected(body("{\"year\":2026}", "\"amountUnit\":\"억원\"")).field()).isEqualTo("amountUnit");
        assertThat(rejected(body("{\"year\":2026}", "\"page\":2")).field()).isEqualTo("page");
        verifyNoInteractions(bills);
    }
}
