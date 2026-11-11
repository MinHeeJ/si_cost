package com.sicost.exportsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.ApiException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PeopleExportInputTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static JsonNode body(String text) throws Exception { return JSON.readTree(text); }
    private static void rejects(String text, String field) {
        assertThatThrownBy(() -> PeopleExportInput.parse(body(text))).isInstanceOfSatisfying(ApiException.class,
            e -> assertThat(e.field()).isEqualTo(field));
    }

    @Test void routesOnlyPeopleViews() throws Exception {
        assertThat(PeopleExportInput.handles(body("{\"view\":\"DEVELOPERS\"}"))).isTrue();
        assertThat(PeopleExportInput.handles(body("{\"view\":\"CANDIDATES\"}"))).isTrue();
        assertThat(PeopleExportInput.handles(body("{\"view\":\"CLIENTS\"}"))).isFalse();
        assertThat(PeopleExportInput.handles(body("{\"view\":\"PARTS\"}"))).isFalse();
        assertThat(PeopleExportInput.handles(body("{}"))).isFalse();
    }
    @Test void developerRequestKeepsFilterSortColumnsAndUnit() throws Exception {
        var export = PeopleExportInput.parse(body("{\"view\":\"DEVELOPERS\",\"filters\":{\"employeeType\":\"OS\",\"q\":\"홍\",\"includeStopped\":true,\"jobCode\":\"\",\"workStatus\":null},"
            + "\"sort\":\"name\",\"direction\":\"DESC\",\"columns\":[\"name\",\"monthlyRate\",\"status\"],\"amountUnit\":\"백만원\"}"));
        assertThat(export.filters()).isEqualTo(Map.of("employeeType", "OS", "q", "홍", "includeStopped", "true"));
        assertThat(export.sort()).isEqualTo("name");
        assertThat(export.direction()).isEqualTo("DESC");
        assertThat(export.columns()).containsExactly("name", "monthlyRate", "status");
        assertThat(export.amountUnit()).isEqualTo("백만원");
    }
    @Test void defaultsToAscendingAndThousandWon() throws Exception {
        var export = PeopleExportInput.parse(body("{\"view\":\"CANDIDATES\",\"filters\":{},\"columns\":[\"name\"]}"));
        assertThat(export.direction()).isEqualTo("ASC");
        assertThat(export.amountUnit()).isEqualTo("천원");
        assertThat(export.sort()).isNull();
    }
    @Test void candidatesAcceptTheirOwnFiltersOnly() throws Exception {
        var export = PeopleExportInput.parse(body("{\"view\":\"CANDIDATES\",\"filters\":{\"availability\":\"NEXT_MONTH\",\"contactStatus\":\"ITVW\"},\"columns\":[\"name\",\"source\"]}"));
        assertThat(export.filters()).containsEntry("availability", "NEXT_MONTH").containsEntry("contactStatus", "ITVW");
        rejects("{\"view\":\"CANDIDATES\",\"filters\":{\"q\":\"x\"},\"columns\":[\"name\"]}", "filters.q");
        rejects("{\"view\":\"DEVELOPERS\",\"filters\":{\"availability\":\"NEXT_MONTH\"},\"columns\":[\"name\"]}", "filters.availability");
    }
    @Test void rejectsUnknownTopLevelFilterShapeAndColumns() {
        rejects("{\"view\":\"DEVELOPERS\",\"filters\":{},\"columns\":[\"name\"],\"page\":1}", "page");
        rejects("{\"view\":\"DEVELOPERS\",\"columns\":[\"name\"]}", "filters");
        rejects("{\"view\":\"DEVELOPERS\",\"filters\":{\"page\":1},\"columns\":[\"name\"]}", "filters.page");
        rejects("{\"view\":\"DEVELOPERS\",\"filters\":{\"q\":{}},\"columns\":[\"name\"]}", "filters.q");
        rejects("{\"view\":\"DEVELOPERS\",\"filters\":{}}", "columns");
        rejects("{\"view\":\"DEVELOPERS\",\"filters\":{},\"columns\":[]}", "columns");
        rejects("{\"view\":\"DEVELOPERS\",\"filters\":{},\"columns\":[\"monthlyPay\"]}", "columns");
        rejects("{\"view\":\"DEVELOPERS\",\"filters\":{},\"columns\":[\"name\",\"name\"]}", "columns");
        rejects("{\"view\":\"DEVELOPERS\",\"filters\":{},\"columns\":[\"grade\"]}", "columns");
        rejects("{\"view\":\"CANDIDATES\",\"filters\":{},\"columns\":[\"name\",\"phone\"]}", "columns");
        rejects("{\"view\":\"DEVELOPERS\",\"filters\":{},\"columns\":[\"name\"],\"amountUnit\":\"만원\"}", "amountUnit");
    }
    @Test void everyListColumnIsAccepted() throws Exception {
        assertThat(PeopleExportInput.parse(body("{\"view\":\"DEVELOPERS\",\"filters\":{},\"columns\":" + JSON.writeValueAsString(PeopleExportInput.DEVELOPER_COLUMNS) + "}")).columns())
            .isEqualTo(List.copyOf(PeopleExportInput.DEVELOPER_COLUMNS));
        assertThat(PeopleExportInput.parse(body("{\"view\":\"CANDIDATES\",\"filters\":{},\"columns\":" + JSON.writeValueAsString(PeopleExportInput.CANDIDATE_COLUMNS) + "}")).columns())
            .isEqualTo(List.copyOf(PeopleExportInput.CANDIDATE_COLUMNS));
    }
}
