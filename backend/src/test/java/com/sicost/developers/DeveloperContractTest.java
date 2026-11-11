package com.sicost.developers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.areasops.AddressService;
import com.sicost.areasops.AddressDtos.*;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.persistence.MasterIdService;
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
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeveloperController.class)
@ContextConfiguration(classes=DeveloperController.class)
@Import({DeveloperService.class,DeveloperQueryService.class,DeveloperRepository.class,DeveloperAggregateRevision.class,RevisionService.class,ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class DeveloperContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @Autowired DeveloperRepository repository;
    @MockBean DeveloperMapper mapper;
    @MockBean DeveloperRevisionMapper revisionMapper;
    @MockBean AggregateLockMapper locks;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean Clock clock;
    @MockBean MasterIdService ids;
    @MockBean AddressService addresses;
    @MockBean PlatformTransactionManager manager;
    Map<String,Object> row;
    List<Map<String,Object>> prices,skills,states;
    @BeforeEach void setup(){
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));when(clock.withZone(any())).thenReturn(Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        when(users.userId()).thenReturn("admin");when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(ids.nextDeveloperId()).thenReturn("DEV-000901");when(ids.nextManagementNo()).thenReturn("K-0901");
        when(mapper.code(anyString(),anyString())).thenAnswer(i->Map.of("code",i.getArgument(1),"useYn","Y","delYn","N"));
        when(mapper.part(anyString())).thenReturn(Map.of("id","HQ","useYn","Y"));when(mapper.company(anyString())).thenReturn(Map.of("id","C-901","type","VNDR","useYn","Y"));
        when(addresses.resolve(any(),any(),eq("EMD"))).thenReturn(new Selection(null,null));
        when(mapper.mmOver(any())).thenReturn("1.0000");when(mapper.labels(any())).thenReturn(Map.of("job","개발"));
        row=new LinkedHashMap<>();row.put("id","DEV-000901");row.put("managementNo","K-0901");row.put("name","기존");row.put("employeeType","FT");row.put("jobCode","DEV");row.put("positionCode","MEMBER");row.put("directClass","DRCT");row.put("partId","HQ");row.put("status","IDLE");row.put("useYn","Y");row.put("regUserId","admin");row.put("regDttm",LocalDateTime.parse("2026-01-01T00:00:00.123456"));
        prices=new ArrayList<>();prices.add(price("202601",null,null,null));skills=new ArrayList<>();states=new ArrayList<>();
        when(mapper.find(anyString(),anyBoolean())).thenAnswer(i->i.getArgument(0).equals(row.get("id"))?new LinkedHashMap<>(row):null);
        when(revisionMapper.developer(anyString(),anyBoolean())).thenAnswer(i->i.getArgument(0).equals(row.get("id"))?json.writeValueAsString(row):null);
        when(revisionMapper.prices(anyString(),anyBoolean())).thenAnswer(i->prices.stream().map(Object::toString).toList());
        when(revisionMapper.skills(anyString(),anyBoolean())).thenAnswer(i->skills.stream().map(Object::toString).toList());
        when(revisionMapper.states(anyString(),anyBoolean())).thenAnswer(i->states.stream().map(Object::toString).toList());
        when(mapper.prices(any(),anyBoolean())).thenAnswer(i->prices);when(mapper.skills(any(),anyBoolean())).thenAnswer(i->skills);when(mapper.states(any(),anyBoolean())).thenAnswer(i->states);
        when(mapper.ids()).thenReturn(List.of());when(mapper.assignments(any(),any(),any())).thenReturn(List.of());when(mapper.samePerson(any(),any())).thenReturn(List.of());
        when(mapper.list(any())).thenAnswer(i->List.of(row));when(mapper.count(any())).thenReturn(1L);
        when(mapper.insert(any())).thenAnswer(i->{row=new LinkedHashMap<>(i.getArgument(0));row.put("status","IDLE");row.put("useYn","Y");return 1;});
        when(mapper.update(any())).thenAnswer(i->{row=new LinkedHashMap<>(i.getArgument(0));return 1;});
        when(mapper.insertPrice(any())).thenReturn(1);when(mapper.updatePrice(any())).thenReturn(1);when(mapper.closePrice(any())).thenReturn(1);when(mapper.insertSkill(any())).thenReturn(1);when(mapper.updateStatus(any())).thenReturn(1);
    }
    Map<String,Object> price(String month,String end,String rate,String pay){Map<String,Object> p=new LinkedHashMap<>();p.put("aply_bgn_ym",month);p.put("aply_end_ym",end);p.put("mon_uprc",rate==null?null:new java.math.BigDecimal(rate));p.put("mon_pay_amt",pay==null?null:new java.math.BigDecimal(pay));return p;}
    ObjectNode body(){return json.createObjectNode().put("name"," 신규 ").put("employeeType","FT").putNull("companyId").put("jobCode","DEV").put("directClass","DRCT").put("partId","HQ");}
    ObjectNode edit(){return body().put("name",row.get("name").toString()).put("expectedRevision",repository.revision("DEV-000901",false));}
    void bad(ObjectNode b,String field)throws Exception{mvc.perform(post("/api/developers").contentType("application/json").content(b.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));verify(mapper,never()).insert(any());verifyNoInteractions(history);}
    @Test void requirement450CreatesWithoutAuthenticationAsAdmin()throws Exception{
        var b=body().put("hireDate","2020-02-29");b.putArray("skills").add(" Java,SQL ").add("Java");
        mvc.perform(post("/api/developers").contentType("application/json").content(b.toString())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value("DEV-000901")).andExpect(jsonPath("$.data.managementNo").value("K-0901")).andExpect(jsonPath("$.data.warnings").isArray());
        assertThat(row.get("name")).isEqualTo("신규");assertThat(row.get("regUserId")).isEqualTo("admin");assertThat(row.get("phone")).isNull();
        verify(mapper).insertPrice(argThat(p->p.get("month").equals("202002")&&p.get("rate")==null&&p.get("pay")==null));
        verify(mapper).insertSkill(argThat(p->p.get("name").equals("Java")&&p.get("order").equals(1)));verify(mapper).insertSkill(argThat(p->p.get("name").equals("SQL")&&p.get("order").equals(2)));
        verify(history,times(2)).insert(argThat(h->h.type().equals("NEW")&&h.userId().equals("admin")));
    }
    @Test void requirement457MissingHireDateCreatesNullAmountsAtSeoulMonth()throws Exception{mvc.perform(post("/api/developers").contentType("application/json").content(body().toString())).andExpect(status().isCreated());verify(mapper).insertPrice(argThat(p->p.get("month").equals("202606")&&p.get("end")==null&&p.get("rate")==null&&p.get("pay")==null));}
    @Test void requirement455DuplicateNameAndHireWarnsWithExistingNumber()throws Exception{when(mapper.samePerson(any(),any())).thenReturn(List.of("K-0007"));mvc.perform(post("/api/developers").contentType("application/json").content(body().put("hireDate","2020-01-01").toString())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.warnings[0].message").value(org.hamcrest.Matchers.containsString("K-0007")));}
    @Test void requirement452StoppedNumberStillConflicts()throws Exception{when(mapper.managementExists("Case-1")).thenReturn(true);mvc.perform(post("/api/developers").contentType("application/json").content(body().put("managementNo","Case-1").toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_MANAGEMENT_NO"));verify(mapper,never()).insert(any());}
    @Test void requirement452CasePreserved()throws Exception{mvc.perform(post("/api/developers").contentType("application/json").content(body().put("managementNo","Case-1").toString())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.managementNo").value("Case-1"));}
    @Test void exhaustedIdRollsBack()throws Exception{when(ids.nextDeveloperId()).thenThrow(new ApiException(409,"ID_EXHAUSTED","번호 소진"));mvc.perform(post("/api/developers").contentType("application/json").content(body().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ID_EXHAUSTED"));verify(mapper,never()).insert(any());verify(manager).rollback(any());}
    @ParameterizedTest @ValueSource(strings={"actor","regUserId","modDttm","phone","retireDate","status","useYn"})void requirement454RejectsSystemFields(String field)throws Exception{bad(body().put(field,"injected"),field);}
    @Test void blankName()throws Exception{bad(body().put("name"," "),"name");}
    @Test void longName()throws Exception{bad(body().put("name","가".repeat(51)),"name");}
    @ParameterizedTest @ValueSource(strings={"1.1","-1","1e3","1000000000000000"})void invalidMoney(String amount)throws Exception{bad(body().put("monthlyRate",amount),"monthlyRate");}
    @Test void invalidDate()throws Exception{bad(body().put("hireDate","2026-02-30"),"hireDate");}
    @Test void outsourcingRequiresVendor()throws Exception{bad(body().put("employeeType","OS"),"companyId");}
    @Test void stoppedCompanyCannotBeNewlySelected()throws Exception{when(mapper.company(any())).thenReturn(Map.of("type","VNDR","useYn","N"));bad(body().put("companyId","C-901"),"companyId");}
    @Test void customerCannotBeSelectedAsVendor()throws Exception{when(mapper.company(any())).thenReturn(Map.of("type","CUST","useYn","Y"));bad(body().put("companyId","C-901"),"companyId");}
    @Test void missingPart()throws Exception{bad(body().put("partId",""),"partId");}
    @Test void stoppedPart()throws Exception{when(mapper.part(any())).thenReturn(Map.of("useYn","N"));bad(body(),"partId");}
    @Test void inactiveCode()throws Exception{when(mapper.code(eq("JOB"),any())).thenReturn(Map.of("useYn","N","delYn","N"));bad(body(),"jobCode");}
    @Test void invalidAreaPath()throws Exception{when(addresses.resolve(any(),any(),eq("EMD"))).thenThrow(new ApiException(400,"INVALID_AREA","거주지를 확인해 주세요.","homeAreaCode",null));bad(body().put("homeAreaCode","1234567890"),"homeAreaCode");}
    @Test void requirement471DetailIncludesExplicitNullsPriceAndRevision()throws Exception{mvc.perform(get("/api/developers/DEV-000901")).andExpect(status().isOk()).andExpect(jsonPath("$.data.skills").isArray()).andExpect(jsonPath("$.data.priceHistory[0].effectiveMonth").value("2026-01")).andExpect(jsonPath("$.data.revision").isString()).andExpect(content().string(org.hamcrest.Matchers.containsString("\"monthlyRate\":null")));}
    @Test void requirement468FilteredTotalAndOverpage()throws Exception{when(mapper.list(any())).thenReturn(List.of());when(mapper.count(any())).thenReturn(25L);mvc.perform(get("/api/developers").param("q","검색").param("page","3").param("size","20")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(25)).andExpect(jsonPath("$.data.items").isEmpty());verify(mapper).list(argThat(s->s.offset()==40&&s.query().filters().get("q").equals("검색")));}
    @ParameterizedTest @ValueSource(strings={"actor=other","page=0","size=21","sort=phone","direction=drop","workStatus=WORK","employeeType=FRE","includeStopped=yes"})void invalidList(String q)throws Exception{String[] p=q.split("=");mvc.perform(get("/api/developers").param(p[0],p[1])).andExpect(status().isBadRequest());verify(mapper,never()).updateStatus(any());}
    @Test void detailRejectsAnyQuery()throws Exception{mvc.perform(get("/api/developers/DEV-000901").param("actor","other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));}
    @Test void notFound()throws Exception{mvc.perform(get("/api/developers/DEV-999999")).andExpect(status().isNotFound());}
    @Test void requirement430NoOp()throws Exception{mvc.perform(put("/api/developers/DEV-000901").contentType("application/json").content(edit().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));verify(mapper,never()).update(any());verifyNoInteractions(history);}
    @Test void requirement451UpdateBasicPreservesPriceAndPhone()throws Exception{row.put("phone","010-1111-2222");mvc.perform(put("/api/developers/DEV-000901").contentType("application/json").content(edit().put("name","변경").toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));assertThat(row.get("phone")).isEqualTo("010-1111-2222");verify(mapper,never()).insertPrice(any());verify(history).insert(argThat(h->h.item().equals("name")&&h.before().equals("기존")&&h.after().equals("변경")));}
    @Test void requirement458CurrentMonthUpdates()throws Exception{prices=List.of(price("202606",null,"100",null));mvc.perform(put("/api/developers/DEV-000901").contentType("application/json").content(edit().put("monthlyRate","200").toString())).andExpect(status().isOk());verify(mapper).updatePrice(argThat(p->p.get("month").equals("202606")&&p.get("rate").toString().equals("200")&&p.get("pay")==null));verify(mapper,never()).insertPrice(any());}
    @Test void requirement459ConnectsIntervalsPreservingFuture()throws Exception{prices=List.of(price("202601","202608","100","80"),price("202609",null,"900","700"));mvc.perform(put("/api/developers/DEV-000901").contentType("application/json").content(edit().put("monthlyRate","200").toString())).andExpect(status().isOk());verify(mapper).closePrice(argThat(p->p.get("month").equals("202601")&&p.get("end").equals("202605")));verify(mapper).insertPrice(argThat(p->p.get("end").equals("202608")&&p.get("pay").toString().equals("80")));verify(mapper,never()).updatePrice(any());}
    @Test void requirement461FutureOnlyUneditedMoneyCreatesNoPrice()throws Exception{prices=List.of(price("202609",null,"900","700"));mvc.perform(put("/api/developers/DEV-000901").contentType("application/json").content(edit().put("name","수정").toString())).andExpect(status().isOk());verify(mapper,never()).insertPrice(any());}
    @Test void requirement460ClosedMonthRejectsMoney()throws Exception{when(mapper.closed(any())).thenReturn(true);mvc.perform(put("/api/developers/DEV-000901").contentType("application/json").content(edit().put("monthlyRate","1").toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLOSED_MONTH"));verify(mapper,never()).update(any());}
    @Test void requirement460ClosedMonthAllowsBasic()throws Exception{when(mapper.closed(any())).thenReturn(true);mvc.perform(put("/api/developers/DEV-000901").contentType("application/json").content(edit().put("name","수정").toString())).andExpect(status().isOk());verify(mapper,never()).lockClose();}
    @Test void requirement428ChildChangeWithNullTimestampsIsStale()throws Exception{var b=edit();states.add(new HashMap<>(Map.of("stat_sn",1,"work_stat_cd","WORK","bgn_dt",LocalDate.of(2026,1,1))));mvc.perform(put("/api/developers/DEV-000901").contentType("application/json").content(b.toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));verify(mapper,never()).update(any());}
    @Test void requirement451HireAfterRetireRejected()throws Exception{row.put("retireDate",LocalDate.of(2026,7,1));mvc.perform(put("/api/developers/DEV-000901").contentType("application/json").content(edit().put("hireDate","2026-07-02").toString())).andExpect(status().isBadRequest());verify(mapper,never()).update(any());}
    @ParameterizedTest @ValueSource(strings={"managementNo","retireDate","useYn","actor","regUserId","effectiveMonth","phone"})void updateRejectsOwnedFields(String field)throws Exception{mvc.perform(put("/api/developers/DEV-000901").contentType("application/json").content(edit().put(field,"injected").toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verify(mapper,never()).update(any());}
}
