package com.sicost.operatingstandards;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.sicost.operatingstandards.OperatingStandardsDtos.*;

@WebMvcTest(OperatingStandardsController.class)
@org.springframework.test.context.ContextConfiguration(classes=com.sicost.common.CostManagementApplication.class)
@Import({OperatingStandardsService.class,RevisionService.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class OperatingStandardsRulesTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean OperatingStandardsRepository mapper;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryWriter history;
    @MockBean CommitInvalidation invalidation;
    @MockBean Clock clock;
    List<Stored> rows;
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(users.userId()).thenReturn("admin");rows=new ArrayList<>();
        add("CASH_BASE_YM","202601","202601");add("CASH_BASE_AMT","202601","1000");add("VAT_RT","202601","10");
        add("DUN_D1","202601","7");add("DUN_D2","202601","30");add("DUN_D3","202601","60");
        for(String key:OperatingStandardsInput.GROUPS.get(1))add(key,"202601","50");
        for(String key:OperatingStandardsInput.GROUPS.get(2))add(key,"202601","50");
        add("BILL_MISS_RT","202601","0.5");add("BILL_MISS_MIN","202601","10000");add("WTHDR_NOTI_D","202601","30");
        List<String> ai=OperatingStandardsInput.GROUPS.get(4);for(int i=0;i<ai.size();i++)add(ai.get(i),"202601",i==0?"30":"10");
        when(mapper.all()).thenAnswer(call->sorted(rows));
        when(mapper.rows(anyString(),anyBoolean())).thenAnswer(call->byKey(call.getArgument(0)));
        when(mapper.closedMonths()).thenReturn(List.of());
        when(mapper.update(any())).thenAnswer(call->{Mutation m=call.getArgument(0);rows.removeIf(row->row.key().equals(m.original().key())&&row.month().equals(m.month()));rows.add(mutated(m));return 1;});
        when(mapper.insert(any())).thenAnswer(call->{rows.add(mutated(call.getArgument(0)));return 1;});
    }
    Stored mutated(Mutation m) {return new Stored(m.original().key(),m.month(),m.original().name(),m.value(),m.original().unitNm(),m.original().description(),"admin",m.now(),"admin",m.now());}
    void add(String key,String month,String value) {rows.add(new Stored(key,month,"시험 "+key,new BigDecimal(value),"단위","시험 설명","SYSTEM",null,null,null));}
    List<Stored> sorted(List<Stored> values) {return values.stream().sorted(Comparator.comparing(Stored::key).thenComparing(Stored::month,Comparator.reverseOrder())).toList();}
    List<Stored> byKey(String key) {return sorted(rows).stream().filter(row->row.key().equals(key)).toList();}
    String token(String key) {return revisions.revision(byKey(key));}
    Map<String,Object> change(String key,String value) {return Map.of("key",key,"value",value,"expectedRevision",token(key));}
    org.springframework.test.web.servlet.ResultActions save(String month,List<?> changes) throws Exception {
        Map<String,Object> body=new HashMap<>();body.put("changes",changes);if(month!=null)body.put("effectiveMonth",month);
        return mvc.perform(put("/api/operating-standards").contentType("application/json").content(json.writeValueAsString(body)));
    }
    void unchanged() {verify(mapper,never()).update(any());verify(mapper,never()).insert(any());verifyNoInteractions(history,invalidation);}
    @Test void listIsSortedNonpagedAndDoesNotUseFutureOrInventZero() throws Exception {
        add("VAT_RT","202607","99");add("FUTURE_ONLY","202607","88");
        int extra=43-(int)rows.stream().map(Stored::key).distinct().count();for(int i=0;i<extra;i++)add("TEST_"+i,"202601","1");
        var response=mvc.perform(get("/api/operating-standards")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(43)).andReturn();
        var items=json.readTree(response.getResponse().getContentAsString()).at("/data/items");
        List<String> keys=new ArrayList<>();items.forEach(row->{keys.add(row.get("key").asText());if(row.get("key").asText().equals("VAT_RT"))assertThat(row.get("value").asText()).isEqualTo("10");if(row.get("key").asText().equals("FUTURE_ONLY")){assertThat(row.get("value").isNull()).isTrue();assertThat(row.get("effectiveMonth").isNull()).isTrue();}});
        assertThat(keys).isSorted();unchanged();
    }
    @Test void historyIsDescendingWithCurrentFuturePastAndRowRevisions() throws Exception {
        add("VAT_RT","202603","11");add("VAT_RT","202607","12");
        mvc.perform(get("/api/operating-standards/VAT_RT/history")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].status").value("예정")).andExpect(jsonPath("$.data.items[1].status").value("현재"))
            .andExpect(jsonPath("$.data.items[2].status").value("지난")).andExpect(jsonPath("$.data.items[0].effectiveMonth").value("2026-07"))
            .andExpect(jsonPath("$.data.keyRevision").value(token("VAT_RT"))).andExpect(jsonPath("$.data.items[0].revision").value(revisions.revision(byKey("VAT_RT").get(0))));unchanged();
    }
    @Test void absentHistoryReturns404() throws Exception {mvc.perform(get("/api/operating-standards/ABSENT/history")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));unchanged();}
    @Test void keyWideRevisionChangesEvenWithNullTimestamps() throws Exception {
        String old=token("VAT_RT");add("VAT_RT","202701","15");
        save("2026-06",List.of(Map.of("key","VAT_RT","value","11","expectedRevision",old))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));unchanged();
    }
    @Test void normalOverwriteAndInsertKeepOtherMonthsAndActor() throws Exception {
        save("2026-01",List.of(change("VAT_RT","11"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        save("2026-07",List.of(change("VAT_RT","12"))).andExpect(status().isOk());
        assertThat(byKey("VAT_RT")).extracting(Stored::month).containsExactly("202607","202601");
        verify(history).append(argThat(c->"UPD".equals(c.type())&&"10".equals(c.before())&&"11".equals(c.after())));
        verify(history).append(argThat(c->"NEW".equals(c.type())&&c.before()==null&&"12".equals(c.after())));
        verify(mapper).update(argThat(m->m.actor().equals("admin")));
    }
    @Test void noChangeWritesNoTimestampHistoryOrInvalidation() throws Exception {save("2026-01",List.of(change("VAT_RT","10.0000"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false)).andExpect(jsonPath("$.data.message").value("바뀐 내용이 없습니다."));unchanged();}
    @ParameterizedTest @ValueSource(strings={"","-1","1e2","NaN","Infinity","1x","100000000000","1.00001"})
    void decimalValidation(String value) throws Exception {save("2026-06",List.of(change("VAT_RT",value))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("changes[0].value"));unchanged();}
    @ParameterizedTest @ValueSource(strings={"24","26"})
    void aiSum99And101AreRejected(String value) throws Exception {save("2026-06",List.of(change("AI_SCORE_JOB",value),change("AI_SCORE_SKILL","15"))).andExpect(status().isBadRequest());unchanged();}
    @Test void aiExact100LocksAllEightAndWritesOnlyChangedTwo() throws Exception {
        save("2026-06",List.of(change("AI_SCORE_JOB","25"),change("AI_SCORE_SKILL","15"))).andExpect(status().isOk());
        for(String key:OperatingStandardsInput.GROUPS.get(4))verify(mapper).rows(key,true);
        verify(mapper,times(2)).insert(any());verify(history,times(2)).append(any());
    }
    @ParameterizedTest @CsvSource({"DUN_D1,0","DUN_D1,30","DUN_D2,60","DUN_D3,30","EXT_PRBL_H,101","STEP_PRBL_1,101","WTHDR_NOTI_D,0","WTHDR_NOTI_D,1.5","WTHDR_NOTI_D,1.0"})
    void eachDistinctConstraintIsRejected(String key,String value) throws Exception {save("2026-06",List.of(change(key,value))).andExpect(status().isBadRequest());unchanged();}
    @ParameterizedTest @CsvSource({"EXT_PRBL_H,0","EXT_PRBL_H,100","STEP_PRBL_1,0","STEP_PRBL_1,100","WTHDR_NOTI_D,1","WTHDR_NOTI_D,99999999999","VAT_RT,99999999999.9999","BILL_MISS_RT,150","DUN_D1,1.5"})
    void inclusiveRangesAndNoInventedUnitUpperBound(String key,String value) throws Exception {save("2026-06",List.of(change(key,value))).andExpect(status().isOk());}
    @Test void duplicateKey400() throws Exception {save("2026-06",List.of(change("VAT_RT","11"),change("VAT_RT","12"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("changes[1].key"));unchanged();}
    @Test void newKey400() throws Exception {save("2026-06",List.of(change("NOT_A_KEY","11"))).andExpect(status().isBadRequest());unchanged();}
    @Test void missingMonth400() throws Exception {save(null,List.of(change("VAT_RT","11"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("effectiveMonth"));unchanged();}
    @Test void cashAndNormalMixture400() throws Exception {save(null,List.of(change("CASH_BASE_AMT","11"),change("VAT_RT","12"))).andExpect(status().isBadRequest());unchanged();}
    @Test void cashEffectiveMonth400() throws Exception {save("2026-06",List.of(change("CASH_BASE_AMT","11"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("effectiveMonth"));unchanged();}
    @ParameterizedTest @ValueSource(strings={"202600","202613","000001","2026-01","202601.0","20261"})
    void cashMonthMustBeRealSixDigitInteger(String value) throws Exception {save(null,List.of(change("CASH_BASE_YM",value))).andExpect(status().isBadRequest());unchanged();}
    @Test void cashOldClosedMonthCannotBeBypassedByNewMonth() throws Exception {when(mapper.closedMonths()).thenReturn(List.of("202601"));save(null,List.of(change("CASH_BASE_YM","202607"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));unchanged();}
    @Test void cashAmountIsAlsoLockedByOldStart() throws Exception {when(mapper.closedMonths()).thenReturn(List.of("202601"));save(null,List.of(change("CASH_BASE_AMT","11"))).andExpect(status().isConflict());unchanged();}
    @Test void cashUpdateKeepsSingleRowAndOriginalEffectiveMonth() throws Exception {
        save(null,List.of(change("CASH_BASE_YM","202602"))).andExpect(status().isOk());
        save(null,List.of(change("CASH_BASE_AMT","1234.5678"))).andExpect(status().isOk());
        assertThat(byKey("CASH_BASE_YM")).hasSize(1).first().extracting(Stored::month).isEqualTo("202601");verify(mapper,never()).insert(any());
        verify(history).append(argThat(c->c.type().equals("UPD")&&c.before().equals("202601")&&c.after().equals("202602")));
        verify(invalidation,times(2)).afterCommit("period-context");
    }
    @Test void beforeBaseMinimum409() throws Exception {save("2025-12",List.of(change("VAT_RT","11"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));unchanged();}
    @Test void beforeLastClosePlusOne409() throws Exception {when(mapper.closedMonths()).thenReturn(List.of("202605"));save("2026-05",List.of(change("VAT_RT","11"))).andExpect(status().isConflict());unchanged();}
    @Test void actualChosenMonthIsNotClampedToNow() throws Exception {save("2026-02",List.of(change("VAT_RT","11"))).andExpect(status().isOk());verify(mapper).insert(argThat(m->m.month().equals("202602")));}
    @ParameterizedTest @ValueSource(strings={"9","11"})
    void futureAiBoundaryFailurePreventsEveryWrite(String value) throws Exception {
        add("AI_SCORE_LOC","202608",value);
        save("2026-06",List.of(change("AI_SCORE_JOB","25"),change("AI_SCORE_SKILL","15"),change("VAT_RT","11"))).andExpect(status().isBadRequest());unchanged();
        for(String key:OperatingStandardsInput.GROUPS.get(4))verify(mapper).rows(key,true);
    }
    @Test void futureDunBoundaryFailurePreventsEveryWrite() throws Exception {add("DUN_D2","202608","5");save("2026-06",List.of(change("DUN_D1","8"))).andExpect(status().isBadRequest());unchanged();}
    @Test void locksAreInAscendingKeyOrderIncludingUnchangedCashAndGroup() throws Exception {
        save("2026-06",List.of(change("DUN_D1","8"))).andExpect(status().isOk());var order=inOrder(mapper);
        for(String key:List.of("CASH_BASE_AMT","CASH_BASE_YM","DUN_D1","DUN_D2","DUN_D3"))order.verify(mapper).lockKey(key);
    }
    @Test void staleSecondSave409() throws Exception {
        var change=change("VAT_RT","11");save("2026-01",List.of(change)).andExpect(status().isOk());save("2026-01",List.of(change)).andExpect(status().isConflict());verify(mapper,times(1)).update(any());
    }
    @Test void metadataInjection400() throws Exception {Map<String,Object> input=new HashMap<>(change("VAT_RT","11"));input.put("unitNm","원");save("2026-06",List.of(input)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));unchanged();}
    @Test void missingRequiredDbContextIsServerErrorNotFabricatedAuth() throws Exception {when(users.currentUser()).thenThrow(new IllegalStateException("internal"));mvc.perform(get("/api/operating-standards")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal"))));unchanged();}
    @Test void contractLoadedFromClasspath() throws Exception {assertThat(new org.springframework.core.io.ClassPathResource("contracts/openapi.yaml").getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("operationId: getOperatingStandards","operationId: saveOperatingStandards","operationId: getOperatingStandardHistory");}
}
