package com.sicost.shared;

import com.sicost.common.api.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.shared.SharedQueryDtos.MenuRow;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SharedQueriesController.class)
@ContextConfiguration(classes=SharedQueriesController.class)
@Import({MenuService.class,SharedQueriesService.class,ApiExceptionHandler.class,RequestIdFilter.class,JsonConfiguration.class})
class MenusContractTest {
    @Autowired MockMvc mvc;
    @MockBean SharedQueriesMapper mapper;
    @MockBean CurrentUserProvider users;
    @MockBean Clock clock;
    MenuRow row(String id,String parent,String screen,int level,int order) { return new MenuRow(id,parent,"DB "+id,screen,level,order,"Y","database",true); }
    @Test void validHierarchyKeepsDatabasePresentationAndStableOrder() throws Exception {
        when(mapper.menus()).thenReturn(List.of(row("G",null,null,1,1),row("B","G","UI-01",2,1),row("A","G","UI-16",2,1)));
        mvc.perform(get("/api/menus")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].name").value("DB G"))
            .andExpect(jsonPath("$.data.items[0].icon").value("database")).andExpect(jsonPath("$.data.items[0].pcOnly").value(true))
            .andExpect(jsonPath("$.data.items[0].children[0].menuId").value("A")).andExpect(jsonPath("$.data.diagnostics").isEmpty());
        verify(mapper).menus(); verifyNoMoreInteractions(mapper);
    }
    @Test void businessInvalidTreesAreExcludedWithoutReparentOrWrites() throws Exception {
        var rows=new ArrayList<>(List.of(row("G",null,null,1,1),row("OK","G","UI-16",2,2),row("SELF","SELF",null,2,1),
            row("C1","C2",null,2,1),row("C2","C1",null,2,1),row("MISSING","NO",null,2,1),
            row("DEPTH","G","UI-01",3,1),row("CHILD","DEPTH","UI-02",3,1),row("UNKNOWN","G","UI-20",2,1),row("UNKNOWN_CHILD","UNKNOWN","UI-03",3,1),
            row("DUP","G","UI-16",2,9),row("DUP_CHILD","DUP","UI-04",3,1),row("STOP_CHILD","STOP","UI-05",2,1)));
        rows.add(new MenuRow("STOP",null,"중지",null,1,1,"N",null,false));
        when(mapper.menus()).thenReturn(rows);
        mvc.perform(get("/api/menus")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].children.length()").value(1)).andExpect(jsonPath("$.data.items[0].children[0].menuId").value("OK"))
            .andExpect(jsonPath("$.data.diagnostics.length()").value(rows.size()-2));
        verify(mapper).menus(); verifyNoMoreInteractions(mapper);
    }
    @Test void invalidDuplicateCannotDefeatValidHierarchy() throws Exception {
        when(mapper.menus()).thenReturn(List.of(row("G",null,null,1,1),row("GOOD","G","UI-16",2,9),row("BAD","NO","UI-16",2,0)));
        mvc.perform(get("/api/menus")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].children[0].menuId").value("GOOD"));
    }
    @Test void emptyDatabaseReturnsEmptyAndDoesNotSeedMenus() throws Exception {
        when(mapper.menus()).thenReturn(List.of());
        mvc.perform(get("/api/menus")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        verify(mapper).menus(); verifyNoMoreInteractions(mapper);
    }
}
