package com.sicost.apiops;

import com.sicost.common.context.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static com.sicost.apiops.MasterHistoryDtos.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class MasterHistoryDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;
    MasterHistoryService service;
    @Configuration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();
        configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PersistenceBoundary());factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        context.registerBean(MasterHistoryMapper.class,()->session.getMapper(MasterHistoryMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,MasterHistoryRepository.class,MasterHistoryService.class);
        context.refresh();service=context.getBean(MasterHistoryService.class);
        sql.update("INSERT INTO tb_bs_part(part_id,part_nm,reg_user_id) VALUES ('PART-901','이력 검증 파트','admin')");
        sql.update("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES ('C-901','CUST','이력 검증 거래처','901-00-00001','admin')");
    }
    @AfterEach void close() {if(context!=null)context.close();}
    long history(String table,String key,String type,String item,String before,String after,String time) {
        return sql.queryForObject("INSERT INTO tb_sy_chg_hist(trgt_tbl_nm,trgt_key,chg_type_cd,chg_item_nm,bfr_val,aft_val,reg_user_id,reg_dttm) VALUES (?,?,?,?,?,?,'admin',?::timestamp) RETURNING hist_id",
            Long.class,table,key,type,item,before,after,time);
    }
    Page read(String type,String id,int page,int size) {return service.list(new Query(type,id,page,size,(long)(page-1)*size));}
    @Test void partCombinesOnlyItsExactTimelineKeysAndOrdersByRawTimeThenId() {
        long old=history("TB_BS_PART","PART-901","NEW","등록",null,"당시 파트명","2026-01-01 09:00:59");
        long first=history("TB_BS_PART_STD","PART-901:202701","UPD","PRFT_DSTRB_RT","40","50","2026-01-02 09:00:00");
        long last=history("TB_BS_PART_STD","PART-901:202812","UPD","PART_LDR_USER_ID","이전 이름/코드","당시 이름/코드","2026-01-02 09:00:00");
        for(String key:List.of("PART-9010:202701","PART-901:202700","PART-901:000001","PART-901:202701:extra","PART-901:202713"))
            history("TB_BS_PART_STD",key,"NEW","등록",null,"제외","2026-01-03 00:00:00");
        history("TB_BS_CLNT","PART-901","NEW","등록",null,"다른 테이블","2026-01-04 00:00:00");
        var before=sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id");
        Page result=read("PART","PART-901",1,20);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.items()).extracting(Entry::historyId).containsExactly(last,first,old);
        assertThat(result.items().get(0).afterValue()).isEqualTo("당시 이름/코드");
        assertThat(result.items().get(0).createdAt()).isEqualTo("2026-01-02 09:00");
        assertThat(sql.queryForList("SELECT * FROM tb_sy_chg_hist ORDER BY hist_id")).isEqualTo(before);
    }
    @Test void userAndClientCountsUseSameFilterAsPagedRows() {
        for(int i=0;i<21;i++) history("TB_BS_CLNT","C-901","UPD","CLNT_NM","이전","변경 당시 이름","2001-01-01 00:00:00");
        history("TB_SY_USER","admin","UPD","USER_NM","이전 이름","저장 당시 이름","2001-01-01 00:00:00");
        var first=read("CLIENT","C-901",1,20);var second=read("CLIENT","C-901",2,20);
        assertThat(first.total()).isEqualTo(21);assertThat(first.items()).hasSize(20);
        assertThat(second.total()).isEqualTo(21);assertThat(second.items()).hasSize(1);
        assertThat(first.items()).doesNotContainAnyElementsOf(second.items());
        assertThat(read("CLIENT","C-901",3,20).items()).isEmpty();
        var user=read("USER","admin",1,20);assertThat(user.total()).isEqualTo(1);
        assertThat(user.items().get(0).afterValue()).isEqualTo("저장 당시 이름");
    }
    @Test void existingEmptyTargetAndMissingTargetAreDifferent() {
        assertThat(read("PART","PART-901",1,50).items()).isEmpty();
        assertThatThrownBy(()->read("PART","PART-absent",1,50))
            .isInstanceOfSatisfying(com.sicost.common.api.ApiException.class,e->assertThat(e.code()).isEqualTo("NOT_FOUND"));
    }
    @Test void accountHistoryRemainsMaskedAtDatabaseBoundary() {
        history("TB_BS_CLNT","C-901","UPD","계좌","****1234","****5678","2026-01-01 00:00:00");
        var row=read("CLIENT","C-901",1,50).items().get(0);
        assertThat(row.beforeValue()).isEqualTo("****1234");assertThat(row.afterValue()).isEqualTo("****5678");
    }
}
