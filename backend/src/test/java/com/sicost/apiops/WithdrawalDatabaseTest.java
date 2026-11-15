package com.sicost.apiops;

import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.sicost.apiops.WithdrawalDtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class WithdrawalDatabaseTest {
    JdbcTemplate sql;
    WithdrawalMapper mapper;
    String developer,project,part,customer,opportunity,key;
    LocalDate today=LocalDate.of(2080,11,15);
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        mapper=new SqlSessionTemplate(factory.getObject()).getMapper(WithdrawalMapper.class);
        key=UUID.randomUUID().toString().replace("-","").substring(0,10);developer="D-"+key;project="P-"+key;part="T-"+key;customer="C-"+key;opportunity="O-"+key;
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,reg_user_id) VALUES (?,?,'admin')",part,part);
        sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES (?,'CUST',?,?,'admin')",customer,customer,key);
        sql.update("INSERT INTO tb_pj_proj(proj_id,proj_nm,cust_clnt_id,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES (?, ?,?,'MO','END','2080-01-01','2081-12-31',100,100,0,0,'admin')",project,project,customer);
        sql.update("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,use_yn,reg_user_id) VALUES (?,?,?,'FT','BE','DRCT',?,'IDLE','N','admin')",developer,"000"+key,developer,part);
    }
    String assignment(String suffix,LocalDate start,LocalDate end,String extension,String status){String id="A-"+key+suffix;sql.update("INSERT INTO tb_pj_asgn(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,input_type_cd,ext_psbl_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES (?,?,?,'BE',?,?,1,'NEW',?,0,?,'admin')",id,project,developer,start,end,extension,status);return id;}
    List<Map<String,Object>> all(String table){return sql.queryForList("SELECT * FROM "+table);}
    @Test void mapsAssignmentRowsAndPreservesStoppedPeopleAndYearBoundary(){
        String first=assignment("1",today.minusDays(1),today,null,"ACT"),second=assignment("2",today.minusDays(1),today,"H","ACT");
        assignment("3",today.minusDays(1),LocalDate.of(2081,1,31),null,"ACT");assignment("4",today.minusDays(1),today,null,"CNCL");
        var rows=mapper.sources(new Window(2080,today,LocalDate.of(2081,1,31)));
        assertThat(rows).extracting(Source::assignmentId).containsExactly(first,second,"A-"+key+"3");
        assertThat(rows.get(0).developerUse()).isEqualTo("N");assertThat(rows.get(0).managementNumber()).startsWith("000");assertThat(rows.get(0).extensionPossibility()).isNull();
    }
    @Test void nextPriorityExcludesCancelledAndReleasedAndDoneButRetainsAllFutureActualRows(){
        String original=assignment("1",today.minusMonths(1),today,null,"ACT");
        String real=assignment("2",today.plusMonths(2),today.plusMonths(3),null,"ACT");
        assignment("3",today.plusDays(2),today.plusMonths(3),null,"CNCL");
        sql.update("INSERT INTO tb_sl_opp(opp_id,opp_nm,cust_clnt_id,part_id,sales_step_cd,win_prbl_rt,expct_amt,bgn_plan_ym,opp_stat_cd,aprv_stat_cd,proj_id,reg_user_id) VALUES (?, ?,?,?,'LEAD',70,100,'208012','ING','NONE',?,'admin')",opportunity,opportunity,customer,part,project);
        sql.update("INSERT INTO tb_sl_opp_need(opp_id,need_sn,job_cd,grade_cd,need_cnt,bgn_ym,prd_mcnt,mon_mm,reg_user_id) VALUES (?,1,'BE','MD',2,'208012',6,1,'admin')",opportunity);
        for(String state:List.of("ACT","CNVT","DONE","DROP","REL"))sql.update("INSERT INTO tb_sl_tent_asgn(opp_id,need_sn,dev_id,bgn_ym,tent_stat_cd,src_scrn_id,reg_user_id) VALUES (?,1,?,'208012',?,'UI-05','admin')",opportunity,developer,state);
        var next=mapper.nextAssignments(new NextQuery(original,developer,today));
        assertThat(next).extracting(NextAssignment::kind).containsExactly("CONFIRMED","CANDIDATE","TENTATIVE");assertThat(next.get(0).assignmentId()).isEqualTo(real);
        assertThat(mapper.positions(developer)).singleElement().satisfies(p->assertThat(p.remainingCount()).isEqualTo(2));
    }
    @Test void repeatQueriesLeaveBusinessAuditAndRecommendationSequenceUnchanged(){
        String id=assignment("1",today.minusMonths(1),today,null,"ACT");
        List<String> tables=List.of("tb_pj_asgn","tb_pj_asgn_hist","tb_pj_asgn_mon","tb_sl_rcmd_log","tb_sl_tent_asgn","tb_hr_mon_pay","tb_co_mon_cost","tb_sy_chg_hist","tb_sy_accs_log");
        Map<String,List<Map<String,Object>>> before=new HashMap<>();tables.forEach(t->before.put(t,all(t)));
        var sequence=sql.queryForMap("SELECT last_value,is_called FROM sq_tb_sl_rcmd_log");
        for(int i=0;i<3;i++){mapper.sources(new Window(2080,today,today.plusMonths(2)));mapper.nextAssignments(new NextQuery(id,developer,today));mapper.positions(developer);mapper.leaves(developer);}
        tables.forEach(t->assertThat(all(t)).as(t).isEqualTo(before.get(t)));assertThat(sql.queryForMap("SELECT last_value,is_called FROM sq_tb_sl_rcmd_log")).isEqualTo(sequence);
    }
    @Test void jobSortUsesOnlyNamesAvailableInTheScreenCodeLookup(){
        String displayed=mapper.jobName("BE");
        assertThat(displayed).isNotBlank();
        sql.update("UPDATE tb_sy_code SET use_yn='N' WHERE code_grp_id='JOB' AND code='BE'");
        assertThat(mapper.jobName("BE")).isEqualTo(displayed);
        sql.update("UPDATE tb_sy_code SET use_yn='Y',del_yn='Y' WHERE code_grp_id='JOB' AND code='BE'");
        assertThat(mapper.jobName("BE")).isNull();
        assertThat(mapper.jobName("MISSING")).isNull();
    }

}
