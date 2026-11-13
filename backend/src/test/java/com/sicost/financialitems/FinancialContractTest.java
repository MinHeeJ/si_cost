package com.sicost.financialitems;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.shared.SharedQueriesMapper;
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
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.financialitems.FinancialDtos.*;
import static com.sicost.financialitems.FinancialRepository.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FinancialController.class)
@ContextConfiguration(classes=FinancialController.class)
@Import({FinancialService.class,FinancialRepository.class,FinancialCommands.class,FinancialWriteService.class,WriteCoordinator.class,RevisionService.class,ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class FinancialContractTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired FinancialService service;@Autowired FinancialRepository repository;
    @MockBean FinancialMapper mapper;@MockBean SharedQueriesMapper shared;@MockBean MasterLockMapper master;@MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;@MockBean AggregateLockMapper locks;@MockBean CommitInvalidation invalidation;@MockBean PlatformTransactionManager manager;@MockBean Clock clock;@MockBean FinancialEditorSources sources;
    Map<Kind,List<ObjectNode>> rows;List<String> closed;List<ChangeHistoryEntry> audits;
    @BeforeEach void setup(){
        rows=new EnumMap<>(Kind.class);for(Kind kind:Kind.values())rows.put(kind,new ArrayList<>());closed=new ArrayList<>();audits=new ArrayList<>();
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));when(users.userId()).thenReturn("admin");
        when(clock.instant()).thenReturn(Instant.parse("2026-07-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());when(shared.cashBaseMonth()).thenReturn("202501");when(mapper.closedMonths()).thenAnswer(c->closed.stream().sorted(Comparator.reverseOrder()).toList());
        when(mapper.code(anyString(),anyString())).thenReturn("Y");when(mapper.greatestCardNumber()).thenReturn("9");when(mapper.nextId(any())).thenReturn("101");
        when(mapper.rows(any())).thenAnswer(c->{Scope s=c.getArgument(0);return rows.get(s.kind()).stream().filter(r->s.kind().scope==null||s.kind().isDated()?s.kind().scope==null||text(r,s.kind().scope).startsWith(s.start().toString().substring(0,7)):s.month().equals(text(r,s.kind().scope))).sorted(Comparator.comparing(r->s.kind().isNumericId()?String.format("%020d",r.path(s.kind().idColumn).asLong()):text(r,s.kind().idColumn))).map(ObjectNode::toString).toList();});
        when(mapper.insert(any())).thenAnswer(c->{Mutation m=c.getArgument(0);ObjectNode row=json.createObjectNode();m.cells().forEach(cell->row.set(cell.column(),json.valueToTree(cell.value())));row.put("reg_user_id",m.actor()).put("reg_dttm",m.now().toString()).putNull("mod_user_id").putNull("mod_dttm");rows.get(m.scope().kind()).add(row);return 1;});
        when(mapper.update(any())).thenAnswer(c->{Mutation m=c.getArgument(0);ObjectNode row=find(m);m.cells().forEach(cell->row.set(cell.column(),json.valueToTree(cell.value())));row.put("mod_user_id",m.actor()).put("mod_dttm",m.now().toString());return 1;});
        when(mapper.delete(any())).thenAnswer(c->{Mutation m=c.getArgument(0);rows.get(m.scope().kind()).remove(find(m));return 1;});
        when(history.insert(any())).thenAnswer(c->{audits.add(c.getArgument(0));return 1;});
        when(sources.allocationValue(anyString())).thenReturn(new com.sicost.corporatetaxesops.CorporateTaxDtos.Allocation("2026","0","0","0",12,List.of(),true,List.of()));
    }
    ObjectNode find(Mutation m){return rows.get(m.scope().kind()).stream().filter(r->text(r,m.scope().kind().idColumn).equals(m.id().toString())).filter(r->m.scope().kind()!=Kind.TAX_PAYMENTS||text(r,"pay_type_cd").equals(m.cells().get(0).value())).findFirst().orElseThrow();}
    ObjectNode row(Kind kind,String data)throws Exception{ObjectNode row=(ObjectNode)json.readTree(data);row.put("reg_user_id","admin").put("reg_dttm","2026-07-01T09:00:00").putNull("mod_user_id").putNull("mod_dttm");rows.get(kind).add(row);return row;}
    ObjectNode item(String month,int id,String amount)throws Exception{return row(Kind.ITEMS,"{\"fin_ym\":\""+month+"\",\"item_sn\":"+id+",\"fin_cls_cd\":\"SGA\",\"sga_item_cd\":null,\"item_nm\":\"비용\",\"amt\":"+amount+",\"vat_amt\":0,\"io_dt\":null,\"rpt_yn\":\"Y\",\"auto_fill_yn\":\"N\",\"rmrk\":null,\"upld_id\":null}");}
    ObjectNode body(){return json.createObjectNode().put("month","2026-07").put("expectedRevision",service.revision(repository.snapshot(YearMonth.of(2026,7))));}
    ObjectNode value(){return json.createObjectNode().put("finClass","SGA").put("itemName","비용").put("amount","10").put("repeat","Y");}
    ResultActions save(ObjectNode body)throws Exception{return mvc.perform(put("/api/financial-items").contentType("application/json").content(body.toString()));}
    ResultActions read(String month)throws Exception{return mvc.perform(get("/api/financial-items").param("month",month));}
    void noWrites(){verify(mapper,never()).insert(any());verify(mapper,never()).update(any());verify(mapper,never()).delete(any());verifyNoInteractions(history,invalidation);}
    @Test void publicGetDefaultsToServerMonthAndDoesNotInitializeAnything()throws Exception{mvc.perform(get("/api/financial-items")).andExpect(status().isOk()).andExpect(jsonPath("$.data.month").value("2026-07")).andExpect(jsonPath("$.data.cardMonth.exists").value(false)).andExpect(jsonPath("$.data.items").isArray()).andExpect(jsonPath("$.data.revision").isString());noWrites();}
    @Test void readReturnsStringAmountsIdentifiersAndStoredOrder()throws Exception{item("202607",10,"999999999999999");item("202607",2,"0");read("2026-07").andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].itemSn").value("2")).andExpect(jsonPath("$.data.items[1].amount").value("999999999999999"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","REG_USER_ID"}) void forgedReadActorIsUnknown(String field)throws Exception{mvc.perform(get("/api/financial-items").param(field,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","REG_USER_ID","autoFill"}) void forgedWriteActorIsUnknown(String field)throws Exception{save(body().put(field,"forged")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"2026-13","2026-7","0000-01","July"}) void malformedReadMonth(String month)throws Exception{read(month).andExpect(status().isBadRequest());noWrites();}
    @Test void futureReadIsReadOnlyConflict()throws Exception{read("2026-08").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERIOD_READ_ONLY"));noWrites();}
    @Test void recurringDraftOnlyWhenNoStoredItemsAndClampsMonthEnd()throws Exception{item("202606",1,"10").put("io_dt","2026-05-31");read("2026-07").andExpect(jsonPath("$.data.automaticDrafts[0].ioDate").value("2026-07-31")).andExpect(jsonPath("$.data.automaticDrafts[0].autoFill").value("Y"));item("202607",2,"0");read("2026-07").andExpect(jsonPath("$.data.automaticDrafts").isEmpty());noWrites();}
    @Test void closedReadNeverCreatesDrafts()throws Exception{item("202606",1,"10");closed.add("202607");read("2026-07").andExpect(jsonPath("$.data.editable").value(false)).andExpect(jsonPath("$.data.automaticDrafts").isEmpty());noWrites();}
    @Test void previousYearNeverCreatesDrafts()throws Exception{item("202506",1,"10");read("2025-07").andExpect(jsonPath("$.data.editable").value(false)).andExpect(jsonPath("$.data.automaticDrafts").isEmpty());noWrites();}
    @Test void beforeOperatingStartIsReadableWithoutDrafts()throws Exception{read("2024-12").andExpect(status().isOk()).andExpect(jsonPath("$.data.editable").value(false));noWrites();}
    @Test void deletedDraftCodeBecomesRowErrorAndInactiveIsKept()throws Exception{item("202606",1,"10").put("sga_item_cd","REMOVED");when(mapper.code("SGA_ITEM","REMOVED")).thenReturn(null);read("2026-07").andExpect(jsonPath("$.data.automaticDrafts[0].errors[0].field").value("sgaItemCode"));noWrites();}
    @Test void targetMonthsUseLatestActualCloseAndDescendingTwelveMonthWindow()throws Exception{closed.addAll(List.of("202607","202606","202508","202507","202506"));assertThat(repository.targets(YearMonth.of(2026,7))).containsExactly("2026-06","2025-08");}
    @Test void noOpDoesNotTouchTimesHistoryOrInvalidation()throws Exception{item("202607",1,"10");ObjectNode body=body();body.putObject("items").putArray("update").add(value().put("itemSn","1"));save(body).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));noWrites();}
    @Test void createsBothTabsPreservesOmittedRowsAndAuditsActualFields()throws Exception{item("202607",9,"20");ObjectNode body=body();body.putObject("items").putArray("add").add(value());body.putObject("loans").putArray("add").addObject().put("date","2026-07-15").put("direction","OUT").put("amount","1");save(body).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));assertThat(rows.get(Kind.ITEMS)).hasSize(2);assertThat(rows.get(Kind.LOANS)).hasSize(1);assertThat(audits).anySatisfy(a->{assertThat(a.tableName()).isEqualTo("TB_FI_ITEM");assertThat(a.targetKey()).isEqualTo("202607:10");assertThat(a.userId()).isEqualTo("admin");});}
    @Test void explicitDeleteKeepsFullBeforeValues()throws Exception{item("202607",1,"20");ObjectNode body=body();body.putObject("items").putArray("deleteIds").add("1");save(body).andExpect(status().isOk());assertThat(rows.get(Kind.ITEMS)).isEmpty();assertThat(audits).anySatisfy(a->{assertThat(a.item()).isEqualTo("AMT");assertThat(a.before()).isEqualTo("20");assertThat(a.after()).isNull();assertThat(a.type()).isEqualTo("DEL");});}
    @ParameterizedTest @ValueSource(strings={"1.5","1e3","1000000000000000","-1",""}) void invalidMoneyDoesNotWriteEitherTab(String amount)throws Exception{ObjectNode body=body();body.putObject("items").putArray("add").add(value().put("amount",amount));save(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.financial.violations[0].field").value("amount"));noWrites();}
    @Test void requiredAmountCannotBeMissing()throws Exception{ObjectNode body=body(),value=value();value.remove("amount");body.putObject("items").putArray("add").add(value);save(body).andExpect(status().isBadRequest());noWrites();}
    @Test void hiddenTabValidationPreventsAnyWrite()throws Exception{ObjectNode body=body();body.putObject("items").putArray("add").add(value());body.putObject("loans").putArray("add").addObject().put("date","2026-07-01").put("direction","IN").put("amount","0");save(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.financial.violations[0].tab").value("CARD_TRANSFER_LOAN"));noWrites();}
    @Test void duplicateUpdatesRejected()throws Exception{item("202607",1,"10");ObjectNode body=body();body.putObject("items").putArray("update").add(value().put("itemSn","1")).add(value().put("itemSn","1"));save(body).andExpect(status().isBadRequest());noWrites();}
    @Test void updateAndDeleteSameIdentityRejected()throws Exception{item("202607",1,"10");ObjectNode body=body(),changes=body.putObject("items");changes.putArray("update").add(value().put("itemSn","1"));changes.putArray("deleteIds").add("1");save(body).andExpect(status().isBadRequest());noWrites();}
    @Test void otherMonthIdentityCannotBeModified()throws Exception{item("202606",1,"10");ObjectNode body=body();body.putObject("items").putArray("update").add(value().put("itemSn","1"));save(body).andExpect(status().isBadRequest());noWrites();}
    @Test void adjustmentZeroRejected()throws Exception{ObjectNode body=body();body.putObject("adjustments").putArray("add").addObject().put("targetMonth","2026-06").put("itemCode","COST").put("amount","0").put("reason","정정");save(body).andExpect(status().isBadRequest());noWrites();}
    @Test void releasedAdjustmentCanOnlyBeDeleted()throws Exception{row(Kind.ADJUSTMENTS,"{\"adj_id\":1,\"aply_ym\":\"202607\",\"trgt_ym\":\"202606\",\"adj_item_cd\":\"COST\",\"adj_amt\":-1,\"adj_rsn\":\"정정\"}");ObjectNode body=body();body.putObject("adjustments").putArray("update").addObject().put("adjustmentId","1").put("targetMonth","2026-06").put("itemCode","COST").put("amount","-2").put("reason","정정");save(body).andExpect(status().isBadRequest());noWrites();}
    @Test void staleRevisionIsCheckedBeforeDomainValidation()throws Exception{ObjectNode body=body().put("expectedRevision","stale");body.putObject("items").putArray("add").add(value().put("amount","bad"));save(body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();}
    @Test void selectedCloseRejectsEvenNoop()throws Exception{closed.add("202607");save(body()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MONTH_CLOSED"));noWrites();}
    @Test void oldCashCloseRejectsChangedItem()throws Exception{item("202607",1,"10").put("io_dt","2026-06-15");closed.add("202606");ObjectNode body=body();body.putObject("items").putArray("update").add(value().put("itemSn","1").put("amount","11"));save(body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MONTH_CLOSED"));noWrites();}
    @Test void newCashCloseRejectsChangedItem()throws Exception{closed.add("202606");ObjectNode body=body();body.putObject("items").putArray("add").add(value().put("ioDate","2026-06-15"));save(body).andExpect(status().isConflict());noWrites();}
    @Test void futureCashMonthAllowed()throws Exception{ObjectNode body=body();body.putObject("items").putArray("add").add(value().put("ioDate","2027-01-15"));save(body).andExpect(status().isOk());}
    @Test void itemSequenceExhaustionDoesNotRenumber()throws Exception{item("202607",99999,"10");ObjectNode body=body();body.putObject("items").putArray("add").add(value());save(body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ID_EXHAUSTED"));noWrites();}
    @Test void closeLockPrecedesSortedBusinessLocks()throws Exception{save(body()).andExpect(status().isOk());var order=inOrder(master,locks);order.verify(master).lockClose();order.verify(locks).lock("CASH_MONTH:202607");order.verify(locks).lock("CORPORATE_TAX:2026");order.verify(locks).lock("FINANCIAL_CARDS");order.verify(locks).lock("FINANCIAL_MONTH:202607");}
    ObjectNode card()throws Exception{return row(Kind.CARDS,"{\"card_id\":\"K-09\",\"card_nm\":\"법인카드\",\"card_no_l4\":\"0001\",\"use_yn\":\"Y\"}");}
    @Test void manualZeroIsStillManualAndParentPrecedesPayment()throws Exception{card();ObjectNode body=body(),cards=body.putObject("cards");cards.putArray("payments").addObject().put("cardId","K-09").put("amount","0");cards.putObject("cashOverride").put("mode","MANUAL").put("amount","0");save(body).andExpect(status().isOk()).andExpect(jsonPath("$.data.editor.cardMonth.editYn").value("Y"));assertThat(text(rows.get(Kind.CARD_MONTH).get(0),"edit_yn")).isEqualTo("Y");var captor=org.mockito.ArgumentCaptor.forClass(Mutation.class);verify(mapper,times(2)).insert(captor.capture());assertThat(captor.getAllValues().stream().map(m->m.scope().kind()).toList()).containsExactly(Kind.CARD_MONTH,Kind.PAYMENTS);}
    @Test void stoppedCardCannotAcquireNewPositivePayment()throws Exception{card().put("use_yn","N");ObjectNode body=body();body.putObject("cards").putArray("payments").addObject().put("cardId","K-09").put("amount","1");save(body).andExpect(status().isBadRequest());noWrites();}
    @Test void stoppedExistingPositiveMayBeCorrected()throws Exception{card().put("use_yn","N");row(Kind.PAYMENTS,"{\"pay_ym\":\"202607\",\"card_id\":\"K-09\",\"pay_amt\":10}");ObjectNode body=body();body.putObject("cards").putArray("payments").addObject().put("cardId","K-09").put("amount","11");save(body).andExpect(status().isOk());assertThat(text(rows.get(Kind.PAYMENTS).get(0),"pay_amt")).isEqualTo("11");}
    @Test void duplicateCardReturnsExistingCardAndRestoreGuidance()throws Exception{card();ObjectNode body=body();body.putObject("cards").putArray("add").addObject().put("clientKey","new").put("cardName","법인카드").put("last4","0001");save(body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CARD_EXISTS")).andExpect(jsonPath("$.details.financial.card.cardId").value("K-09"));noWrites();}
    @Test void newCardUsesGlobalNumericMaximumAndStringMapping()throws Exception{ObjectNode body=body();body.putObject("cards").putArray("add").addObject().put("clientKey","new").put("cardName","법인카드").put("last4","0001");save(body).andExpect(status().isOk()).andExpect(jsonPath("$.data.createdCards[0].cardId").value("K-10"));}
    @Test void auditFailureReturnsInternalErrorAndRollsBackTransaction()throws Exception{doThrow(new IllegalStateException("private SQL")).when(history).insert(any());ObjectNode body=body();body.putObject("items").putArray("add").add(value());save(body).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));verify(manager,atLeastOnce()).rollback(any());verifyNoInteractions(invalidation);}
}
