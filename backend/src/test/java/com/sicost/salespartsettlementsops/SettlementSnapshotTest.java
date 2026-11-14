package com.sicost.salespartsettlementsops;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.salespartsettlements.SalesPartSettlementSnapshotReader;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.sicost.salespartsettlements.SalesPartSettlementDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementSnapshotTest {
    final JsonMapper json=JsonMapper.builder().findAndAddModules().build();
    final SettlementSnapshotCodec codec=new SettlementSnapshotCodec(json);
    @Test void jsonPreservesCommaNamesAndSortsWithoutAddingMetadata() throws Exception {
        var value=codec.projects("developer",List.of("나, 프로젝트","가 프로젝트"));
        assertThat(json.readTree(value).get(1).textValue()).isEqualTo("나, 프로젝트");assertThat(json.readTree(value).size()).isEqualTo(2);
    }
    @Test void snapshotBoundaryCountsUnicodeCodepointsNotUtf16Units() {
        String value=codec.projects("developer",List.of("😀".repeat(996)));
        assertThat(value.codePointCount(0,value.length())).isEqualTo(1000);
        assertThatThrownBy(()->codec.projects("developer",List.of("😀".repeat(997)))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("SNAPSHOT_TEXT_TOO_LONG"));
    }
    @Test void auditWidthIsSeparate409AndNeverTruncatesSnapshot() {
        var mapper=mock(ChangeHistoryMapper.class);
        var writer=new ChangeHistoryWriter(mapper,mock(CurrentUserProvider.class),Clock.systemUTC(),json);
        String value=codec.projects("developer",List.of("가".repeat(996)));
        assertThat(value.codePointCount(0,value.length())).isEqualTo(1000);
        assertThatThrownBy(()->writer.preflightSettlement(List.of(new ChangeHistoryWriter.Change("TB_CO_PART_SETL_DTL","part:202607:developer","NEW","PROJ_NM_LIST",null,value,"수동확정"))))
            .isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.status()).isEqualTo(409);assertThat(e.code()).isEqualTo("STATE_CONFLICT");assertThat(e.getMessage()).contains("500");});
        verifyNoInteractions(mapper);
    }
    @Test void legacyStringNonArrayAndDamagedJsonAreReadAsRawText() {
        var reader=new SalesPartSettlementSnapshotReader(json);
        for(String text:List.of("기존, 프로젝트","{\"name\":\"원문\"}","[손상","[1]")) {
            var d=new Detail("part","202607","developer","현재이름","현재번호","FT",text,BigDecimal.ONE,BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.TEN,BigDecimal.ONE,"급여 예정",null,null,null,null);
            var row=reader.read(List.of(d),"2026-07").get(0);
            assertThat(row.legacyProjectText()).isEqualTo(text);assertThat(row.projectNames()).isEmpty();assertThat(row.basis().distributionRate()).isNull();assertThat(row.basis().paySource()).isEqualTo("SNAPSHOT_UNKNOWN");
        }
    }
}
