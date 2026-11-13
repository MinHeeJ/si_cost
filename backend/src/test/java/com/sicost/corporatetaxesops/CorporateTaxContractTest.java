package com.sicost.corporatetaxesops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.shared.SharedQueriesMapper;
import com.sicost.shared.SharedQueryDtos.CloseRow;
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
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.corporatetaxesops.CorporateTaxDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CorporateTaxController.class)
@ContextConfiguration(classes=CorporateTaxController.class)
@Import({CorporateTaxService.class,CorporateTaxCommands.class,CorporateTaxViews.class,CorporateTaxRepository.class,CorporateTaxAllocationService.class,
    WriteCoordinator.class,RevisionService.class,ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class CorporateTaxContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CorporateTaxViews views;
    @MockBean CorporateTaxMapper mapper;
    @MockBean SharedQueriesMapper shared;
    @MockBean MasterLockMapper master;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    Map<String,StoredAnnual> annuals;
    List<StoredPayment> payments;
    List<StoredMonth> months;
    List<ChangeHistoryEntry> audits;
    static final LocalDateTime NOW=LocalDateTime.of(2026,10,15,9,0);
    @BeforeEach void setup(){
        annuals=new HashMap<>();payments=new ArrayList<>();months=new ArrayList<>();audits=new ArrayList<>();
        annuals.put("2026",annual("2026","18400000"));
        for(int m=1;m<=12;m++)months.add(new StoredMonth(YearMonth.of(2026,m).toString(),"WORK",null,null,null));
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","임시 관리자",List.of("ADMIN"),true));when(users.userId()).thenReturn("admin");
        when(clock.instant()).thenReturn(Instant.parse("2026-10-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(clock.withZone(any())).thenReturn(clock);
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(shared.cashBaseMonth()).thenReturn("202501");when(shared.monthCloses(anyString(),anyString())).thenReturn(List.of());
        when(mapper.annual(anyString())).thenAnswer(c->annuals.get(c.getArgument(0)));
        when(mapper.payments(anyString())).thenAnswer(c->payments.stream().filter(p->p.year().equals(c.getArgument(0))).sorted(Comparator.comparing(StoredPayment::payType)).toList());
        when(mapper.months(anyString())).thenAnswer(c->List.copyOf(months));
        when(mapper.insertAnnual(any())).thenAnswer(c->{AnnualWrite w=c.getArgument(0);annuals.put(w.year(),new StoredAnnual(w.year(),w.estimatedTax(),w.actor(),w.now(),null,null));return 1;});
        when(mapper.insertPayment(any())).thenAnswer(c->{PaymentWrite w=c.getArgument(0);payments.add(new StoredPayment(w.year(),w.payType(),w.dueDate(),null,null,"N",w.actor(),w.now(),null,null));return 1;});
        when(mapper.confirm(any())).thenAnswer(c->{ConfirmationWrite w=c.getArgument(0);StoredPayment old=payments.stream().filter(p->p.year().equals(w.year())&&p.payType().equals(w.payType())).findFirst().orElseThrow();payments.remove(old);payments.add(new StoredPayment(old.year(),old.payType(),old.dueDate(),w.payAmount(),w.paidDate(),"Y",old.regUserId(),old.regDttm(),w.actor(),w.now()));return 1;});
        doAnswer(c->{audits.add(c.getArgument(0));return 1;}).when(history).insert(any());
    }
    StoredAnnual annual(String year,String amount){return new StoredAnnual(year,new BigDecimal(amount),"admin",NOW.minusDays(1),null,null);}
    void payment(String year,String type,String amount,String state){payments.add(new StoredPayment(year,type,LocalDate.parse(year+"-03-31"),amount==null?null:new BigDecimal(amount),state.equals("Y")?LocalDate.of(2026,9,1):null,state,"admin",NOW.minusDays(1),null,null));}
    ObjectNode init(){return json.createObjectNode().put("month","2026-10");}
    ObjectNode confirmation(){return json.createObjectNode().put("month","2026-10").put("expectedRevision",views.confirmationRevision("2025","CNFM")).put("paidDate","2026-10-15").put("payAmount","0");}
    ResultActions initialize(ObjectNode b)throws Exception{return mvc.perform(post("/api/corporate-taxes/initialize").contentType("application/json").content(b.toString()));}
    ResultActions confirm(ObjectNode b)throws Exception{return mvc.perform(post("/api/corporate-taxes/2025/payments/CNFM/confirmation").contentType("application/json").content(b.toString()));}
    ResultActions allocation()throws Exception{return mvc.perform(get("/api/corporate-taxes/2026/allocation"));}
    void noWrites(){verify(mapper,never()).insertAnnual(any());verify(mapper,never()).insertPayment(any());verify(mapper,never()).confirm(any());verifyNoInteractions(history,invalidation);}
    void close(String month){when(shared.monthCloses(month,month)).thenReturn(List.of(new CloseRow(month,"CLOSE")));}
    @Test void publicAllocationHasExactRemainderAndNoSideEffects()throws Exception{allocation().andExpect(status().isOk()).andExpect(jsonPath("$.data.months[0].amount").value("1533333")).andExpect(jsonPath("$.data.months[10].amount").value("1533333")).andExpect(jsonPath("$.data.months[11].amount").value("1533337")).andExpect(jsonPath("$.data.verified").value(true));noWrites();verify(users).currentUser();}
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","REG_USER_ID"}) void querySpoofRejected(String field)throws Exception{mvc.perform(get("/api/corporate-taxes/2026/allocation").param(field,"other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"0000","abc","2026.0","20260"}) void invalidYear(String year)throws Exception{mvc.perform(get("/api/corporate-taxes/"+year+"/allocation")).andExpect(status().isBadRequest());noWrites();}
    @Test void missingAnnualIsUnknownNotFabricatedZero()throws Exception{annuals.clear();allocation().andExpect(status().isOk()).andExpect(jsonPath("$.data.estimatedTax").isEmpty()).andExpect(jsonPath("$.data.months[0].amount").isEmpty()).andExpect(jsonPath("$.data.verified").value(false));noWrites();}
    @Test void closedNineMonthsArePreservedAndRemainingReallocated()throws Exception{annuals.put("2026",annual("2026","20000000"));for(int m=0;m<9;m++)months.set(m,new StoredMonth(months.get(m).month(),"CLOSE",new BigDecimal("1533333"),"Y","N"));allocation().andExpect(jsonPath("$.data.closedTotal").value("13799997")).andExpect(jsonPath("$.data.months[9].amount").value("2066667")).andExpect(jsonPath("$.data.months[10].amount").value("2066667")).andExpect(jsonPath("$.data.months[11].amount").value("2066669"));noWrites();}
    @Test void negativeRemainderIsNotClamped()throws Exception{annuals.put("2026",annual("2026","0"));months.set(0,new StoredMonth("2026-01","CLOSE",new BigDecimal("12"),"Y","N"));allocation().andExpect(jsonPath("$.data.months[1].amount").value("-1")).andExpect(jsonPath("$.data.months[11].amount").value("-2")).andExpect(jsonPath("$.data.warnings[0]").value("금액이 맞는지 확인하세요"));noWrites();}
    @Test void allClosedUsesCopiesWithoutDivision()throws Exception{for(int m=0;m<12;m++)months.set(m,new StoredMonth(months.get(m).month(),"CLOSE",BigDecimal.ONE,"Y","Y"));allocation().andExpect(jsonPath("$.data.openMonthCount").value(0)).andExpect(jsonPath("$.data.months[11].amount").value("1"));noWrites();}
    @Test void missingClosedCopyMakesOpenAmountsUnverified()throws Exception{months.set(0,new StoredMonth("2026-01","CLOSE",null,null,null));allocation().andExpect(jsonPath("$.data.verified").value(false)).andExpect(jsonPath("$.data.months[0].amount").isEmpty()).andExpect(jsonPath("$.data.months[1].amount").isEmpty()).andExpect(jsonPath("$.data.warnings[0]").value("마감 법인세 자료를 확인할 수 없습니다"));noWrites();}
    @Test void operatingStartFiltersOnlyEarlierMonthsNotFuture()throws Exception{when(shared.cashBaseMonth()).thenReturn("202604");allocation().andExpect(jsonPath("$.data.months.length()").value(9)).andExpect(jsonPath("$.data.months[0].month").value("2026-04")).andExpect(jsonPath("$.data.months[8].month").value("2026-12"));noWrites();}
    @Test void initializationCopiesPreviousAndCreatesOnlyMissingThenNoop()throws Exception{annuals.remove("2026");annuals.put("2025",annual("2025","37"));initialize(init()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.corporateTax.estimatedTax").value("37"));assertThat(payments).hasSize(2).anySatisfy(p->{assertThat(p.payAmount()).isNull();assertThat(p.paidDate()).isNull();assertThat(p.payYn()).isEqualTo("N");assertThat(p.dueDate()).isIn(LocalDate.of(2026,8,31),LocalDate.of(2027,3,31));});var before=List.copyOf(payments);int count=audits.size();initialize(init()).andExpect(jsonPath("$.data.changed").value(false));assertThat(payments).isEqualTo(before);assertThat(audits).hasSize(count);}
    @Test void initializationWithoutPriorUsesZero()throws Exception{annuals.clear();initialize(init()).andExpect(jsonPath("$.data.corporateTax.estimatedTax").value("0"));}
    @Test void initializationPreservesDifferentExistingAnnualAndPaymentValues()throws Exception{payment("2026","MID","17","Y");var before=payments.get(0);initialize(init()).andExpect(jsonPath("$.data.corporateTax.estimatedTax").value("18400000"));assertThat(payments).contains(before).hasSize(2);verify(mapper,never()).insertAnnual(any());}
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","REG_USER_ID","expectedRevision"}) void initializeUnknownFields(String field)throws Exception{initialize(init().put(field,"spoof")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"2025-10","2027-01","2026-11"}) void initializeReadOnlyMonth(String month)throws Exception{initialize(init().put("month",month)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERIOD_READ_ONLY"));noWrites();}
    @Test void initializeBeforeStart()throws Exception{when(shared.cashBaseMonth()).thenReturn("202610");initialize(init().put("month","2026-09")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERIOD_READ_ONLY"));noWrites();}
    @Test void initializeClosedMonth()throws Exception{close("202610");initialize(init()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MONTH_CLOSED"));noWrites();}
    @Test void initializationAuditFailureRollsBackTransaction()throws Exception{doThrow(new IllegalStateException("private SQL")).when(history).insert(any());initialize(init()).andExpect(status().isInternalServerError()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private SQL"))));verify(manager,atLeastOnce()).rollback(any());verifyNoInteractions(invalidation);}
    @Test void confirmationAllowsPriorYearTaxZeroAndDifferentOpenActualMonth()throws Exception{payment("2025","CNFM",null,"N");close("202503");var due=payments.get(0).dueDate();confirm(confirmation().put("paidDate","2026-09-30")).andExpect(status().isOk()).andExpect(jsonPath("$.data.payment.payAmount").value("0")).andExpect(jsonPath("$.data.payment.payYn").value("Y")).andExpect(jsonPath("$.data.payment.paidDate").value("2026-09-30"));assertThat(payments.get(0).dueDate()).isEqualTo(due);assertThat(audits).extracting(ChangeHistoryEntry::item).containsExactly("PAY_DT","PAY_AMT","PAY_YN");assertThat(audits).allSatisfy(a->assertThat(a.userId()).isEqualTo("admin"));}
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","REG_USER_ID","payYn","audit"}) void confirmationUnknownFields(String field)throws Exception{payment("2025","CNFM",null,"N");confirm(confirmation().put(field,"other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"month","expectedRevision","paidDate","payAmount"}) void confirmationMissingFields(String field)throws Exception{payment("2025","CNFM",null,"N");var b=confirmation();b.remove(field);confirm(b).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"2026-02-30","2026-10-16","2026-1-01"}) void invalidActualDates(String date)throws Exception{payment("2025","CNFM",null,"N");confirm(confirmation().put("paidDate",date)).andExpect(status().isBadRequest());noWrites();}
    @ParameterizedTest @ValueSource(strings={"-1","1e3","1.5","1000000000000000",""}) void invalidActualAmount(String amount)throws Exception{payment("2025","CNFM",null,"N");confirm(confirmation().put("payAmount",amount)).andExpect(status().isBadRequest());noWrites();}
    @Test void confirmationNormalizesAmount()throws Exception{payment("2025","CNFM",null,"N");confirm(confirmation().put("payAmount"," 1,234 원 ")).andExpect(jsonPath("$.data.payment.payAmount").value("1234"));}
    @Test void confirmationRejectsLastYearActualDate()throws Exception{payment("2025","CNFM",null,"N");confirm(confirmation().put("paidDate","2025-12-31")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERIOD_READ_ONLY"));noWrites();}
    @Test void confirmationRejectsBeforeStartActualDate()throws Exception{payment("2025","CNFM",null,"N");when(shared.cashBaseMonth()).thenReturn("202610");confirm(confirmation().put("paidDate","2026-09-30")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PERIOD_READ_ONLY"));noWrites();}
    @Test void confirmationRejectsClosedActualMonth()throws Exception{payment("2025","CNFM",null,"N");close("202609");confirm(confirmation().put("paidDate","2026-09-30")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MONTH_CLOSED"));noWrites();}
    @Test void confirmationRejectsClosedSelectedMonth()throws Exception{payment("2025","CNFM",null,"N");close("202610");confirm(confirmation().put("paidDate","2026-09-30")).andExpect(status().isConflict());noWrites();}
    @Test void confirmationMissingPaymentIs404()throws Exception{confirm(confirmation()).andExpect(status().isNotFound());noWrites();}
    @Test void staleConfirmationDoesNotWrite()throws Exception{payment("2025","CNFM",null,"N");confirm(confirmation().put("expectedRevision","old")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();}
    @Test void repeatedConfirmationIsRejectedAndAmountRemainsFixed()throws Exception{payment("2025","CNFM",null,"N");var body=confirmation();confirm(body).andExpect(status().isOk());var before=payments.get(0);confirm(body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));confirm(confirmation()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAX_ALREADY_PAID"));annuals.put("2025",annual("2025","99"));assertThat(views.payment(payments.get(0)).displayAmount()).isEqualTo("0");assertThat(payments.get(0)).isEqualTo(before);}
    @Test void sameYearAnnualAndMidAffectConfirmationRevision()throws Exception{payment("2025","CNFM",null,"N");var body=confirmation();annuals.put("2025",annual("2025","1"));confirm(body).andExpect(status().isConflict());body=confirmation();payment("2025","MID","1","N");confirm(body).andExpect(status().isConflict());noWrites();}
    @Test void confirmAuditFailureRollsBack()throws Exception{payment("2025","CNFM",null,"N");doThrow(new IllegalStateException("audit")).when(history).insert(any());confirm(confirmation()).andExpect(status().isInternalServerError());verify(manager,atLeastOnce()).rollback(any());verifyNoInteractions(invalidation);}
    @Test void closeLockPrecedesSortedAdvisoryKeys()throws Exception{initialize(init()).andExpect(status().isOk());var order=inOrder(mapper,master,locks);order.verify(mapper).lockTimeout();order.verify(master).lockClose();order.verify(locks).lock("CORPORATE_TAX:2026");order.verify(locks).lock("FINANCIAL_MONTH:202610");order.verify(locks).lock("TAX_PAYMENT:2026:CNFM");order.verify(locks).lock("TAX_PAYMENT:2026:MID");order.verify(locks).lock("operating-standards:CASH_BASE_YM");}
}
