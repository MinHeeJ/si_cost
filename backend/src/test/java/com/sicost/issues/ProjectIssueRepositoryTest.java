package com.sicost.issues;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.ProjectProgressView;
import com.sicost.common.persistence.PersistenceBoundary;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import static com.sicost.issues.ProjectIssueDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectIssueRepositoryTest {
    final ProjectIssueMapper mapper=mock(ProjectIssueMapper.class);
    final ProjectIssueRepository repository=new ProjectIssueRepository(mapper,new StaticListableBeanFactory().getBeanProvider(ProjectProgressView.class));
    final Mutation mutation=new Mutation("P-owned",null,new Input(2026,"token","제목",LocalDate.of(2026,7,1),"HIGH","OPEN",null),"admin",LocalDateTime.of(2026,7,1,0,0));
    @Test void returnedGeneratedKeyIsUsedWithoutDetailReadback() {
        when(mapper.insert(anyMap())).thenAnswer(c->{Map<String,Object> values=c.getArgument(0);values.put("issueId",9999999999L);return 1;});
        assertThat(repository.insert(mutation)).isEqualTo(9999999999L);verify(mapper,never()).issues(anyString());verify(mapper,never()).project(anyString());
    }
    @Test void overflowingGeneratedKeyBecomesConflictWithoutTruncationOrRetry() {
        when(mapper.insert(anyMap())).thenAnswer(c->{Map<String,Object> values=c.getArgument(0);values.put("issueId",10000000000L);return 1;});
        assertThatThrownBy(()->repository.insert(mutation)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("ID_EXHAUSTED"));verify(mapper,times(1)).insert(anyMap());
    }
    @Test void postgresNumericOverflowBecomesExhaustionButOtherSqlErrorsAreNotReclassified() {
        doThrow(new IllegalStateException(new SQLException("numeric overflow","22003"))).when(mapper).insert(anyMap());
        assertThatThrownBy(()->repository.insert(mutation)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(409));
        doThrow(new IllegalStateException(new SQLException("connection failed","08006"))).when(mapper).insert(anyMap());
        assertThatThrownBy(()->repository.insert(mutation)).isInstanceOf(IllegalStateException.class);
    }
    @Test void realXmlStatementsBindRecordPropertiesAndPassSharedPersistenceBoundary() throws Exception {
        Configuration config=new Configuration();config.setArgNameBasedConstructorAutoMapping(true);
        String path="mappers/issues/ProjectIssueMapper.xml";
        try(var input=getClass().getClassLoader().getResourceAsStream(path)){assertThat(input).isNotNull();new XMLMapperBuilder(input,config,path,config.getSqlFragments()).parse();}
        var values=new HashMap<String,Object>();values.put("data",mutation);
        for(String method:List.of("insert","update")){
            var sql=config.getMappedStatement(ProjectIssueMapper.class.getName()+"."+method).getBoundSql(method.equals("insert")?values:mutation);
            assertThatCode(()->PersistenceBoundary.requireAllowed(sql.getSql())).doesNotThrowAnyException();
            for(var parameter:sql.getParameterMappings())assertThatCode(()->config.newMetaObject(method.equals("insert")?values:mutation).getValue(parameter.getProperty())).doesNotThrowAnyException();
        }
    }
}
