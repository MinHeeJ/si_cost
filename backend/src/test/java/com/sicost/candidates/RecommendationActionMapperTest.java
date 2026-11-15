package com.sicost.candidates;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.persistence.PersistenceBoundary;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.time.*;
import static com.sicost.candidates.RecommendationActionDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecommendationActionMapperTest {
    Configuration configuration()throws Exception{
        var config=new Configuration();config.setArgNameBasedConstructorAutoMapping(true);
        for(String resource:new String[]{"mappers/salescommands/SalesCommandMapper.xml","mappers/candidates/RecommendationActionMapper.xml"})try(var in=new ClassPathResource(resource).getInputStream()){new XMLMapperBuilder(in,config,resource,config.getSqlFragments()).parse();}return config;
    }
    @Test void emittedSelectionSqlIsAllowedAndEveryNamedValueExists()throws Exception{
        var choice=new Choice(new Key("101",3),"EXCL","SCHD","admin",LocalDateTime.of(2026,7,1,9,0));var bound=configuration().getMappedStatement("com.sicost.candidates.RecommendationActionMapper.choose").getBoundSql(choice);
        var properties=org.apache.ibatis.reflection.SystemMetaObject.forObject(choice);bound.getParameterMappings().forEach(p->assertThat(properties.getValue(p.getProperty())).isNotNull());
        assertThatCode(()->PersistenceBoundary.requireAllowed(bound.getSql())).doesNotThrowAnyException();assertThat(bound.getSql()).contains("user_act_cd IS NULL","rank_ord=?").doesNotContain("score_dtl=","reg_dttm=");
    }
    @Test void immutableAndUnboundedLogWritesRemainForbidden(){
        for(String sql:new String[]{"DELETE FROM tb_sl_rcmd_log WHERE rcmd_id=? AND rank_ord=?","UPDATE tb_sl_rcmd_log SET score_dtl=? WHERE rcmd_id=? AND rank_ord=?","UPDATE tb_sl_rcmd_log SET reg_user_id=? WHERE rcmd_id=?","UPDATE tb_sl_rcmd_log SET user_act_cd=?,rcmd_excl_cd=?,act_user_id=?,act_dttm=? WHERE rcmd_id=CAST(? AS numeric) AND user_act_cd IS NULL","UPDATE tb_sl_rcmd_log SET user_act_cd=?,rcmd_excl_cd=?,act_user_id=?,act_dttm=? WHERE rcmd_id=CAST(? AS numeric) AND rank_ord=? AND user_act_cd IS NULL OR 1=1"})assertThatThrownBy(()->PersistenceBoundary.requireAllowed(sql)).isInstanceOf(IllegalStateException.class);
    }
    @Test void insertOnlyAdmitsExactFinalSnapshotFields(){
        String sql="INSERT INTO tb_sl_rcmd_log(rcmd_id,rank_ord,rcmd_type_cd,opp_id,need_sn,proj_id,job_cd,bgn_ym,req_grade_cd,dev_id,tot_score,score_dtl,rcmd_rsn,reg_user_id,reg_dttm) VALUES(CAST(? AS numeric),?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        assertThatCode(()->PersistenceBoundary.requireAllowed(sql)).doesNotThrowAnyException();assertThatThrownBy(()->PersistenceBoundary.requireAllowed(sql.replace("tot_score","user_act_cd"))).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->PersistenceBoundary.requireAllowed(sql+" ON CONFLICT DO NOTHING")).isInstanceOf(IllegalStateException.class);
    }
    @Test void snapshotCoversOffPagePayrollPriceSkillTentativeAndCloseRows()throws Exception{
        var sql=configuration().getMappedStatement("com.sicost.candidates.RecommendationActionMapper.snapshot").getBoundSql("O-01").getSql();assertThat(sql).contains("tb_hr_mon_pay","tb_hr_dev_prc","tb_hr_dev_skill","tb_pj_asgn_hist","tb_pj_asgn_mon","tb_cl_close","tb_sl_tent_asgn","tb_sl_opp_need").doesNotContain("LIMIT","OFFSET"," IS NULL OR ");
    }
    @Test void requiredAuditWritesFourFieldsIncludingNullToNullAndRejectsZeroRows(){
        var mapper=mock(ChangeHistoryMapper.class);var users=mock(CurrentUserProvider.class);when(users.userId()).thenReturn("admin");when(mapper.insert(any())).thenReturn(1);
        var writer=new ChangeHistoryWriter(mapper,users,Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"),ZoneId.of("Asia/Seoul")),JsonMapper.builder().findAndAddModules().build());
        for(String field:new String[]{"USER_ACT_CD","RCMD_EXCL_CD","ACT_USER_ID","ACT_DTTM"})writer.append(new ChangeHistoryWriter.Change("TB_SL_RCMD_LOG","101:3","UPD",field,null,null,"선택"));verify(mapper,times(4)).insert(any());
        when(mapper.insert(any())).thenReturn(0);assertThatThrownBy(()->writer.append(new ChangeHistoryWriter.Change("TB_SL_RCMD_LOG","101:3","UPD","USER_ACT_CD",null,"TENT",null))).isInstanceOf(IllegalStateException.class);
    }
    @Test void auditRejectsImmutableFieldsInvalidCompositeKeysAndOversizedPayload(){
        var mapper=mock(ChangeHistoryMapper.class);var writer=new ChangeHistoryWriter(mapper,mock(CurrentUserProvider.class),Clock.systemUTC(),JsonMapper.builder().findAndAddModules().build());
        for(var change:new ChangeHistoryWriter.Change[]{new ChangeHistoryWriter.Change("TB_SL_RCMD_LOG","101:3","UPD","SCORE_DTL",null,"{}",null),new ChangeHistoryWriter.Change("TB_SL_RCMD_LOG","101:0","UPD","USER_ACT_CD",null,"TENT",null),new ChangeHistoryWriter.Change("TB_SL_RCMD_LOG","101:3","UPD","expectedRevision",null,"x".repeat(501),null)})assertThatThrownBy(()->writer.append(change)).isInstanceOf(ApiException.class);verifyNoInteractions(mapper);
    }
}
