package app.nowtask.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
class GitHubWebhookTest {

    private static final UUID ORG = UUID.fromString("dddddddd-0000-0000-0000-00000000000d");
    private static final UUID USER = UUID.fromString("66666666-0000-0000-0000-000000000006");
    private static final UUID ROLE = UUID.fromString("77777777-0000-0000-0000-000000000007");
    private static final UUID PROJECT = UUID.fromString("88888888-0000-0000-0000-000000000008");
    private static final UUID TODO = UUID.fromString("99999999-0000-0000-0000-000000000009");
    private static final UUID DOING = UUID.fromString("aaaaaaa1-0000-0000-0000-00000000000a");
    private static final UUID REVIEW = UUID.fromString("aaaaaaa2-0000-0000-0000-00000000000b");
    private static final UUID CODER = UUID.fromString("bbbbbbb1-0000-0000-0000-00000000000c");
    private static final long CODER_GITHUB_ID = 90210;
    private static final UUID DOCS = UUID.fromString("ccccccc1-0000-0000-0000-00000000000d");
    private static final UUID DOCS_STATUS = UUID.fromString("ccccccc2-0000-0000-0000-00000000000e");

    private static final String SECRET = "webhook-secret";
    private static final long INSTALLATION = 4242;
    private static final String APP_PASSWORD = "test_app_password";

    private static PostgreSQLContainer<?> container;
    private static String privateKeyPem;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> running = database();
        registry.add("spring.datasource.url", running::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "nowtask_app");
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("nowtask.github.app-id", () -> "123456");
        registry.add("nowtask.github.slug", () -> "nowtask-test");
        registry.add("nowtask.github.webhook-secret", () -> SECRET);
        registry.add("nowtask.github.private-key", GitHubWebhookTest::key);
    }

    private static synchronized String key() {
        if (privateKeyPem == null) {
            try {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                PrivateKey generated = generator.generateKeyPair().getPrivate();
                privateKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(generated.getEncoded())
                        + "\n-----END PRIVATE KEY-----\n";
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return privateKeyPem;
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
    private GitHubWebhooks webhooks;

    @BeforeEach
    void seed() throws SQLException {
        try (Connection owner = asOwner()) {
            owner.setAutoCommit(true);
            run(owner, "INSERT INTO app_user (id, name, short_name, initials, email, role, capacity, pending)"
                    + " VALUES (?, 'Osoba D', 'Osoba D', 'D', 'd@example.test', 'admin', 0, false)"
                    + " ON CONFLICT (id) DO NOTHING", USER);
            run(owner, "INSERT INTO organization (id, name, slug) VALUES (?, 'Firma D', 'firma-d')"
                    + " ON CONFLICT (id) DO NOTHING", ORG);
            run(owner, "INSERT INTO organization_role (id, organization_id, code, name, position, protected)"
                    + " VALUES (?, ?, 'admin', 'Administrator', 0, TRUE) ON CONFLICT (id) DO NOTHING", ROLE, ORG);
            run(owner, "INSERT INTO organization_member (id, organization_id, user_id, role_id, state)"
                    + " VALUES (?, ?, ?, ?, 'active') ON CONFLICT DO NOTHING",
                    UUID.randomUUID(), ORG, USER, ROLE);
            run(owner, "INSERT INTO workspace_settings (id, organization_id, block_disallowed_drag)"
                    + " VALUES (?, ?, FALSE) ON CONFLICT (organization_id) DO UPDATE"
                    + " SET block_disallowed_drag = FALSE", UUID.randomUUID(), ORG);
            run(owner, "INSERT INTO project (id, organization_id, name, code, position)"
                    + " VALUES (?, ?, 'Kod', 'CODE', 0) ON CONFLICT (id) DO NOTHING", PROJECT, ORG);
            run(owner, "INSERT INTO status_def (id, organization_id, project_id, code, label, category, position)"
                    + " VALUES (?, ?, ?, 'todo', 'Do zrobienia', 'notStarted', 0)"
                    + " ON CONFLICT (id) DO NOTHING", TODO, ORG, PROJECT);
            run(owner, "INSERT INTO status_def (id, organization_id, project_id, code, label, category, position)"
                    + " VALUES (?, ?, ?, 'doing', 'W toku', 'inFlight', 1)"
                    + " ON CONFLICT (id) DO NOTHING", DOING, ORG, PROJECT);
            run(owner, "INSERT INTO status_def (id, organization_id, project_id, code, label, category, position)"
                    + " VALUES (?, ?, ?, 'review', 'W recenzji', 'inFlight', 2)"
                    + " ON CONFLICT (id) DO NOTHING", REVIEW, ORG, PROJECT);
            run(owner, "INSERT INTO app_user (id, name, short_name, initials, email, role, capacity, pending)"
                    + " VALUES (?, 'Osoba E', 'Osoba E', 'E', 'e@example.test', 'member', 0, false)"
                    + " ON CONFLICT (id) DO NOTHING", CODER);
            run(owner, "INSERT INTO organization_member (id, organization_id, user_id, role_id, state)"
                    + " VALUES (?, ?, ?, ?, 'active') ON CONFLICT DO NOTHING",
                    UUID.randomUUID(), ORG, CODER, ROLE);
            run(owner, "INSERT INTO github_account (user_id, github_user_id, login)"
                    + " VALUES (?, ?, 'ala-koduje') ON CONFLICT (user_id) DO NOTHING",
                    CODER, CODER_GITHUB_ID);
            run(owner, "INSERT INTO task (id, organization_id, project_id, task_key, title, status_id, priority)"
                    + " VALUES (?, ?, ?, 'CODE-1', 'Naprawić import', ?, 'medium')",
                    UUID.randomUUID(), ORG, PROJECT, TODO);
            run(owner, "INSERT INTO project (id, organization_id, name, code, position)"
                    + " VALUES (?, ?, 'Dokumentacja', 'DOCS', 1) ON CONFLICT (id) DO NOTHING", DOCS, ORG);
            run(owner, "INSERT INTO status_def (id, organization_id, project_id, code, label, category, position)"
                    + " VALUES (?, ?, ?, 'todo', 'Do zrobienia', 'notStarted', 0)"
                    + " ON CONFLICT (id) DO NOTHING", DOCS_STATUS, ORG, DOCS);
            run(owner, "INSERT INTO task (id, organization_id, project_id, task_key, title, status_id, priority)"
                    + " VALUES (?, ?, ?, 'DOCS-1', 'Opisać import', ?, 'medium')",
                    UUID.randomUUID(), ORG, DOCS, DOCS_STATUS);
            run(owner, "INSERT INTO github_installation (id, organization_id, installation_id, account_login,"
                    + " account_type, connected_by) VALUES (?, ?, ?, 'acme', 'Organization', ?)",
                    UUID.randomUUID(), ORG, INSTALLATION, USER);
            run(owner, "INSERT INTO integration (id, organization_id, kind, name, enabled, config)"
                    + " VALUES (?, ?, 'github', 'GitHub firmy', TRUE, ?::JSONB)",
                    UUID.randomUUID(), ORG,
                    "{\"repos\": [\"acme/backend\"], \"statusOnBranchPush\": \"doing\","
                            + " \"commentOnPush\": true, \"statusOnReviewApproved\": \"review\","
                            + " \"projects\": [\"CODE\"],"
                            + " \"statusOnWorkflowFailure\": \"todo\"}");
        }
    }

    @AfterEach
    void clean() throws SQLException {
        try (Connection owner = asOwner()) {
            owner.setAutoCommit(true);
            run(owner, "DELETE FROM github_delivery WHERE organization_id = ?", ORG);
            run(owner, "DELETE FROM github_link WHERE organization_id = ?", ORG);
            run(owner, "DELETE FROM github_installation WHERE organization_id = ?", ORG);
            run(owner, "DELETE FROM integration_delivery WHERE organization_id = ?", ORG);
            run(owner, "DELETE FROM integration WHERE organization_id = ?", ORG);
            run(owner, "DELETE FROM task_comment WHERE task_id IN"
                    + " (SELECT id FROM task WHERE organization_id = ?)", ORG);
            run(owner, "DELETE FROM task_history WHERE task_id IN"
                    + " (SELECT id FROM task WHERE organization_id = ?)", ORG);
            run(owner, "DELETE FROM task WHERE organization_id = ?", ORG);
            run(owner, "DELETE FROM github_account WHERE user_id = ?", CODER);
        }
    }

    @Test
    void aPushCommentsOnTheTaskItNamesAndMovesIt() {
        send("push", "d-1", push("CODE-1 repair the importer"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(comments()).as("comments on CODE-1")
                    .hasSize(1)
                    .first(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                    .contains("acme/backend@feature/CODE-1-import")
                    .contains("repair the importer");
            assertThat(status()).as("status of CODE-1").isEqualTo("doing");
        });
    }

    @Test
    void theSameDeliveryTwiceLeavesOnlyOneComment() {
        send("push", "d-2", push("CODE-1 repair the importer"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(comments()).hasSize(1));

        send("push", "d-2", push("CODE-1 repair the importer"));

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(6))
                .untilAsserted(() -> assertThat(comments()).as("comments after a redelivery").hasSize(1));
    }

    @Test
    void aPushNamingNoKnownTaskChangesNothing() {
        send("push", "d-3", push("CODE-404 a task that does not exist")
                .replace("feature/CODE-1-import", "feature/CODE-404-import"));

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(6))
                .untilAsserted(() -> assertThat(comments()).isEmpty());
    }

    @Test
    void aBodyWithABrokenSignatureNeverReachesTheTask() {
        String body = push("CODE-1 repair the importer");
        GitHubWebhooks.Reply reply = webhooks.receive(
                "push", "d-4", "sha256=deadbeef", body.getBytes(StandardCharsets.UTF_8), body);

        assertThat(reply.rejected()).isTrue();
        assertThat(reply.detail()).isEqualTo("The signature does not match");
        assertThat(comments()).isEmpty();
    }

    @Test
    void anUnknownInstallationIsIgnored() {
        String body = push("CODE-1 repair the importer").replace("\"id\": 4242", "\"id\": 999999");
        send("push", "d-5", body);

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(6))
                .untilAsserted(() -> assertThat(comments()).isEmpty());
    }

    @Test
    void aCommentFromALinkedGitHubAccountIsWrittenByThatPerson() {
        send("issue_comment", "d-6", """
                {
                  "action": "created",
                  "installation": {"id": 4242},
                  "repository": {"full_name": "acme/backend"},
                  "sender": {"login": "ala-koduje", "id": 90210, "type": "User"},
                  "issue": {"number": 12, "title": "CODE-1 the importer"},
                  "comment": {"body": "I am on it", "user": {"login": "ala-koduje", "type": "User"}}
                }
                """);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(comments()).hasSize(1);
            assertThat(commentAuthors()).as("who the comment belongs to").containsExactly(CODER);
        });
    }

    @Test
    void aCommentFromAStrangerFallsBackToTheConnectingAccount() {
        send("issue_comment", "d-7", """
                {
                  "action": "created",
                  "installation": {"id": 4242},
                  "repository": {"full_name": "acme/backend"},
                  "sender": {"login": "nieznajomy", "id": 555, "type": "User"},
                  "issue": {"number": 12, "title": "CODE-1 the importer"},
                  "comment": {"body": "drive by remark", "user": {"login": "nieznajomy", "type": "User"}}
                }
                """);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(comments()).hasSize(1);
            assertThat(commentAuthors()).containsExactly(USER);
        });
    }

    @Test
    void anApprovingReviewMovesTheTaskAndLeavesANote() {
        send("pull_request_review", "d-8", """
                {
                  "action": "submitted",
                  "installation": {"id": 4242},
                  "repository": {"full_name": "acme/backend"},
                  "sender": {"login": "ala-koduje", "id": 90210, "type": "User"},
                  "review": {"state": "approved", "body": "looks right",
                             "user": {"login": "ala-koduje"},
                             "html_url": "https://github.com/acme/backend/pull/7#r1"},
                  "pull_request": {"number": 7, "title": "CODE-1 rewrite the importer",
                                   "head": {"ref": "feature/CODE-1-import"}, "body": ""}
                }
                """);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(status()).as("status after an approving review").isEqualTo("review");
            assertThat(comments()).first(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                    .contains("approved").contains("looks right");
        });
    }

    @Test
    void aFailedWorkflowMovesTheTaskBack() {
        send("push", "d-9a", push("CODE-1 repair the importer"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(status()).isEqualTo("doing"));

        send("workflow_run", "d-9b", """
                {
                  "action": "completed",
                  "installation": {"id": 4242},
                  "repository": {"full_name": "acme/backend"},
                  "sender": {"login": "ala-koduje", "id": 90210, "type": "User"},
                  "workflow_run": {"id": 991, "name": "build", "conclusion": "failure",
                                   "head_branch": "feature/CODE-1-import", "display_title": "CODE-1",
                                   "html_url": "https://github.com/acme/backend/actions/runs/991",
                                   "actor": {"login": "ala-koduje"}, "head_commit": {"message": "CODE-1"}}
                }
                """);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(status()).as("status after a failed workflow").isEqualTo("todo");
            assertThat(links()).as("links on CODE-1").anyMatch(link -> link.startsWith("workflow"));
        });
    }

    @Test
    void aPushKeepsTheCommitOnTheTask() {
        send("push", "d-10", push("CODE-1 repair the importer"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(links()).anyMatch(link -> link.startsWith("commit | abcdef1234567890")));
    }

    @Test
    void aTaskFromAProjectTheIntegrationDoesNotCoverIsLeftAlone() {
        send("push", "d-11", push("DOCS-1 describe the importer")
                .replace("feature/CODE-1-import", "feature/DOCS-1-describe"));

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(6)).untilAsserted(() -> {
            assertThat(commentsOn("DOCS-1")).as("comments on a task outside the covered project").isEmpty();
            assertThat(comments()).isEmpty();
        });
    }

    private void send(String event, String delivery, String body) {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        GitHubWebhooks.Reply reply = webhooks.receive(event, delivery, "sha256=" + sign(raw), raw, body);
        assertThat(reply.rejected()).as("the reply to a signed %s", event).isFalse();
    }

    private static String push(String message) {
        return """
                {
                  "ref": "refs/heads/feature/CODE-1-import",
                  "installation": {"id": 4242},
                  "repository": {"full_name": "acme/backend"},
                  "commits": [
                    {"id": "abcdef1234567890", "message": "%s",
                     "url": "https://github.com/acme/backend/commit/abcdef",
                     "author": {"name": "Ala"}}
                  ]
                }
                """.formatted(message);
    }

    private static String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> comments() {
        return commentsOn("CODE-1");
    }

    private static List<String> commentsOn(String taskKey) {
        List<String> bodies = new ArrayList<>();
        try (Connection owner = asOwner();
                PreparedStatement statement = owner.prepareStatement(
                        "SELECT c.body FROM task_comment c JOIN task t ON t.id = c.task_id"
                                + " WHERE t.task_key = ? ORDER BY c.created_at")) {
            statement.setString(1, taskKey);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    bodies.add(rows.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return bodies;
    }

    private static List<UUID> commentAuthors() {
        List<UUID> authors = new ArrayList<>();
        try (Connection owner = asOwner();
                PreparedStatement statement = owner.prepareStatement(
                        "SELECT c.author_id FROM task_comment c JOIN task t ON t.id = c.task_id"
                                + " WHERE t.task_key = 'CODE-1' ORDER BY c.created_at");
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                authors.add(rows.getObject(1, UUID.class));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return authors;
    }

    private static List<String> links() {
        List<String> rows = new ArrayList<>();
        try (Connection owner = asOwner();
                PreparedStatement statement = owner.prepareStatement(
                        "SELECT kind || ' | ' || ref || ' | ' || state FROM github_link"
                                + " WHERE task_key = 'CODE-1' ORDER BY created_at");
                ResultSet found = statement.executeQuery()) {
            while (found.next()) {
                rows.add(found.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return rows;
    }

    private static String status() {
        try (Connection owner = asOwner();
                PreparedStatement statement = owner.prepareStatement(
                        "SELECT s.code FROM task t JOIN status_def s ON s.id = t.status_id"
                                + " WHERE t.task_key = 'CODE-1'");
                ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getString(1) : "";
        } catch (SQLException e) {
            throw new IllegalStateException(e);
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
