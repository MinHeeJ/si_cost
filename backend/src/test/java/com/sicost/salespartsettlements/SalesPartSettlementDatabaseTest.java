package com.sicost.salespartsettlements;

import com.sicost.common.installation.PostgreSqlDatabase;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class SalesPartSettlementDatabaseTest {
    PostgreSqlDatabase db;
    SalesPartSettlementMapper mapper;
    String part,developer,actor;
    @BeforeEach void install() throws Exception {
        db=new PostgreSqlDatabase();db.install();
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(SalesPartSettlementMapper.class);configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        factory.setConfiguration(configuration);factory.setMapperLocations(new ClassPathResource("mappers/salespartsettlements/SalesPartSettlementMapper.xml"));
        mapper=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject())).getMapper(SalesPartSettlementMapper.class);
        part="P"+UUID.randomUUID().toString().replace("-","").substring(0,12);developer="D"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        actor=db.sql.queryForObject("SELECT user_id FROM tb_sy_user ORDER BY user_id LIMIT 1",String.class);
        db.sql.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES (?,?,'N','Y',?)",part,part,actor);
        db.sql.update("INSERT INTO tb_bs_part_std(part_id,aply_bgn_ym,part_ldr_user_id,prft_dstrb_rt,sales_exps_amt,reg_user_id) VALUES (?,'202601',?,40,10,?)",part,actor,actor);
        db.sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FT','DEV','DRCT',?,'WORK',?)",developer,developer,developer,part,actor);
    }
    @AfterEach void removeOwnedSchema() {
        if(db==null)return;
        String schema=db.sql.queryForObject("SELECT current_schema()",String.class);
        if(schema==null||!schema.matches("sicost_test_[a-f0-9]{32}"))throw new IllegalStateException("시험 스키마만 정리합니다.");
        db.sql.execute("DROP SCHEMA "+schema+" CASCADE");
    }
    Map<String,List<String>> stored() {
        Map<String,List<String>> result=new LinkedHashMap<>();
        for(String table:List.of("tb_bs_part","tb_bs_part_std","tb_hr_dev","tb_hr_dev_prc","tb_hr_mon_pay","tb_pj_asgn","tb_pj_asgn_mon","tb_co_mon_cost","tb_co_part_setl","tb_co_part_setl_dtl","tb_sy_chg_hist","tb_cl_close"))
            result.put(table,db.sql.queryForList("SELECT row_to_json(t)::text FROM "+table+" t ORDER BY row_to_json(t)::text",String.class));
        return result;
    }
    @Test void allReadProjectionsUseInstalledIdentifiersWithoutCreatingMissingSettlement() {
        var before=stored();
        assertThat(mapper.parts()).anyMatch(p->p.partId().equals(part));
        assertThat(mapper.standards()).anyMatch(s->s.partId().equals(part)&&s.rate().intValueExact()==40);
        mapper.useHistory();mapper.firstInputs();
        assertThat(mapper.parents("202607")).noneMatch(p->p.partId().equals(part));
        assertThat(mapper.details("202607")).noneMatch(d->d.partId().equals(part));
        assertThat(mapper.revisionSources("202607")).anyMatch(r->r.source().equals("TB_HR_DEV")&&r.storedRows().contains(developer));
        assertThat(stored()).isEqualTo(before);
    }
    @Test void physicalParentDetailAmountsMetadataAndSnapshotTextAreReadLosslessly() {
        db.sql.update("INSERT INTO tb_co_part_setl(part_id,setl_ym,corp_card_amt,part_prft_amt,setl_stat_cd,cnfm_user_id,cnfm_dttm,cncl_rsn,reg_user_id) VALUES (?,'202607',5,31,'AUTO',?,CURRENT_TIMESTAMP,?,?)",part,actor,"이전 취소 사유",actor);
        db.sql.update("INSERT INTO tb_co_part_setl_dtl(part_id,setl_ym,dev_id,emp_type_cd,proj_nm_list,mm,uprc_amt,pay_amt,sales_exps_amt,retire_amt,cost_amt,diff_amt,prft_amt,rmrk,reg_user_id) VALUES (?,'202607',?,'FT',?,0.75,100,10,0,0,10,90,36,?,?)",part,developer,"[\"프로젝트, 쉼표\"]","급여 예정",actor);
        var before=stored();var parent=mapper.parents("202607").stream().filter(p->p.partId().equals(part)).findFirst().orElseThrow();
        assertThat(parent.cardAmount()).isEqualByComparingTo("5");assertThat(parent.partProfitAmount()).isEqualByComparingTo("31");assertThat(parent.confirmedBy()).isEqualTo(actor);assertThat(parent.cancellationReason()).isEqualTo("이전 취소 사유");
        var detail=mapper.details("202607").stream().filter(d->d.partId().equals(part)).findFirst().orElseThrow();
        assertThat(detail.mm()).isEqualByComparingTo("0.75");assertThat(detail.projectText()).isEqualTo("[\"프로젝트, 쉼표\"]");assertThat(detail.employeeType()).isEqualTo("FT");assertThat(detail.share()).isEqualByComparingTo("36");assertThat(detail.regDttm()).isNotNull();assertThat(detail.modDttm()).isNull();
        assertThat(stored()).isEqualTo(before);
    }
}
