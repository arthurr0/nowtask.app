package app.nowtask.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.PostgreSqlJdbcIndexedSessionRepositoryCustomizer;

@Configuration
class SessionStoreConfig {

    @Bean
    PostgreSqlJdbcIndexedSessionRepositoryCustomizer sessionAttributeUpsert() {
        return new PostgreSqlJdbcIndexedSessionRepositoryCustomizer();
    }
}
