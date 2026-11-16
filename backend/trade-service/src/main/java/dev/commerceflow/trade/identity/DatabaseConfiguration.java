package dev.commerceflow.trade.identity;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@Profile("database")
@EnableTransactionManagement
public class DatabaseConfiguration {
    @Bean @ConfigurationProperties("spring.datasource")
    DataSourceProperties tradeDataSourceProperties() { return new DataSourceProperties(); }

    @Bean(destroyMethod = "close")
    HikariDataSource dataSource(DataSourceProperties properties) throws java.sql.SQLException {
        HikariDataSource source = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        source.setMaximumPoolSize(8);
        source.setConnectionTimeout(3000);
        source.setInitializationFailTimeout(5000);
        source.addDataSourceProperty("connectionTimeZone", "UTC");
        source.addDataSourceProperty("forceConnectionTimeZoneToSession", "true");
        try (var ignored = source.getConnection()) {
            // Fail the explicit database profile at startup on invalid credentials.
        } catch (java.sql.SQLException failure) {
            source.close();
            throw failure;
        }
        return source;
    }

    @Bean JdbcTemplate jdbcTemplate(DataSource source) {
        var jdbc = new JdbcTemplate(source);
        jdbc.queryForList("SELECT id, login_name, password_hash, role, enabled FROM app_user LIMIT 0");
        return jdbc;
    }
    @Bean PlatformTransactionManager transactionManager(DataSource source) {
        return new DataSourceTransactionManager(source);
    }
}
