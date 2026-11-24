package com.sicost.permission;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AssignmentPeopleMappingTest {
    @Test
    void selectedAliasesMatchExplicitConstructorAndMaterializeEveryFieldWithoutDatabase() throws Exception {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var properties = yaml.getObject();
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(Boolean.parseBoolean(
            properties.getProperty("mybatis.configuration.map-underscore-to-camel-case")));
        configuration.setArgNameBasedConstructorAutoMapping(Boolean.parseBoolean(
            properties.getProperty("mybatis.configuration.arg-name-based-constructor-auto-mapping")));
        configuration.addMapper(PermissionMapper.class);
        var statement = configuration.getMappedStatement(PermissionMapper.class.getName() + ".assignmentPeople");
        var bound = statement.getBoundSql("검색");
        var matcher = Pattern.compile("AS \"([^\"]+)\"").matcher(bound.getSql());
        List<String> aliases = matcher.results().map(result -> result.group(1)).toList();
        var components = Arrays.stream(PermissionMapper.PersonOption.class.getRecordComponents())
            .map(component -> component.getName()).toList();
        assertThat(aliases).containsExactlyElementsOf(components);
        assertThat(statement.getResultMaps().get(0).getConstructorResultMappings())
            .extracting(ResultMapping::getColumn).containsExactlyElementsOf(aliases);
        assertThat(bound.getSql()).contains("max(a.input_end_dt)", "a.asgn_stat_cd='ACT'",
            "a.input_bgn_dt <=", "a.input_end_dt >=", "AT TIME ZONE 'Asia/Seoul'");

        ResultSet result = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        Statement jdbc = mock(Statement.class);
        when(jdbc.getResultSet()).thenReturn(result);
        when(jdbc.getUpdateCount()).thenReturn(-1);
        when(result.getMetaData()).thenReturn(metadata);
        when(result.next()).thenReturn(true, false);
        when(metadata.getColumnCount()).thenReturn(aliases.size());
        for (int index = 0; index < aliases.size(); index++) {
            when(metadata.getColumnLabel(index + 1)).thenReturn(aliases.get(index));
            when(metadata.getColumnType(index + 1)).thenReturn(index == 6 ? Types.DATE : Types.VARCHAR);
            when(metadata.getColumnClassName(index + 1)).thenReturn(
                index == 6 ? LocalDate.class.getName() : String.class.getName());
        }
        when(result.getString("id")).thenReturn("D-mapping");
        when(result.getString("name")).thenReturn("매핑 검증자");
        when(result.getString("managementNo")).thenReturn("M-mapping");
        when(result.getString("jobCode")).thenReturn("DEV");
        when(result.getString("gradeCode")).thenReturn("MD");
        when(result.getString("status")).thenReturn("WTHDR");
        when(result.getObject("withdrawalDate", LocalDate.class)).thenReturn(LocalDate.of(2027, 2, 28));
        var handler = new DefaultResultSetHandler(null, statement, null, null, bound, RowBounds.DEFAULT);
        assertThat(handler.handleResultSets(jdbc)).containsExactly(new PermissionMapper.PersonOption(
            "D-mapping", "매핑 검증자", "M-mapping", "DEV", "MD", "WTHDR", LocalDate.of(2027, 2, 28)));
    }
}
