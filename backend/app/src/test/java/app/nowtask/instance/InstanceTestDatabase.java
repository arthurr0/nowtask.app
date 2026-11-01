package app.nowtask.instance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

final class InstanceTestDatabase {

    static final String APP_PASSWORD = "test_app_password";

    private final PostgreSQLContainer<?> container;

    InstanceTestDatabase() {
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

    String jdbcUrl() {
        return container.getJdbcUrl();
    }

    Connection owner() throws SQLException {
        return DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    String scalar(String sql, Object... arguments) throws SQLException {
        try (Connection connection = owner();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    void execute(String sql, Object... arguments) throws SQLException {
        try (Connection connection = owner();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            statement.executeUpdate();
        }
    }

    UUID userId(String email) throws SQLException {
        return UUID.fromString(scalar("SELECT id::text FROM app_user WHERE email = ?", email));
    }

    UUID insertUser(String email, String state, boolean verified) throws SQLException {
        UUID id = UUID.randomUUID();
        execute("INSERT INTO app_user (id, name, short_name, initials, email, role, capacity, pending,"
                        + " email_verified_at, state) VALUES (?, ?, ?, 'T', ?, 'member', 0, false,"
                        + " CASE WHEN ? THEN now() END, ?)",
                id, email, email, email, verified, state);
        return id;
    }

    String createUser(String name) throws SQLException {
        String email = name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        insertUser(email, "active", true);
        return email;
    }

    UUID createOrganization(String slug) throws SQLException {
        return createOrganization(slug, "Org " + slug);
    }

    UUID createOrganization(String slug, String name) throws SQLException {
        UUID id = UUID.randomUUID();
        execute("INSERT INTO organization (id, name, slug) VALUES (?, ?, ?)", id, name, slug);
        return id;
    }

    UUID createRole(UUID organization) throws SQLException {
        UUID id = UUID.randomUUID();
        execute("INSERT INTO organization_role (id, organization_id, code, name, position, protected)"
                + " VALUES (?, ?, 'admin', 'Administrator', 0, TRUE)", id, organization);
        return id;
    }

    void addMember(UUID organization, UUID role, String email) throws SQLException {
        execute("INSERT INTO organization_member (id, organization_id, user_id, role_id, state)"
                        + " VALUES (?, ?, (SELECT id FROM app_user WHERE email = ?), ?, 'active')",
                UUID.randomUUID(), organization, email, role);
        execute("INSERT INTO workspace_settings (id, organization_id) VALUES (?, ?)",
                UUID.randomUUID(), organization);
    }

    String createApiKey(UUID organization, UUID role, String ownerEmail) throws Exception {
        String prefix = "nt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String token = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));

        execute("INSERT INTO api_key (id, organization_id, role_id, prefix, label, token_hash, scopes, owner_id)"
                        + " VALUES (?, ?, ?, ?, 'test key', ?, 'tasks:read', (SELECT id FROM app_user WHERE email = ?))",
                UUID.randomUUID(), organization, role, prefix, hash, ownerEmail);
        return token;
    }

    private static void bind(PreparedStatement statement, Object[] arguments) throws SQLException {
        for (int index = 0; index < arguments.length; index++) {
            statement.setObject(index + 1, arguments[index]);
        }
    }
}
