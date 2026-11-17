package com.sicost.menumanagementops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.common.api.*;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.menumanagement.*;
import com.sicost.menumanagement.MenuManagementDtos.Stored;
import com.sicost.shared.MenuService;
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
import static com.sicost.menumanagementops.MenuRegistrationDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MenuRegistrationController.class)
@ContextConfiguration(classes=MenuRegistrationController.class)
@Import({MenuRegistrationService.class,MenuRegistrationRepository.class,MenuManagementService.class,MenuManagementRepository.class,
    WriteCoordinator.class,RevisionService.class,ChangeHistoryWriter.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class MenuRegistrationContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RevisionService revisions;
    @MockBean MenuRegistrationMapper mapper;
    @MockBean MenuManagementMapper treeMapper;
    @MockBean MenuService screens;
    @MockBean CurrentUserProvider users;
    @MockBean ChangeHistoryMapper history;
    @MockBean AggregateLockMapper locks;
    @MockBean CommitInvalidation invalidation;
    @MockBean PlatformTransactionManager manager;
    @MockBean Clock clock;
    List<Stored> rows;
    static final LocalDateTime AT=LocalDateTime.of(2026,1,1,0,0,0,123456000);
    Stored row(String id,String parent,String name,int level,String screen,int order,boolean use) {
        return new Stored(id,parent,name,level,screen,order,use,false,false,level==1?"folder":null,"admin",AT,null,null);
    }
    @BeforeEach void setup() {
        rows=new ArrayList<>(List.of(row("G001",null,"업무",1,null,1,true),row("M001","G001","중지 화면",2,"UI-02",1,false)));
        when(treeMapper.tree()).thenAnswer(c->rows.stream().sorted(Comparator.comparing(Stored::menuId)).toList());
        when(screens.screenRegistrations()).thenReturn(List.of(new MenuService.ScreenRegistration("UI-03","개인별 수익성",true),
            new MenuService.ScreenRegistration("UI-02","인력",true),new MenuService.ScreenRegistration("UI-01","준비",false),
            new MenuService.ScreenRegistration("UI-20","제외",true),new MenuService.ScreenRegistration("UI-22","로그인",true)));
        when(users.userId()).thenReturn("admin");
        when(users.currentUser()).thenReturn(new CurrentUserProvider.CurrentUser("admin","관리자",List.of("ADMIN"),true));
        when(clock.instant()).thenReturn(Instant.parse("2026-06-15T00:00:00Z"));when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(manager.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        when(mapper.roleIds()).thenReturn(List.of("ADMIN","STOPPED"));
        when(history.insert(any())).thenReturn(1);when(mapper.insertPermission(any())).thenReturn(1);
        when(mapper.insertMenu(any())).thenAnswer(c->{NewMenu n=c.getArgument(0);Create i=n.input();
            rows.add(new Stored(n.menuId(),i.parentId(),i.name(),i.level(),i.screenId(),n.sortOrder(),true,i.pcOnly(),false,i.level()==1?"folder":null,n.actor(),n.now(),null,null));return 1;});
    }
    String revision() {return revisions.revision(rows.stream().sorted(Comparator.comparing(Stored::menuId)).toList());}
    ObjectNode root() {return json.createObjectNode().put("level",1).put("name"," 신규 ").put("expectedRevision",revision());}
    ObjectNode child() {return root().put("level",2).put("parentId","G001").put("screenId","UI-03");}
    org.springframework.test.web.servlet.ResultActions create(ObjectNode body) throws Exception {
        return mvc.perform(post("/api/menu-management/menus").contentType("application/json").content(body.toString()));
    }
    void noWrites() {verify(mapper,never()).insertMenu(any());verify(mapper,never()).insertPermission(any());verifyNoInteractions(history,invalidation);}
    @Test void publicAvailableScreenReadMatchesRegistryAndSubtractsStoppedLinksWithoutWrites() throws Exception {
        String before=revision();
        mvc.perform(get("/api/menu-management/screens")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].screenId").value("UI-03"))
            .andExpect(jsonPath("$.data.items[0].name").value("개인별 수익성")).andExpect(jsonPath("$.data.revision").value(before));
        verify(users).currentUser();assertThat(revision()).isEqualTo(before);noWrites();
    }
    @Test void fullyLinkedRegistryReturnsEmptyWithoutManufacturingScreens() throws Exception {
        rows.add(row("M002","G001","개인별 수익성",2,"UI-03",2,true));
        mvc.perform(get("/api/menu-management/screens")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());noWrites();
    }
    @Test void readFailureIsSafeAndNeverSeedsOrWrites() throws Exception {
        when(treeMapper.tree()).thenThrow(new IllegalStateException("private sql"));
        mvc.perform(get("/api/menu-management/screens")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"actorId","regUserId","sort","screenId","q","expectedRevision"})
    void readQueryInjectionRejected(String key) throws Exception {
        mvc.perform(get("/api/menu-management/screens").param(key,"x")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD")).andExpect(jsonPath("$.field").value(key));noWrites();
    }
    @Test void readBodyInjectionRejected() throws Exception {
        mvc.perform(get("/api/menu-management/screens").contentType("application/json").content("{\"actorId\":\"other\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();
    }
    @Test void rootCreateWithoutAuthIssuesIdAndDefaultsAtTreeEndWithNoPermissionOrAccessRows() throws Exception {
        create(root()).andExpect(status().isCreated()).andExpect(jsonPath("$.data.changed").value(true))
            .andExpect(jsonPath("$.data.value.developmentMode").value(true)).andExpect(jsonPath("$.data.value.accessEnforcement").value(false));
        Stored n=rows.get(rows.size()-1);assertThat(n.menuId()).isEqualTo("G002");assertThat(n.name()).isEqualTo("신규");
        assertThat(n.icon()).isEqualTo("folder");assertThat(n.screenId()).isNull();assertThat(n.parentId()).isNull();
        assertThat(n.pcOnly()).isFalse();assertThat(n.locked()).isFalse();assertThat(n.use()).isTrue();assertThat(n.sortOrder()).isEqualTo(2);
        verify(mapper,never()).insertPermission(any());verify(locks).lock("TB_SY_MENU");verify(locks).lock("TB_SY_ROLE");
        verify(history,atLeastOnce()).insert(argThat(h->h.userId().equals("admin")&&h.type().equals("NEW")&&h.tableName().equals("TB_SY_MENU")));
    }
    @Test void childUsesParentEndAndSelectedPcAndAllRolesIncludingStoppedReceiveFalseRows() throws Exception {
        create(child().put("pcOnly",true)).andExpect(status().isCreated());
        Stored n=rows.get(rows.size()-1);assertThat(n.menuId()).isEqualTo("M002");assertThat(n.icon()).isNull();assertThat(n.pcOnly()).isTrue();
        assertThat(n.sortOrder()).isEqualTo(2);assertThat(n.screenId()).isEqualTo("UI-03");assertThat(n.regUserId()).isEqualTo("admin");
        verify(mapper).insertPermission(argThat(p->p.roleId().equals("STOPPED")&&p.menuId().equals("M002")));
        verify(history,times(2)).insert(argThat(h->h.tableName().equals("TB_SY_ROLE_MENU_AUTH")&&h.targetKey().endsWith(":M002")
            &&h.after().equals("{\"INQ_YN\":\"N\",\"REG_YN\":\"N\",\"UPD_YN\":\"N\",\"DEL_YN\":\"N\",\"UPLD_YN\":\"N\",\"DNLD_YN\":\"N\",\"CNFM_YN\":\"N\"}")));
    }
    @Test void omittedPcDefaultsFalse() throws Exception {create(child()).andExpect(status().isCreated());assertThat(rows.get(rows.size()-1).pcOnly()).isFalse();}
    @Test void idsUseNumericMaximumAcrossZeroPaddingAndSkipExistingSeries() throws Exception {
        rows.add(row("G0009",null,"다른 그룹",1,null,2,true));rows.add(row("G010",null,"마지막 그룹",1,null,3,true));
        create(root()).andExpect(status().isCreated());assertThat(rows.get(rows.size()-1).menuId()).isEqualTo("G011");
    }
    @ParameterizedTest @ValueSource(strings={"blank","length","missingRevision","parentMissing","screenMissing","level","levelString","fraction","nameType","pcString","pcNull","parentLength","screenLength"})
    void invalidInputNeverWrites(String rule) throws Exception {
        ObjectNode b=child();
        switch(rule) {
            case "blank"->b.put("name"," ");case "length"->b.put("name","가".repeat(51));case "missingRevision"->b.remove("expectedRevision");
            case "parentMissing"->b.remove("parentId");case "screenMissing"->b.remove("screenId");case "level"->b.put("level",3);
            case "levelString"->b.put("level","2");case "fraction"->b.put("level",1.5);case "nameType"->b.put("name",true);
            case "pcString"->b.put("pcOnly","Y");case "pcNull"->b.putNull("pcOnly");case "parentLength"->b.put("parentId","x".repeat(21));case "screenLength"->b.put("screenId","x".repeat(11));
        }
        create(b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").isString())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.matchesPattern(".*[가-힣].*")));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"menuId","locked","icon","sortOrder","use","actorId","regUserId","url","regDttm"})
    void serverOwnedAndUnknownInputsRejected(String key) throws Exception {
        create(child().put(key,"injected")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"parentId","screenId","pcOnly"})
    void rootCannotRetainSubmenuFields(String key) throws Exception {create(root().put(key,"x")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));noWrites();}
    @Test void postUnknownQueryRejected() throws Exception {mvc.perform(post("/api/menu-management/menus").param("actorId","x").contentType("application/json").content(root().toString())).andExpect(status().isBadRequest());noWrites();}
    @Test void missingParent404() throws Exception {create(child().put("parentId","missing")).andExpect(status().isNotFound());noWrites();}
    @Test void submenuParentCannotBeChild() throws Exception {create(child().put("parentId","M001")).andExpect(status().isBadRequest());noWrites();}
    @Test void stoppedLinkedScreenConflictsWithResumeGuidance() throws Exception {create(child().put("screenId","UI-02")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_SCREEN")).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("재개")));noWrites();}
    @ParameterizedTest @ValueSource(strings={"UI-20","UI-22","UI-01","UI-99","/UI-03","https://a"})
    void onlyImplementedBusinessRegistryScreensAccepted(String screen) throws Exception {create(child().put("screenId",screen)).andExpect(status().isBadRequest());noWrites();}
    @Test void duplicateRootNameConflicts() throws Exception {create(root().put("name"," 업무 ")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));noWrites();}
    @Test void duplicateSiblingNameConflicts() throws Exception {create(child().put("name","중지 화면")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));noWrites();}
    @Test void sameNameInDifferentParentIsAllowed() throws Exception {rows.add(row("G002",null,"다른 그룹",1,null,2,true));create(child().put("parentId","G002").put("name","중지 화면")).andExpect(status().isCreated());}
    @Test void staleTreeChangesNothing() throws Exception {create(root().put("expectedRevision","old")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WRITE"));noWrites();}
    @Test void treeRevisionIncludesMicrosecondAuditPrecision() throws Exception {String old=revision();Stored r=rows.get(0);rows.set(0,new Stored(r.menuId(),r.parentId(),r.name(),r.level(),r.screenId(),r.sortOrder(),r.use(),r.pcOnly(),r.locked(),r.icon(),r.regUserId(),r.regDttm().plusNanos(1000),null,null));create(root().put("expectedRevision",old)).andExpect(status().isConflict());noWrites();}
    @Test void sortOverflowIsConflictBeforeInsert() throws Exception {rows.add(row("G002",null,"끝",1,null,99999,true));create(root()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SORT_CAPACITY_EXCEEDED"));noWrites();}
    @Test void permissionFailureRequestsRollbackAndNoInvalidation() throws Exception {when(mapper.insertPermission(any())).thenReturn(0);create(child()).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));verify(manager).rollback(any());verifyNoInteractions(invalidation);}
    @Test void requiredHistoryZeroRequestsRollback() throws Exception {when(history.insert(any())).thenReturn(0);create(root()).andExpect(status().isInternalServerError());verify(manager).rollback(any());verifyNoInteractions(invalidation);}
}
