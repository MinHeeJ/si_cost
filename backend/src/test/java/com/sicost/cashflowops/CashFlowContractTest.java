package com.sicost.cashflowops;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.AccessLogWriter;
import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.shared.SharedQueryDtos.CloseRow;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.sicost.cashflowops.CashFlowDtos.*;

class CashFlowContractTest {
    final JsonMapper json=JsonMapper.builder().findAndAddModules().build();
    final Clock clock=Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"),ZoneOffset.UTC);
    CashFlowRepository repository;CurrentUserProvider users;AccessLogWriter logs;PlatformTransactionManager transactions;
    CashFlowService service;MockMvc mvc;CashFlowRepository.Snapshot snapshot;
    @BeforeEach void setup() throws Exception {
        repository=mock(CashFlowRepository.class);users=mock(CurrentUserProvider.class);logs=mock(AccessLogWriter.class);transactions=mock(PlatformTransactionManager.class);
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("server-actor","관리자",List.of("ADMIN"),true));
        when(transactions.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        var excel=new ExcelExportService(users,logs,clock,transactions);
        service=new CashFlowService(repository,users,clock,excel);
        mvc=MockMvcBuilders.standaloneSetup(new CashFlowController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
        snapshot=new CashFlowRepository.Snapshot(YearMonth.of(2026,1),List.of(),List.of(),List.of(
            new Standard("CASH_BASE_AMT","202601",new BigDecimal("1000")),new Standard("MIN_CASH","202601",new BigDecimal("500")),new Standard("VAT_RT","202601",BigDecimal.TEN)),List.of(),List.of());
        when(repository.base()).thenReturn(snapshot.base());when(repository.load(anyInt())).thenAnswer(c->snapshot);
        when(repository.financial(anyString())).thenReturn(json.readTree("{}"));when(repository.receipts(anyString())).thenReturn(json.readTree("{}"));
    }
    @Test void splitReceiptsAndRemainingUnpaidAreFullReadOnlySourcesWithoutAuthentication() throws Exception {
        when(repository.receipts("2026-11")).thenReturn(json.readTree("""
            {"receipts":[{"rcpt_id":"001","rcpt_dt":"2026-11-01","rcpt_amt":30},{"rcpt_id":"002","rcpt_dt":"2026-11-05","rcpt_amt":20}],"bills":[{"bill_id":"003","bill_ym":"202610","pay_due_dt":"2026-11-30","unpaid":50}]}
            """));
        mvc.perform(get("/api/cash-flow/sources").queryParam("month","2026-11").queryParam("item","CASH_SALES").queryParam("size","20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.items[0].sourceId").value("003"))
            .andExpect(jsonPath("$.data.items[1].sourceId").value("001")).andExpect(jsonPath("$.data.items[1].attributionDate").value("2026-11-01"))
            .andExpect(jsonPath("$.data.items[0].status").value("PLANNED")).andExpect(jsonPath("$.data.items[1].status").value("ACTUAL"));
        verifyNoInteractions(logs,transactions);verify(users).currentUser();
    }
    @Test void pageTwoAndBeyondDoNotChangeFullCountOrCalculation() throws Exception {
        var data=json.createObjectNode();var receipts=data.putArray("receipts");for(int i=1;i<=21;i++)receipts.addObject().put("rcpt_id",String.format("%03d",i)).put("rcpt_dt","2026-11-01").put("rcpt_amt",1);
        when(repository.receipts("2026-11")).thenReturn(data);
        mvc.perform(get("/api/cash-flow/sources?month=2026-11&item=CASH_SALES&page=2&size=20")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(21)).andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.hasMore").value(false));
        mvc.perform(get("/api/cash-flow/sources?month=2026-11&item=CASH_SALES&page=3&size=20")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
        assertThat(service.calculateCashFlow(new Query(2026,Unit.WON)).months().get(10).incomeTotal()).isEqualTo("21");verifyNoInteractions(logs,transactions);
    }
    @Test void validEmptyMonthReturnsEmptyPage() throws Exception {mvc.perform(get("/api/cash-flow/sources?month=2026-11&item=MISC")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));}
    @Test void decemberFutureEvidenceIsAllowedByDecision() throws Exception {mvc.perform(get("/api/cash-flow/sources?month=2026-12&item=REG_PAY")).andExpect(status().isOk());}
    @ParameterizedTest @ValueSource(strings={"month=2026-13&item=MISC","month=2026-11&item=TOTAL","month=2026-11&item=MISC&page=0","month=2026-11&item=MISC&size=30","month=2026-11&item=MISC&page=1&page=2","item=MISC","month=2026-11"})
    void invalidSourceInputHasNoDatabaseOrAuditInteraction(String query) throws Exception {mvc.perform(get("/api/cash-flow/sources?"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.field").isString());verifyNoInteractions(repository,logs);}
    @ParameterizedTest @ValueSource(strings={"sources?month=2026-11&item=MISC&actor=bad","sources?month=2026-11&item=MISC&regUserId=bad","export?year=2026&actor=bad","export?year=2026&regUserId=bad"})
    void serverFieldsCannotBeInjected(String path) throws Exception {mvc.perform(get("/api/cash-flow/"+path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(repository,logs,transactions);}
    @ParameterizedTest @ValueSource(strings={"sources?month=2025-12&item=MISC","sources?month=2027-01&item=MISC","export?year=2025","export?year=2027"})
    void calendarValidOutsideOperatingRangeIs409Not403(String path) throws Exception {mvc.perform(get("/api/cash-flow/"+path)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERIOD_OUT_OF_SCOPE"));verifyNoInteractions(logs);}
    @ParameterizedTest @ValueSource(strings={"year=bad","unit=BAD","year=0","year=10000","year=2026&year=2026"})
    void invalidExportNeverGeneratesFileOrAudit(String query) throws Exception {mvc.perform(get("/api/cash-flow/export?"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));verifyNoInteractions(repository,logs,transactions);}
    @Test void sameYearUnitDateTwelveMonthsHalfYearAndAnnualAreExported() throws Exception {
        var response=mvc.perform(get("/api/cash-flow/export?year=2026&unit=MILLION_WON")).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn().getResponse();
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet=workbook.getSheetAt(0);assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short)16);
            assertThat(sheet.getRow(0).getCell(12).getStringCellValue()).isEqualTo("12월 예정");
            assertThat(sheet.getRow(0).getCell(13).getStringCellValue()).isEqualTo("상반기");
            assertThat(sheet.getRow(0).getCell(14).getStringCellValue()).isEqualTo("하반기");
            assertThat(sheet.getRow(0).getCell(15).getStringCellValue()).isEqualTo("연간");
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(0.001);
            var info=workbook.getSheet("다운로드정보");assertThat(info.getRow(4).getCell(1).getStringCellValue()).isEqualTo("백만원");
            assertThat(info.getRow(7).getCell(1).getStringCellValue()).isEqualTo("2026-11-15");
        }
        var order=inOrder(transactions,logs);order.verify(transactions).getTransaction(any());order.verify(transactions).commit(any());order.verify(transactions).getTransaction(any());order.verify(logs).append(argThat(e->e.action().equals("DNLD")&&e.screenId().equals("UI-21")),any());order.verify(transactions).commit(any());
    }
    @Test void auditFailureReturnsJsonAndNoXlsxBytes() throws Exception {
        doThrow(new IllegalStateException("SQL private")).when(logs).append(any(),any());
        var result=mvc.perform(get("/api/cash-flow/export?year=2026")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn().getResponse();
        assertThat(result.getContentType()).doesNotContain("spreadsheetml");assertThat(result.getContentAsString()).doesNotContain("SQL private");assertThat(result.getHeader("Content-Disposition")).isNull();verify(transactions).rollback(any());
    }
    @Test void carryStartsOnlyOnceAtBaseAndContinuesAcrossYears() throws Exception {
        snapshot=new CashFlowRepository.Snapshot(YearMonth.of(2025,12),List.of(),List.of(),snapshot.standards(),List.of(),List.of());
        when(repository.base()).thenReturn(snapshot.base());
        when(repository.receipts("2025-12")).thenReturn(json.readTree("{\"receipts\":[{\"rcpt_id\":\"1\",\"rcpt_dt\":\"2025-12-01\",\"rcpt_amt\":10}]}"));
        var result=service.calculateCashFlow(new Query(2026,Unit.WON));
        assertThat(result.months().get(0).openingBalance()).isEqualTo("1010");
        assertThat(result.months().get(11).closingBalance()).isEqualTo("1010");
        assertThat(result.periodTotals().get(2).incomeTotal()).isEqualTo("0");
        assertThat(result.periodTotals().get(2).closingBalance()).isEqualTo("1010");
    }
    @Test void unknownSourceBreaksDependentTotalsAndAllLaterCarryButPreservesKnownItems() throws Exception {
        when(repository.financial("2026-02")).thenReturn(json.readTree("{\"cardPayments\":[{\"pay_ym\":\"202602\",\"card_id\":\"001\"}]}"));
        var result=service.calculateCashFlow(new Query(2026,Unit.WON));
        assertThat(result.months().get(0).closingBalance()).isEqualTo("1000");
        assertThat(result.months().get(1).incomeTotal()).isEqualTo("0");
        assertThat(result.months().get(1).expenseTotal()).isNull();
        assertThat(result.months().subList(1,12)).allMatch(m->m.closingBalance()==null&&!m.shortage());
        assertThat(result.months().get(2).openingBalance()).isNull();
        assertThat(result.months().get(2).incomeTotal()).isEqualTo("0");
    }
    @Test void shortageUsesRawWonStrictLessThanOnlyForPlannedMonths() throws Exception {
        when(repository.financial("2026-11")).thenReturn(json.readTree("{\"loans\":[{\"loan_sn\":1,\"loan_dt\":\"2026-11-01\",\"io_cd\":\"OUT\",\"amt\":501}]}"));
        assertThat(service.calculateCashFlow(new Query(2026,Unit.MILLION_WON)).months().get(10).shortage()).isTrue();
        snapshot=new CashFlowRepository.Snapshot(snapshot.base(),snapshot.people(),snapshot.payroll(),snapshot.standards(),List.of(new CloseRow("202611","CLOSE")),List.of());
        assertThat(service.calculateCashFlow(new Query(2026,Unit.WON)).months().get(10).shortage()).isFalse();
    }
    @Test void equalityWithMinimumIsNotShortage() throws Exception {
        when(repository.financial("2026-11")).thenReturn(json.readTree("{\"loans\":[{\"loan_sn\":1,\"loan_dt\":\"2026-11-01\",\"io_cd\":\"OUT\",\"amt\":500}]}"));
        assertThat(service.calculateCashFlow(new Query(2026,Unit.MILLION_WON)).months().get(10).shortage()).isFalse();
    }
    @Test void closeImpactUsesAnnualLedgerForVatDeductionsAndNeverCreatesAClosedRow() throws Exception {
        snapshot=new CashFlowRepository.Snapshot(snapshot.base(),List.of(),List.of(
            new Payroll("202611","own-os",null,null,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,new BigDecimal("100"),"OS","DRCT",null,null,null,null,null),
            new Payroll("202610","own-ft",BigDecimal.TEN,BigDecimal.ZERO,new BigDecimal("2"),new BigDecimal("3"),BigDecimal.ZERO,new BigDecimal("4"),new BigDecimal("5"),BigDecimal.TEN,null,"FT","DRCT",null,null,null,null,null)),snapshot.standards(),List.of(),List.of());
        when(repository.receipts("2026-11")).thenReturn(json.readTree("""
            {"receipts":[{"rcpt_id":"own-receipt","rcpt_dt":"2026-11-01","rcpt_amt":30}],"bills":[{"bill_id":"own-bill","bill_ym":"202611","pay_due_dt":"2026-11-30","unpaid":50}]}
            """));
        when(repository.financial("2026-11")).thenReturn(json.readTree("""
            {"taxPayments":[{"tax_yr":"2026","pay_type_cd":"CNFM","pay_due_dt":"2026-11-30","pay_yn":"N","pay_amt":20}]}
            """));
        var annual=service.calculateCashFlow(new Query(2026,Unit.WON)).months().get(10);
        var impact=service.calculateCloseImpact("2026-11");
        assertThat(impact.before()).isEqualTo(annual);
        assertThat(impact.before().expenseTotal()).isEqualTo("144");
        assertThat(impact.after().expenseTotal()).isEqualTo("124");
        assertThat(impact.before().incomeTotal()).isEqualTo("80");
        assertThat(impact.after().incomeTotal()).isEqualTo("30");
        assertThat(new BigDecimal(impact.after().closingBalance()).subtract(new BigDecimal(impact.before().closingBalance()))).isEqualByComparingTo("-30");
        assertThat(snapshot.closes()).isEmpty();verifyNoInteractions(logs,transactions);
    }
    @Test void closeImpactKeepsUnconfirmedBalanceNullInsteadOfInventingZero() throws Exception {
        when(repository.financial("2026-02")).thenReturn(json.readTree("{\"cardPayments\":[{\"pay_ym\":\"202602\",\"card_id\":\"own-card\"}]}"));
        var impact=service.calculateCloseImpact("2026-11");
        assertThat(impact.before().closingBalance()).isNull();assertThat(impact.after().closingBalance()).isNull();
        assertThat(impact.before().incomeTotal()).isEqualTo("0");assertThat(impact.after().incomeTotal()).isEqualTo("0");
    }
    @Test void sourceWorkbookPreservesTextLeadingZerosFormulaPrefixesDatesNullZeroAndNegative() throws Exception {
        var sources=new ArrayList<Source>();String[] ids={"001","=SUM(A1)","+cmd","-cmd","@name"};String[] amounts={null,"0","-1","2","3"};
        for(int i=0;i<ids.length;i++)sources.add(new Source("TB_FI_LOAN",ids[i],Item.LOAN,LocalDate.of(2026,11,1),"2026-11","2026-11","ACTUAL",amounts[i],"0",true,null,null,amounts[i]!=null,"원금","/UI-10",List.of()));
        var result=service.calculateCashFlow(new Query(2026,Unit.WON));
        var excel=new ExcelExportService(users,logs,clock,transactions);var bytes=new ByteArrayOutputStream();
        try(var download=excel.generateWorkbook(new ExcelExportService.Specification("자금수지표",null,List.of(new ExcelExportService.Column("item","항목",ExcelExportService.CellType.TEXT)),"2026","원","UI-21"),()->CashFlowService.workbook(result,sources,Map.of()),new org.springframework.mock.web.MockHttpServletRequest())){download.transferTo(bytes);}
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
            var sheet=workbook.getSheet("원천근거");for(int i=0;i<ids.length;i++){assertThat(sheet.getRow(i+1).getCell(1).getCellType()).isEqualTo(CellType.STRING);assertThat(sheet.getRow(i+1).getCell(1).getStringCellValue()).isEqualTo(ids[i]);assertThat(org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(sheet.getRow(i+1).getCell(2))).isTrue();}
            assertThat(sheet.getRow(1).getCell(7).getCellType()).isEqualTo(CellType.BLANK);assertThat(sheet.getRow(2).getCell(7).getNumericCellValue()).isZero();assertThat(sheet.getRow(3).getCell(7).getNumericCellValue()).isEqualTo(-1);
        }
    }
    @Test void payrollEvidenceUsesAttributionMonthCloseAndPlainKoreanWithoutChangingMoney() throws Exception {
        var payroll=new ArrayList<Payroll>();
        for(String type:List.of("FT","CT","FL","OS")) {
            for(String month:List.of("202610","202611"))
                payroll.add(new Payroll(month,type,BigDecimal.valueOf(100),BigDecimal.TEN,
                    BigDecimal.ONE,BigDecimal.valueOf(2),BigDecimal.ZERO,BigDecimal.valueOf(3),
                    BigDecimal.valueOf(4),BigDecimal.valueOf(106),BigDecimal.valueOf(100),
                    type,"DRCT",null,null,null,null,null));
        }
        snapshot=new CashFlowRepository.Snapshot(snapshot.base(),List.of(),payroll,
            snapshot.standards(),List.of(new CloseRow("202610","CLOSE")),List.of());
        when(repository.employeeTypeLabels()).thenReturn(Map.of("FT","정규직","CT","계약직","FL","프리랜서","OS","외주"));
        var october=service.getCashFlowSources(new SourceQuery("2026-10",Item.REG_PAY,1,50));
        var november=service.getCashFlowSources(new SourceQuery("2026-11",Item.REG_PAY,1,50));
        assertThat(october.items()).allMatch(s->s.status().equals("ACTUAL"));
        assertThat(november.items()).allMatch(s->s.status().equals("PLANNED"));
        assertThat(november.items().get(0).amount()).isEqualTo("106");
        assertThat(november.items().get(0).formula()).isEqualTo("실지급액");
        var deductions=service.getCashFlowSources(new SourceQuery("2026-11",Item.EXPENSE,1,50));
        assertThat(deductions.items()).allMatch(s->s.status().equals("PLANNED"));
        assertThat(deductions.items()).allMatch(s->s.formula().equals("전달 급여의 4대보험(회사 + 본인)·원천세·근로소득세"));
        mvc.perform(get("/api/cash-flow/sources?month=2026-11&item=EXPENSE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.employeeTypeLabels.FT").value("정규직"))
            .andExpect(jsonPath("$.data.items[0].sourceType").value("TB_HR_MON_PAY"))
            .andExpect(jsonPath("$.data.items[0].sourceTypeLabel").value("급여 공제(4대보험·세금)"));
        var result=service.calculateCashFlow(new Query(2026,Unit.WON));
        assertThat(result.months().get(10).expenseTotal()).isEqualTo("468");
        assertThat(result.months().get(10).closingBalance()).isEqualTo("104");
        assertThat(result.periodTotals().get(2).expenseTotal()).isEqualTo("936");
        assertThat(result.periodTotals().get(1).openingBalance()).isEqualTo("1000");
        assertThat(result.periodTotals().get(1).closingBalance()).isEqualTo("64");
        var response=mvc.perform(get("/api/cash-flow/export?year=2026&unit=WON"))
            .andExpect(status().isOk()).andReturn().getResponse();
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var statement=workbook.getSheet("자금수지표");
            assertThat(statement.getRow(0).getCell(10).getStringCellValue()).isEqualTo("10월 실적");
            assertThat(statement.getRow(0).getCell(11).getStringCellValue()).isEqualTo("11월 예정");
            var evidence=workbook.getSheet("원천근거");
            Set<String> types=new HashSet<>();
            for(int i=1;i<=evidence.getLastRowNum();i++) {
                var row=evidence.getRow(i);
                types.add(row.getCell(9).getStringCellValue());
                assertThat(row.getCell(6).getStringCellValue()).isEqualTo(
                    row.getCell(4).getStringCellValue().equals("2026-10")?"실적":"예정");
            }
            assertThat(types).containsExactlyInAnyOrder("정규직","계약직","프리랜서","외주");
        }
    }
    @Test void exportUsesBusinessLabelsAndKoreanEvidenceWhileKeepingSourceNumbers() throws Exception {
        when(repository.receipts("2026-11")).thenReturn(json.readTree("""
            {"receipts":[{"rcpt_id":"001","rcpt_dt":"2026-11-01","rcpt_amt":30}],
             "bills":[{"bill_id":"002","bill_ym":"202611","pay_due_dt":"2026-11-30","unpaid":50}]}
            """));
        var response=mvc.perform(get("/api/cash-flow/export?year=2026&unit=WON"))
            .andExpect(status().isOk()).andReturn().getResponse();
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var statement=workbook.getSheet("자금수지표");
            List<String> labels=new ArrayList<>();
            for(int i=1;i<=statement.getLastRowNum();i++)labels.add(statement.getRow(i).getCell(0).getStringCellValue());
            assertThat(labels).contains("현금매출","인건비(정규직)","인건비(계약직·프리랜서)","용역비(외주)","법인세 납부","이월금")
                .doesNotContain("외주비","월말 잔액","정규직 급여");
            var sheet=workbook.getSheet("원천근거");
            List<String> headers=new ArrayList<>();
            sheet.getRow(0).forEach(c->headers.add(c.getStringCellValue()));
            assertThat(headers).containsExactly("원천 종류","원천 번호","귀속일","기준월","귀속월","항목","실적·예정","금액","참조 급여월","직원구분","계산 근거","미확인 사유");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("입금 예정(청구)");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("002");
            assertThat(sheet.getRow(1).getCell(5).getStringCellValue()).isEqualTo("현금매출");
            assertThat(sheet.getRow(1).getCell(6).getStringCellValue()).isEqualTo("예정");
            assertThat(sheet.getRow(2).getCell(6).getStringCellValue()).isEqualTo("실적");
            for(var row:sheet)for(var cell:row)
                if(cell.getCellType()==CellType.STRING)
                    assertThat(cell.getStringCellValue()).doesNotContain("TB_","VAT_AMT","IO_DT","REAL_PAY_AMT","ACTUAL","PLANNED");
        }
    }
}
