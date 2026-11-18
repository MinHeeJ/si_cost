package com.sicost.menumanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
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
import static com.sicost.menumanagement.MenuManagementDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@com.sicost.common.BusinessMvcSlice
@WebMvcTest(MenuManagementController.class)
@ContextConfiguration(classes=MenuManagementController.class)
@Import({MenuManagementService.class,MenuManagementRepository.class,WriteCoordinator.class,RevisionService.class,
    ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class MenuManagementContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean MenuManagementMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper audit;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    List<Stored> rows;
    static final LocalDateTime AT=LocalDateTime.of(2026,1,1,0,0,0,123456000);
    Stored row(String id,String parent,String name,int level,String screen,int order,boolean use,boolean pc,boolean locked) {
        return new Stored(id,parent,name,level,screen,order,use,pc,locked,null,"admin",AT,null,null);
    }
    @BeforeEach void setup() {
        rows=new ArrayList<>(List.of(row("G1",null,"업무",1,null,1,true,false,false),row("G2",null,"시스템",1,null,2,true,false,true),
            row("M1","G1","목록",2,"UI-01",1,true,false,false),row("M2","G1","중지",2,"UI-02",2,false,true,false),
            row("M3","G2","메뉴",2,"UI-25",1,true,true,true)));
        when(mapper.tree()).thenAnswer(c->List.copyOf(rows));
        when(users.userId()).thenReturn("admin");
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(audit.insert(any())).thenReturn(1);
        when(mapper.update(any())).thenAnswer(c->{
            Mutation m=c.getArgument(0);MenuInput n=m.input();Stored old=rows.stream().filter(r->r.menuId().equals(n.menuId())).findFirst().orElseThrow();
            rows.set(rows.indexOf(old),new Stored(old.menuId(),n.parentId(),n.name(),old.level(),old.screenId(),n.sortOrder(),m.useYn().equals("Y"),m.pcOnlyYn().equals("Y"),old.locked(),old.icon(),old.regUserId(),old.regDttm(),m.actor(),m.now()));return 1;
        });
    }
    String revision() {return revisions.revision(rows);}
    ObjectNode body() {
        ObjectNode body=json.createObjectNode().put("expectedRevision",revision());ArrayNode items=body.putArray("items");
        rows.forEach(r->{ObjectNode n=items.addObject().put("menuId",r.menuId()).put("name",r.name()).put("level",r.level()).put("parentId",r.parentId()).put("screenId",r.screenId()).put("sortOrder",r.sortOrder());
            if(r.level()==2)n.put("use",r.use()).put("pcOnly",r.pcOnly());});return body;
    }
    ObjectNode node(ObjectNode body,int i) {return (ObjectNode)body.get("items").get(i);}
    org.springframework.test.web.servlet.ResultActions save(ObjectNode body) throws Exception {
        return mvc.perform(put("/api/menu-management").contentType("application/json").content(body.toString()));
    }
    void noWrites() {verify(mapper,never()).update(any());verifyNoInteractions(audit,invalidation);}
    @Test void publicReadIncludesStoppedAndEmptyRootsAndExactPublicFieldsWithoutWrites() throws Exception {
        String before=revision();
        mvc.perform(get("/api/menu-management")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(5))
            .andExpect(jsonPath("$.data.items[2].use").value(false)).andExpect(jsonPath("$.data.items[4].locked").value(true))
            .andExpect(jsonPath("$.data.items[0].regUserId").doesNotExist()).andExpect(jsonPath("$.data.revision").value(before))
            .andExpect(jsonPath("$.data.developmentMode").value(false)).andExpect(jsonPath("$.data.accessEnforcement").value(true));
        assertThat(revision()).isEqualTo(before);verify(users).currentUser();noWrites();
    }
    @Test void emptyTreeIsReadOnly() throws Exception {rows.clear();mvc.perform(get("/api/menu-management")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());noWrites();}
    @Test void readFailureNeverCreatesInitialValues() throws Exception {when(mapper.tree()).thenThrow(new IllegalStateException("private sql"));mvc.perform(get("/api/menu-management")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));noWrites();}
    @ParameterizedTest @ValueSource(strings={"actorId","regUserId","role","businessRole","sort","q"})
    void queryInjectionRejected(String field) throws Exception {mvc.perform(get("/api/menu-management").param(field,"x")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();}
    @Test void getBodyInjectionRejected() throws Exception {mvc.perform(get("/api/menu-management").contentType("application/json").content("{\"actorId\":\"other\"}")).andExpect(status().isBadRequest());noWrites();}
    @Test void completeEditMovesOrdinaryChildAndNormalizesSiblingOrdersWithFieldAuditOnly() throws Exception {
        ObjectNode body=body();node(body,2).put("parentId","G2").put("sortOrder",7).put("name"," 새 목록 ").put("use",false).put("pcOnly",true);
        node(body,4).put("name","새 메뉴").put("pcOnly",false);
        save(body).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        assertThat(rows.get(2).parentId()).isEqualTo("G2");assertThat(rows.get(2).sortOrder()).isEqualTo(2);assertThat(rows.get(2).name()).isEqualTo("새 목록");
        assertThat(rows.get(4).locked()).isTrue();assertThat(rows.get(4).pcOnly()).isFalse();
        verify(locks).lock("TB_SY_MENU");verify(locks).lock("TB_SY_ROLE");
        verify(audit,atLeastOnce()).insert(argThat(h->h.tableName().equals("TB_SY_MENU") && h.userId().equals("admin") && h.type().equals("UPD")));
        clearInvocations(mapper,audit,invalidation);save(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"missing","duplicate","unregistered","parent","self","cycle","third","level","screen","locked","icon","rootUse","rootPc","name","length","boolean","fraction","order","actorId","regUserId"})
    void invalidWholeSetNeverWrites(String rule) throws Exception {
        ObjectNode body=body();ObjectNode n=node(body,2);
        switch(rule) {
            case "missing" -> ((ArrayNode)body.get("items")).remove(2);
            case "duplicate" -> ((ArrayNode)body.get("items")).add(n.deepCopy());
            case "unregistered" -> n.put("menuId","absent");
            case "parent" -> n.put("parentId","absent");
            case "self" -> n.put("parentId","M1");
            case "cycle" -> {n.put("parentId","M2");node(body,3).put("parentId","M1");}
            case "third" -> n.put("level",3);
            case "level" -> {n.put("level",1).putNull("parentId").putNull("screenId");n.remove(List.of("use","pcOnly"));}
            case "screen" -> n.put("screenId","UI-03");
            case "locked","icon" -> n.put(rule,true);
            case "rootUse" -> node(body,0).put("use",false);
            case "rootPc" -> node(body,0).put("pcOnly",true);
            case "name" -> n.put("name"," ");
            case "length" -> n.put("name","가".repeat(51));
            case "boolean" -> n.put("use","Y");
            case "fraction" -> n.put("sortOrder",1.5);
            case "order" -> n.put("sortOrder",0);
            default -> body.put(rule,"injected");
        }
        String before=revision();save(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").isString()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.matchesPattern(".*[가-힣].*")));
        assertThat(revision()).isEqualTo(before);noWrites();
    }
    @Test void lockedUseIsConflict() throws Exception {ObjectNode b=body();node(b,4).put("use",false);save(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MENU_LOCKED"));noWrites();}
    @Test void lockedParentIsConflictEvenAfterRename() throws Exception {ObjectNode b=body();node(b,4).put("name","임의 이름").put("parentId","G1");save(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MENU_LOCKED"));noWrites();}
    @Test void protectedScreenCannotBypassLockWhenFlagIsFalse() throws Exception {Stored r=rows.get(4);rows.set(4,row(r.menuId(),r.parentId(),"변경된 이름",2,"UI-25",1,true,false,false));ObjectNode b=body();node(b,4).put("use",false);save(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MENU_LOCKED"));noWrites();}
    @Test void duplicateSiblingNameConflicts() throws Exception {ObjectNode b=body();node(b,3).put("name","목록");save(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));noWrites();}
    @Test void duplicateRootNameConflicts() throws Exception {ObjectNode b=body();node(b,1).put("name","업무");save(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));noWrites();}
    @Test void sameNameUnderDifferentParentsAllowed() throws Exception {ObjectNode b=body();node(b,4).put("name","목록");save(b).andExpect(status().isOk());}
    @Test void stalePrecedesNoOp() throws Exception {save(body().put("expectedRevision","old")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();}
    @Test void noOpPreservesAuditAndTimestamps() throws Exception {save(body()).andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));noWrites();}
    @Test void auditZeroIsSafe500AndTransactionRollbackRequested() throws Exception {when(audit.insert(any())).thenReturn(0);ObjectNode b=body();node(b,2).put("name","수정");save(b).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));verify(manager).rollback(any());verifyNoInteractions(invalidation);}
}
