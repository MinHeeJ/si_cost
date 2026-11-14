package com.sicost.imports;

import com.sicost.apiops.FinancialReadInput;
import com.sicost.common.api.ApiException;
import com.sicost.common.persistence.PersistenceBoundary;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.LinkedMultiValueMap;
import static org.assertj.core.api.Assertions.*;

class BusinessPlanImportPersistenceTest {
    @Test void productionMapperHasValidRecordBindingsAndOnlyNarrowTargetWrites() throws Exception {
        var configuration=new org.apache.ibatis.session.Configuration();
        try(var input=new ClassPathResource("mappers/imports/BusinessPlanImportMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input,configuration,"mappers/imports/BusinessPlanImportMapper.xml",configuration.getSqlFragments()).parse();
        }
        var write=new BusinessPlanImportDtos.TargetWrite("208001",java.math.BigDecimal.ZERO,java.math.BigDecimal.ONE,19,"admin",java.time.LocalDateTime.of(2080,1,1,0,0));
        for(String id:List.of("insertTarget","updateTarget")) {
            var statement=configuration.getMappedStatement("com.sicost.imports.BusinessPlanImportMapper."+id);
            var bound=statement.getBoundSql(write);PersistenceBoundary.requireAllowed(bound.getSql());
            for(var binding:bound.getParameterMappings()) assertThat(configuration.newMetaObject(write).getValue(binding.getProperty())).isNotNull();
        }
    }
    @Test void targetDeletesOtherPartsUnboundedUpdatesAndRegistrationMutationAreRejected() {
        for(String sql:List.of("DELETE FROM tb_fi_biz_plan WHERE plan_ym=? AND part_id='ALL'",
            "UPDATE tb_fi_biz_plan SET sales_tgt_amt=? WHERE plan_ym=?",
            "UPDATE tb_fi_biz_plan SET sales_tgt_amt=? WHERE plan_ym=? AND part_id=?",
            "UPDATE tb_fi_biz_plan SET reg_dttm=? WHERE plan_ym=? AND part_id='ALL'",
            "INSERT INTO tb_fi_biz_plan(plan_ym,part_id) VALUES(?,?)"))
            assertThatThrownBy(() -> PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
    }
    @Test void approvedHistoryScopesAcceptExactUploadIdAndOldScopesRejectIt() {
        for(String scope:List.of("BUSINESS_PLAN","HISTORICAL_PROFIT_LOSS")) {
            var values=new LinkedMultiValueMap<String,String>();values.add("month","2080-07");values.add("scope",scope);values.add("uploadId","19");
            assertThat(FinancialReadInput.history(values).uploadId()).isEqualTo("19");
        }
        for(String scope:List.of("MONTH","TAX","CARDS")) {
            var values=new LinkedMultiValueMap<String,String>();values.add("month","2080-07");values.add("scope",scope);values.add("uploadId","19");
            assertThatThrownBy(() -> FinancialReadInput.history(values)).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.field()).isEqualTo("uploadId"));
        }
    }
    @Test void historyListAndCountUseSameDynamicUploadFilter() throws Exception {
        var configuration=new org.apache.ibatis.session.Configuration();
        try(var input=new ClassPathResource("mappers/apiops/FinancialReadMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input,configuration,"mappers/apiops/FinancialReadMapper.xml",configuration.getSqlFragments()).parse();
        }
        var values=new LinkedMultiValueMap<String,String>();values.add("month","2080-07");values.add("scope","BUSINESS_PLAN");values.add("uploadId","19");
        var query=FinancialReadInput.history(values);
        var list=configuration.getMappedStatement("com.sicost.apiops.FinancialReadMapper.history").getBoundSql(query);
        var count=configuration.getMappedStatement("com.sicost.apiops.FinancialReadMapper.historyCount").getBoundSql(query);
        assertThat(list.getSql()).contains("split_part(h.chg_rsn,':',2)=?");
        assertThat(count.getSql()).contains("split_part(h.chg_rsn,':',2)=?");
        assertThat(list.getParameterMappings().stream().map(p -> p.getProperty()).toList()).contains("uploadId");
    }
}
