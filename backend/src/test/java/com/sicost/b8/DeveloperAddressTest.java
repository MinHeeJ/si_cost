package com.sicost.b8;

import com.sicost.b7.ExcelImportService;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sicost.b8.B8Dtos.*;

class DeveloperAddressTest {
    B8Mapper mapper=mock(B8Mapper.class);B8ImportDefinition definition=new B8ImportDefinition(mapper);
    @BeforeEach void setup(){when(mapper.codes()).thenReturn(DeveloperWorkbook.codes());when(mapper.parts()).thenReturn(List.of(new Part("HQ","본사",true)));when(mapper.managementPreview()).thenReturn(BigInteger.ONE);
        when(mapper.areas()).thenReturn(List.of(new Area("1100000000","SIDO",null,"서울","서울특별시",null,null),
            new Area("1111000000","SGG","1100000000","서울","서울특별시","광진구",null),
            new Area("1111000100","EMD","1111000000","서울","서울특별시","광진구","자양동"),
            new Area("1111000200","EMD","1111000000","서울","서울특별시","광진구","구의동"),
            new Area("4100000000","SIDO",null,"경기","경기도",null,null),new Area("4111000000","SGG","4100000000","경기","경기도","성남시 분당구",null),
            new Area("3600000000","SIDO",null,"세종","세종특별자치시",null,null),new Area("3611000100","EMD","3600000000","세종","세종특별자치시",null,"조치원읍"),
            new Area("4600000000","SIDO",null,"전남광주","전라남도",null,null)));
        when(mapper.hdongs()).thenReturn(List.of(new Hdong("자양제3동","1111000100"),new Hdong("여러동","1111000100"),new Hdong("여러동","1111000200"),new Hdong("구의동","1111000100")));
    }
    private Checked address(String sido,String sgg,String emd) throws Exception {
        var f=DeveloperWorkbook.file(b->{DeveloperWorkbook.validRow(b,1);var row=b.getSheetAt(0).getRow(1);row.createCell(14).setCellValue(sido);row.createCell(15).setCellValue(sgg);row.createCell(16).setCellValue(emd);});
        return definition.validate(new ExcelImportService().parse(f,B8ImportDefinition.FORMAT));
    }
    @ParameterizedTest @ValueSource(strings={"서울특별시","서울시","서울"})
    void seoulAliasesResolve(String sido) throws Exception {assertThat(address(sido,"광진구","자양동").developers().get(0).address().code()).isEqualTo("1111000100");}
    @ParameterizedTest @ValueSource(strings={"경기도","경기","경기시"})
    void districtContainingSpaceIsOneExactName(String sido) throws Exception {assertThat(address(sido,"성남시 분당구","").developers().get(0).address().code()).isEqualTo("4111000000");}
    @ParameterizedTest @ValueSource(strings={"광주광역시","광주시","광주","전라남도","전남"})
    void gwangjuAndJeonnamUseProvidedCombinedSido(String sido) throws Exception {assertThat(address(sido,"","").developers().get(0).address().code()).isEqualTo("4600000000");}
    @Test void emptyAddressHasNoWarningAndUnknownSidoClearsEverything() throws Exception {
        assertThat(address("","","").issues()).isEmpty();var bad=address("없는곳","광진구","자양동");assertThat(bad.developers().get(0).address().code()).isNull();assertThat(bad.issues()).singleElement().satisfies(i->assertThat(i.reason()).isEqualTo("거주지를 못 찾아 비웠습니다"));assertThat(bad.summary().errorRows()).isZero();
    }
    @Test void wrongParentDistrictOrSplitSpaceKeepsSidoOnly() throws Exception {
        assertThat(address("경기","광진구","자양동").developers().get(0).address().code()).isEqualTo("4100000000");assertThat(address("경기","성남시","분당구").issues()).anyMatch(i->i.reason().equals("시·군·구를 못 찾아 비웠습니다"));
    }
    @Test void legalDongWinsOverAdministrativeNameMapping() throws Exception {
        var c=address("서울","광진구","구의동");assertThat(c.developers().get(0).address().code()).isEqualTo("1111000200");assertThat(c.issues()).isEmpty();
    }
    @Test void singleAdministrativeMappingRemovesJeBeforeNumberAndWarns() throws Exception {
        var c=address("서울","광진구","자양3동");assertThat(c.developers().get(0).address().text()).isEqualTo("서울 광진구 자양동");assertThat(c.summary().warningRows()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"여러동","도로명 123","자양제동"})
    void ambiguousOrUnknownDongKeepsOnlyDistrict(String emd) throws Exception {
        var c=address("서울","광진구",emd);assertThat(c.developers().get(0).address().code()).isEqualTo("1111000000");assertThat(c.issues()).anyMatch(i->i.reason().equals("동을 못 찾아 비웠습니다"));assertThat(c.summary().errorRows()).isZero();
    }
    @Test void sidoWithoutDistrictResolvesDongDirectlyAndFailureKeepsSido() throws Exception {
        assertThat(address("세종","","조치원읍").developers().get(0).address().code()).isEqualTo("3611000100");assertThat(address("세종","","없는동").developers().get(0).address().code()).isEqualTo("3600000000");
    }
}
