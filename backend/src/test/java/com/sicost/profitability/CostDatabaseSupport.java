package com.sicost.profitability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.*;
import com.sicost.common.persistence.PersistenceBoundary;
import com.sicost.developers.*;
import com.sicost.shared.*;
import java.time.Clock;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/** Runner-owned existing-data copy ONLY. No schema creation, Flyway, DML fixture or cleanup. */
public final class CostDatabaseSupport implements AutoCloseable {
    public final AnnotationConfigApplicationContext context;
    public final JdbcTemplate sql;
    public final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();
    private final TransactionTemplate reads;

    @Configuration
    @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }

    public CostDatabaseSupport() throws Exception { this(Clock.system(ZoneId.of("Asia/Seoul"))); }

    public CostDatabaseSupport(Clock clock) throws Exception {
        String url = required("SICOST_COST_TEST_DB_URL");
        String schema = required("SICOST_COST_TEST_DB_SCHEMA");
        // The dedicated runner connection opts into this read-only existing-data test.
        // Honor an explicit refusal, but do not require an extra, runner-unknown flag.
        String confirmed = System.getenv("SICOST_COST_TEST_BASELINE_CONFIRMED");
        if (confirmed != null && !"true".equals(confirmed))
            throw new IllegalStateException("독립 기존자료 시험 DB 확인이 거부되었습니다. 운영 DB를 지정하지 마세요.");
        if (!schema.matches("[a-z][a-z0-9_]{0,62}")) throw new IllegalArgumentException("시험 스키마 이름을 확인해 주세요.");
        if (url.equals(System.getenv("DB_URL")) || url.equals(System.getenv("SICOST_TEST_DB_URL")))
            throw new IllegalArgumentException("앱 DB 또는 재설치용 시험 DB 대신 독립 자료 사본을 지정하세요.");
        var source = new DriverManagerDataSource();
        source.setUrl(url);
        source.setUsername(required("SICOST_COST_TEST_DB_USER"));
        source.setPassword(required("SICOST_COST_TEST_DB_PASSWORD"));
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        properties.setProperty("options", "-c default_transaction_read_only=on -c TimeZone=Asia/Seoul");
        source.setConnectionProperties(properties);
        sql = new JdbcTemplate(source);
        if (!"on".equals(sql.queryForObject("SHOW default_transaction_read_only", String.class)))
            throw new IllegalStateException("조회 전용 연결이 필요합니다.");
        if (!schema.equals(sql.queryForObject("SELECT current_schema()", String.class)))
            throw new IllegalStateException("runner의 기존 시험 스키마가 없습니다. 자동 설치하지 않습니다.");
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
        var configuration = new org.apache.ibatis.session.Configuration();
        configuration.setArgNameBasedConstructorAutoMapping(true);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AggregateLockMapper.class);
        configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        configuration.addInterceptor(new PersistenceBoundary()); factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        var session = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> source);
        context.registerBean(ObjectMapper.class, () -> json);
        context.registerBean(Clock.class, () -> clock);
        context.registerBean(MonthlyCostMapper.class, () -> session.getMapper(MonthlyCostMapper.class));
        context.registerBean(DeveloperMapper.class, () -> session.getMapper(DeveloperMapper.class));
        context.registerBean(SharedQueriesMapper.class, () -> session.getMapper(SharedQueriesMapper.class));
        context.registerBean(CurrentUserMapper.class, () -> session.getMapper(CurrentUserMapper.class));
        context.registerBean(AggregateLockMapper.class, () -> session.getMapper(AggregateLockMapper.class));
        context.registerBean(DeveloperRevisionMapper.class, () -> session.getMapper(DeveloperRevisionMapper.class));
        context.register(Transactions.class, RevisionService.class, DeveloperAggregateRevision.class,
            CurrentUserProvider.class, DeveloperRepository.class, SharedQueriesService.class, MenuService.class,
            MonthlyCostRepository.class);
        context.refresh();
        reads = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        reads.setReadOnly(true); reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public <T> T read(Supplier<T> action) { return reads.execute(status -> action.get()); }

    /** Compare ALL rows/keys/nullable values/audit precision, not only aggregate totals. */
    public Map<String, List<String>> snapshot() {
        return read(() -> {
            var result = new LinkedHashMap<String, List<String>>();
            // Includes excluded billing/finance and initial standards, so accidental writes cannot hide.
            for (String table : List.of("tb_co_mon_cost", "tb_hr_mon_pay", "tb_pj_asgn", "tb_pj_asgn_mon", "tb_co_mon_pl",
                "tb_hr_dev", "tb_hr_dev_prc", "tb_hr_dev_stat", "tb_pj_proj", "tb_pj_proj_part", "tb_pj_tk_dstrb",
                "tb_pj_drct_exps", "tb_bs_clnt", "tb_bs_part", "tb_sy_oper_std", "tb_sy_code", "tb_cl_close",
                "tb_bl_bill", "tb_bl_rcpt", "tb_fi_item", "tb_fi_card", "tb_fi_card_pay", "tb_fi_card_mon",
                "tb_fi_xfer", "tb_fi_loan", "tb_fi_corp_tax", "tb_fi_tax_pay", "tb_co_prev_adj",
                "tb_sy_chg_hist", "tb_sy_accs_log", "tb_sy_user_pref")) {
                result.put(table, List.copyOf(sql.queryForList("SELECT row_to_json(t)::text FROM " + table + " t ORDER BY row_to_json(t)::text COLLATE \"C\"", String.class)));
            }
            return Collections.unmodifiableMap(result);
        });
    }

    public MonthlyCostDtos.Range existingRange() {
        String from = sql.queryForObject("SELECT min(month) FROM (SELECT cost_ym AS month FROM tb_co_mon_cost UNION ALL SELECT pay_ym FROM tb_hr_mon_pay UNION ALL SELECT input_ym FROM tb_pj_asgn_mon) t", String.class);
        String to = sql.queryForObject("SELECT max(month) FROM (SELECT cost_ym AS month FROM tb_co_mon_cost UNION ALL SELECT pay_ym FROM tb_hr_mon_pay UNION ALL SELECT input_ym FROM tb_pj_asgn_mon) t", String.class);
        if (from == null || to == null) throw new IllegalStateException("기존 원가·급여·월투입 원천 fixture가 필요합니다. 예제 행을 생성하지 않습니다.");
        return new MonthlyCostDtos.Range(from, to);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " 설정이 필요합니다.");
        return value;
    }
    @Override public void close() { context.close(); }
}
