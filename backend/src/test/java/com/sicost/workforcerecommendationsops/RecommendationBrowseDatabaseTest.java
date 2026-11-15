package com.sicost.workforcerecommendationsops;

import com.sicost.common.installation.PostgreSqlDatabase;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.sicost.workforcerecommendationsops.RecommendationBrowseDtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class RecommendationBrowseDatabaseTest {
    JdbcTemplate sql;RecommendationBrowseMapper mapper;
    String opportunity,project,developer,customer,part;
    @BeforeEach void setup() throws Exception {
        var db=new PostgreSqlDatabase();db.install();sql=db.sql;
        var factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var c=new org.apache.ibatis.session.Configuration();c.setArgNameBasedConstructorAutoMapping(true);c.setMapUnderscoreToCamelCase(true);factory.setConfiguration(c);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        mapper=new SqlSessionTemplate(factory.getObject()).getMapper(RecommendationBrowseMapper.class);
        String key=UUID.randomUUID().toString().replace("-","").substring(0,12);
        opportunity="O-"+key;project="P-"+key;developer="D-"+key;customer="C-"+key;part="T-"+key;
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,reg_user_id) VALUES (?,?,'admin')",part,part);
        sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES (?,'CUST',?,right('0000000000'||abs(hashtext(?))::text,10),'admin')",customer,customer,key);
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES (?,?,?,'FT','BE','DRCT',?,'IDLE','admin')",developer,developer,developer,part);
        sql.update("INSERT INTO tb_sl_opp(opp_id,opp_nm,cust_clnt_id,part_id,sales_step_cd,win_prbl_rt,expct_amt,bgn_plan_ym,opp_stat_cd,aprv_stat_cd,reg_user_id) VALUES (?,?,?,?,'PRE',50,100,'208007','ING','NONE','admin')",opportunity,opportunity,customer,part);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?,?,'MO','ING','2080-07-01','2080-12-31',100,0,0,'admin')",project,project);
        sql.update("INSERT INTO tb_sl_opp_need(opp_id,need_sn,job_cd,need_cnt,bgn_ym,prd_mcnt,mon_mm,reg_user_id) VALUES (?,7,'BE',2,'208007',2,0.50,'admin')",opportunity);
    }
    String snapshot(){return sql.queryForObject("SELECT jsonb_build_object('logs',(SELECT jsonb_agg(to_jsonb(r) ORDER BY rcmd_id,rank_ord) FROM tb_sl_rcmd_log r),'audit',(SELECT jsonb_agg(to_jsonb(h) ORDER BY hist_id) FROM tb_sy_chg_hist h),'opportunities',(SELECT jsonb_agg(to_jsonb(o) ORDER BY opp_id) FROM tb_sl_opp o),'payroll',(SELECT jsonb_agg(to_jsonb(p) ORDER BY pay_ym,dev_id) FROM tb_hr_mon_pay p),'standards',(SELECT jsonb_agg(to_jsonb(s) ORDER BY std_cd,aply_bgn_ym) FROM tb_sy_oper_std s),'sequence',(SELECT last_value FROM sq_tb_sl_rcmd_log))::text",String.class);}
    @Test void physicalMapperCountsOnlyActAndTargetsExcludeEveryClosedStateAndInternalProject(){
        sql.update("INSERT INTO tb_sl_tent_asgn(opp_id,need_sn,dev_id,bgn_ym,tent_stat_cd,src_scrn_id,reg_user_id) VALUES (?,7,?,'208007','CAND','UI-30','admin')",opportunity,developer);
        assertThat(mapper.needs(opportunity)).singleElement().satisfies(n->{assertThat(n.needSn()).isEqualTo("7");assertThat(n.activeCount()).isZero();assertThat(n.monthlyMm()).isEqualTo("0.50");});
        sql.update("UPDATE tb_sl_tent_asgn SET tent_stat_cd='ACT' WHERE opp_id=?",opportunity);
        assertThat(mapper.needs(opportunity).get(0).activeCount()).isEqualTo(1);
        for(String state:List.of("WIN","LOSE","STOP")){
            sql.update("UPDATE tb_sl_opp SET opp_stat_cd=? WHERE opp_id=?",state,opportunity);
            assertThat(mapper.targets(LocalDate.of(2080,7,1))).noneMatch(t->t.targetId().equals(opportunity));
        }
        for(String state:List.of("PRE","LOSE","END")){
            sql.update("UPDATE tb_pj_proj SET proj_stat_cd=? WHERE proj_id=?",state,project);
            assertThat(mapper.targets(LocalDate.of(2080,7,1))).noneMatch(t->t.targetId().equals(project));
        }
        sql.update("UPDATE tb_pj_proj SET proj_stat_cd='ING',ctrt_type_cd='IN' WHERE proj_id=?",project);
        assertThat(mapper.targets(LocalDate.of(2080,7,1))).noneMatch(t->t.targetId().equals(project));
    }
    @Test void historyUsesSavedConditionsDistinctRoundsNumericOrderTotalAndNeverWrites(){
        for(int id=1;id<=25;id++)for(int rank=1;rank<=2;rank++)
            sql.update("INSERT INTO tb_sl_rcmd_log(rcmd_id,rank_ord,rcmd_type_cd,opp_id,job_cd,bgn_ym,req_grade_cd,dev_id,tot_score,score_dtl,reg_user_id,reg_dttm) VALUES (?,?,'AI',?,'OLD','208007','SR',?,80,'{}','admin','2080-07-01 10:00:00')",id,rank,opportunity,developer);
        var q=new HistoryQuery("OPPORTUNITY",opportunity,"OLD","AI",1,20);
        String before=snapshot();assertThat(mapper.historyCount(q)).isEqualTo(25);
        var rows=mapper.history(q,0);assertThat(rows).hasSize(20);assertThat(rows.get(0).rcmdId()).isEqualTo("25");assertThat(rows.get(19).rcmdId()).isEqualTo("6");assertThat(rows.get(0).candidateCount()).isEqualTo(2);
        sql.update("UPDATE tb_sl_opp SET opp_stat_cd='STOP',opp_nm='변경된 이름' WHERE opp_id=?",opportunity);
        assertThat(mapper.history(q,0)).isEqualTo(rows);
        String afterSourceChange=snapshot();
        for(int i=0;i<3;i++){mapper.history(q,20);mapper.historyCount(q);mapper.targets(LocalDate.of(2080,7,1));mapper.opportunitySnapshot(opportunity);mapper.projectSnapshot(project);}
        assertThat(snapshot()).isEqualTo(afterSourceChange);assertThat(before).isNotEqualTo(afterSourceChange);
        assertThat(mapper.history(new HistoryQuery(null,null,"NOPE",null,1,50),0)).isEmpty();
    }
}
