package com.sicost.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.persistence.MasterIdService;
import com.sicost.common.security.AccountCipher;
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
import static com.sicost.clients.ClientDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientController.class)
@ContextConfiguration(classes=ClientController.class)
@Import({ClientService.class,ClientRepository.class,RevisionService.class,ChangeHistoryWriter.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ClientContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean ClientMapper mapper;
    @MockBean AggregateLockMapper locks;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean CommitInvalidation invalidation;
    @MockBean Clock clock;
    @MockBean AccountCipher cipher;
    @MockBean MasterIdService ids;
    @MockBean PlatformTransactionManager manager;
    Stored stored;
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(clock.withZone(any())).thenReturn(Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        when(users.userId()).thenReturn("admin");when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(ids.nextClientId()).thenReturn("C-901");
        when(mapper.sameName(anyString(),any())).thenReturn(List.of());
        when(mapper.labels(anyString())).thenReturn(new Labels(null,null,"은행",true));
        when(mapper.counts(anyString(),any())).thenReturn(new Counts(0,0));
        when(mapper.code(anyString(),anyString())).thenAnswer(i->new Code(i.getArgument(1),"코드명","Y","N"));
        when(cipher.encrypt(anyString())).thenReturn("encrypted-test-value");when(cipher.decrypt("encrypted-test-value")).thenReturn("001-2345678");
        when(cipher.masked("encrypted-test-value")).thenReturn("****5678");
        stored=new Stored("C-901","VNDR","거래처","123-45-67890",null,null,null,"01000112233",null,"001","encrypted-test-value","예금주","Y","admin",LocalDateTime.parse("2026-01-01T00:00:00.123456"),null,null);
        when(mapper.find(anyString(),anyBoolean())).thenAnswer(i->i.getArgument(0).equals(stored.id())?stored:null);
        when(mapper.insert(any())).thenAnswer(i->{stored=((Mutation)i.getArgument(0)).row();return 1;});
        when(mapper.update(any())).thenAnswer(i->{stored=((Mutation)i.getArgument(0)).row();return 1;});
        when(mapper.list(any())).thenAnswer(i->List.of(stored));when(mapper.count(any())).thenReturn(1L);
    }
    ObjectNode create(){return json.createObjectNode().put("type","CUST").put("name"," 신규 ").put("businessNo","1112233333").put("taxEmail"," test@example.test ");}
    ObjectNode edit(){return json.createObjectNode().put("type","VNDR").put("name","수정").put("phone",stored.phone()).put("accountAction","KEEP").put("expectedRevision",revisions.revision(stored));}
    ObjectNode vendor(){return create().put("type","VNDR").put("bankCode","001").put("accountNumber","001-2345678").put("depositor","예금주");}
    void postBad(ObjectNode body,String field) throws Exception {
        mvc.perform(post("/api/clients").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));
        verify(mapper,never()).insert(any());verifyNoInteractions(history);
    }
    @Test void createWithoutAuthenticationNormalizesAndWritesExactlyOneNewAsAdmin() throws Exception {
        mvc.perform(post("/api/clients").contentType("application/json").content(create().toString()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.id").value("C-901")).andExpect(jsonPath("$.data.warnings").isArray());
        mvc.perform(get("/api/clients/C-901")).andExpect(status().isOk()).andExpect(jsonPath("$.data.businessNo").value("111-22-33333")).andExpect(jsonPath("$.data.name").value("신규"));
        assertThat(stored.useYn()).isEqualTo("Y");assertThat(stored.regUserId()).isEqualTo("admin");
        verify(history).insert(argThat(e->e.type().equals("NEW")&&e.userId().equals("admin")));verify(manager).commit(any());
    }
    @Test void keepEditsWithoutAccountPropertiesAndPreservesCiphertext() throws Exception {
        mvc.perform(put("/api/clients/C-901").contentType("application/json").content(edit().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        assertThat(stored.name()).isEqualTo("수정");assertThat(stored.encryptedAccount()).isEqualTo("encrypted-test-value");verify(cipher,never()).encrypt(any());
        verify(history).insert(argThat(e->e.item().equals("CLNT_NM")&&e.before().equals("거래처")&&e.after().equals("수정")));
    }
    @Test void noOpPreservesRevisionTimestampAndHistory() throws Exception {
        Stored old=stored;var request=edit().put("name",stored.name());
        mvc.perform(put("/api/clients/C-901").contentType("application/json").content(request.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false)).andExpect(jsonPath("$.data.message").value("바뀐 내용이 없습니다."));
        assertThat(stored).isEqualTo(old);verify(mapper,never()).update(any());verifyNoInteractions(history);
    }
    @Test void staleIsCheckedBeforeReferencesAndNoOp() throws Exception {
        mvc.perform(put("/api/clients/C-901").contentType("application/json").content(edit().put("expectedRevision","stale").toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        verify(mapper,never()).update(any());verifyNoInteractions(history);
    }
    @Test void revisionIncludesValuesAtIdenticalTimestampButNotCountsOrDate() {
        String before=revisions.revision(stored);Stored after=new Stored(stored.id(),stored.type(),"다른 이름",stored.businessNo(),stored.ceo(),stored.industryCode(),stored.contactName(),stored.phone(),stored.taxEmail(),stored.bankCode(),stored.encryptedAccount(),stored.depositor(),stored.useYn(),stored.regUserId(),stored.regDttm(),stored.modUserId(),stored.modDttm());
        assertThat(revisions.revision(after)).isNotEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"regUserId","modDttm","id","accountAction"}) void createRejectsUnknown(String field) throws Exception {
        postBad(create().put(field,"injected"),field);
    }
    @ParameterizedTest @ValueSource(strings={"regUserId","businessNo","id","useYn"}) void editRejectsIdentityAndSystemFields(String field) throws Exception {
        mvc.perform(put("/api/clients/C-901").contentType("application/json").content(edit().put(field,"injected").toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));verify(mapper,never()).update(any());verifyNoInteractions(history);
    }
    @Test void customerRequiresEmail(){assertThatThrownBy(()->ClientInput.parse(create().remove(List.of("taxEmail")),false)).isInstanceOf(ApiException.class);}
    @Test void invalidBankLength() throws Exception {postBad(vendor().put("bankCode","12345678901"),"bankCode");}
    @Test void invalidAccountCharacters() throws Exception {postBad(vendor().put("accountNumber","abcd123456"),"accountNumber");}
    @Test void hyphenOnlyAccount() throws Exception {postBad(vendor().put("accountNumber","--------"),"accountNumber");}
    @ParameterizedTest @ValueSource(strings={"bankCode","accountNumber","depositor"}) void vendorRequired(String field) throws Exception {var request=vendor();request.remove(field);postBad(request,field);}
    @ParameterizedTest @ValueSource(strings={"bankCode","accountNumber","depositor"}) void keepRejectsEvenExplicitNull(String field) throws Exception {
        mvc.perform(put("/api/clients/C-901").contentType("application/json").content(edit().putNull(field).toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(field));verify(mapper,never()).update(any());
    }
    @Test void keepRequiresExistingAccount() throws Exception {
        stored=new Stored(stored.id(),"CUST",stored.name(),stored.businessNo(),null,null,null,null,"a@b.test",null,null,null,"Y","admin",stored.regDttm(),null,null);
        mvc.perform(put("/api/clients/C-901").contentType("application/json").content(edit().toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("accountAction"));verify(mapper,never()).update(any());
    }
    @Test void customerDiscardsInvalidAccountWithoutLeakingItsValue() throws Exception {
        var request=create().put("bankCode","invalid-bank-with-too-many-characters").put("accountNumber","secret-invalid").put("depositor","x");
        var response=mvc.perform(post("/api/clients").contentType("application/json").content(request.toString())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.warnings.length()").value(3)).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("secret-invalid");assertThat(stored.encryptedAccount()).isNull();verify(cipher,never()).encrypt(any());
    }
    @Test void vendorDiscardsInvalidEmail() throws Exception {
        mvc.perform(post("/api/clients").contentType("application/json").content(vendor().put("taxEmail","invalid").toString())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.warnings[0].field").value("taxEmail"));assertThat(stored.taxEmail()).isNull();
    }
    @Test void duplicateIncludesStoppedClientName() throws Exception {
        when(mapper.byBusinessNo("111-22-33333")).thenReturn(stored);
        mvc.perform(post("/api/clients").contentType("application/json").content(create().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_BUSINESS_NO")).andExpect(jsonPath("$.details.clientName").value("거래처"));verify(mapper,never()).insert(any());verifyNoInteractions(history);
    }
    @Test void sameNameIsWarningNotConflict() throws Exception {
        String existingNumber=stored.businessNo();
        when(mapper.sameName("신규",null)).thenReturn(List.of(stored));mvc.perform(post("/api/clients").contentType("application/json").content(create().toString())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.warnings[0].message").value(org.hamcrest.Matchers.containsString(existingNumber)));
    }
    @Test void customerRemovalWithOngoingProjectReturnsReferenceCount() throws Exception {
        stored=new Stored(stored.id(),"BOTH",stored.name(),stored.businessNo(),null,null,null,stored.phone(),"a@b.test",stored.bankCode(),stored.encryptedAccount(),stored.depositor(),"Y","admin",stored.regDttm(),null,null);
        when(mapper.projectReferences(stored.id())).thenReturn(1L);
        mvc.perform(put("/api/clients/C-901").contentType("application/json").content(edit().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFERENCE_IN_USE")).andExpect(jsonPath("$.details.references[0].count").value(1));verify(mapper,never()).update(any());verifyNoInteractions(history);
    }
    @Test void vendorRemovalWithAnyDeveloperIsBlocked() throws Exception {
        when(mapper.developerReferences(stored.id())).thenReturn(2L);
        mvc.perform(put("/api/clients/C-901").contentType("application/json").content(edit().put("type","CUST").put("taxEmail","a@b.test").put("accountAction","CLEAR").toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.details.references[0].count").value(2));verify(mapper,never()).update(any());
    }
    @Test void identicalReplaceDoesNotEncryptOrAppendAccountHistory() throws Exception {
        var request=edit().put("name",stored.name()).put("accountAction","REPLACE").put("bankCode","001").put("accountNumber","001-2345678").put("depositor","예금주");
        mvc.perform(put("/api/clients/C-901").contentType("application/json").content(request.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));verify(cipher,never()).encrypt(any());verifyNoInteractions(history);
    }
    @Test void encryptionConfigurationFailureDoesNotWrite() throws Exception {
        when(cipher.encrypt(anyString())).thenThrow(new ApiException(503,"ENCRYPTION_CONFIG_ERROR","계좌 암호화 설정을 확인해 주세요."));
        var response=mvc.perform(post("/api/clients").contentType("application/json").content(vendor().toString())).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ENCRYPTION_CONFIG_ERROR")).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("001-2345678");verify(mapper,never()).insert(any());verifyNoInteractions(history);verify(manager).rollback(any());
    }
    @Test void detailIsMaskedAndUsesSeoulDateWithNoCiphertext() throws Exception {
        var response=mvc.perform(get("/api/clients/C-901")).andExpect(status().isOk()).andExpect(jsonPath("$.data.account").value("은행 ****5678")).andExpect(jsonPath("$.data.basisDate").value("2026-06-15")).andReturn().getResponse();assertThat(response.getContentAsString()).doesNotContain("encrypted-test-value","001-2345678");
    }
    @Test void updateEncryptionFailureRollsBackBeforeAnyWrite() throws Exception {
        var request=edit().put("accountAction","REPLACE").put("bankCode","001").put("accountNumber","009-8765432").put("depositor","다른예금주");
        when(cipher.encrypt(anyString())).thenThrow(new ApiException(503,"ENCRYPTION_CONFIG_ERROR","계좌 암호화 설정을 확인해 주세요."));
        mvc.perform(put("/api/clients/C-901").contentType("application/json").content(request.toString())).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ENCRYPTION_CONFIG_ERROR"));
        verify(mapper,never()).update(any());verifyNoInteractions(history);verify(manager).rollback(any());
    }
    @Test void inactiveExistingBankCanBeKeptButCannotBeSelectedNew() throws Exception {
        when(mapper.code("BANK","002")).thenReturn(new Code("002","중지은행","N","N"));
        var request=edit().put("accountAction","REPLACE").put("bankCode","002").put("accountNumber","001-2345678").put("depositor","예금주");
        mvc.perform(put("/api/clients/C-901").contentType("application/json").content(request.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("bankCode"));verify(mapper,never()).update(any());
    }
    @Test void vendorCannotClearAccount() throws Exception {
        mvc.perform(put("/api/clients/C-901").contentType("application/json").content(edit().put("accountAction","CLEAR").toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("accountAction"));verify(mapper,never()).update(any());
    }
    @ParameterizedTest @ValueSource(strings={"123456789","12345678901","1234-5-67890"}) void rejectsMalformedBusinessNumber(String number) throws Exception {postBad(create().put("businessNo",number),"businessNo");}
    @ParameterizedTest @ValueSource(strings={"ceo","contactName","phone"}) void optionalTextBoundaryTwentyAllowedAndTwentyOneRejected(String field) throws Exception {
        postBad(create().put(field,"가".repeat(21)),field);
        mvc.perform(post("/api/clients").contentType("application/json").content(create().put(field,"가".repeat(20)).toString())).andExpect(status().isCreated());
    }
    @Test void namesAreTrimmedBeforeRequiredAndLengthValidation() throws Exception {postBad(create().put("name","   "),"name");}
    @Test void requiredEmailFormatIsValidated() throws Exception {postBad(create().put("taxEmail","invalid email"),"taxEmail");}
    @Test void updateMissingEmailForCustomerIsRejected() throws Exception {
        mvc.perform(put("/api/clients/C-901").contentType("application/json").content(edit().put("type","CUST").put("accountAction","CLEAR").toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("taxEmail"));verify(mapper,never()).update(any());
    }
    @Test void filteredTotalAndOverpageAreReturnedWithoutServerUseDefault() throws Exception {
        when(mapper.list(any())).thenReturn(List.of());when(mapper.count(any())).thenReturn(25L);
        mvc.perform(get("/api/clients").param("type","CUST").param("q","123-45").param("page","3").param("size","20")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(25)).andExpect(jsonPath("$.data.items").isEmpty());
        verify(mapper).count(argThat(q->q.useYn()==null&&q.type().equals("CUST")));verify(mapper).list(argThat(q->q.offset()==40));
    }
    @Test void notFoundIs404() throws Exception {mvc.perform(get("/api/clients/absent")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));}
    @ParameterizedTest @ValueSource(strings={"size=21","page=0","sort=account","direction=DROP","useYn=ALL","type=unknown"}) void invalidListQuery(String query) throws Exception {String[] parts=query.split("=");mvc.perform(get("/api/clients").param(parts[0],parts[1])).andExpect(status().isBadRequest());}
}
