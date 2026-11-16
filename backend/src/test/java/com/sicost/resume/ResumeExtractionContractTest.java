package com.sicost.resume;

import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.resume.ResumeExtractionDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ResumeExtractionController.class)
@ContextConfiguration(classes=ResumeExtractionController.class)
@Import({ResumeExtractionService.class,ResumeDocumentParser.class,ResumeFieldExtractor.class,
    ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class ResumeExtractionContractTest {
    @Autowired MockMvc mvc;
    @MockBean ResumeExtractionMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean Clock clock;
    @MockBean PlatformTransactionManager transactions;
    @BeforeEach void setup() {
        var fixed=Clock.fixed(Instant.parse("2026-11-16T01:00:00Z"),ZoneId.of("Asia/Seoul"));
        when(clock.withZone(any())).thenReturn(fixed);
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        when(transactions.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(mapper.codes()).thenReturn(List.of(new Code("RSM_LABEL","NAME","성명","NAME",1)));
        when(mapper.areas()).thenReturn(List.of());when(mapper.vendors()).thenReturn(List.of());
    }
    MockPart file(String filename,String text) {return new MockPart("file",filename,text.getBytes(StandardCharsets.UTF_8));}
    MockPart target(String value) {return new MockPart("targetType",value.getBytes(StandardCharsets.UTF_8));}
    @Test void durableContractDeclaresExtractionWithoutBusinessWrites() throws Exception {
        assertThat(new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8))
            .contains("operationId: createResumeExtraction","/api/resume-extractions:");
        assertThat(ResumeExtractionMapper.class.getDeclaredMethods()).allMatch(m->m.isAnnotationPresent(org.apache.ibatis.annotations.Select.class));
    }
    @Test void happyAuthExtractionRunsRealParserWithDatabaseDictionaryAndReadOnlySnapshot() throws Exception {
        mvc.perform(multipart("/api/resume-extractions").part(file("resume.txt","성명: 시험이름"),target("DEVELOPER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.targetType").value("DEVELOPER"))
            .andExpect(jsonPath("$.data.values.name").value("시험이름"))
            .andExpect(jsonPath("$.data.fields").isArray()).andExpect(jsonPath("$.data.warnings").isArray());
        verify(users).currentUser();verify(mapper).codes();verify(mapper).areas();verify(mapper).vendors();
        verifyNoMoreInteractions(mapper);
        verify(transactions).getTransaction(argThat(d->d.isReadOnly()&&d.getIsolationLevel()==org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ));
        verify(transactions).commit(any());
    }
    @Test void businessNoRecognizedTextReturnsEmptyProposalRatherThanInventedPerson() throws Exception {
        mvc.perform(multipart("/api/resume-extractions").part(file("resume.txt","   "),target("CANDIDATE")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.values").isEmpty())
            .andExpect(jsonPath("$.data.notice").value("가져올 칸을 찾지 못했습니다. 직접 적어 주세요."));
        verify(users).currentUser();verifyNoMoreInteractions(users);
    }
    @Test void validationMissingFileEmptyFileInvalidTargetAndActorInjectionNeverReadDictionary() throws Exception {
        mvc.perform(multipart("/api/resume-extractions").part(target("DEVELOPER")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("file"));
        mvc.perform(multipart("/api/resume-extractions").part(file("resume.txt",""),target("DEVELOPER")))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/resume-extractions").part(file("resume.txt","성명: 시험"),target("ADMIN")))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/resume-extractions").part(file("resume.txt","성명: 시험"),target("DEVELOPER"),new MockPart("actor","other".getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(mapper,transactions);
    }
    @Test void validationDuplicateFilesAreRejectedWithoutServiceWork() throws Exception {
        mvc.perform(multipart("/api/resume-extractions").part(file("one.txt","성명: 하나"),file("two.txt","성명: 둘"),target("DEVELOPER")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(mapper,users,transactions);
    }
    @Test void parserFailureIsJsonAndNeverReturnsOriginalDocumentOrPartialProposal() throws Exception {
        mvc.perform(multipart("/api/resume-extractions").part(file("resume.pdf","private document not a PDF"),target("CANDIDATE")))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.requestId").isString()).andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private document"))));
        verify(mapper).codes();verify(mapper).areas();verify(mapper).vendors();
        verifyNoMoreInteractions(mapper);
    }
}
