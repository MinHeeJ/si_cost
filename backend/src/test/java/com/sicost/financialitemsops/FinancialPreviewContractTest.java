package com.sicost.financialitemsops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.financial.FinancialCalculation;
import com.sicost.common.history.ChangeHistoryMapper;
import com.sicost.financialitems.FinancialDtos;
import com.sicost.financialitems.FinancialRepository;
import com.sicost.shared.SharedQueriesMapper;
import com.sicost.shared.SharedQueryDtos.CloseRow;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.financialitemsops.FinancialPreviewDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FinancialPreviewController.class)
@ContextConfiguration(classes=FinancialPreviewController.class)
@Import({FinancialPreviewService.class,FinancialPreviewRepository.class,RevisionService.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class FinancialPreviewContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @Autowired FinancialPreviewService service;
    @MockBean FinancialRepository editorRepository;
    @MockBean FinancialPreviewMapper mapper;
    @MockBean SharedQueriesMapper shared;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    ObjectNode stored;ArrayNode previous;
    @BeforeEach void setup(){
        stored=json.createObjectNode();for(String k:List.of("items","adjustments","transfers","loans","cards","cardMonths","cardPayments","taxes","taxPayments"))stored.putArray(k);
        ((ArrayNode)stored.get("taxes")).addObject().put("tax_yr","2026").put("est_tax_amt","18400000");previous=json.createArrayNode();
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));
        when(clock.instant()).thenReturn(Instant.parse("2026-07-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());when(shared.cashBaseMonth()).thenReturn("202501");when(shared.monthCloses(anyString(),anyString())).thenReturn(List.of());
        when(mapper.snapshot(anyString())).thenAnswer(c->stored.toString());when(mapper.previousItems(anyString())).thenAnswer(c->previous.toString());
        when(editorRepository.snapshot(any())).thenAnswer(c->{
            Map<FinancialDtos.Kind,List<ObjectNode>> rows=new EnumMap<>(FinancialDtos.Kind.class);
            for(FinancialDtos.Kind kind:FinancialDtos.Kind.values()){
                String key=switch(kind){case PAYMENTS->"cardPayments";case CARD_MONTH->"cardMonths";case ANNUAL->"taxes";case TAX_PAYMENTS->"taxPayments";default->kind.collection();};
                List<ObjectNode> values=new ArrayList<>();stored.path(key).forEach(row->values.add((ObjectNode)row));
                rows.put(kind,values);
            }
            return new FinancialDtos.Snapshot(rows);
        });
        when(mapper.yearStatus(anyString())).thenAnswer(c->IntStream.rangeClosed(1,12).mapToObj(m->new StoredMonth(c.getArgument(0)+String.format("-%02d",m),m==1?"CLOSE":"WORK",m==2?3:0)).toList());
        when(mapper.taxMonths(anyString())).thenAnswer(c->IntStream.rangeClosed(1,12).mapToObj(m->new FinancialCalculation.TaxMonth("2026"+String.format("-%02d",m),"WORK",null,null,null)).toList());
    }
    ObjectNode body(){return json.createObjectNode().put("month","2026-07").put("expectedRevision",service.revision(YearMonth.of(2026,7))).put("action","CALCULATE");}
    ResultActions post(ObjectNode b)throws Exception{return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/financial-items/preview").contentType("application/json").content(b.toString()));}
    ObjectNode item(String name,String amount){return json.createObjectNode().put("finClass","SGA").put("itemName",name).put("amount",amount);}
    ObjectNode storedItem(ArrayNode rows,String month,String id,String name){return rows.addObject().put("fin_ym",month).put("item_sn",id).put("fin_cls_cd","SGA").putNull("sga_item_cd").put("item_nm",name).put("amt","7").put("vat_amt","1").put("io_dt","2026-06-30").put("rpt_yn","N").put("auto_fill_yn","N").putNull("rmrk").putNull("upld_id");}
    ObjectNode add(ObjectNode b,String collection,ObjectNode row){b.putObject(collection).putArray("add").add(row);return b;}
    void invalid(ObjectNode b,String field)throws Exception{post(b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));verifyNoInteractions(history);}
    @Test void publicYearStatusHasExactlyTwelveStoredCountsActualCloseAndFutureNull()throws Exception{mvc.perform(get("/api/financial-items/year-status").param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.months.length()").value(12)).andExpect(jsonPath("$.data.months[0].closeStatus").value("CLOSE")).andExpect(jsonPath("$.data.months[1].itemCount").value(3)).andExpect(jsonPath("$.data.months[7].itemCount").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("$.data.months[7].selectable").value(false));verify(users).currentUser();verifyNoInteractions(history);}
    @Test void yearBeforeOperatingMonthIsReadOnlyButSelectable()throws Exception{when(shared.cashBaseMonth()).thenReturn("202608");mvc.perform(get("/api/financial-items/year-status").param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.months[6].selectable").value(true)).andExpect(jsonPath("$.data.months[6].editable").value(false));}
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","REG_USER_ID"})
    void yearActorSpoofRejected(String field)throws Exception{mvc.perform(get("/api/financial-items/year-status").param("year","2026").param(field,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper,history);}
    @ParameterizedTest @ValueSource(strings={"2027","0000","2026.0","1e3","2026-07","2024"})
    void invalidYearLeavesSourcesUntouched(String value)throws Exception{mvc.perform(get("/api/financial-items/year-status").param("year",value)).andExpect(status().isBadRequest());verifyNoInteractions(mapper,history);}
    @Test void duplicateYearRejected()throws Exception{mvc.perform(get("/api/financial-items/year-status").param("year","2026","2025")).andExpect(status().isBadRequest());}
    @Test void yearReadNeverInvokesPreviewOrHistory()throws Exception{String before=stored.toString();mvc.perform(get("/api/financial-items/year-status").param("year","2026")).andExpect(status().isOk());assertThat(stored.toString()).isEqualTo(before);verify(mapper,never()).snapshot(anyString());verifyNoInteractions(history);}
    @Test void calculateOverlaysBothTabsPreservesOmittedRowsAndDoesNotWrite()throws Exception{
        storedItem((ArrayNode)stored.get("items"),"202607","1","저장항목").putNull("io_dt");
        ObjectNode b=add(body(),"items",item("신규항목","100").put("vatAmount","20"));
        add(b,"transfers",json.createObjectNode().put("date","2026-07-02").put("classCode","EXP").put("itemName","이체").put("amount","9"));String before=stored.toString();
        post(b).andExpect(status().isOk()).andExpect(jsonPath("$.data.contributions.sgaAmount").value("107")).andExpect(jsonPath("$.data.contributions.sgaVatAmount").value("21")).andExpect(jsonPath("$.data.contributions.transferTotal").value("9")).andExpect(jsonPath("$.data.copiedCount").value(0));assertThat(stored.toString()).isEqualTo(before);verify(users).currentUser();verifyNoInteractions(history);
    }
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","REG_USER_ID","autoFill","uploadId"})
    void previewRejectsSpoofedServerFields(String field)throws Exception{post(body().put(field,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper,history);}
    @Test void nestedActorRejected()throws Exception{post(add(body(),"items",item("항목","1").put("regUserId","forged"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verifyNoInteractions(mapper,history);}
    @ParameterizedTest @ValueSource(strings={"1.2","1e3","1000000000000000","-1","","+1","hello"})
    void rejectsInvalidAmountWithoutChangingSnapshot(String amount)throws Exception{String before=stored.toString();invalid(add(body(),"items",item("항목",amount)),"amount");assertThat(stored.toString()).isEqualTo(before);}
    @Test void normalizationDoesNotRoundAndPreservesExplicitZero()throws Exception{post(add(body(),"items",item("항목"," 1,234 원 ").put("vatAmount","0"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.contributions.sgaAmount").value("1234"));post(add(body(),"items",item("항목","0"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.contributions.sgaAmount").value("0"));}
    @Test void amountPlusVatRangeRejected()throws Exception{invalid(add(body(),"items",item("항목","999999999999999").put("vatAmount","1")),"items.amount");}
    @Test void numericAmountIsNotCoerced()throws Exception{invalid(add(body(),"items",item("항목","1").put("amount",1)),"amount");}
    @Test void adjustmentZeroRejected()throws Exception{invalid(add(body(),"adjustments",json.createObjectNode().put("targetMonth","2026-01").put("itemCode","SGA").put("amount","0").put("reason","조정")),"amount");}
    @Test void negativeTransferRejected()throws Exception{invalid(add(body(),"transfers",json.createObjectNode().put("date","2026-07-01").put("classCode","EXP").put("itemName","이체").put("amount","-1")),"amount");}
    @Test void missingDeleteReferenceRejected()throws Exception{ObjectNode b=body();b.putObject("items").putArray("deleteIds").add("99");invalid(b,"items.itemSn");}
    @Test void duplicateDeleteReferenceRejected()throws Exception{storedItem((ArrayNode)stored.get("items"),"202607","1","항목");ObjectNode b=body();b.putObject("items").putArray("deleteIds").add("1").add("1");invalid(b,"items.deleteIds");}
    @Test void otherMonthItemCannotBeDeletedEvenIfItsCashIsSelected()throws Exception{storedItem((ArrayNode)stored.get("items"),"202606","1","항목");ObjectNode b=body();b.putObject("items").putArray("deleteIds").add("1");invalid(b,"items.itemSn");}
    @Test void monthDateMismatchRejected()throws Exception{invalid(add(body(),"loans",json.createObjectNode().put("date","2026-06-01").put("direction","IN").put("amount","1")),"loans.date");}
    @Test void invalidCalendarDayRejected()throws Exception{invalid(add(body(),"items",item("항목","1").put("ioDate","2026-02-30")),"ioDate");}
    @Test void staleRevisionRejectedWithoutCalculating()throws Exception{post(body().put("expectedRevision","stale")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));verifyNoInteractions(history);}
    @Test void previewUsesEditorRevisionInsteadOfCalculationSnapshotRevision()throws Exception{
        ObjectNode request=body();
        assertThat(request.path("expectedRevision").asText()).isNotEqualTo(revisions.revision(stored));
        post(request).andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(request.path("expectedRevision").asText()));
        post(request.put("expectedRevision",revisions.revision(stored))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        verifyNoInteractions(history);
    }
    @Test void closedMonthRejected()throws Exception{when(shared.monthCloses("202607","202607")).thenReturn(List.of(new CloseRow("202607","CLOSE")));post(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MONTH_CLOSED"));verifyNoInteractions(history);}
    @Test void pastYearPreviewRejected()throws Exception{post(body().put("month","2025-12")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERIOD_READ_ONLY"));}
    @Test void beforeBasePreviewRejected()throws Exception{when(shared.cashBaseMonth()).thenReturn("202608");post(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERIOD_READ_ONLY"));}
    @Test void futurePreviewRejected()throws Exception{invalid(body().put("month","2026-08"),"month");}
    @Test void closedCashMonthRejected()throws Exception{when(shared.monthCloses("202606","202606")).thenReturn(List.of(new CloseRow("202606","CLOSE")));post(add(body(),"items",item("항목","1").put("ioDate","2026-06-30"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MONTH_CLOSED"));}
    @Test void previousCopiesEveryAbsentKeyRowInOrderAndSecondClickAddsZero()throws Exception{
        storedItem(previous,"202606","1"," 임대료 ");storedItem(previous,"202606","2","임대료");storedItem(previous,"202606","3","유지");storedItem((ArrayNode)stored.get("items"),"202607","1","유지");
        String before=stored.toString();ObjectNode b=body().put("action","LOAD_PREVIOUS");String response=post(b).andExpect(status().isOk()).andExpect(jsonPath("$.data.copiedCount").value(2)).andExpect(jsonPath("$.data.addedDrafts[0].sourceItemSn").value("1")).andExpect(jsonPath("$.data.addedDrafts[1].sourceItemSn").value("2")).andExpect(jsonPath("$.data.addedDrafts[0].ioDate").value("2026-07-30")).andExpect(jsonPath("$.data.addedDrafts[0].autoFill").value("N")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        ArrayNode additions=b.putObject("items").putArray("add");for(var row:json.readTree(response).path("data").path("addedDrafts")){ObjectNode r=(ObjectNode)row.deepCopy();r.remove(List.of("sourceMonth","sourceItemSn","autoFill","errors"));r.putObject("copySource").put("month",row.path("sourceMonth").asText()).put("itemSn",row.path("sourceItemSn").asText()).put("mode","PREVIOUS");additions.add(r);}
        post(b).andExpect(status().isOk()).andExpect(jsonPath("$.data.copiedCount").value(0)).andExpect(jsonPath("$.data.message").value("불러올 항목이 없습니다"));assertThat(stored.toString()).isEqualTo(before);verifyNoInteractions(history);
    }
    @Test void previousKeyDistinguishesNullAndEmptyCode()throws Exception{storedItem(previous,"202606","1","동명").put("sga_item_cd","");storedItem((ArrayNode)stored.get("items"),"202607","1","동명");post(body().put("action","LOAD_PREVIOUS")).andExpect(status().isOk()).andExpect(jsonPath("$.data.copiedCount").value(1));}
    @Test void noPreviousPreservesExistingDraft()throws Exception{post(add(body().put("action","LOAD_PREVIOUS"),"items",item("초안","5"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.copiedCount").value(0)).andExpect(jsonPath("$.data.contributions.sgaAmount").value("5"));}
    @Test void lastMonthReceivesExactRemainder()throws Exception{when(clock.instant()).thenReturn(Instant.parse("2026-12-15T00:00:00Z"));post(body().put("month","2026-12")).andExpect(status().isOk()).andExpect(jsonPath("$.data.allocation.months[11].amount").value("1533337")).andExpect(jsonPath("$.data.contributions.corporateTaxAllocation").value("1533337"));}
    @Test void negativeAllocationIsRetainedAndWarned()throws Exception{when(mapper.taxMonths(anyString())).thenReturn(List.of(new FinancialCalculation.TaxMonth("2026-01","CLOSE",new BigDecimal("20000000"),"Y","N"),new FinancialCalculation.TaxMonth("2026-07","WORK",null,null,null)));post(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.contributions.corporateTaxAllocation").value("-1600000")).andExpect(jsonPath("$.data.allocation.warnings").isNotEmpty());}
    @Test void noOpenMonthsNeverDividesByZero()throws Exception{when(mapper.taxMonths(anyString())).thenReturn(List.of(new FinancialCalculation.TaxMonth("2026-01","CLOSE",BigDecimal.ONE,"Y","N")));post(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.allocation.openMonthCount").value(0));}
    @Test void missingClosedSnapshotIsUnknownNotZero()throws Exception{when(mapper.taxMonths(anyString())).thenReturn(List.of(new FinancialCalculation.TaxMonth("2026-01","CLOSE",null,null,null),new FinancialCalculation.TaxMonth("2026-07","WORK",null,null,null)));post(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.allocation.verified").value(false)).andExpect(jsonPath("$.data.allocation.remainingAmount").value(org.hamcrest.Matchers.nullValue()));}
    @Test void annualChangeCannotHideMissingClosedSnapshot()throws Exception{when(mapper.taxMonths(anyString())).thenReturn(List.of(new FinancialCalculation.TaxMonth("2026-01","CLOSE",null,null,null)));post(body().put("estimatedTax","1")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERIOD_READ_ONLY"));}
    @Test void cardManualZeroIsCashZeroEvenWithPayments()throws Exception{((ArrayNode)stored.get("cards")).addObject().put("card_id","K-01").put("use_yn","Y");ObjectNode b=body();ObjectNode c=b.putObject("cards");c.putArray("payments").addObject().put("cardId","K-01").put("amount","9");c.putObject("cashOverride").put("mode","MANUAL").put("amount","0");post(b).andExpect(status().isOk()).andExpect(jsonPath("$.data.contributions.cashCardExpense").value("0"));}
    @Test void automaticCashOverrideCannotCarryAmount()throws Exception{ObjectNode b=body();b.putObject("cards").putObject("cashOverride").put("mode","AUTO").put("amount","0");invalid(b,"cards.cashOverride.amount");}
    @Test void explicitDeletionDoesNotDeleteOmittedRowsOrOtherMonthSameOrdinal()throws Exception{
        storedItem((ArrayNode)stored.get("items"),"202606","1","현금월 참조").put("io_dt","2026-07-01");storedItem((ArrayNode)stored.get("items"),"202607","1","선택월").putNull("io_dt");storedItem((ArrayNode)stored.get("items"),"202607","2","유지").putNull("io_dt");
        ObjectNode b=body();b.putObject("items").putArray("deleteIds").add("1");post(b).andExpect(status().isOk()).andExpect(jsonPath("$.data.contributions.sgaAmount").value("7")).andExpect(jsonPath("$.data.contributions.cashFinancialExpense").value("16"));assertThat(stored.path("items")).hasSize(3);
    }
    @Test void updateOverlaysOnlySpecifiedIdentityAndPreservesOriginalSnapshot()throws Exception{
        storedItem((ArrayNode)stored.get("items"),"202607","1","원래").putNull("io_dt").put("upld_id","999999999999");storedItem((ArrayNode)stored.get("items"),"202607","2","유지").putNull("io_dt");String before=stored.toString();ObjectNode b=body();b.putObject("items").putArray("update").add(item("변경","20").put("itemSn","1"));post(b).andExpect(status().isOk()).andExpect(jsonPath("$.data.contributions.sgaAmount").value("27"));assertThat(stored.toString()).isEqualTo(before);
    }
    @Test void sameIdentityCannotBeUpdatedAndDeleted()throws Exception{storedItem((ArrayNode)stored.get("items"),"202607","1","원래").putNull("io_dt");ObjectNode b=body(),c=b.putObject("items");c.putArray("deleteIds").add("1");c.putArray("update").add(item("변경","20").put("itemSn","1"));invalid(b,"items.itemSn");}
    @Test void nonexistentSgaReferenceIsRejected()throws Exception{invalid(add(body(),"items",item("항목","1").put("sgaItemCode","MISSING")),"items.sgaItemCode");}
    @Test void copiedInvalidReferenceIsReportedOnDraftWithoutWrites()throws Exception{storedItem(previous,"202606","1","코드항목").put("sga_item_cd","MISSING");post(body().put("action","LOAD_PREVIOUS")).andExpect(status().isOk()).andExpect(jsonPath("$.data.addedDrafts[0].errors[0].field").value("sgaItemCode"));verifyNoInteractions(history);}
    @Test void adjustmentUsesSignedProfitWithoutReversingCost()throws Exception{when(shared.monthCloses("202601","202601")).thenReturn(List.of(new CloseRow("202601","CLOSE")));ObjectNode b=add(body(),"adjustments",json.createObjectNode().put("targetMonth","2026-01").put("itemCode","COST").put("amount","-7").put("reason","마감 차액"));post(b).andExpect(status().isOk()).andExpect(jsonPath("$.data.contributions.adjustmentProfit").value("-7")).andExpect(jsonPath("$.data.contributions.cashFinancialExpense").value("0"));verifyNoInteractions(history);}
    @Test void adjustmentToReopenedMonthRejected()throws Exception{invalid(add(body(),"adjustments",json.createObjectNode().put("targetMonth","2026-01").put("itemCode","COST").put("amount","-7").put("reason","차액")),"adjustments.targetMonth");}
    @Test void loanDirectionControlsCashSignButStoredAmountRemainsPositive()throws Exception{ObjectNode b=add(body(),"loans",json.createObjectNode().put("date","2026-07-01").put("direction","OUT").put("amount","9"));post(b).andExpect(status().isOk()).andExpect(jsonPath("$.data.contributions.cashLoanOut").value("9")).andExpect(jsonPath("$.data.contributions.cashLoanNet").value("-9"));}
    @Test void confirmedTaxPaymentCannotBeEdited()throws Exception{((ArrayNode)stored.get("taxPayments")).addObject().put("tax_yr","2026").put("pay_type_cd","MID").put("pay_yn","Y").put("pay_due_dt","2026-08-31").put("pay_amt","1");ObjectNode b=body();b.putArray("taxSchedules").addObject().put("year","2026").put("payType","MID").put("dueDate","2026-08-31").put("payAmount","0");post(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAX_ALREADY_PAID"));}
}
