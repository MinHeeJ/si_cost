package com.sicost.b1;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import com.sicost.common.persistence.PersistenceBoundary;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.b1.B1Dtos.*;

class B1MapperTest {
    Configuration configuration;
    @BeforeEach void parseRealMapper() throws Exception {
        configuration=new Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);
        try(InputStream xml=getClass().getResourceAsStream("/mappers/b1/B1Mapper.xml")) {
            assertThat(xml).isNotNull();new XMLMapperBuilder(xml,configuration,"mappers/b1/B1Mapper.xml",configuration.getSqlFragments()).parse();
        }
    }
    String sql(String statement,Object input) { return configuration.getMappedStatement("com.sicost.b1.B1Mapper."+statement).getBoundSql(input).getSql(); }
    @Test void payrollWritesAreAcceptedByBoundedFoundationPolicyAndKeepRegOnUpdate() {
        var z=BigDecimal.ZERO;var value=new Values(z,z,z,z,z,z,z,z,z,"FT","DRCT");
        var write=new PayWrite("202606","D-owned",value,1,"admin",LocalDateTime.of(2026,6,15,9,0));
        for(String operation:List.of("insertPay","updatePay"))assertThatCode(()->PersistenceBoundary.requireAllowed(sql(operation,write))).doesNotThrowAnyException();
        assertThat(sql("updatePay",write)).doesNotContain("reg_user_id","reg_dttm");
        assertThatCode(()->PersistenceBoundary.requireAllowed(sql("deletePay",Map.of("month","202606","developerId","D-owned")))).doesNotThrowAnyException();
    }
    @Test void payrollMaterializationUsesNestedTypedValuesWithNullableNet() {
        var root=configuration.getResultMap("com.sicost.b1.B1Mapper.payrollRow");
        assertThat(root.getConstructorResultMappings()).hasSize(2);
        var nested=configuration.getResultMap("com.sicost.b1.B1Mapper.payrollValues");
        assertThat(nested.getConstructorResultMappings()).hasSize(11);
        assertThat(sql("payroll","202606")).contains("real_pay_amt AS \"realPay\"","ORDER BY pay_ym,dev_id").doesNotContain("COALESCE","fix_yn");
    }
    @Test void protectionUsesOnlyExactCloseAndLocksReferences() {
        assertThat(sql("closed","202606")).contains("close_stat_cd='CLOSE'");
        assertThat(sql("rates",Map.of())).contains("FOR SHARE OF c,g","c.use_yn='Y'","c.del_yn='N'");
        assertThat(sql("lockDevelopers",Map.of("ids",List.of("D-owned")))).contains("ORDER BY dev_id FOR SHARE");
    }
}
