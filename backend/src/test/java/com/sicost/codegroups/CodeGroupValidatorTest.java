package com.sicost.codegroups;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sicost.codegroups.CodeGroupDtos.*;

class CodeGroupValidatorTest {
    final CodeGroupValidator validator=new CodeGroupValidator(new CodeReferenceGuard(mock(CodeGroupMapper.class)));
    final ObjectMapper json=new ObjectMapper();
    StoredGroup group(String id) {return new StoredGroup(id,"독립 시험","N","N",1,"Y","admin",null,null,null);}
    StoredCode row(String group,String code,String use,String del,String csv) {
        return new StoredCode(group,code,"이름",1,use,del,null,null,csv,null,null,"admin",null,null,null);
    }
    Change change(String properties) throws Exception {
        return CodeGroupInput.save(json.readTree("{\"expectedRevision\":\"revision\",\"changes\":[{"+properties+"}]}")).changes().get(0);
    }
    @ParameterizedTest @CsvSource({"JOB,2,1.1","JOB,2,1000000000000000","JOB,2,-1","BANK,1,01","BANK,1,0001","BANK,1,0a1","PROJ_STEP,1,100.1","PROJ_STEP,1,101","PROJ_STEP,1,-1","RSM_LABEL,1,OTHER"})
    void invalidAdditionalValues(String group,int index,String input) {
        assertThatThrownBy(()->CodeGroupValidator.extra(group,index,input,"changes[0].addVal"+index))
            .isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.status()).isEqualTo(400);assertThat(e.field()).isEqualTo("changes[0].addVal"+index);});
    }
    @Test void bankLeadingZeroAndJobIntegerNormalizationArePreserved() {
        assertThat(CodeGroupValidator.extra("BANK",1," 001 ","field")).isEqualTo("001");
        assertThat(CodeGroupValidator.extra("JOB",2,"999,999,999,999,999원","field")).isEqualTo("999999999999999");
        assertThat(CodeGroupValidator.extra("PROJ_STEP",1,"100","field")).isEqualTo("100");
    }
    @ParameterizedTest @ValueSource(strings={"NAME","TEL","MAIL","ADDR","SKILL","WANT","FROM","VNDR"})
    void resumeLabelSupportsExactlyTheDeclaredEightValues(String input) {
        assertThat(CodeGroupValidator.extra("RSM_LABEL",1,input.toLowerCase(Locale.ROOT),"field")).isEqualTo(input);
    }
    @ParameterizedTest @ValueSource(strings={"A,,B",",A","A,",","," ","A,   ,B"})
    void csvRejectsEveryEmptyItem(String input) {
        assertThatThrownBy(()->CodeGroupValidator.extra("SKILL",1,input,"changes[0].addVal1"))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.field()).isEqualTo("changes[0].addVal1"));
    }
    @Test void csvPreservesFirstAppearanceOrderAndDeduplicates() {
        assertThat(CodeGroupValidator.extra("SKILL",1," b, A,b , c,A ","field")).isEqualTo("B,A,C");
    }
    @Test void sameRequestNewJobAndSelfReferenceUseFinalState() throws Exception {
        Change change=change("\"code\":\"A\",\"isNew\":true,\"name\":\"직무\",\"addVal1\":\"a,b,a\",\"addVal2\":\"1,000원\"");
        StoredCode next=validator.apply(group("JOB"),null,change,1);
        assertThat(next.addVal1()).isEqualTo("A,B");
        validator.references(group("JOB"),change,null,next,Map.of("A",next,"B",row("JOB","B","Y","N",null)));
    }
    @Test void unchangedStoppedReferenceMayBeKept() throws Exception {
        Change change=change("\"code\":\"A\",\"isNew\":false,\"name\":\"변경\"");
        StoredCode old=row("SKILL","A","Y","N","B"),next=validator.apply(group("SKILL"),old,change,1);
        validator.references(group("SKILL"),change,old,next,Map.of("B",row("JOB","B","N","N",null)));
    }
    @ParameterizedTest @ValueSource(strings={"missing","deleted","stopped"})
    void newCsvReferencesMustExistAndBeActive(String state) throws Exception {
        Change change=change("\"code\":\"A\",\"isNew\":true,\"name\":\"스킬\",\"addVal1\":\"B\"");
        StoredCode next=validator.apply(group("SKILL"),null,change,1);
        Map<String,StoredCode> targets=state.equals("missing")?Map.of():Map.of("B",row("JOB","B",state.equals("stopped")?"N":"Y",state.equals("deleted")?"Y":"N",null));
        assertThatThrownBy(()->validator.references(group("SKILL"),change,null,next,targets))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.field()).isEqualTo("changes[0].addVal1"));
    }
    @Test void anotherGroupsAdditionalFieldIsRejected() throws Exception {
        Change change=change("\"code\":\"A\",\"isNew\":true,\"name\":\"코드\",\"addVal1\":\"B\"");
        assertThatThrownBy(()->validator.apply(group("EDUCATION"),null,change,1))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.field()).isEqualTo("changes[0].addVal1"));
    }
    @Test void mandatoryIsNewAndTypedCodeAreNotInferred() throws Exception {
        assertThatThrownBy(()->change("\"code\":\"A\",\"name\":\"이름\""))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.field()).isEqualTo("changes[0].isNew"));
    }
    @Test void ordinaryNewRowNeedsNameAndApplicableExtraValues() throws Exception {
        Change missingName=change("\"code\":\"A\",\"isNew\":true");
        assertThatThrownBy(()->validator.apply(group("EDUCATION"),null,missingName,1)).isInstanceOf(ApiException.class);
        Change missingExtra=change("\"code\":\"A\",\"isNew\":true,\"name\":\"이름\"");
        assertThatThrownBy(()->validator.apply(group("BANK"),null,missingExtra,1))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.field()).isEqualTo("changes[0].addVal1"));
    }
}
