package com.sicost.areasops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
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
import static com.sicost.areasops.AddressDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AddressController.class)
@ContextConfiguration(classes=AddressController.class)
@Import({AddressService.class,AddressRepository.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class AddressContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AddressService service;
    @MockBean AddressMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean PlatformTransactionManager manager;
    Area sido=new Area("0100000000","SIDO",null,"경기","Y");
    Area sgg=new Area("0101000000","SGG","0100000000","성남시 분당구","Y");
    Area emd=new Area("0101010000","EMD","0101000000","정자동","Y");
    @BeforeEach void setup() {
        when(users.userId()).thenReturn("admin");when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(mapper.sidos()).thenReturn(List.of(sido));
        when(mapper.find(sido.code())).thenReturn(sido);when(mapper.find(sgg.code())).thenReturn(sgg);when(mapper.find(emd.code())).thenReturn(emd);
        when(mapper.children(sido.code(),"SGG")).thenReturn(List.of(sgg));
        when(mapper.children(sgg.code(),"EMD")).thenReturn(List.of(emd));
    }
    @Test void sidosWithoutAuthenticationUsesAdminAndPreservesRequiredNullParent() throws Exception {
        var response=mvc.perform(get("/api/areas/sidos")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.items[0].code").value(sido.code())).andReturn().getResponse();
        var area=json.readTree(response.getContentAsString()).path("data").path("items").get(0);
        assertThat(area.has("parentCode")).isTrue();assertThat(area.get("parentCode").isNull()).isTrue();assertThat(area.size()).isEqualTo(5);
        verify(users).userId();verify(mapper).sidos();
    }
    @Test void childrenWithoutAuthenticationUsesExactParentAndLevel() throws Exception {
        mvc.perform(get("/api/areas/children").param("parentCode"," "+sido.code()+" ").param("level"," SGG "))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].parentCode").value(sido.code()))
            .andExpect(jsonPath("$.data.items[0].name").value(sgg.name()));
        verify(users).userId();verify(mapper).children(sido.code(),"SGG");
    }
    @Test void pathWithoutAuthenticationRestoresCodesAndNamesInParentOrder() throws Exception {
        mvc.perform(get("/api/areas/"+emd.code()+"/path")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].code").value(sido.code())).andExpect(jsonPath("$.data.items[1].code").value(sgg.code()))
            .andExpect(jsonPath("$.data.items[2].code").value(emd.code())).andExpect(jsonPath("$.data.homeAddress").value("경기 성남시 분당구 정자동"));
        verify(users).userId();
    }
    @ParameterizedTest @ValueSource(strings={"/api/areas/sidos","/api/areas/children","/api/areas/0101010000/path"})
    void actorInjectionIsUnknownFieldBeforeAnyRead(String path) throws Exception {
        mvc.perform(get(path).param("actor","other")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
        verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @ValueSource(strings={"/api/areas/sidos","/api/areas/children","/api/areas/0101010000/path"})
    void paginationIsNotAcceptedOnNonPaginatedOperations(String path) throws Exception {
        var response=mvc.perform(get(path).param("page","0")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andReturn().getResponse();
        assertThat(json.readTree(response.getContentAsString()).path("requestId").asText()).isEqualTo(response.getHeader("X-Request-Id"));
        verifyNoInteractions(mapper,users);
    }
    @ParameterizedTest @ValueSource(strings={"SIDO","emd","","SGG;DROP"})
    void childrenRejectsInvalidLevel(String level) throws Exception {
        mvc.perform(get("/api/areas/children").param("parentCode",sido.code()).param("level",level)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("level"));
        verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings={"123","12345678901","abcdefghij","-123456789"})
    void childrenRejectsMalformedCode(String code) throws Exception {
        mvc.perform(get("/api/areas/children").param("parentCode",code).param("level","SGG")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);
    }
    @Test void childrenRequiresParent() throws Exception {mvc.perform(get("/api/areas/children").param("level","SGG")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);}
    @Test void childrenRequiresLevel() throws Exception {mvc.perform(get("/api/areas/children").param("parentCode",sido.code())).andExpect(status().isBadRequest());verifyNoInteractions(mapper);}
    @Test void repeatedQueryIsRejected() throws Exception {mvc.perform(get("/api/areas/children").param("parentCode",sido.code(),sgg.code()).param("level","SGG")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);}
    @Test void missingParentIs404() throws Exception {mvc.perform(get("/api/areas/children").param("parentCode","9999999999").param("level","SGG")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));}
    @Test void missingPathIs404() throws Exception {mvc.perform(get("/api/areas/9999999999/path")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));}
    @Test void malformedPathIs400() throws Exception {mvc.perform(get("/api/areas/abc/path")).andExpect(status().isBadRequest());verifyNoInteractions(mapper);}
    @Test void emptyListsAreSuccessful() throws Exception {
        when(mapper.sidos()).thenReturn(List.of());when(mapper.children(sido.code(),"SGG")).thenReturn(List.of());
        mvc.perform(get("/api/areas/sidos")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/areas/children").param("parentCode",sido.code()).param("level","SGG")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }
    @Test void directEmdPathSkipsSgg() throws Exception {
        when(mapper.find(emd.code())).thenReturn(new Area(emd.code(),"EMD",sido.code(),"조치원읍","Y"));
        mvc.perform(get("/api/areas/"+emd.code()+"/path")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2));
    }
    @Test void stoppedPathIsReadableAndMayOnlyBeKeptUnchanged() throws Exception {
        when(mapper.find(sgg.code())).thenReturn(new Area(sgg.code(),sgg.level(),sgg.parentCode(),sgg.name(),"N"));
        mvc.perform(get("/api/areas/"+emd.code()+"/path")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[1].useYn").value("N"));
        assertThat(service.resolve(emd.code(),emd.code(),"EMD").homeAreaCode()).isEqualTo(emd.code());
        assertThatThrownBy(()->service.resolve(emd.code(),null,"EMD")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("INVALID_AREA"));
    }
    @Test void candidateCannotChooseEmd() {assertThatThrownBy(()->service.resolve(emd.code(),null,"SGG")).isInstanceOf(ApiException.class);}
    @Test void candidateCanChooseSidoAndSgg() {assertThat(service.resolve(sido.code(),null,"SGG").homeAddress()).isEqualTo("경기");assertThat(service.resolve(sgg.code(),null,"SGG").homeAddress()).isEqualTo("경기 성남시 분당구");}
    @Test void clearReturnsBothColumnsNull() {assertThat(service.resolve(" ",emd.code(),"EMD")).isEqualTo(new Selection(null,null));}
    @Test void missingSelectionIsInvalidAreaNot404() {assertThatThrownBy(()->service.resolve("9999999999",null,"EMD")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("INVALID_AREA"));}
    @Test void brokenParentIsInvalidArea() throws Exception {
        when(mapper.find(sgg.code())).thenReturn(null);
        mvc.perform(get("/api/areas/"+emd.code()+"/path")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_AREA"));
    }
    @Test void cyclicParentIsInvalidArea() throws Exception {
        when(mapper.find(sgg.code())).thenReturn(new Area(sgg.code(),"SGG",emd.code(),sgg.name(),"Y"));
        mvc.perform(get("/api/areas/"+emd.code()+"/path")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_AREA"));
    }
    @Test void wrongLevelChainIsInvalidArea() throws Exception {
        when(mapper.find(sgg.code())).thenReturn(new Area(sgg.code(),"EMD",sido.code(),sgg.name(),"Y"));
        mvc.perform(get("/api/areas/"+emd.code()+"/path")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_AREA"));
    }
    @Test void longDisplayIsRejectedWithoutTruncation() throws Exception {
        when(mapper.find(sgg.code())).thenReturn(new Area(sgg.code(),"SGG",sido.code(),"가".repeat(30),"Y"));
        when(mapper.find(emd.code())).thenReturn(new Area(emd.code(),"EMD",sgg.code(),"나".repeat(30),"Y"));
        mvc.perform(get("/api/areas/"+emd.code()+"/path")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_AREA"));
    }
    @ParameterizedTest @ValueSource(strings={"/api/areas/sidos","/api/areas/children?parentCode=0100000000&level=SGG","/api/areas/0101010000/path"})
    void databaseFailuresAreSafe(String path) throws Exception {
        reset(mapper);when(mapper.sidos()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("SELECT private_data"));
        when(mapper.find(anyString())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("SELECT private_data"));
        var response=mvc.perform(get(path)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("SELECT","private_data","Exception");
    }
}
