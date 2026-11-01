package app.nowtask.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import app.nowtask.identity.InstanceAdminGrants;
import app.nowtask.identity.api.Announcements;

@SpringBootTest
class InstanceAdminTest {

    private static final String APP_PASSWORD = "test_app_password";
    private static final String ENV_ADMIN = "env-admin@example.test";
    private static final String LATE_ADMIN = "late-admin@example.test";

    private static PostgreSQLContainer<?> container;

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> running = database();
        registry.add("spring.datasource.url", running::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "nowtask_app");
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("nowtask.instance.admins", () -> "Env-Admin@Example.test, " + LATE_ADMIN);
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

            try (Connection owner = asOwner()) {
                insertUser(owner, UUID.randomUUID(), ENV_ADMIN, "active");
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }
        return container;
    }

    private static Connection asOwner() throws SQLException {
        PostgreSQLContainer<?> running = database();
        return java.sql.DriverManager.getConnection(
                running.getJdbcUrl(), running.getUsername(), running.getPassword());
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private Announcements announcements;

    @Autowired
    private InstanceAdminGrants grants;

    private MockMvc mvc;

    @BeforeEach
    void reset() throws SQLException {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        try (Connection owner = asOwner()) {
            execute(owner, "DELETE FROM instance_audit_event");
            execute(owner, "UPDATE instance_settings SET signup_mode = NULL, announcement_text = NULL,"
                    + " announcement_level = NULL, announcement_until = NULL, updated_by = NULL");
            execute(owner, "UPDATE app_user SET instance_admin = FALSE WHERE email <> ?", ENV_ADMIN);
            execute(owner, "UPDATE app_user SET state = 'active' WHERE email = ?", ENV_ADMIN);
        }
    }

    @Test
    void theConfiguredAddressIsGrantedAtStartup() throws SQLException {
        assertThat(grants).isInstanceOf(ApplicationRunner.class);
        assertThat(scalar("SELECT instance_admin::text FROM app_user WHERE email = ?", ENV_ADMIN)).isEqualTo("true");
    }

    @Test
    void anAccountRegisteredLaterWithAListedAddressIsGranted() throws Exception {
        mvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Late Admin\",\"email\":\"" + LATE_ADMIN
                                + "\",\"password\":\"a-long-enough-password\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.instanceAdmin").value(true));

        assertThat(scalar("SELECT instance_admin::text FROM app_user WHERE email = ?", LATE_ADMIN))
                .isEqualTo("true");
    }

    @Test
    void theCurrentUserCarriesTheFlag() throws Exception {
        String regular = createUser("flag-regular");

        mvc.perform(get("/api/auth/me").with(user(ENV_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceAdmin").value(true));
        mvc.perform(get("/api/auth/me").with(user(regular)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceAdmin").value(false));
    }

    @Test
    void anonymousIsRejectedAndAnOrdinaryUserGets403() throws Exception {
        String regular = createUser("plain");

        mvc.perform(get("/api/instance/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/instance/overview").with(user(regular))).andExpect(status().isForbidden());
        mvc.perform(get("/api/instance/users").with(user(regular))).andExpect(status().isForbidden());
        mvc.perform(post("/api/instance/mail/test").with(user(regular)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anApiKeyGets403EvenWhenItsOwnerIsAnInstanceAdmin() throws Exception {
        UUID organization = createOrganization("key-org");
        UUID role = createRole(organization);
        String token = createApiKey(organization, role, ENV_ADMIN);

        mvc.perform(get("/api/instance/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/instance/organizations/" + organization + "/suspend")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        assertThat(scalar("SELECT state FROM organization WHERE id = ?", organization)).isEqualTo("active");
    }

    @Test
    void anAdminWithoutAnyOrganizationReadsTheOverview() throws Exception {
        assertThat(scalar("SELECT count(*)::text FROM organization_member m JOIN app_user u ON u.id = m.user_id"
                + " WHERE u.email = ?", ENV_ADMIN)).isEqualTo("0");

        mvc.perform(get("/api/instance/overview").with(user(ENV_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").isNotEmpty())
                .andExpect(jsonPath("$.schemaVersion").value("81"))
                .andExpect(jsonPath("$.counts.users").isNumber())
                .andExpect(jsonPath("$.counts.instanceAdmins").value(1))
                .andExpect(jsonPath("$.signupMode.effective").value("open"))
                .andExpect(jsonPath("$.signupMode.source").value("env"))
                .andExpect(jsonPath("$.health.database").value("up"))
                .andExpect(jsonPath("$.health.mail").value("not_configured"))
                .andExpect(jsonPath("$.health.github").value("not_configured"));

        mvc.perform(get("/api/instance/users").with(user(ENV_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void suspendAndRestoreChangeTheStateAndWriteTheAudit() throws Exception {
        UUID organization = createOrganization("suspend-me");

        mvc.perform(post("/api/instance/organizations/" + organization + "/suspend")
                        .with(user(ENV_ADMIN))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(scalar("SELECT state FROM organization WHERE id = ?", organization)).isEqualTo("suspended");
        assertThat(scalar("SELECT (suspended_at IS NOT NULL)::text FROM organization WHERE id = ?", organization))
                .isEqualTo("true");

        mvc.perform(get("/api/instance/organizations?state=suspended&q=suspend-me").with(user(ENV_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("suspend-me"))
                .andExpect(jsonPath("$.items[0].state").value("suspended"));

        mvc.perform(post("/api/instance/organizations/" + organization + "/restore")
                        .with(user(ENV_ADMIN))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(scalar("SELECT state FROM organization WHERE id = ?", organization)).isEqualTo("active");

        assertThat(scalar("SELECT count(*)::text FROM instance_audit_event WHERE action = 'organization.suspend'"
                + " AND target_id = ? AND actor_id = (SELECT id FROM app_user WHERE email = ?)",
                organization.toString(), ENV_ADMIN)).isEqualTo("1");
        assertThat(scalar("SELECT count(*)::text FROM instance_audit_event WHERE action = 'organization.restore'"
                + " AND target_id = ?", organization.toString())).isEqualTo("1");

        mvc.perform(get("/api/instance/audit").with(user(ENV_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].action").value("organization.restore"))
                .andExpect(jsonPath("$.items[0].actor.name").isNotEmpty())
                .andExpect(jsonPath("$.items[0].targetType").value("organization"));
    }

    @Test
    void deletingNeedsTheMatchingSlug() throws Exception {
        UUID organization = createOrganization("delete-me");

        mvc.perform(delete("/api/instance/organizations/" + organization)
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmSlug\":\"something-else\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SLUG_MISMATCH"));

        assertThat(scalar("SELECT state FROM organization WHERE id = ?", organization)).isEqualTo("active");

        mvc.perform(delete("/api/instance/organizations/" + organization)
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmSlug\":\"delete-me\"}"))
                .andExpect(status().isNoContent());

        assertThat(scalar("SELECT state FROM organization WHERE id = ?", organization)).isEqualTo("deleted");
        assertThat(scalar("SELECT count(*)::text FROM instance_audit_event WHERE action = 'organization.delete'"
                + " AND target_id = ?", organization.toString())).isEqualTo("1");

        mvc.perform(get("/api/instance/organizations?q=delete-me").with(user(ENV_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void anUnknownOrganizationIs404() throws Exception {
        mvc.perform(post("/api/instance/organizations/" + UUID.randomUUID() + "/suspend")
                        .with(user(ENV_ADMIN))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void disablingAUserClosesTheirSessions() throws Exception {
        String victim = createUser("victim");
        UUID victimId = UUID.fromString(scalar("SELECT id::text FROM app_user WHERE email = ?", victim));

        try (Connection owner = asOwner()) {
            execute(owner, "INSERT INTO user_session (id, user_id, session_key) VALUES (?, ?, ?)",
                    UUID.randomUUID(), victimId, "key-" + victimId);
            execute(owner, "INSERT INTO spring_session (primary_id, session_id, creation_time, last_access_time,"
                    + " max_inactive_interval, expiry_time, principal_name) VALUES (?, ?, 0, 0, 100, 9999999999999, ?)",
                    UUID.randomUUID().toString(), UUID.randomUUID().toString(), victim);
        }

        mvc.perform(post("/api/instance/users/" + victimId + "/disable").with(user(ENV_ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(scalar("SELECT state FROM app_user WHERE id = ?", victimId)).isEqualTo("disabled");
        assertThat(scalar("SELECT (revoked_at IS NOT NULL)::text FROM user_session WHERE user_id = ?", victimId))
                .isEqualTo("true");
        assertThat(scalar("SELECT count(*)::text FROM spring_session WHERE principal_name = ?", victim))
                .isEqualTo("0");
        assertThat(scalar("SELECT count(*)::text FROM instance_audit_event WHERE action = 'user.disable'"
                + " AND target_id = ?", victimId.toString())).isEqualTo("1");

        mvc.perform(get("/api/instance/users?state=disabled&q=victim").with(user(ENV_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].state").value("disabled"))
                .andExpect(jsonPath("$.items[0].email").value(victim));

        mvc.perform(post("/api/instance/users/" + victimId + "/enable").with(user(ENV_ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(scalar("SELECT state FROM app_user WHERE id = ?", victimId)).isEqualTo("active");

        mvc.perform(post("/api/instance/users/" + victimId + "/sessions/revoke")
                        .with(user(ENV_ADMIN))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void anAdminCannotDisableTheirOwnAccount() throws Exception {
        UUID self = UUID.fromString(scalar("SELECT id::text FROM app_user WHERE email = ?", ENV_ADMIN));

        mvc.perform(post("/api/instance/users/" + self + "/disable").with(user(ENV_ADMIN)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION"));

        assertThat(scalar("SELECT state FROM app_user WHERE id = ?", self)).isEqualTo("active");
    }

    @Test
    void theLastInstanceAdminCannotBeRevoked() throws Exception {
        UUID self = UUID.fromString(scalar("SELECT id::text FROM app_user WHERE email = ?", ENV_ADMIN));

        mvc.perform(patch("/api/instance/users/" + self)
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instanceAdmin\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_INSTANCE_ADMIN"));

        assertThat(scalar("SELECT instance_admin::text FROM app_user WHERE id = ?", self)).isEqualTo("true");
    }

    @Test
    void grantingAndRevokingAnotherAdminWorksButNotOnYourself() throws Exception {
        String colleague = createUser("colleague");
        UUID colleagueId = UUID.fromString(scalar("SELECT id::text FROM app_user WHERE email = ?", colleague));
        UUID self = UUID.fromString(scalar("SELECT id::text FROM app_user WHERE email = ?", ENV_ADMIN));

        mvc.perform(patch("/api/instance/users/" + colleagueId)
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instanceAdmin\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceAdmin").value(true))
                .andExpect(jsonPath("$.email").value(colleague));

        mvc.perform(patch("/api/instance/users/" + self)
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instanceAdmin\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION"));

        mvc.perform(patch("/api/instance/users/" + colleagueId)
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instanceAdmin\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceAdmin").value(false));

        assertThat(scalar("SELECT count(*)::text FROM instance_audit_event WHERE action IN"
                + " ('user.instance_admin_grant', 'user.instance_admin_revoke')")).isEqualTo("2");
    }

    @Test
    void theSignupModeOverrideDecidesWhoCanRegister() throws Exception {
        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupMode\":\"invite-only\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signupMode").value("invite-only"))
                .andExpect(jsonPath("$.effectiveSignupMode").value("invite-only"))
                .andExpect(jsonPath("$.envSignupMode").value("open"))
                .andExpect(jsonPath("$.updatedBy.name").isNotEmpty());

        mvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Closed Door\",\"email\":\"closed-door@example.test\","
                                + "\"password\":\"a-long-enough-password\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SIGNUP_INVITE_ONLY"));

        mvc.perform(get("/api/instance/overview").with(user(ENV_ADMIN)))
                .andExpect(jsonPath("$.signupMode.effective").value("invite-only"))
                .andExpect(jsonPath("$.signupMode.source").value("settings"));

        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupMode\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signupMode").isEmpty())
                .andExpect(jsonPath("$.effectiveSignupMode").value("open"));

        mvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Open Door\",\"email\":\"open-door@example.test\","
                                + "\"password\":\"a-long-enough-password\"}"))
                .andExpect(status().isCreated());

        assertThat(scalar("SELECT count(*)::text FROM instance_audit_event WHERE action = 'settings.update'"))
                .isEqualTo("2");
    }

    @Test
    void anInvalidSignupModeIsRejected() throws Exception {
        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signupMode\":\"whenever\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theAnnouncementIsActiveUntilItExpires() throws Exception {
        assertThat(announcements.active()).isNull();

        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"announcement\":{\"text\":\"Maintenance tonight\",\"level\":\"warning\","
                                + "\"until\":\"" + Instant.now().plusSeconds(3600) + "\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcement.text").value("Maintenance tonight"))
                .andExpect(jsonPath("$.announcement.level").value("warning"));

        assertThat(announcements.active()).isNotNull();
        assertThat(announcements.active().text()).isEqualTo("Maintenance tonight");

        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"announcement\":{\"text\":\"Old news\",\"level\":\"info\","
                                + "\"until\":\"" + Instant.now().minusSeconds(60) + "\"}}"))
                .andExpect(status().isOk());

        assertThat(announcements.active()).isNull();

        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"announcement\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcement").isEmpty());
    }

    @Test
    void anAnnouncementLongerThan500CharactersIs422() throws Exception {
        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"announcement\":{\"text\":\"" + "x".repeat(501) + "\",\"level\":\"info\"}}"))
                .andExpect(status().isUnprocessableContent());

        mvc.perform(patch("/api/instance/settings")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"announcement\":{\"text\":\"" + "x".repeat(500) + "\",\"level\":\"info\"}}"))
                .andExpect(status().isOk());
    }

    @Test
    void theMailTestAnswers503WhenMailIsNotConfigured() throws Exception {
        mvc.perform(post("/api/instance/mail/test")
                        .with(user(ENV_ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MAIL_NOT_CONFIGURED"));
    }

    private static String createUser(String name) throws SQLException {
        String email = name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
        try (Connection owner = asOwner()) {
            insertUser(owner, UUID.randomUUID(), email, "active");
        }
        return email;
    }

    private static void insertUser(Connection owner, UUID id, String email, String state) throws SQLException {
        execute(owner, "INSERT INTO app_user (id, name, short_name, initials, email, role, capacity, pending,"
                + " email_verified_at, state) VALUES (?, ?, ?, 'T', ?, 'member', 0, false, now(), ?)",
                id, email, email, email, state);
    }

    private static UUID createOrganization(String slug) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection owner = asOwner()) {
            execute(owner, "INSERT INTO organization (id, name, slug) VALUES (?, ?, ?)", id, "Org " + slug, slug);
        }
        return id;
    }

    private static UUID createRole(UUID organization) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection owner = asOwner()) {
            execute(owner, "INSERT INTO organization_role (id, organization_id, code, name, position, protected)"
                    + " VALUES (?, ?, 'admin', 'Administrator', 0, TRUE)", id, organization);
        }
        return id;
    }

    private static String createApiKey(UUID organization, UUID role, String ownerEmail) throws Exception {
        String prefix = "nt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String token = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));

        try (Connection owner = asOwner()) {
            execute(owner, "INSERT INTO api_key (id, organization_id, role_id, prefix, label, token_hash, scopes,"
                    + " owner_id) VALUES (?, ?, ?, ?, 'test key', ?, 'tasks:read',"
                    + " (SELECT id FROM app_user WHERE email = ?))",
                    UUID.randomUUID(), organization, role, prefix, hash, ownerEmail);
        }
        return token;
    }

    private static String scalar(String sql, Object... arguments) throws SQLException {
        try (Connection owner = asOwner();
                PreparedStatement statement = owner.prepareStatement(sql)) {
            bind(statement, arguments);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    private static void execute(Connection connection, String sql, Object... arguments) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object[] arguments) throws SQLException {
        for (int index = 0; index < arguments.length; index++) {
            statement.setObject(index + 1, arguments[index]);
        }
    }
}
