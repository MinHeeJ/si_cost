package com.sicost.candidates;

import java.sql.*;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static com.sicost.candidates.RecommendationActionDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecommendationMessageMappingTest {
    @ParameterizedTest @ValueSource(booleans={true,false})
    void aliasesAndConstructorMaterializeNamesIncludingNullablePreviousOpportunity(boolean moved) throws Exception {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var properties = yaml.getObject();
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(Boolean.parseBoolean(properties.getProperty("mybatis.configuration.map-underscore-to-camel-case")));
        configuration.setArgNameBasedConstructorAutoMapping(Boolean.parseBoolean(properties.getProperty("mybatis.configuration.arg-name-based-constructor-auto-mapping")));
        for (String path : new String[]{"mappers/salescommands/SalesCommandMapper.xml", "mappers/candidates/RecommendationActionMapper.xml"}) {
            var resource = new ClassPathResource(path);
            try (var input = resource.getInputStream()) {
                new XMLMapperBuilder(input, configuration, resource.toString(), configuration.getSqlFragments()).parse();
            }
        }
        var mapped = configuration.getMappedStatement(RecommendationActionMapper.class.getName() + ".messageNames");
        var bound = mapped.getBoundSql(new MessageContext("D-1", "BE", moved ? "O-1" : null));
        var aliases = Pattern.compile("AS \"([^\"]+)\"").matcher(bound.getSql()).results().map(m -> m.group(1)).toList();
        assertThat(aliases).containsExactlyElementsOf(Arrays.stream(MessageNames.class.getRecordComponents()).map(c -> c.getName()).toList());
        assertThat(mapped.getResultMaps().get(0).getConstructorResultMappings()).extracting(ResultMapping::getColumn).containsExactlyElementsOf(aliases);
        ResultSet result = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        Statement jdbc = mock(Statement.class);
        when(jdbc.getResultSet()).thenReturn(result);
        when(jdbc.getUpdateCount()).thenReturn(-1);
        when(result.getMetaData()).thenReturn(metadata);
        when(result.next()).thenReturn(true, false);
        when(metadata.getColumnCount()).thenReturn(aliases.size());
        for (int i = 0; i < aliases.size(); i++) {
            when(metadata.getColumnLabel(i + 1)).thenReturn(aliases.get(i));
            when(metadata.getColumnType(i + 1)).thenReturn(Types.VARCHAR);
            when(metadata.getColumnClassName(i + 1)).thenReturn(String.class.getName());
        }
        when(result.getString("developerName")).thenReturn("추천 인력");
        when(result.getString("jobName")).thenReturn("백엔드 개발");
        when(result.getString("previousOpportunityName")).thenReturn(moved ? "이전 기회" : null);
        var handler = new DefaultResultSetHandler(null, mapped, null, null, bound, RowBounds.DEFAULT);
        assertThat(handler.handleResultSets(jdbc)).containsExactly(new MessageNames("추천 인력", "백엔드 개발", moved ? "이전 기회" : null));
    }
}
