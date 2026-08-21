package app.nowtask.tenancy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

final class TenantDatabase {

    static final String APP_PASSWORD = "test_app_password";

    private static PostgreSQLContainer<?> container;

    private TenantDatabase() {
    }

    static synchronized PostgreSQLContainer<?> start() {
        if (container == null) {
            container = new PostgreSQLContainer<>("postgres:18-alpine")
                    .withDatabaseName("nowtask")
                    .withUsername("owner")
                    .withPassword("owner");
            container.start();

            Flyway.configure()
                    .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                    .locations("classpath:db/migration")
                    .placeholders(Map.of("appDbPassword", APP_PASSWORD))
                    .load()
                    .migrate();
        }
        return container;
    }

    static Connection asOwner() throws SQLException {
        PostgreSQLContainer<?> running = start();
        return DriverManager.getConnection(
                running.getJdbcUrl(), running.getUsername(), running.getPassword());
    }

    static Connection asApplication() throws SQLException {
        PostgreSQLContainer<?> running = start();
        return DriverManager.getConnection(running.getJdbcUrl(), "nowtask_app", APP_PASSWORD);
    }

    static void enter(Connection connection, String userId, String organizationId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT set_config('app.user_id', ?, false), set_config('app.organization_id', ?, false)")) {
            statement.setString(1, userId == null ? "" : userId);
            statement.setString(2, organizationId == null ? "" : organizationId);
            statement.execute();
        }
    }
}
