package com.sicost.common.installation;

import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.api.RequestIdFilter;
import com.sicost.common.health.HealthController;
import com.sicost.common.health.HealthMapper;
import com.sicost.common.health.HealthService;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="SICOST_TEST_DB_URL", matches=".+")
class HealthDatabaseTest {
    @Test void realMyBatisSelectReturns200AndClosedConnectionReturns503() throws Exception {
        PostgreSqlDatabase db = new PostgreSqlDatabase();
        Configuration config = new Configuration(new Environment("health",new JdbcTransactionFactory(),db.source));
        config.addMapper(HealthMapper.class);
        var factory = new SqlSessionFactoryBuilder().build(config);
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(HealthMapper.class);
            var mvc = MockMvcBuilders.standaloneSetup(new HealthController(new HealthService(mapper)))
                    .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestIdFilter()).build();
            mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap());
            session.getConnection().close();
            session.clearCache();
            mvc.perform(get("/api/health")).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("DB_UNAVAILABLE"))
                    .andExpect(jsonPath("$.requestId").isString()).andExpect(header().exists("X-Request-Id"));
        }
    }
}
