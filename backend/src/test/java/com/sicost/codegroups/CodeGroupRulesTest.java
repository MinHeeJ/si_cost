package com.sicost.codegroups;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.sicost.codegroups.CodeGroupDtos.*;

@WebMvcTest(CodeGroupController.class)
@org.springframework.test.context.ContextConfiguration(classes=com.sicost.common.CostManagementApplication.class)
@Import({CodeGroupService.class,CodeGroupValidator.class,CodeReferenceGuard.class,RevisionService.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class CodeGroupRulesTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean CodeGroupMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryWriter history;
    @MockBean CommitInvalidation invalidation;
    StoredGroup group;
    List<StoredCode> rows;
    @BeforeEach void setup() {
        when(users.userId()).thenReturn("admin");
        configure("EDUCATION","N","N",List.of(code("EDUCATION","A","원래 이름",1,"Y","N",null,null,null,null)));
    }
    void configure(String id,String sys,String val,List<StoredCode> values) {
        group=new StoredGroup(id,"독립 시험 그룹",sys,val,1,"Y","admin",null,null,null);rows=values;
        when(mapper.group(eq(id),anyBoolean())).thenReturn(group);
        when(mapper.codes(eq(id),anyBoolean())).thenAnswer(call -> rows);
        when(mapper.references(anyString(),anyString())).thenReturn(List.of());
        doAnswer(call -> {
            Mutation mutation=call.getArgument(0);List<StoredCode> changed=new ArrayList<>(rows);
            changed.removeIf(row -> row.code().equals(mutation.row().code()));changed.add(mutation.row());rows=changed;return null;
        }).when(mapper).updateCode(any());
        doAnswer(call -> {Mutation mutation=call.getArgument(0);List<StoredCode> changed=new ArrayList<>(rows);changed.add(mutation.row());rows=changed;return null;}).when(mapper).insertCode(any());
    }
    StoredCode code(String id,String code,String name,int order,String use,String del,String value,String unit,String a1,String a2) {
        return new StoredCode(id,code,name,order,use,del,value==null?null:new BigDecimal(value),unit,a1,a2,null,"admin",null,null,null);
    }
    String token() {return revisions.revision(new Snapshot(group,rows));}
    org.springframework.test.web.servlet.ResultActions putChanges(String changes) throws Exception {
        String body="{\"expectedRevision\":\""+token()+"\",\"changes\":"+changes+"}";
        return mvc.perform(put("/api/code-groups/"+group.groupId()+"/codes").contentType("application/json").content(body));
    }
    void unchanged() {verify(mapper,never()).updateCode(any());verify(mapper,never()).insertCode(any());verifyNoInteractions(history,invalidation);}

    @Test void listIncludesStoppedNotDeletedAndCountIsWholeGroup() throws Exception {
        configure("EDUCATION","N","N",List.of(rows.get(0),code("EDUCATION","B","중지",2,"N","N",null,null,null,null),code("EDUCATION","C","삭제",3,"Y","Y",null,null,null,null)));
        mvc.perform(get("/api/code-groups/EDUCATION/codes")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.items[1].useYn").value("N"))
            .andExpect(jsonPath("$.data.items[0].addVal1").isEmpty()).andExpect(jsonPath("$.data.groupRevision").value(token()));unchanged();
    }
    @Test void otherPagesContributeToRevisionWithNullAuditTimestamps() throws Exception {
        String before=token();rows=List.of(code("EDUCATION","A","외부에서 변경",1,"Y","N",null,null,null,null));
        assertThat(token()).isNotEqualTo(before);
        mvc.perform(put("/api/code-groups/EDUCATION/codes").contentType("application/json").content("{\"expectedRevision\":\""+before+"\",\"changes\":[{\"code\":\"A\",\"isNew\":false,\"name\":\"후행\"}]}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));unchanged();
    }
    @ParameterizedTest @ValueSource(strings={"page=0","page=bad","size=30","size=0"})
    void paginationValidation(String query) throws Exception {
        mvc.perform(get("/api/code-groups/EDUCATION/codes?"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value(query.split("=")[0]));unchanged();
    }
    @Test void missingGroup404() throws Exception {
        mvc.perform(get("/api/code-groups/MISSING/codes")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));unchanged();
    }
    @Test void changedRowsOnlyAndNewRowsInRequestOrder() throws Exception {
        putChanges("[{\"code\":\"A\",\"isNew\":false,\"name\":\"동명 허용\"},{\"code\":\"Z\",\"isNew\":true,\"name\":\"동명 허용\"},{\"code\":\"B\",\"isNew\":true,\"name\":\"동명 허용\"}]")
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true)).andExpect(jsonPath("$.data.items[1].code").value("Z"));
        assertThat(rows).filteredOn(row -> row.code().equals("Z")).extracting(StoredCode::sortOrder).containsExactly(2);
        assertThat(rows).filteredOn(row -> row.code().equals("B")).extracting(StoredCode::sortOrder).containsExactly(3);
        verify(history).append(argThat(change -> change.item().equals("CODE_NM")&&change.before().equals("원래 이름")&&change.after().equals("동명 허용")));
        verify(invalidation).afterCommit("codes:EDUCATION");
    }
    @Test void noChangeDoesNotUpdateTimestampOrHistory() throws Exception {
        putChanges("[{\"code\":\"A\",\"isNew\":false,\"name\":\"원래 이름\"}]")
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false)).andExpect(jsonPath("$.data.message").value("바뀐 내용이 없습니다."));unchanged();
    }
    @ParameterizedTest @ValueSource(strings={"modUserId","sysCodeYn","delYn","addVal3","regDttm"})
    void injectedFieldsFailBeforeModeRules(String field) throws Exception {
        putChanges("[{\"code\":\"A\",\"isNew\":false,\""+field+"\":\"other\"}]")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value("changes[0]."+field));unchanged();
    }
    @ParameterizedTest @ValueSource(strings={"useYn","codeVal","unitNm","addVal1","sortOrder"})
    void systemPrecedesValueAndMixedChangeIsEntirelyRefused(String field) throws Exception {
        configure("CTRT_TYPE","Y","Y",List.of(code("CTRT_TYPE","TK","턴키",1,"Y","N","1","원",null,null)));
        putChanges("[{\"code\":\"TK\",\"isNew\":false,\"name\":\"새 이름\",\""+field+"\":\"N\"}]")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SYSTEM_CODE_LOCKED"));unchanged();
    }
    @Test void systemNewCodeIsLockedEvenWhenItsPrimaryKeyExistsAtOrderLimit() throws Exception {
        configure("CTRT_TYPE","Y","Y",List.of(code("CTRT_TYPE","TK","턴키",99999,"Y","N",null,null,null,null)));
        putChanges("[{\"code\":\"TK\",\"isNew\":true,\"name\":\"신규\"}]")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SYSTEM_CODE_LOCKED"));unchanged();
    }
    @Test void systemContractNameAloneSucceeds() throws Exception {
        configure("CTRT_TYPE","Y","N",List.of(code("CTRT_TYPE","TK","턴키",1,"Y","N",null,null,null,null)));
        putChanges("[{\"code\":\"TK\",\"isNew\":false,\"name\":\"새 이름\"}]").andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].name").value("새 이름"));
    }
    @Test void systemValueNameAloneSucceeds() throws Exception {
        configure("SYSTEM_VALUE","Y","Y",List.of(code("SYSTEM_VALUE","A","이름",1,"Y","N","1","원",null,null)));
        putChanges("[{\"code\":\"A\",\"isNew\":false,\"name\":\"새 이름\"}]").andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings={"name","unitNm","useYn","sortOrder","addVal1"})
    void valueExistingNonValueFieldsLocked(String field) throws Exception {
        configure("ALLOWANCE","N","Y",List.of(code("ALLOWANCE","A","이름",1,"Y","N","1","원",null,null)));
        putChanges("[{\"code\":\"A\",\"isNew\":false,\""+field+"\":\"변경\"}]")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VALUE_CODE_FIELD_LOCKED"));unchanged();
    }
    @Test void valueFourDecimalPlacesRemainExactAndDefaultNewValueIsZero() throws Exception {
        configure("ALLOWANCE","N","Y",List.of(code("ALLOWANCE","A","이름",1,"Y","N","1","원",null,null)));
        putChanges("[{\"code\":\"A\",\"isNew\":false,\"codeVal\":\"9.1234%\"},{\"code\":\"NEW\",\"isNew\":true,\"name\":\"새 수당\"}]")
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].codeVal").value("9.1234")).andExpect(jsonPath("$.data.items[1].unitNm").value("원"));
        assertThat(rows.get(1).codeVal()).isEqualByComparingTo("0");
    }
    @ParameterizedTest @ValueSource(strings={"1.12345","-1","1e2","NaN","Infinity","100000000000","2x"})
    void invalidDecimalsFailWithoutRounding(String value) throws Exception {
        configure("ALLOWANCE","N","Y",List.of(code("ALLOWANCE","A","이름",1,"Y","N","1","원",null,null)));
        putChanges("[{\"code\":\"A\",\"isNew\":false,\"codeVal\":\""+value+"\"}]")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("changes[0].codeVal"));unchanged();
    }
    @Test void duplicateSubmittedCode400() throws Exception {
        putChanges("[{\"code\":\"A\",\"isNew\":false},{\"code\":\"a\",\"isNew\":true,\"name\":\"새\"}]").andExpect(status().isBadRequest());unchanged();
    }
    @Test void deletedPrimaryKeyCannotBeReused() throws Exception {
        configure("EDUCATION","N","N",List.of(code("EDUCATION","A","삭제",1,"Y","Y",null,null,null,null)));
        putChanges("[{\"code\":\"A\",\"isNew\":true,\"name\":\"새\"}]").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));unchanged();
    }
    @Test void missingExistingRowIsNotUpserted() throws Exception {
        putChanges("[{\"code\":\"UNKNOWN\",\"isNew\":false,\"name\":\"새\"}]").andExpect(status().isNotFound());unchanged();
    }
    @Test void orderLimitRefusesWholeGroupBeforeFirstWrite() throws Exception {
        configure("EDUCATION","N","N",List.of(code("EDUCATION","A","최대",99999,"Y","N",null,null,null,null)));
        putChanges("[{\"code\":\"A\",\"isNew\":false,\"name\":\"변경\"},{\"code\":\"B\",\"isNew\":true,\"name\":\"신규\"}]")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_LIMIT"));unchanged();
    }
    @Test void stopAndResumeHaveSpecificHistory() throws Exception {
        putChanges("[{\"code\":\"A\",\"isNew\":false,\"useYn\":\"N\"}]").andExpect(status().isOk());
        verify(history).append(argThat(c -> c.type().equals("STOP")&&c.item().equals("USE_YN")&&c.before().equals("Y")&&c.after().equals("N")));
        putChanges("[{\"code\":\"A\",\"isNew\":false,\"useYn\":\"Y\"}]").andExpect(status().isOk());
        verify(history).append(argThat(c -> c.type().equals("RESUME")&&c.item().equals("USE_YN")));
    }
    @Test void deleteRechecksReferencesAfterLocksAndRevision() throws Exception {
        when(mapper.references("EDUCATION","A")).thenReturn(List.of(new Reference("표기 대응",1)));
        mvc.perform(delete("/api/code-groups/EDUCATION/codes/A").param("expectedRevision",token()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CODE_IN_USE"))
            .andExpect(jsonPath("$.details.references[0].count").value(1));
        var ordered=inOrder(mapper);ordered.verify(mapper).group("EDUCATION",true);ordered.verify(mapper).codes("EDUCATION",true);ordered.verify(mapper).references("EDUCATION","A");unchanged();
    }
    @Test void deleteMarksPhysicalRowAndWritesOnlyDelHistory() throws Exception {
        mvc.perform(delete("/api/code-groups/EDUCATION/codes/A").param("expectedRevision",token())).andExpect(status().isNoContent());
        assertThat(rows).hasSize(1);assertThat(rows.get(0).delYn()).isEqualTo("Y");
        verify(history).append(argThat(c -> c.type().equals("DEL")&&c.item().equals("DEL_YN")&&c.before().equals("N")&&c.after().equals("Y")));
    }
    @ParameterizedTest @ValueSource(strings={"SYSTEM","VALUE"})
    void protectedModesCannotDelete(String mode) throws Exception {
        configure("PROTECTED",mode.equals("SYSTEM")?"Y":"N","Y",List.of(code("PROTECTED","A","이름",1,"Y","N",null,null,null,null)));
        mvc.perform(delete("/api/code-groups/PROTECTED/codes/A").param("expectedRevision",token()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(mode.equals("SYSTEM")?"SYSTEM_CODE_LOCKED":"VALUE_CODE_FIELD_LOCKED"));unchanged();
    }
    @Test void unknownDeleteQueryIsNotActor() throws Exception {
        mvc.perform(delete("/api/code-groups/EDUCATION/codes/A").param("expectedRevision",token()).param("userId","other"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("userId"));unchanged();
    }
    @Test void createUsesTableLockAndServerOwnedDefaultsAndActor() throws Exception {
        when(mapper.maxGroupOrder()).thenReturn(64);
        mvc.perform(post("/api/code-groups").contentType("application/json").content("{\"groupId\":\" new_group \",\"name\":\" 교육 과정 \"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true));
        var ordered=inOrder(mapper,history,invalidation);
        ordered.verify(mapper).lockGroups();ordered.verify(mapper).group("NEW_GROUP",false);
        ordered.verify(mapper).duplicateName("교육 과정");ordered.verify(mapper).maxGroupOrder();
        ordered.verify(mapper).insertGroup(argThat(row -> row.group().groupId().equals("NEW_GROUP")&&row.actor().equals("admin")&&row.sortOrder()==65));
        ordered.verify(history).append(argThat(c -> c.type().equals("NEW")&&c.before()==null&&c.tableName().equals("TB_SY_CODE_GRP")));
        ordered.verify(invalidation).afterCommit("code-groups");
    }
    @Test void duplicateGroupIdReturnsSpecificConflict() throws Exception {
        mvc.perform(post("/api/code-groups").contentType("application/json").content("{\"groupId\":\"EDUCATION\",\"name\":\"교육\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_GROUP"));
        verify(mapper,never()).insertGroup(any());unchanged();
    }
    @Test void trimmedDuplicateGroupNameReturnsSpecificConflict() throws Exception {
        when(mapper.duplicateName("교육")).thenReturn(1L);
        mvc.perform(post("/api/code-groups").contentType("application/json").content("{\"groupId\":\"NEW_GROUP\",\"name\":\" 교육 \"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_GROUP_NAME"));
        verify(mapper,never()).insertGroup(any());unchanged();
    }
    @ParameterizedTest @ValueSource(strings={"regUserId","sysCodeYn","valCodeYn","delYn","regDttm"})
    void createInjection400(String field) throws Exception {
        mvc.perform(post("/api/code-groups").contentType("application/json").content("{\"groupId\":\"NEW_GROUP\",\"name\":\"교육\",\""+field+"\":\"other\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value(field));
        verify(mapper,never()).lockGroups();unchanged();
    }
    @ParameterizedTest @ValueSource(strings={"0BAD","AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"," ","BAD-ID"})
    void createInvalidGroupId400(String id) throws Exception {
        mvc.perform(post("/api/code-groups").contentType("application/json").content(json.writeValueAsString(Map.of("groupId",id,"name","교육"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("groupId"));
        verify(mapper,never()).lockGroups();unchanged();
    }
    @ParameterizedTest @ValueSource(strings={" ","가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다라마바사아자차카"})
    void createInvalidName400(String name) throws Exception {
        mvc.perform(post("/api/code-groups").contentType("application/json").content(json.writeValueAsString(Map.of("groupId","NEW_GROUP","name",name))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("name"));
        verify(mapper,never()).lockGroups();unchanged();
    }
    @Test void staleDeleteDoesNotEvenCheckReferences() throws Exception {
        mvc.perform(delete("/api/code-groups/EDUCATION/codes/A").param("expectedRevision","old"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));
        verify(mapper,never()).references(anyString(),anyString());unchanged();
    }
}
