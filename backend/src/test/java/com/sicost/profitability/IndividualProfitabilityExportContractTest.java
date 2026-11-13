package com.sicost.profitability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.history.*;
import com.sicost.me.*;
import com.sicost.shared.SharedQueriesService;
import com.sicost.shared.SharedQueryDtos.*;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.util.LinkedMultiValueMap;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static com.sicost.profitability.MonthlyCostCalculationTest.*;
import static com.sicost.profitability.IndividualProfitabilityCalculationTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real controller/export/projection/POI/audit path. Only source adapters and transaction infrastructure are doubled. */
class IndividualProfitabilityExportContractTest {
    final Clock clock=Clock.fixed(Instant.parse("2026-01-31T00:00:00Z"),ZoneId.of("Asia/Seoul"));
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    MonthlyCostRepository repository;
    MonthlyCostMapper mapper;
    SharedQueriesService shared;
    PreferenceService preferences;
    PreferenceValidator validator;
    AccessLogMapper logs;
    PlatformTransactionManager transactions;
    CurrentUserProvider users;
    ExcelExportService excel;
    IndividualProfitabilityService service;
    IndividualProfitabilityExportService exports;
    MockMvc mvc;
    Sources source;
    @BeforeEach void setup() {
        repository=mock(MonthlyCostRepository.class);mapper=mock(MonthlyCostMapper.class);shared=mock(SharedQueriesService.class);
        preferences=mock(PreferenceService.class); var preferenceMapper=mock(PreferenceMapper.class);
        when(preferenceMapper.screens()).thenReturn(List.of("UI-03"));
        validator=new PreferenceValidator(preferenceMapper,json,clock);
        when(preferences.getPreferences()).thenReturn(new PreferenceDtos.PreferencesData(List.of()));
        users=mock(CurrentUserProvider.class);logs=mock(AccessLogMapper.class);transactions=mock(PlatformTransactionManager.class);
        when(users.userId()).thenReturn("actor");
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("actor","처리자",List.of("ADMIN"),true));
        when(logs.insert(any())).thenReturn(1);
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        var payroll=new EstimatedPayrollAdapter();var turnkey=new TurnkeyRevenueCalculation();
        service=new IndividualProfitabilityService(new MonthlyCostService(repository,new MonthlyCostCalculation(payroll),turnkey,payroll),
            new IndividualProfitabilityCalculation(turnkey),shared,mapper);
        excel=new ExcelExportService(users,new AccessLogWriter(logs,users,clock),clock,transactions);
        exports=new IndividualProfitabilityExportService(excel,service,preferences,validator,shared,json);
        mvc=MockMvcBuilders.standaloneSetup(new IndividualProfitabilityExportController(exports),new IndividualProfitabilityController(service))
            .setControllerAdvice(new ApiExceptionHandler()).build();
        source=sources(person("FT"),List.of(pay("FT","1001")),List.of(assignment("0001","0.60")),List.of(project("MO","ACT")));
        when(repository.loadYear(any())).thenAnswer(call -> source);
        when(shared.getCodes(anyString(),eq(true))).thenAnswer(call -> new Items<>(switch((String)call.getArgument(0)) {
            case "JOB" -> List.of(code("JOB","직무"));case "GRADE" -> List.of(code("JR","초급"));
            case "EMP_TYPE" -> List.of(code("FT","정규직")); default -> List.of();
        }));
    }
    static CodeLookup code(String code,String name) {return new CodeLookup(code,name,null,null,null,null,null);}
    byte[] download(String query) throws Exception {
        return mvc.perform(get("/api/individual-profitability/export"+query)).andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("2026_20260131_090000.xlsx")))
            .andReturn().getResponse().getContentAsByteArray();
    }
    XSSFWorkbook workbook(String query) throws Exception {return new XSSFWorkbook(new ByteArrayInputStream(download(query)));}
    static List<String> names(org.apache.poi.ss.usermodel.Sheet sheet) {
        var result=new ArrayList<String>();
        for(int i=1;i<=sheet.getLastRowNum();i++) {var text=sheet.getRow(i).getCell(0).getStringCellValue();if(!text.equals("전체 합계"))result.add(text);}
        return result;
    }
    @Test void allPeopleNotCurrentPageAndTotalsMatchApiWithOneSnapshot() throws Exception {
        var people=IntStream.range(0,51).mapToObj(i -> identity("owned-"+i,"인력"+i,null)).toList();
        source=new Sources(source.period(),source.actor(),source.asOfDate(),people,source.prices(),source.states(),source.payroll(),source.assignments(),
            source.projectMonths(),source.projects(),source.projectParts(),source.turnkey(),source.storedCosts(),source.standards(),source.parts(),source.closes(),source.allowanceCodes());
        var expected=service.project(service.snapshot(query()),query(),true);
        clearInvocations(repository);
        try(var book=workbook("")) {
            assertThat(names(book.getSheetAt(0))).hasSize(51).containsExactlyElementsOf(expected.items().stream().map(p -> p.name()+" ("+p.managementNo()+")").toList());
            assertThat(book.getSheetAt(0).getRow(52).getCell(0).getStringCellValue()).isEqualTo("전체 합계");
            assertThat(book.getNumberOfSheets()).isEqualTo(4);
            assertThat(book.getSheetAt(1).getSheetName()).isEqualTo("턴키단가미포함");
        }
        verify(repository).loadYear(null);verifyNoMoreInteractions(repository);
    }
    @Test void lossIsIndependentlyAppliedAfterEachTurnkeyViewAndDetailUsesSelectedPopulation() throws Exception {
        source=sources(person("FT"),List.of(pay("FT","1001")),List.of(assignment("0001","1.00")),List.of(project("TK","ACT")));
        var included=service.getIndividualProfitability(query("show","LOSS"));
        var excluded=service.getIndividualProfitability(query("show","LOSS","includeTurnkey","false"));
        assertThat(included.total()).isZero();assertThat(excluded.total()).isEqualTo(1);
        clearInvocations(repository);
        try(var book=workbook("?show=LOSS")) {
            assertThat(names(book.getSheetAt(0))).isEmpty();assertThat(names(book.getSheetAt(1))).containsExactly("시험 (owned)");
            assertThat(book.getSheet("월별배분상세").getLastRowNum()).isZero();
        }
        verify(repository).loadYear(null);
        try(var book=workbook("?show=LOSS&includeTurnkey=false")) {
            assertThat(names(book.getSheetAt(0))).isEqualTo(names(book.getSheetAt(1)));
            assertThat(book.getSheet("월별배분상세").getLastRowNum()).isEqualTo(2);
        }
    }
    @Test void monthlyAverageParentButEveryOriginalMonthAndAllocationIsExportedRegardlessOfExpansion() throws Exception {
        source=annual(source,"202602",List.of(payAt(MONTH,"FT","1001"),payAt("202602","FT","1002")),List.of(at(MONTH,"0001","0.60")));
        try(var book=workbook("?view=MONTHLY_AVERAGE&unit=원")) {
            assertThat(book.getSheetAt(0).getRow(0).getCell(8).getStringCellValue()).contains("월평균");
            assertThat(book.getSheetAt(0).getRow(1).getCell(8).getNumericCellValue()).isEqualTo(1001);
            var detail=book.getSheet("월별배분상세");
            // January MONTH + assignment + IDLE; February MONTH + IDLE.
            assertThat(detail.getLastRowNum()).isEqualTo(5);
            assertThat(detail.getRow(1).getCell(24).getNumericCellValue()).isEqualTo(1001);
            assertThat(detail.getRow(4).getCell(24).getNumericCellValue()).isEqualTo(1002);
            assertThat(detail.getRow(2).getCell(8).getStringCellValue()).isEqualTo("0001");
            assertThat(detail.getRow(2).getCell(8).getCellType()).isEqualTo(CellType.STRING);
            assertThat(detail.getRow(2).getCell(15).getNumericCellValue()).isEqualTo(0.6);
            assertThat(info(book,"부모/상세 기준").getStringCellValue()).contains("월평균","원본");
            for(String key:List.of("처리자ID","처리자명","생성시각","기간","조건","단위","보기","인원","미확인수")) assertThat(info(book,key)).isNotNull();
        }
    }
    static org.apache.poi.ss.usermodel.Cell info(XSSFWorkbook book,String label) {
        for(var row:book.getSheet("다운로드정보")) if(label.equals(row.getCell(0).getStringCellValue()))return row.getCell(1);
        throw new AssertionError("정보 누락: "+label);
    }
    @Test void preferencesSupplyColumnOrderHiddenAndUnitAndExplicitQueryOverridesWithoutSaving() throws Exception {
        var cols=validator.defaultValue("COLS","UI-03");
        var order=new ArrayList<String>();cols.get("individualProfitability").get("order").forEach(n -> order.add(n.asText()));
        Collections.swap(order,0,7);
        ((com.fasterxml.jackson.databind.node.ObjectNode)cols.get("individualProfitability")).set("order",json.valueToTree(order));
        when(preferences.getPreferences()).thenReturn(new PreferenceDtos.PreferencesData(List.of(
            new PreferenceDtos.Preference("COLS","UI-03",cols,"revision",true,null),
            new PreferenceDtos.Preference("AMT_UNIT","*",json.getNodeFactory().textNode("백만원"),null,false,null))));
        try(var book=workbook("")) {
            assertThat(book.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).contains("개인 단가","백만원");
            assertThat(book.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short)18);
            assertThat(info(book,"단위").getStringCellValue()).isEqualTo("백만원");
        }
        try(var book=workbook("?hidden=&unit=원")) {assertThat(book.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short)22);}
        verify(preferences,times(2)).getPreferences();verifyNoMoreInteractions(preferences);
    }
    @Test void nullBlankAndZeroNumericWithoutRoundingThousandUnits() throws Exception {
        try(var book=workbook("?hidden=&unit=천원")) {
            var row=book.getSheetAt(0).getRow(1);
            assertThat(row.getCell(8).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row.getCell(8).getNumericCellValue()).isEqualTo(1.001);
            assertThat(row.getCell(9).getNumericCellValue()).isZero();
            assertThat(row.getCell(18).getCellType()).isEqualTo(CellType.BLANK);
        }
    }
    @ParameterizedTest @ValueSource(strings={"=1+1","+SUM(A1)","-1","@external"})
    void userTextNeverBecomesFormula(String name) throws Exception {
        var p=identity("owned",name,null);
        source=sources(p,List.of(),List.of(),List.of());
        try(var book=workbook("")) {
            var cell=book.getSheetAt(0).getRow(1).getCell(0);
            assertThat(cell.getCellType()).isEqualTo(CellType.STRING);assertThat(cell.getStringCellValue()).startsWith(name);
            for(var sheet:book)for(var row:sheet)for(var value:row)assertThat(value.getCellType()).isNotEqualTo(CellType.FORMULA);
        }
    }
    @Test void emptyResultsContainTitlesAndInformationButNoInventedZeroTotals() throws Exception {
        try(var book=workbook("?name=absent")) {
            for(int i=0;i<3;i++)assertThat(book.getSheetAt(i).getPhysicalNumberOfRows()).isEqualTo(1);
            assertThat(info(book,"인원").getNumericCellValue()).isZero();
        }
    }
    @ParameterizedTest @ValueSource(strings={"?page=1","?size=20","?expanded=actual","?unit=USD","?includeTurnkey=TRUE","?year=2026&year=2026","?hidden=name","?hidden=profit","?order=name","?hidden=pay,pay","?actor=other"})
    void strictRequestValidationReturnsJsonWithoutAttachment(String query) throws Exception {
        mvc.perform(get("/api/individual-profitability/export"+query)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false)).andExpect(header().doesNotExist("Content-Disposition"));
        verifyNoInteractions(logs);
    }
    @Test void auditOccursInSeparateTransactionAndCompletesBeforeFirstResponseByte() throws Exception {
        download("");
        var order=inOrder(transactions,repository,logs);
        order.verify(transactions).getTransaction(argThat(d -> d.isReadOnly() && d.getIsolationLevel()==TransactionDefinition.ISOLATION_REPEATABLE_READ));
        order.verify(repository).loadYear(null);order.verify(transactions).commit(any());
        order.verify(transactions).getTransaction(argThat(d -> !d.isReadOnly() && d.getPropagationBehavior()==TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        order.verify(logs).insert(argThat(e -> e.action().equals("DNLD") && e.screenId().equals("UI-03") && e.userId().equals("actor")
            && e.description().strip().equals("개인원가수익 1") && e.clientIp()!=null && e.now().equals(LocalDateTime.of(2026,1,31,9,0))));
        order.verify(transactions).commit(any());
    }
    static Set<Path> spools() throws Exception {
        try(var paths=Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.filter(p -> p.getFileName().toString().startsWith("sicost-export-")).collect(java.util.stream.Collectors.toSet());
        }
    }
    @Test void auditFailureOrCommitFailureNeverExposesXlsxBytesAndDeletesTemporaryFile() throws Exception {
        var before=spools();when(logs.insert(any())).thenThrow(new IllegalStateException("private payroll SELECT amount"));
        var response=mvc.perform(get("/api/individual-profitability/export")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andExpect(header().doesNotExist("Content-Disposition"))
            .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("private payroll","SELECT","PK");
        assertThat(Arrays.copyOf(response.getContentAsByteArray(),2)).isNotEqualTo(new byte[]{(byte)'P',(byte)'K'});
        assertThat(spools()).isEqualTo(before);verify(transactions).rollback(any());
        reset(logs);when(logs.insert(any())).thenReturn(1);reset(transactions);
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        doNothing().doThrow(new IllegalStateException("audit commit")).when(transactions).commit(any());
        mvc.perform(get("/api/individual-profitability/export")).andExpect(status().isInternalServerError()).andExpect(header().doesNotExist("Content-Disposition"));
        assertThat(spools()).isEqualTo(before);
    }
    @Test void sourceFailureNeverLogsOrLeaksAndSuccessfulTransferAlsoDeletesSpool() throws Exception {
        var before=spools();download("");assertThat(spools()).isEqualTo(before);clearInvocations(logs);
        when(repository.loadYear(any())).thenThrow(new IllegalStateException("sensitive payroll"));
        mvc.perform(get("/api/individual-profitability/export")).andExpect(status().isInternalServerError())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive"))));
        verifyNoInteractions(logs);assertThat(spools()).isEqualTo(before);
    }
    @Test void ui03AllowsOnlyOwnKindAndDownloadActionWhileLegacyExportPrecisionRemainsText() throws Exception {
        var writer=new AccessLogWriter(logs,users,clock);var request=new org.springframework.mock.web.MockHttpServletRequest();
        for(var event:List.of(new AccessLogWriter.Event("ACCT_VIEW","UI-03",null,"개인원가수익",1L),
            new AccessLogWriter.Event("DNLD","UI-03",null,"개발자",1L),new AccessLogWriter.Event("DNLD","UI-03","123","개인원가수익",1L)))
            assertThatThrownBy(() -> writer.append(event,request)).isInstanceOf(com.sicost.common.api.ApiException.class);
        var columns=List.of(new ExcelExportService.Column("value","금액",ExcelExportService.CellType.NUMBER));
        try(var file=excel.generate(new ExcelExportService.Specification("영업파트",null,columns,"","원"),sink -> sink.accept(List.of(new java.math.BigDecimal("12345678901234567"))),request)) {
            var bytes=new java.io.ByteArrayOutputStream();file.transferTo(bytes);
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {assertThat(book.getSheetAt(0).getRow(1).getCell(0).getCellType()).isEqualTo(CellType.STRING);}
        }
        try(var file=excel.generateWorkbook(new ExcelExportService.Specification("개인원가수익",null,columns,"","원","UI-03"),
            () -> new ExcelExportService.WorkbookData(List.of(new ExcelExportService.Sheet("개인원가수익",columns,sink -> sink.accept(List.of(new java.math.BigDecimal("12345678901234567"))))),List.of(),1,"2026"),request)) {
            var bytes=new java.io.ByteArrayOutputStream();file.transferTo(bytes);
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {assertThat(book.getSheetAt(0).getRow(1).getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);}
        }
    }
}
