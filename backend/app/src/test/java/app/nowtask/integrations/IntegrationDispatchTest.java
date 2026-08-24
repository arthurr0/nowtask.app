package app.nowtask.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import app.nowtask.shared.OrganizationContext;
import app.nowtask.shared.OrganizationContextHolder;
import app.nowtask.shared.events.TaskEvents;

@SpringBootTest
class IntegrationDispatchTest {

    private static final UUID ORG = UUID.fromString("cccccccc-0000-0000-0000-00000000000c");
    private static final UUID USER = UUID.fromString("33333333-0000-0000-0000-000000000003");
    private static final UUID PROJECT = UUID.fromString("44444444-0000-0000-0000-000000000004");
    private static final UUID STATUS = UUID.fromString("55555555-0000-0000-0000-000000000005");

    private static final String APP_PASSWORD = "test_app_password";
    private static PostgreSQLContainer<?> container;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> running = database();
        registry.add("spring.datasource.url", running::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "nowtask_app");
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.enabled", () -> false);
    }

    private static synchronized PostgreSQLContainer<?> database() {
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

    private static Connection asOwner() throws SQLException {
        PostgreSQLContainer<?> running = database();
        return java.sql.DriverManager.getConnection(
                running.getJdbcUrl(), running.getUsername(), running.getPassword());
    }

    @Autowired
    private ApplicationEventPublisher events;

    private HttpServer server;
    private final AtomicReference<String> received = new AtomicReference<>();

    @BeforeEach
    void listeningWebhook() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        seed("http://127.0.0.1:" + server.getAddress().getPort() + "/hook");
    }

    @AfterEach
    void stop() throws SQLException {
        server.stop(0);
        try (Connection owner = asOwner()) {
            owner.setAutoCommit(true);
            run(owner, "DELETE FROM integration_delivery WHERE organization_id = ?", ORG);
            run(owner, "DELETE FROM integration WHERE organization_id = ?", ORG);
            run(owner, "DELETE FROM task WHERE organization_id = ?", ORG);
        }
    }

    @Test
    void aTaskEventReachesTheWebhookOfTheOrganizationThatRaisedIt() {
        OrganizationContextHolder.runAs(
                new OrganizationContext(USER, ORG, null, null, Set.of()),
                () -> events.publishEvent(new TaskEvents.TaskCreated(
                        "MINING-1", "Kopanie rudy w kopalni", USER, Instant.now(), null)));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(received.get())
                .as("the webhook body")
                .contains("MINING-1"));
    }

    @Test
    void theDiscordEmbedCarriesTheTitleAssigneeAndPriorityOfTheTask() {
        OrganizationContextHolder.runAs(
                new OrganizationContext(USER, ORG, null, null, Set.of()),
                () -> events.publishEvent(new TaskEvents.TaskStatusChanged(
                        "MINING-1", "new", "canceled", "Reported", "Cancelled", USER, Instant.now(), null)));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(received.get())
                .as("the webhook body")
                .contains("MINING-1 · Kopanie rudy w kopalni")
                .contains("Reported → Cancelled")
                .contains("Osoba C")
                .contains("Wysoki"));
    }

    @Test
    void anEventRaisedWithoutAnOrganizationSendsNothing() {
        events.publishEvent(new TaskEvents.TaskCreated("NOW-2", "Bez organizacji", USER, Instant.now(), null));

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(received.get()).isNull());
    }

    private static void seed(String url) throws SQLException {
        try (Connection owner = asOwner()) {
            owner.setAutoCommit(true);
            run(owner, "INSERT INTO app_user (id, name, short_name, initials, email, role, capacity, pending)"
                    + " VALUES (?, 'Osoba C', 'Osoba C', 'C', 'c@example.test', 'admin', 0, false)"
                    + " ON CONFLICT (id) DO NOTHING", USER);
            run(owner, "INSERT INTO organization (id, name, slug) VALUES (?, 'Firma C', 'firma-c')"
                    + " ON CONFLICT (id) DO NOTHING", ORG);
            run(owner, "INSERT INTO project (id, organization_id, name, code, position)"
                    + " VALUES (?, ?, 'Kopalnia', 'MINING', 0) ON CONFLICT (id) DO NOTHING", PROJECT, ORG);
            run(owner, "INSERT INTO status_def (id, organization_id, project_id, code, label, category, position)"
                    + " VALUES (?, ?, ?, 'new', 'Reported', 'notStarted', 0) ON CONFLICT (id) DO NOTHING",
                    STATUS, ORG, PROJECT);
            run(owner, "INSERT INTO task (id, organization_id, project_id, task_key, title, status_id,"
                    + " priority, assignee_id) VALUES (?, ?, ?, 'MINING-1', 'Kopanie rudy w kopalni', ?,"
                    + " 'high', ?)",
                    UUID.randomUUID(), ORG, PROJECT, STATUS, USER);
            run(owner, "INSERT INTO integration (id, organization_id, kind, name, enabled, config)"
                    + " VALUES (?, ?, 'webhook', 'kanał zespołu', TRUE, ?::JSONB)",
                    UUID.randomUUID(), ORG, "{\"url\": \"" + url + "\", \"format\": \"discord\"}");
        }
    }

    private static void run(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < params.length; index++) {
                statement.setObject(index + 1, params[index]);
            }
            statement.execute();
        }
    }
}
