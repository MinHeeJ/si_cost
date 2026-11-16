package com.sicost.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.common.api.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AnalysisQuestionInputTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void validatesTypesAndNeverAcceptsClientOwnership()throws Exception {
        for(String body:new String[]{"{\"question\":123}","{\"question\":true}","{\"question\":\"조회\",\"context\":[]}","{\"question\":\"조회\",\"context\":{\"developerId\":42}}","{\"question\":\"조회\",\"userId\":\"other\"}","{\"question\":\"조회\",\"context\":{\"userId\":\"other\"}}"})assertThatThrownBy(()->AnalysisQuestionInput.parse(json.readTree(body))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(400));
    }
    @Test void preservesIdentifiersLiterallyAndLeavesTrimmingToAcceptance()throws Exception {
        var q=AnalysisQuestionInput.parse(json.readTree("{\"question\":\"  선택월 매출  \",\"context\":{\"screenId\":\"UI-26\",\"baseMonth\":\"2026-10\",\"projectId\":\"  P-1  \"}}"));assertThat(q.question()).isEqualTo("  선택월 매출  ");assertThat(q.context().projectId()).isEqualTo("  P-1  ");
    }
}
