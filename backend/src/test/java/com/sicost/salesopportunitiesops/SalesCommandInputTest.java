package com.sicost.salesopportunitiesops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SalesCommandInputTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test void previewAcceptsTheSameFormattedNumbersAsOpportunitySave() throws Exception {
        var need=SalesCommandInput.preview(json.readTree("""
            {"needs":[{"jobCode":" BE ","count":2,"startMonth":"2026-12","months":3,
            "monthlyMm":" 0.25 ","expectedRate":" 10,000,000 "}]}
            """)).needs().get(0);
        assertThat(need.jobCode()).isEqualTo("BE");
        assertThat(need.monthlyMm()).isEqualTo("0.25");
        assertThat(need.expectedRate()).isEqualTo("10000000");
    }
    @Test void formattedWinAndStageAmountsAreNormalized() throws Exception {
        var win=SalesCommandInput.win(json.readTree("""
            {"expectedRevision":"r","contractAmount":" 999,999,999,999,999 ","contractType":"TK",
            "startDate":"2026-12-01","endDate":"2027-01-31","projectStatus":"ING"}
            """));
        assertThat(win.contractAmount()).isEqualTo("999999999999999");
        var stage=SalesCommandInput.stage(json.readTree("""
            {"expectedRevision":"r","stage":"PROP","probability":" 33.33 ","manualProbability":true}
            """));
        assertThat(stage.probability()).isEqualTo("33.33");
    }
    @ParameterizedTest @ValueSource(strings={"1,00","1e3","-1","0","1a000","1,000.00","1,000,000,000,000,000"})
    void malformedOrOutOfRangeRatesAreStillRejected(String value) throws Exception {
        var row=json.createObjectNode().put("expectedRate",value);
        var body=json.createObjectNode();body.putArray("needs").add(row);
        assertThatThrownBy(()->SalesCommandInput.preview(body)).isInstanceOf(ApiException.class);
    }
    @Test void blankOptionalRateUsesDefaultRatherThanBecomingZero() throws Exception {
        var body=json.readTree("{\"needs\":[{\"expectedRate\":\"   \"}]}");
        assertThat(SalesCommandInput.preview(body).needs().get(0).expectedRate()).isNull();
    }
}
