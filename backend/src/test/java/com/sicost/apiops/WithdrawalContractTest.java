package com.sicost.apiops;

import com.sicost.assignmentsops.AssignmentActionsRepository;
import com.sicost.common.api.*;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.context.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.apiops.WithdrawalDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest(WithdrawalController.class)
@org.springframework.transaction.annotation.EnableTransactionManagement
@ContextConfiguration(classes=WithdrawalController.class)
@Import({WithdrawalService.class,WithdrawalRepository.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class WithdrawalContractTest {
    @Autowired MockMvc mvc;
    @MockBean WithdrawalMapper mapper;
    @MockBean AssignmentActionsRepository assignments;
    @MockBean AssignmentMonthlyService monthly;
    @MockBean CurrentUserProvider users;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    List<Source> rows;
    LocalDate today=LocalDate.of(2026,11,15);
    @BeforeEach void setup(){
        rows=new ArrayList<>();
        when(clock.instant()).thenReturn(Instant.parse("2026-11-14T15:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        when(monthly.cashBase()).thenReturn(YearMonth.of(2025,1));when(assignments.revision(anyString())).thenReturn("full-assignment-revision");
        when(mapper.sources(any())).thenAnswer(c->List.copyOf(rows));when(mapper.activeJob(anyString())).thenReturn(true);
        when(mapper.nextAssignments(any())).thenReturn(List.of());when(mapper.leaves(anyString())).thenReturn(List.of());
        when(mapper.positions(anyString())).thenReturn(List.of(position(1,"2026-12","BE",null,1,"MO")));
    }
    Source source(String id,LocalDate date,String extension){return new Source(id,"D-001","개발자","00001","BE",null,"MD","FT","P-001","종료 프로젝트","BE",date,2,true,extension,"END","Y","DRCT","IDLE",today.minusYears(1),null,"C-001");}
    Position position(int serial,String month,String job,String related,int left,String type){return new Position("O-001",serial,job,"MD",month,12,left,type,related,"C-001",new BigDecimal("70"));}
    @Test void publicListKeepsAssignmentsAndFilterWideTotalAndDistinctPeople() throws Exception {
        for(int i=0;i<25;i++)rows.add(source("A-"+String.format("%03d",i),today,"M"));
        rows.add(source("A-X",today,"H"));
        mvc.perform(get("/api/withdrawals").param("year","2026").param("extension","M").param("job","BE").param("size","20"))
            .andExpect(DurableOpenApi.response("GET","/api/withdrawals",200))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(20)).andExpect(jsonPath("$.data.totalCount").value(25))
            .andExpect(jsonPath("$.data.guidance.withinThreeMonthsPeople").value(1)).andExpect(jsonPath("$.data.guidance.withoutNextAssignmentPeople").value(1))
            .andExpect(jsonPath("$.data.guidance.withMatchingPositionsPeople").value(1)).andExpect(jsonPath("$.data.items[0].developer.managementNumber").value("00001"));
        verify(users).currentUser();verify(manager,atLeastOnce()).commit(any());
    }
    @ParameterizedTest @CsvSource({"period,90D","extension,''","size,10","page,0","year,2024","year,2028","actor,x","score,100","sort,remainingDays","job,invalid"})
    void invalidQueryHasFieldAndRequestId(String field,String value) throws Exception {
        if(field.equals("job"))when(mapper.activeJob("invalid")).thenReturn(false);
        mvc.perform(get("/api/withdrawals").param(field,value==null?"":value)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.field").value(field)).andExpect(jsonPath("$.requestId").isNotEmpty()).andExpect(jsonPath("$.message").isNotEmpty());
    }
    @Test void duplicatedQueryIsRejected() throws Exception {mvc.perform(get("/api/withdrawals").param("period","ALL","SOON")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("period"));}
    @Test void getBodyInjectionIsRejected() throws Exception {mvc.perform(get("/api/withdrawals").content("{\"actor\":\"x\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("body"));}
    @Test void unsetIsNullOnlyAndAllIsNotUnset() throws Exception {
        rows.add(source("A-1",today,null));rows.add(source("A-2",today,"M"));
        mvc.perform(get("/api/withdrawals").param("extension","UNSET")).andExpect(jsonPath("$.data.totalCount").value(1)).andExpect(jsonPath("$.data.items[0].extensionPossibility").doesNotExist());
        mvc.perform(get("/api/withdrawals").param("extension","ALL")).andExpect(jsonPath("$.data.totalCount").value(2));
    }
    @Test void calendarBoundaryAndCrossYearGuidanceAreIndependentOfSelectedYear() throws Exception {
        rows.add(source("A-today",today,null));rows.add(source("A-end",LocalDate.of(2027,1,31),null));rows.add(source("A-later",LocalDate.of(2027,2,1),null));rows.add(source("A-past",today.minusDays(1),null));
        mvc.perform(get("/api/withdrawals").param("period","SOON")).andExpect(jsonPath("$.data.totalCount").value(1)).andExpect(jsonPath("$.data.items[0].remainingDays").value(0)).andExpect(jsonPath("$.data.guidance.withinThreeMonthsPeople").value(1));
        mvc.perform(get("/api/withdrawals").param("year","2027").param("period","SOON")).andExpect(jsonPath("$.data.items[0].assignmentId").value("A-end")).andExpect(jsonPath("$.data.guidance.visible").value(false));
        mvc.perform(get("/api/withdrawals").param("year","2027").param("period","LATER")).andExpect(jsonPath("$.data.items[0].assignmentId").value("A-later"));
        mvc.perform(get("/api/withdrawals").param("period","PAST")).andExpect(jsonPath("$.data.items[0].assignmentId").value("A-past")).andExpect(jsonPath("$.data.items[0].remainingDays").value(-1));
    }
    @Test void historicalStoppedDeveloperIsPreservedWithoutWriteEligibility() throws Exception {
        var s=source("A-old",today.minusYears(1),null);rows.add(new Source(s.assignmentId(),s.developerId(),s.name(),s.managementNumber(),s.developerJob(),s.subJobCode(),s.gradeCode(),s.employeeTypeCode(),s.projectId(),s.projectName(),s.jobCode(),s.withdrawalDate(),s.extensionCount(),s.earlyWithdrawal(),null,"END","N","DRCT","RETIRE",s.joinDate(),today.minusMonths(1),s.customerId()));
        mvc.perform(get("/api/withdrawals").param("year","2025")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalCount").value(1)).andExpect(jsonPath("$.data.items[0].extensionEditable").value(false)).andExpect(jsonPath("$.data.items[0].canRecommend").value(false)).andExpect(jsonPath("$.data.items[0].canChangeTentative").value(false)).andExpect(jsonPath("$.data.items[0].nextAssignments").isEmpty());
        verify(mapper,never()).nextAssignments(any());
    }
    @Test void highExtensionBlocksNewRecommendations() throws Exception {rows.add(source("A-high",today,"H"));mvc.perform(get("/api/withdrawals")).andExpect(jsonPath("$.data.items[0].canRecommend").value(false));verify(mapper,never()).positions(anyString());}
    @Test void laterBlocksNewRecommendations() throws Exception {rows.add(source("A-later",today.plusMonths(4),null));mvc.perform(get("/api/withdrawals").param("year","2027")).andExpect(jsonPath("$.data.items[0].canRecommend").value(false));}
    @Test void existingTentativeCanChangeOnHighAndLater() throws Exception {
        rows.add(source("A-high",today.plusMonths(4),"H"));when(mapper.nextAssignments(any())).thenReturn(List.of(new NextAssignment("TENTATIVE",null,"1",null,"O-001","기회",null,"2027-04")));
        mvc.perform(get("/api/withdrawals").param("year","2027")).andExpect(jsonPath("$.data.items[0].canChangeTentative").value(true)).andExpect(jsonPath("$.data.items[0].canRecommend").value(false));
    }
    @ParameterizedTest @CsvSource({"CONFIRMED","CANDIDATE"}) void confirmedOrConvertedCannotBeReplaced(String kind) throws Exception {
        rows.add(source("A-confirmed",today,"L"));when(mapper.nextAssignments(any())).thenReturn(List.of(new NextAssignment(kind,"A-next",null,"P-next",null,"프로젝트",today.plusDays(1),null)));
        mvc.perform(get("/api/withdrawals")).andExpect(jsonPath("$.data.items[0].canChangeTentative").value(false)).andExpect(jsonPath("$.data.items[0].canRecommend").value(false));
    }
    @Test void noMatchIsExplicitZeroNotFakePosition() throws Exception {rows.add(source("A-empty",today,null));when(mapper.positions(anyString())).thenReturn(List.of());mvc.perform(get("/api/withdrawals")).andExpect(jsonPath("$.data.items[0].recommendationCount").value(0)).andExpect(jsonPath("$.data.guidance.withMatchingPositionsPeople").value(0));}
    @Test void countsAreTopFiveAndPureAcrossRepeatedPaging() throws Exception {
        rows.add(source("A-1",today,null));when(mapper.positions(anyString())).thenReturn(java.util.stream.IntStream.rangeClosed(1,9).mapToObj(i->position(i,"2026-12","BE",null,1,"MO")).toList());
        for(int page:List.of(1,2,1))mvc.perform(get("/api/withdrawals").param("page",String.valueOf(page))).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalCount").value(1)).andExpect(jsonPath("$.data.guidance.withMatchingPositionsPeople").value(1));
        mvc.perform(get("/api/withdrawals")).andExpect(jsonPath("$.data.items[0].recommendationCount").value(5));
        verify(monthly,times(4)).cashBase();verify(assignments,times(4)).revision("A-1");verifyNoMoreInteractions(monthly,assignments);
    }
    @Test void calculatorUsesDirectionCapacityCalendarAndLeaveRules(){
        Source s=source("A-1",LocalDate.of(2026,11,20),null);
        List<Position> positions=List.of(position(1,"2026-12","BE",null,1,"MO"),position(2,"2027-03","PL","BE",1,"MO"),position(3,"2027-04","BE",null,1,"MO"),position(4,"2026-12","FE",null,1,"MO"),position(5,"2026-12","BE",null,0,"MO"));
        var ranked=WithdrawalPositionCalculator.calculate(s,today,positions,List.of());assertThat(ranked).hasSize(2);assertThat(ranked.get(0).waitDays()).isEqualTo(10);assertThat(ranked.get(1).gap()).isEqualTo(3);
        assertThat(WithdrawalPositionCalculator.calculate(s,today,positions,List.of(new Leave(s.developerId(),LocalDate.of(2026,12,1),LocalDate.of(2027,3,31))))).isEmpty();
    }
    @Test void internalErrorsDoNotExposeSqlOrStack() throws Exception {when(mapper.sources(any())).thenThrow(new IllegalStateException("SELECT private password"));mvc.perform(get("/api/withdrawals")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.requestId").isNotEmpty()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SELECT"))));}
    @ParameterizedTest @CsvSource({"sort,unknown","sort,","direction,down","direction,"})
    void invalidSortDoesNotReadOrWrite(String field,String value) throws Exception {
        mvc.perform(get("/api/withdrawals").param(field,value==null?"":value)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.field").value(field)).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.requestId").isNotEmpty()).andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(mapper,assignments,monthly,users);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"name","project","job","date","days","history","extension","next"})
    void allSortColumnsUseWholeSetBeforePagingAndPkTies(String sort) throws Exception {
        for(int i=25;i>=1;i--)rows.add(source(String.format("A-%02d",i),today,"H"));
        for(String direction:List.of("ASC","DESC")){
            mvc.perform(get("/api/withdrawals").param("sort",sort).param("direction",direction).param("size","20").param("page","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].assignmentId").value("A-01")).andExpect(jsonPath("$.data.items[19].assignmentId").value("A-20"));
            mvc.perform(get("/api/withdrawals").param("sort",sort).param("direction",direction).param("size","20").param("page","2"))
                .andExpect(jsonPath("$.data.items[0].assignmentId").value("A-21")).andExpect(jsonPath("$.data.items[4].assignmentId").value("A-25"));
        }
    }
    @Test void extensionNullsLastBothDirectionsAndDaysAreNumeric() throws Exception {
        rows.add(source("A-null",today,null));rows.add(source("A-2",today.plusDays(2),"H"));rows.add(source("A-10",today.plusDays(10),"L"));
        for(String direction:List.of("ASC","DESC"))mvc.perform(get("/api/withdrawals").param("sort","extension").param("direction",direction))
            .andExpect(jsonPath("$.data.items[2].assignmentId").value("A-null"));
        mvc.perform(get("/api/withdrawals").param("sort","days")).andExpect(jsonPath("$.data.items[1].assignmentId").value("A-2")).andExpect(jsonPath("$.data.items[2].assignmentId").value("A-10"));
        mvc.perform(get("/api/withdrawals").param("sort","days").param("direction","DESC")).andExpect(jsonPath("$.data.items[0].assignmentId").value("A-10"));
    }

}
