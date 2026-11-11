package com.sicost.areasops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.*;
import com.sicost.common.context.*;
import com.sicost.common.installation.PostgreSqlDatabase;
import com.sicost.common.persistence.PersistenceBoundary;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL",matches=".+")
class AddressDatabaseTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate sql;AddressService service;MockMvc mvc;ObjectMapper json;
    @Configuration @EnableTransactionManagement static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void install() throws Exception {
        PostgreSqlDatabase db=new PostgreSqlDatabase();db.install();sql=db.sql;
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean();factory.setDataSource(db.source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.setArgNameBasedConstructorAutoMapping(true);configuration.setMapUnderscoreToCamelCase(true);configuration.addInterceptor(new PersistenceBoundary());
        factory.setConfiguration(configuration);factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session=new SqlSessionTemplate(factory.getObject());
        context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->db.source);
        json=JsonMapper.builder().findAndAddModules().build();
        context.registerBean(AddressMapper.class,()->session.getMapper(AddressMapper.class));
        context.registerBean(CurrentUserMapper.class,()->session.getMapper(CurrentUserMapper.class));
        context.register(Transactions.class,CurrentUserProvider.class,AddressRepository.class,AddressService.class);
        context.refresh();service=context.getBean(AddressService.class);
        mvc=MockMvcBuilders.standaloneSetup(new AddressController(service)).setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
    }
    @AfterEach void close(){if(context!=null)context.close();}
    void area(String code,String level,String parent,String name,String use) {
        sql.update("INSERT INTO tb_sy_adm_area(area_cd,area_lvl_cd,upper_area_cd,sido_nm,sido_full_nm,sgg_nm,emd_nm,use_yn,reg_user_id) VALUES(?,?,?,'시험','시험 전체명',?,?,?,'admin')",code,level,parent,"SGG".equals(level)?name:null,"EMD".equals(level)?name:null,use);
    }
    @Test void sidosMatchDatabaseAndPreserveOriginalIntegratedName() throws Exception {
        var expected=sql.queryForList("SELECT area_cd FROM tb_sy_adm_area WHERE area_lvl_cd='SIDO' AND use_yn='Y' ORDER BY area_cd",String.class);
        var result=mvc.perform(get("/api/areas/sidos")).andExpect(status().isOk()).andReturn().getResponse();
        var items=json.readTree(result.getContentAsString()).path("data").path("items");List<String> actual=new ArrayList<>();items.forEach(a->actual.add(a.path("code").asText()));
        assertThat(actual).containsExactlyElementsOf(expected);assertThat(service.getSidos().items()).anySatisfy(a->{assertThat(a.code()).isEqualTo("1200000000");assertThat(a.name()).isEqualTo("전남광주");});
        assertThat(sql.queryForObject("SELECT count(*) FROM tb_sy_adm_area",Long.class)).isEqualTo(5338);
        assertThat(sql.queryForList("SELECT area_lvl_cd,count(*) n FROM tb_sy_adm_area GROUP BY area_lvl_cd ORDER BY area_lvl_cd")).extracting(row->((Number)row.get("n")).longValue()).containsExactly(5067L,255L,16L);
    }
    @Test void childrenMatchExactParentLevelActivityAndCodeSort() throws Exception {
        area("0100000000","SIDO",null,null,"Y");area("0102000000","SGG","0100000000","나","Y");area("0101000000","SGG","0100000000","가","Y");area("0103000000","SGG","0100000000","중지","N");area("0101010000","EMD","0101000000","동","Y");
        mvc.perform(get("/api/areas/children").param("parentCode","0100000000").param("level","SGG")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(2)).andExpect(jsonPath("$.data.items[0].code").value("0101000000")).andExpect(jsonPath("$.data.items[1].code").value("0102000000"));
        assertThat(service.getChildren("0101000000","EMD").items()).extracting(AddressDtos.Area::code).containsExactly("0101010000");
    }
    @Test void originalSejongDirectEmdRestoresWithoutInventedSgg() throws Exception {
        String code=sql.queryForObject("SELECT area_cd FROM tb_sy_adm_area WHERE area_lvl_cd='EMD' AND upper_area_cd='3600000000' AND emd_nm='조치원읍'",String.class);
        mvc.perform(get("/api/areas/"+code+"/path")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.homeAddress").value("세종 조치원읍"));
        assertThat(service.resolve("3600000000",null,"SGG").homeAddress()).isEqualTo("세종");
    }
    @Test void stoppedOriginalPathCanBeRestoredButNotSelectedNew() {
        area("0100000000","SIDO",null,null,"Y");area("0101000000","SGG","0100000000","중지구","N");area("0101010000","EMD","0101000000","법정동","Y");
        assertThat(service.getPath("0101010000").items()).extracting(AddressDtos.Area::useYn).containsExactly("Y","N","Y");
        assertThat(service.resolve("0101010000","0101010000","EMD").homeAddress()).isEqualTo("시험 중지구 법정동");
        assertThatThrownBy(()->service.resolve("0101010000",null,"EMD")).isInstanceOf(ApiException.class);
    }
    @Test void oneHdongKeepsSeveralLegalAreas() {
        String hdong=sql.queryForObject("SELECT hdong_cd FROM tb_sy_adm_hdong GROUP BY hdong_cd HAVING count(*)>1 ORDER BY hdong_cd LIMIT 1",String.class);
        var before=sql.queryForList("SELECT * FROM tb_sy_adm_hdong WHERE hdong_cd=? ORDER BY area_cd",hdong);
        for(var row:before)service.getPath((String)row.get("area_cd"));
        assertThat(sql.queryForList("SELECT * FROM tb_sy_adm_hdong WHERE hdong_cd=? ORDER BY area_cd",hdong)).isEqualTo(before);
        assertThat(before.size()).isGreaterThan(1);
    }
    @Test void successAndInvalidQueriesNeverWriteBusinessHistoryOrAddressRows() throws Exception {
        Map<String,List<Map<String,Object>>> before=new LinkedHashMap<>();
        for(String table:List.of("tb_sy_adm_area","tb_sy_adm_hdong","tb_hr_dev","tb_hr_cand","tb_sy_chg_hist","tb_sy_accs_log"))before.put(table,sql.queryForList("SELECT * FROM "+table));
        for(String path:List.of("/api/areas/sidos","/api/areas/children?parentCode=3600000000&level=EMD","/api/areas/3600000000/path")) {
            mvc.perform(get(path)).andExpect(status().isOk());mvc.perform(get(path).param("actor","other")).andExpect(status().isBadRequest());
        }
        for(var entry:before.entrySet())assertThat(sql.queryForList("SELECT * FROM "+entry.getKey())).isEqualTo(entry.getValue());
    }
}
