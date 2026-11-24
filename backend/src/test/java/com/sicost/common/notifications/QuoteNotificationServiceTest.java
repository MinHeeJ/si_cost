package com.sicost.common.notifications;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static com.sicost.common.notifications.NotificationPublicationMapper.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuoteNotificationServiceTest {
    final NotificationPublicationMapper mapper = mock(NotificationPublicationMapper.class);
    final NotificationPublisher publisher = mock(NotificationPublisher.class);
    final QuoteNotificationService service = new QuoteNotificationService(mapper, publisher);

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"단가가 원가보다 낮음"})
    void rejectionUsesActualOpportunityNameAndOptionalReason(String reason) {
        when(mapper.quoteRejection("O-3")).thenReturn(new QuoteRejection("sales", 7, "기회반려", reason));
        service.rejected("O-3");
        String content = "「기회반려」 견적이 반려됐습니다" + (reason == null || reason.isBlank() ? "" : ": " + reason);
        verify(publisher).publish(new Event(NotificationEventKeys.quoteRejected("O-3", 7), "QUOTE_REJ", content, new Link("UI-29", "O-3"), null),
            List.of(new NotificationRecipients.Recipient("sales", "sales", null, List.of())));
    }

    @Test void fullReasonStaysInHistoryWhileNotificationRespectsStorageLimit() {
        when(mapper.quoteRejection("O-3")).thenReturn(new QuoteRejection("sales", 7, "기회", "가".repeat(300)));
        service.rejected("O-3");
        verify(publisher).publish(argThat(event -> event.content().codePointCount(0, event.content().length()) == 300 && event.content().endsWith("…")), anyList());
    }

    @Test void quoteRejectionAliasesMatchConstructorAndMaterializeWithoutDatabase() throws Exception {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var properties = yaml.getObject();
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(Boolean.parseBoolean(properties.getProperty("mybatis.configuration.map-underscore-to-camel-case")));
        configuration.setArgNameBasedConstructorAutoMapping(Boolean.parseBoolean(properties.getProperty("mybatis.configuration.arg-name-based-constructor-auto-mapping")));
        var resource = new ClassPathResource("mappers/common/NotificationPublicationMapper.xml");
        try (var input = resource.getInputStream()) {
            new XMLMapperBuilder(input, configuration, resource.toString(), configuration.getSqlFragments()).parse();
        }
        var mapped = configuration.getMappedStatement(NotificationPublicationMapper.class.getName() + ".quoteRejection");
        var bound = mapped.getBoundSql("O-3");
        var aliases = Pattern.compile("AS \"([^\"]+)\"").matcher(bound.getSql()).results().map(m -> m.group(1)).toList();
        assertThat(aliases).containsExactlyElementsOf(Arrays.stream(QuoteRejection.class.getRecordComponents()).map(c -> c.getName()).toList());
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
            when(metadata.getColumnType(i + 1)).thenReturn(i == 1 ? Types.INTEGER : Types.VARCHAR);
            when(metadata.getColumnClassName(i + 1)).thenReturn(i == 1 ? Integer.class.getName() : String.class.getName());
        }
        when(result.getString("userId")).thenReturn("sales");
        when(result.getInt("historySn")).thenReturn(7);
        when(result.getString("opportunityName")).thenReturn("기회반려");
        when(result.getString("reason")).thenReturn("단가 확인");
        var handler = new DefaultResultSetHandler(null, mapped, null, null, bound, RowBounds.DEFAULT);
        assertThat(handler.handleResultSets(jdbc)).containsExactly(new QuoteRejection("sales", 7, "기회반려", "단가 확인"));
    }
}
