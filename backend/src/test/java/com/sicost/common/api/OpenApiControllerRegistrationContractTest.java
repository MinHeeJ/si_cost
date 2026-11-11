package com.sicost.common.api;

import com.sicost.apiops.MasterHistoryController;
import com.sicost.apiops.MasterHistoryService;
import com.sicost.apiops.PeopleHistoryController;
import com.sicost.apiops.PeopleHistoryService;
import com.sicost.areasops.AddressController;
import com.sicost.areasops.AddressService;
import com.sicost.b8.B8Controller;
import com.sicost.b8.B8Service;
import com.sicost.candidates.CandidateController;
import com.sicost.candidates.CandidateQueryService;
import com.sicost.candidates.CandidateService;
import com.sicost.candidatesops.CandidateSummaryController;
import com.sicost.candidatesops.CandidateSummaryService;
import com.sicost.contacts.CandidateContactController;
import com.sicost.contacts.CandidateContactService;
import com.sicost.developers.DeveloperController;
import com.sicost.developers.DeveloperQueryService;
import com.sicost.developers.DeveloperService;
import com.sicost.developersops.DeveloperActionsController;
import com.sicost.developersops.DeveloperActionsQueryService;
import com.sicost.developersops.DeveloperBulkStatusService;
import com.sicost.developersops.DeveloperStatusRecalculationService;
import com.sicost.states.DeveloperStateController;
import com.sicost.states.DeveloperStateService;
import com.sicost.b7.B7Controller;
import com.sicost.b7.B7Service;
import com.sicost.businessusers.BusinessUserController;
import com.sicost.businessusers.BusinessUserService;
import com.sicost.businessusersops.BusinessUserOptionsController;
import com.sicost.businessusersops.BusinessUserOptionsService;
import com.sicost.clients.ClientController;
import com.sicost.clients.ClientService;
import com.sicost.clientsops.ClientActionsController;
import com.sicost.clientsops.ClientActionsService;
import com.sicost.clientsops.ClientAccountService;
import com.sicost.clientsops.ClientBulkStatusService;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.exportsops.MasterDataExportController;
import com.sicost.exportsops.MasterDataExportService;
import com.sicost.salesparts.SalesPartController;
import com.sicost.salesparts.SalesPartService;
import com.sicost.salespartsops.PartStandardController;
import com.sicost.salespartsops.PartStandardService;
import com.sicost.uploadsops.UploadIssuesController;
import com.sicost.uploadsops.UploadIssuesService;
import com.sicost.codegroups.CodeGroupController;
import com.sicost.codegroups.CodeGroupService;
import com.sicost.codemappings.CodeMappingsController;
import com.sicost.codemappings.CodeMappingsService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.health.HealthController;
import com.sicost.common.health.HealthService;
import com.sicost.me.PreferenceController;
import com.sicost.me.PreferenceService;
import com.sicost.operatingstandards.OperatingStandardsController;
import com.sicost.operatingstandards.OperatingStandardsService;
import com.sicost.shared.MenuService;
import com.sicost.shared.SharedQueriesController;
import com.sicost.shared.SharedQueriesService;
import java.time.Clock;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest({HealthController.class, SharedQueriesController.class, CodeGroupController.class,
        CodeMappingsController.class, PreferenceController.class, OperatingStandardsController.class,
        BusinessUserController.class, BusinessUserOptionsController.class, ClientController.class,
        ClientActionsController.class, SalesPartController.class, PartStandardController.class,
        MasterHistoryController.class, B7Controller.class, UploadIssuesController.class, MasterDataExportController.class,
        AddressController.class, CandidateController.class, CandidateSummaryController.class, CandidateContactController.class,
        DeveloperController.class, DeveloperActionsController.class, DeveloperStateController.class,
        PeopleHistoryController.class, B8Controller.class})
@ContextConfiguration(classes={HealthController.class, SharedQueriesController.class, CodeGroupController.class,
        CodeMappingsController.class, PreferenceController.class, OperatingStandardsController.class,
        BusinessUserController.class, BusinessUserOptionsController.class, ClientController.class,
        ClientActionsController.class, SalesPartController.class, PartStandardController.class,
        MasterHistoryController.class, B7Controller.class, UploadIssuesController.class, MasterDataExportController.class,
        AddressController.class, CandidateController.class, CandidateSummaryController.class, CandidateContactController.class,
        DeveloperController.class, DeveloperActionsController.class, DeveloperStateController.class,
        PeopleHistoryController.class, B8Controller.class})
class OpenApiControllerRegistrationContractTest {
    @Autowired RequestMappingHandlerMapping handlerMapping;
    @MockBean HealthService health;
    @MockBean SharedQueriesService shared;
    @MockBean MenuService menus;
    @MockBean CurrentUserProvider users;
    @MockBean CodeGroupService groups;
    @MockBean CodeMappingsService mappings;
    @MockBean PreferenceService preferences;
    @MockBean OperatingStandardsService standards;
    @MockBean BusinessUserService businessUsers;
    @MockBean BusinessUserOptionsService businessUserOptions;
    @MockBean ClientService clients;
    @MockBean ClientActionsService clientActions;
    @MockBean ClientAccountService clientAccounts;
    @MockBean ClientBulkStatusService clientStatuses;
    @MockBean SalesPartService salesParts;
    @MockBean PartStandardService partStandards;
    @MockBean MasterHistoryService masterHistory;
    @MockBean B7Service b7;
    @MockBean ExcelExportService excel;
    @MockBean UploadIssuesService uploadIssues;
    @MockBean MasterDataExportService exports;
    @MockBean AddressService addresses;
    @MockBean CandidateService candidates;
    @MockBean CandidateQueryService candidateQueries;
    @MockBean CandidateSummaryService candidateSummary;
    @MockBean CandidateContactService candidateContacts;
    @MockBean DeveloperService developers;
    @MockBean DeveloperQueryService developerQueries;
    @MockBean DeveloperActionsQueryService developerActions;
    @MockBean DeveloperBulkStatusService developerStatuses;
    @MockBean DeveloperStatusRecalculationService developerRecalculation;
    @MockBean DeveloperStateService developerStates;
    @MockBean PeopleHistoryService peopleHistory;
    @MockBean B8Service b8;
    @MockBean Clock clock;

    @Test void everyDurableOpenApiOperationHasExactlyOneRegisteredLiteralHandler() {
        var yaml=new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource("contracts/openapi.yaml"));
        Map<String,Object> contract=yaml.getObject();
        assertThat(contract).isNotNull();
        assertThat(contract.get("paths")).isInstanceOf(Map.class);
        Map<?,?> paths=(Map<?,?>)contract.get("paths");
        Set<String> expected=new HashSet<>();
        Set<String> verbs=Set.of("get","post","put","delete","patch","head","options");
        paths.forEach((path,item)->((Map<?,?>)item).forEach((method,definition)->{
            if(verbs.contains(method.toString())) {
                Map<?,?> operation=(Map<?,?>)definition;
                expected.add(method.toString().toUpperCase(java.util.Locale.ROOT)+" "+normalize(path.toString()));
                assertThat(operation.get("operationId")).isInstanceOf(String.class);
                var matches=handlerMapping.getHandlerMethods().entrySet().stream().filter(entry->
                    entry.getKey().getPatternValues().stream().anyMatch(p->normalize(p).equals(normalize(path.toString())))
                    && entry.getKey().getMethodsCondition().getMethods().stream().anyMatch(m->m.name().equalsIgnoreCase(method.toString())))
                    .toList();
                assertThat(matches).as("%s %s",method,path).hasSize(1);
                assertThat(matches.get(0).getValue().getMethod().getName()).isEqualTo(operation.get("operationId"));
                assertThat(matches.get(0).getKey().getPatternValues()).hasSize(1);
            }
        }));
        Set<String> actual=new HashSet<>();
        handlerMapping.getHandlerMethods().forEach((mapping,handler)->mapping.getPatternValues().stream()
            .filter(path->path.startsWith("/api/")).forEach(path->mapping.getMethodsCondition().getMethods()
                .forEach(method->actual.add(method.name()+" "+normalize(path)))));
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(expected).isNotEmpty();
    }
    private static String normalize(String path) { return path.replaceAll("\\{[^}]+}","{}"); }
}
