package com.sicost.workforcerecommendationsops;

import com.sicost.common.api.*;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.workforcerecommendationsops.RecommendationBrowseDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest(RecommendationBrowseController.class)
@EnableTransactionManagement
@ContextConfiguration(classes=RecommendationBrowseController.class)
@Import({RecommendationBrowseService.class,RecommendationBrowseRepository.class,RevisionService.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class RecommendationBrowseContractTest {
    @Autowired MockMvc mvc;
    @MockBean RecommendationBrowseMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;

    @BeforeEach void setup(){
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        when(clock.instant()).thenReturn(Instant.parse("2026-06-30T15:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(mapper.cashBase()).thenReturn("202501");when(mapper.firstJob()).thenReturn("BE");
        when(mapper.targets(any())).thenReturn(List.of());when(mapper.history(any(),anyLong())).thenReturn(List.of());
        when(mapper.needs(anyString())).thenReturn(List.of());
        when(mapper.opportunitySnapshot(anyString())).thenReturn("stored-opportunity");when(mapper.projectSnapshot(anyString())).thenReturn("stored-project");
    }
    TargetSource opportunity(String id,String name){return new TargetSource("OPPORTUNITY",id,name,"MO",null,null,null,"2026-08");}
    @Test void targetsArePublicAndOrderedWithNumericNeedsAndDefaults() throws Exception {
        var p=new TargetSource("PROJECT","P-001","가 프로젝트","MO",null,LocalDate.of(2026,7,1),LocalDate.of(2027,1,1),"2026-07");
        when(mapper.targets(any())).thenReturn(List.of(p,opportunity("O-002","나 기회"),opportunity("O-001","가 기회")));
        when(mapper.needs("O-001")).thenReturn(List.of(new NeedSource("10","PM",null,3,"2026-08",2,"0.50",1,"ten"),new NeedSource("2","BE","SR",1,"2026-08",2,"1.00",0,"two")));
        when(mapper.projectJob("P-001")).thenReturn("PM");
        mvc.perform(get("/api/workforce-recommendations/targets")).andExpect(DurableOpenApi.response("GET","/api/workforce-recommendations/targets",200)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(3)).andExpect(jsonPath("$.data.firstTargetId").value("O-001"))
            .andExpect(jsonPath("$.data.items[0].defaults.jobCode").value("BE")).andExpect(jsonPath("$.data.items[0].defaults.needSn").value("2"))
            .andExpect(jsonPath("$.data.items[0].defaults.requestedGradeCode").value("SR")).andExpect(jsonPath("$.data.items[0].needs[1].remainingCount").value(2))
            .andExpect(jsonPath("$.data.items[0].needs[0].revision").isNotEmpty()).andExpect(jsonPath("$.data.items[2].defaults.jobCode").value("PM"))
            .andExpect(jsonPath("$.data.serverDate").value("2026-07-01")).andExpect(jsonPath("$.data.currentMonth").value("2026-07"))
            .andExpect(jsonPath("$.data.baseMonth").value("2025-01")).andExpect(jsonPath("$.data.revision").isNotEmpty());
        verify(mapper).targets(LocalDate.of(2026,7,1));verify(users).currentUser();
    }
    @Test void noTargetsReturnsNullFirstAndNoRound() throws Exception {
        mvc.perform(get("/api/workforce-recommendations/targets")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.firstTargetId").value(org.hamcrest.Matchers.nullValue()));
        verify(mapper,never()).history(any(),anyLong());
    }
    @Test void absentNeedsAndPlanUseActiveJobFallback() throws Exception {
        when(mapper.targets(any())).thenReturn(List.of(opportunity("O-001","기회"),new TargetSource("PROJECT","P-001","프로젝트","MO",null,LocalDate.of(2026,7,1),LocalDate.of(2027,1,1),"2026-07")));
        mvc.perform(get("/api/workforce-recommendations/targets")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].defaults.jobCode").value("BE"))
            .andExpect(jsonPath("$.data.items[0].defaults.needSn").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("$.data.items[1].defaults.jobCode").value("BE"));
    }
    @ParameterizedTest @ValueSource(strings={"page","size","actor","revision","unknown"}) void targetsRejectEveryQuery(String key) throws Exception {
        mvc.perform(get("/api/workforce-recommendations/targets").param(key,"1")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(key)).andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(mapper,users);
    }
    @Test void historyIsPublicAndPreservesDeletedTargetAndJob() throws Exception {
        var row=new HistoryItem("0012","AI","O-DELETED","OLD","2025-01","SR","2025-01-01 10:00:00","admin",2);
        when(mapper.history(any(),anyLong())).thenReturn(List.of(row));when(mapper.historyCount(any())).thenReturn(31L);
        mvc.perform(get("/api/workforce-recommendations/history").param("targetType"," OPPORTUNITY ").param("targetId","O-DELETED").param("job","OLD").param("size","20"))
            .andExpect(DurableOpenApi.response("GET","/api/workforce-recommendations/history",200))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].targetId").value("O-DELETED"))
            .andExpect(jsonPath("$.data.items[0].jobCode").value("OLD")).andExpect(jsonPath("$.data.items[0].startMonth").value("2025-01"))
            .andExpect(jsonPath("$.data.totalCount").value(31)).andExpect(jsonPath("$.data.size").value(20));
        HistoryQuery expected=new HistoryQuery("OPPORTUNITY","O-DELETED","OLD",null,1,20);
        verify(mapper).history(expected,0);verify(mapper).historyCount(expected);verify(mapper,never()).firstJob();verify(mapper,never()).targets(any());
    }
    @Test void emptyHistoryKeepsDefaultPageAndSize() throws Exception {
        mvc.perform(get("/api/workforce-recommendations/history")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.size").value(50)).andExpect(jsonPath("$.data.totalCount").value(0));
    }
    @Test void pageOffsetAndAllFiltersReachBothQueries() throws Exception {
        mvc.perform(get("/api/workforce-recommendations/history").param("targetType","PROJECT").param("targetId","P-001").param("job","BE").param("recommendationType","AI").param("page","2").param("size","100")).andExpect(status().isOk());
        var q=new HistoryQuery("PROJECT","P-001","BE","AI",2,100);verify(mapper).history(q,100);verify(mapper).historyCount(q);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","abc","1.5","10000000000",""}) void invalidPage(String value) throws Exception {invalid("page",value);}
    @ParameterizedTest @ValueSource(strings={"10","0","21","","500"}) void invalidSize(String value) throws Exception {invalid("size",value);}
    @Test void targetTypeNeedsId() throws Exception {invalid("targetType","PROJECT","targetId");}
    @Test void targetIdNeedsType() throws Exception {invalid("targetId","P-001","targetType");}
    @Test void invalidTargetType() throws Exception {invalid("targetType","CAND");}
    @Test void invalidRecommendationType() throws Exception {invalid("recommendationType","OTHER");}
    @Test void blankJob() throws Exception {invalid("job"," ");}
    @Test void overlongJob() throws Exception {invalid("job","ABCDEFGHIJK");}
    @Test void overlongId() throws Exception {invalid("targetId","A".repeat(21));}
    @Test void unknownHistoryQuery() throws Exception {invalid("executedBy","injected");}
    @Test void duplicateQueryIsRejected() throws Exception {
        mvc.perform(get("/api/workforce-recommendations/history").param("job","BE","PM")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("job"));verifyNoInteractions(mapper);
    }
    @Test void repeatedReadsUseOnlyReadMapperMethodsAndReadOnlyTransactions() throws Exception {
        for(int i=0;i<3;i++){mvc.perform(get("/api/workforce-recommendations/targets")).andExpect(status().isOk());mvc.perform(get("/api/workforce-recommendations/history").param("page",String.valueOf(i+1))).andExpect(status().isOk());}
        verify(manager,times(6)).getTransaction(argThat(d->d.isReadOnly()&&d.getIsolationLevel()==4));
        assertThat(Arrays.stream(RecommendationBrowseMapper.class.getMethods()).map(java.lang.reflect.Method::getName))
            .noneMatch(n->n.matches("(?i).*(insert|update|delete|nextId|audit|sequence).*"));
    }
    @Test void internalFailureDoesNotExposeSql() throws Exception {
        when(mapper.targets(any())).thenThrow(new IllegalStateException("private SQL password"));
        mvc.perform(get("/api/workforce-recommendations/targets")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.requestId").isNotEmpty())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));
    }
    void invalid(String key,String value) throws Exception {invalid(key,value,key);}
    void invalid(String key,String value,String field) throws Exception {
        mvc.perform(get("/api/workforce-recommendations/history").param(key,value)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.matchesPattern(".*[가-힣].*"))).andExpect(jsonPath("$.requestId").isNotEmpty());verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"sort,unknown","sort,","direction,down","direction,"})
    void invalidSortDoesNotReadOrWrite(String field,String value) throws Exception {invalid(field,value==null?"":value);}
    @ParameterizedTest @ValueSource(strings={"rcmdId","recommendationType","targetId","jobCode","startMonth","requestedGradeCode","executedAt","executedBy","candidateCount"})
    void optionalSortReachesMapper(String sort) throws Exception {
        for(String direction:List.of("ASC","DESC")){
            mvc.perform(get("/api/workforce-recommendations/history").param("sort",sort).param("direction",direction)).andExpect(status().isOk())
                .andExpect(DurableOpenApi.response("GET","/api/workforce-recommendations/history",200));
            verify(mapper).history(new HistoryQuery(null,null,null,null,1,50,sort,direction),0);
        }
    }

}
