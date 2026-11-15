package com.sicost.workforcerecommendations;

import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.common.history.*;
import com.sicost.common.context.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static com.sicost.workforcerecommendations.WorkforceRecommendationDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkforceRecommendationPersistenceTest {
    @Test void actualBoundSqlAllowsOnlyExplicitFinalInsertAndOneTimeChoice() throws Exception {
        Configuration configuration=new Configuration();String resource="mappers/workforcerecommendations/WorkforceRecommendationMapper.xml";
        try(var stream=getClass().getClassLoader().getResourceAsStream(resource)){new XMLMapperBuilder(stream,configuration,resource,configuration.getSqlFragments()).parse();}
        Row row=new Row("1",1,"AI","O-1","1",null,"BE","202607","MD","D-1","50","{}","근거",null,null,null,null,"admin",LocalDateTime.of(2026,7,1,0,0));
        String sql=configuration.getMappedStatement(WorkforceRecommendationMapper.class.getName()+".insert").getBoundSql(row).getSql();
        assertThatCode(()->PersistenceBoundary.requireAllowed(sql)).doesNotThrowAnyException();
        assertThatCode(()->PersistenceBoundary.requireAllowed("UPDATE tb_sl_rcmd_log SET user_act_cd=?,rcmd_excl_cd=?,act_user_id=?,act_dttm=? WHERE rcmd_id=CAST(? AS numeric) AND rank_ord=? AND user_act_cd IS NULL")).doesNotThrowAnyException();
        for(String forbidden:List.of("DELETE FROM tb_sl_rcmd_log WHERE rcmd_id=?","UPDATE tb_sl_rcmd_log SET score_dtl=? WHERE rcmd_id=? AND rank_ord=?","UPDATE tb_sl_rcmd_log SET reg_user_id=? WHERE rcmd_id=? AND rank_ord=?","UPDATE tb_sl_rcmd_log SET user_act_cd=?,rcmd_excl_cd=?,act_user_id=?,act_dttm=? WHERE rcmd_id=CAST(? AS numeric) AND rank_ord=?"))assertThatThrownBy(()->PersistenceBoundary.requireAllowed(forbidden)).isInstanceOf(IllegalStateException.class);
    }
    @Test void generationAndSelectionShareTheTargetJobLock() {
        var locks=mock(com.sicost.common.concurrent.SettlementSourceLocks.class);
        var repository=new WorkforceRecommendationRepository(mock(WorkforceRecommendationMapper.class),new ObjectMapper(),mock(ChangeHistoryWriter.class),mock(com.sicost.common.concurrent.RevisionService.class),locks,mock(com.sicost.common.concurrent.CommitInvalidation.class));
        repository.lock(new Condition("OPPORTUNITY","O-1","BE","2026-07","MD","1"));
        verify(locks).lock(new TreeSet<>(List.of("OPP:O-1","RCMD_TARGET:OPP:O-1:BE")));
    }
    @Test void requiredAuditRejectsZeroAndImmutableFields() {
        ChangeHistoryMapper mapper=mock(ChangeHistoryMapper.class);CurrentUserProvider users=mock(CurrentUserProvider.class);when(users.userId()).thenReturn("admin");
        var writer=new ChangeHistoryWriter(mapper,users,Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"),ZoneId.of("Asia/Seoul")),new ObjectMapper());
        assertThatThrownBy(()->writer.appendRecommendation(new ChangeHistoryWriter.Change("TB_SL_RCMD_LOG","1:1","NEW","targetRevision",null,"revision",null))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->writer.appendRecommendation(new ChangeHistoryWriter.Change("TB_SL_RCMD_LOG","1:1","UPD","SCORE_DTL","old","new",null))).isInstanceOf(com.sicost.common.api.ApiException.class);
        assertThatThrownBy(()->writer.appendRecommendation(new ChangeHistoryWriter.Change("TB_SL_RCMD_LOG","1","NEW","targetRevision",null,"revision",null))).isInstanceOf(com.sicost.common.api.ApiException.class);
    }
    @Test void oversizedSnapshotNeverTruncates(){var json=new ObjectMapper();var repository=new WorkforceRecommendationRepository(mock(WorkforceRecommendationMapper.class),json,mock(ChangeHistoryWriter.class),new com.sicost.common.concurrent.RevisionService(json),mock(com.sicost.common.concurrent.SettlementSourceLocks.class),mock(com.sicost.common.concurrent.CommitInvalidation.class));var n=new java.math.BigDecimal("100");var detail=new AiScoreDetail(1,Collections.nCopies(8,n),Collections.nCopies(8,n),Collections.nCopies(8,n),new Evidence("P",List.of(1,1,1),"S",0,List.of(1,1,0),"IDLE","S"),"202607","9".repeat(501),null,0);assertThatThrownBy(()->repository.encode(detail,"근거")).isInstanceOf(com.sicost.common.api.ApiException.class).satisfies(e->assertThat(((com.sicost.common.api.ApiException)e).code()).isEqualTo("SCORE_DETAIL_TOO_LONG"));}
}
