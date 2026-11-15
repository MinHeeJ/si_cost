package com.sicost.notificationsops;

import com.sicost.common.persistence.PersistenceBoundary;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static com.sicost.notificationsops.NotificationDtos.*;
import static org.assertj.core.api.Assertions.*;

class NotificationMapperTest {
    @Test void emittedReadCommandsPassFoundationPersistencePolicy() throws Exception {
        var configuration=new Configuration();
        String resource="mappers/notificationsops/NotificationMapper.xml";
        try(InputStream stream=getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            new XMLMapperBuilder(stream,configuration,resource,configuration.getSqlFragments()).parse();
        }
        var now=LocalDateTime.of(2026,7,15,0,0);
        var one=configuration.getMappedStatement(NotificationMapper.class.getName()+".markRead").getBoundSql(new ReadWrite("receiver",BigDecimal.ONE,now,now));
        var all=configuration.getMappedStatement(NotificationMapper.class.getName()+".markAllRead").getBoundSql(new ReadAllWrite("receiver",now));
        assertThatCode(()->PersistenceBoundary.requireAllowed(one.getSql())).doesNotThrowAnyException();
        assertThatCode(()->PersistenceBoundary.requireAllowed(all.getSql())).doesNotThrowAnyException();
        assertThat(one.getParameterMappings()).extracting(p->p.getProperty()).containsExactly("readAt","userId","modifiedAt","userId","id");
        assertThat(all.getParameterMappings()).extracting(p->p.getProperty()).containsExactly("cutoffAt","userId","cutoffAt","userId","cutoffAt");
    }
}
