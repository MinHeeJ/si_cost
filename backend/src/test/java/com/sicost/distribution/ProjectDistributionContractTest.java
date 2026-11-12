package com.sicost.distribution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.sicost.distribution.ProjectDistributionDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectDistributionController.class)
@ContextConfiguration(classes=ProjectDistributionController.class)
@Import({ProjectDistributionService.class,ProjectDistributionRepository.class,RevisionService.class,
         ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ProjectDistributionContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean ProjectDistributionMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean Clock clock;
    Project project;
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.withZone(any())).thenReturn(Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        project=new Project("P-901","배분 검증",null,"TK","ING","2026-06-01","2026-07-31",null,null,null,null,"101",null,"0","0","N",null,null,null,null);
        when(mapper.project(any())).thenReturn(project);
        when(mapper.cells(anyString())).thenReturn(List.of(cell("EST",1,"2026-06","DEV","D-1","100")));
        when(mapper.jobs(anyString())).thenReturn(List.of(new Job("D-1","PM"),new Job("D-1","백엔드"),new Job("D-1","PM")));
        when(mapper.actuals(anyString())).thenReturn(List.of());
        when(mapper.uploads(anyString())).thenReturn(List.of());
        when(mapper.aggregate(anyString())).thenReturn(new Aggregate("project","[]","[]","[]","stages","[]"));
    }
    static StoredCell cell(String step,int row,String month,String type,String dev,String amount) {
        return new StoredCell(step,row,month,type,dev,"항목",new BigDecimal(amount),dev==null?null:"M-"+dev,dev==null?null:"이름 "+dev,dev==null?null:"Y");
    }
    @Test void distributionWithoutAuthenticationReturnsOrderedExactMatrixAndJobs() throws Exception {
        mvc.perform(get("/api/projects/P-901/distribution").param("step","EST").header("X-Request-Id","distribution-read"))
            .andExpect(status().isOk()).andExpect(header().string("X-Request-Id","distribution-read"))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.viewedStep").value("EST")).andExpect(jsonPath("$.data.selectedStep").value("EST"))
            .andExpect(jsonPath("$.data.rows[0].jobNames.length()").value(2))
            .andExpect(jsonPath("$.data.months[1]").value("2026-07"))
            .andExpect(jsonPath("$.data.monthlyTotals[1].amount").value("0"))
            .andExpect(jsonPath("$.data.totalAmount").value("100")).andExpect(jsonPath("$.data.revision").isString());
        verify(users).currentUser();verify(mapper).project(new ProjectLookup("P-901",LocalDate.of(2026,6,15)));
    }
    @Test void comparisonWithoutAuthenticationWarnsEvenOneWon() throws Exception {
        mvc.perform(get("/api/projects/P-901/distribution/comparison"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.differenceAmount").value("1"))
            .andExpect(jsonPath("$.data.mismatchCount").value(1)).andExpect(jsonPath("$.data.items[0].type").value("NO_ASSIGNMENT"))
            .andExpect(jsonPath("$.data.warnings.length()").value(2));verify(users).currentUser();
    }
    @Test void comparisonAggregatesAssignmentsPerPersonMonthWithoutFallingBackToEstimate() throws Exception {
        when(mapper.cells("P-901")).thenReturn(List.of(
            cell("EST",1,"2026-06","DEV","D-1","100"),
            cell("PLAN",1,"2026-06","DEV","D-2","0")));
        when(mapper.actuals("P-901")).thenReturn(List.of(
            new Actual("D-1","M-D-1","개발자 일","2026-06",new BigDecimal("0.50")),
            new Actual("D-1","M-D-1","개발자 일","2026-06",new BigDecimal("0.25")),
            new Actual("D-2","M-D-2","개발자 이","2026-06",BigDecimal.ONE)));
        mvc.perform(get("/api/projects/P-901/distribution/comparison").header("X-Request-Id","comparison-month"))
            .andExpect(status().isOk()).andExpect(header().string("X-Request-Id","comparison-month"))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.selectedStep").value("PLAN"))
            .andExpect(jsonPath("$.data.totalAmount").value("0"))
            .andExpect(jsonPath("$.data.mismatchCount").value(1)).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].developerId").value("D-1"))
            .andExpect(jsonPath("$.data.items[0].type").value("NO_DISTRIBUTION"))
            .andExpect(jsonPath("$.data.items[0].actualMm").value("0.75"));
        verify(mapper).actuals("P-901");verify(mapper).cells("P-901");
    }
    @Test void uploadsWithoutAuthenticationReturnsImmutableRecordedMetadata() throws Exception {
        when(mapper.uploads("P-901")).thenReturn(List.of(new UploadRecord(9,"EST","첫파일.xlsx","처리자","2026-06-15 12:00:00","APPLIED","2026-06-15 12:01:00")));
        mvc.perform(get("/api/projects/P-901/distribution/uploads"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].step").value("EST"))
            .andExpect(jsonPath("$.data.items[0].uploadId").value(9)).andExpect(jsonPath("$.data.items[0].actor").value("처리자"));
        verify(users).currentUser();verify(mapper,never()).cells(anyString());
    }
    @ParameterizedTest @ValueSource(strings={"","/comparison","/uploads"})
    void unknownQueryNeverReachesDatabase(String suffix) throws Exception {
        var request=get("/api/projects/P-901/distribution"+suffix).param("year","2026");
        if(suffix.isEmpty()) request.param("step","EST");
        mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("year"));
        verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"","/comparison","/uploads"})
    void unknownJsonNeverReachesDatabase(String suffix) throws Exception {
        var request=get("/api/projects/P-901/distribution"+suffix).contentType("application/json").content("{\"regUserId\":\"injected\"}");
        if(suffix.isEmpty())request.param("step","EST");
        mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("regUserId"));verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"","bad","est","2026-13","PLANPLANPLAN"})
    void invalidStepIsFieldError(String value) throws Exception {
        mvc.perform(get("/api/projects/P-901/distribution").param("step",value)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT")).andExpect(jsonPath("$.field").value("step"));verifyNoInteractions(mapper);
    }
    @Test void missingAndRepeatedStepAreInvalid() throws Exception {
        mvc.perform(get("/api/projects/P-901/distribution")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("step"));
        mvc.perform(get("/api/projects/P-901/distribution").param("step","EST","PLAN")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"","/comparison","/uploads"})
    void overlongIdIsInvalidWithoutQuery(String suffix) throws Exception {
        var request=get("/api/projects/"+"P".repeat(21)+"/distribution"+suffix);if(suffix.isEmpty())request.param("step","EST");
        mvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("id"));verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"","/comparison","/uploads"})
    void missingProjectIs404(String suffix) throws Exception {
        when(mapper.project(any())).thenReturn(null);
        var request=get("/api/projects/missing/distribution"+suffix);if(suffix.isEmpty())request.param("step","EST");
        mvc.perform(request).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
    @ParameterizedTest @ValueSource(strings={"","/comparison","/uploads"})
    void emptyRealSourcesAreSuccessfulWithoutFabricatedRows(String suffix) throws Exception {
        when(mapper.cells(anyString())).thenReturn(List.of());
        var request=get("/api/projects/P-901/distribution"+suffix);if(suffix.isEmpty())request.param("step","EST");
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath(suffix.isEmpty()?"$.data.rows":"$.data.items").isEmpty());
    }
    @Test void nonTurnkeyIsInvalidNotCallerForbidden() throws Exception {
        when(mapper.project(any())).thenReturn(new Project("P-901","월단위",null,"MO","ING","2026-01-01","2026-12-31",null,null,null,null,"1",null,"0","0","N",null,null,null,null));
        mvc.perform(get("/api/projects/P-901/distribution/comparison")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("id"));
    }
    @Test void databaseFailureDoesNotLeakSqlOrStackAndPreservesRequestId() throws Exception {
        when(mapper.project(any())).thenThrow(new IllegalStateException("SELECT private_column stacktrace"));
        mvc.perform(get("/api/projects/P-901/distribution/uploads").header("X-Request-Id","safe-error"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.requestId").value("safe-error"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private_column"))));
    }
    @Test void allZeroPlanWinsWholeTableWhileViewedEstimateRemainsAvailable() throws Exception {
        when(mapper.cells(anyString())).thenReturn(List.of(cell("EST",1,"2026-06","DEV","D-1","100"),cell("PLAN",2,"2026-07","DEV","D-2","0")));
        mvc.perform(get("/api/projects/P-901/distribution").param("step","EST")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.selectedStep").value("PLAN")).andExpect(jsonPath("$.data.totalAmount").value("100"));
        mvc.perform(get("/api/projects/P-901/distribution/comparison")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalAmount").value("0")).andExpect(jsonPath("$.data.items").isEmpty());
    }
    @Test void comparisonCountsUniquePersonMonthTypeAndIgnoresItemsAndZeroDevPresence() {
        List<StoredCell> cells=List.of(cell("PLAN",1,"2026-06","DEV","D-1","10"),cell("PLAN",1,"2026-07","DEV","D-1","20"),
            cell("PLAN",2,"2026-06","DEV","D-2","0"),cell("PLAN",3,"2026-06","ITEM",null,"5"));
        List<Actual> actuals=List.of(new Actual("D-1","M1","일","2026-06",BigDecimal.ZERO),
            new Actual("D-2","M2","이","2026-06",BigDecimal.ONE),new Actual("D-3","M3","삼","2026-06",new BigDecimal("0.50")),
            new Actual("D-3","M3","삼","2026-06",new BigDecimal("0.25")));
        Comparison result=ProjectDistributionService.compare(new BigDecimal("34"),cells,actuals);
        assertThat(result.totalAmount()).isEqualTo("35");assertThat(result.differenceAmount()).isEqualTo("-1");assertThat(result.mismatchCount()).isEqualTo(3);
        assertThat(result.items()).extracting(Mismatch::type).containsExactly("NO_ASSIGNMENT","NO_ASSIGNMENT","NO_DISTRIBUTION");
        assertThat(result.items().get(2).actualMm()).isEqualTo("0.75");assertThat(result.warnings()).hasSize(2);
    }
    @Test void developerPresenceInAnotherMonthDoesNotHideMissingDistribution() {
        var result=ProjectDistributionService.compare(BigDecimal.ZERO,
            List.of(cell("PLAN",1,"2026-06","DEV","D-1","0")),
            List.of(new Actual("D-1","M-D-1","개발자 일","2026-06",BigDecimal.ONE),
                new Actual("D-1","M-D-1","개발자 일","2026-07",BigDecimal.ONE)));
        assertThat(result.mismatchCount()).isEqualTo(1);
        assertThat(result.items().get(0).month()).isEqualTo("2026-07");
        assertThat(result.items().get(0).type()).isEqualTo("NO_DISTRIBUTION");
        assertThat(result.items().get(0).amount()).isEqualTo("0");
    }
    @Test void arbitrarilyLargeAggregateIsExact() {
        var result=ProjectDistributionService.compare(BigDecimal.ZERO,List.of(cell("CTRT",1,"2026-06","ITEM",null,"999999999999999"),cell("CTRT",2,"2026-06","ITEM",null,"999999999999999")),List.of());
        assertThat(result.totalAmount()).isEqualTo("1999999999999998");assertThat(result.items()).isEmpty();
    }
}
